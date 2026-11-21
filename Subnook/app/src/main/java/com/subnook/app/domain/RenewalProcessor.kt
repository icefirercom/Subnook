package com.subnook.app.domain

import com.subnook.app.data.PaymentRecord
import com.subnook.app.data.SettingsRepository
import com.subnook.app.data.Subscription
import com.subnook.app.data.SubscriptionDao
import com.subnook.app.notify.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 每日处理：
 * 1. 自动续费的订阅，账单日已过 -> 记一笔付款并滚动到下一个账单日
 * 2. 手动付款的订阅，保持不动，等待用户确认
 * 3. 发送"即将扣款/逾期未付"通知
 */
class RenewalProcessor(
    private val dao: SubscriptionDao,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()

    suspend fun run(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        val today = now.toLocalDate()
        val s = settings.get()
        val notifyTimeReached = now.toLocalTime() >= LocalTime.of(s.notifyHour, s.notifyMinute)
        for (orig in dao.getAll().sortedWith(Billing.pendingComparator)) {
            if (!orig.active) continue
            var sub = Installments.normalize(rollAutoRenew(orig, today))
            // 分期还完最后一期会被停用，不再提醒
            if (sub.active) sub = remind(sub, today, notifyTimeReached)
            if (sub != orig) dao.upsert(sub)
        }
    }

    private suspend fun rollAutoRenew(sub: Subscription, today: LocalDate): Subscription {
        if (sub.isOneTime || !sub.autoRenew) return sub
        var next = LocalDate.ofEpochDay(sub.nextBillingDay)
        var guard = 0
        // 分期还完最后一期就停，不再往后记账
        val last = if (sub.isInstallment) Installments.endDate(sub) else LocalDate.MAX
        var balance = sub.balance
        while (next < today && next <= last && guard++ < 1000) {
            val amount = Billing.amountOn(sub, next)
            // 余额付款：余额不够就扣不了款，停在这一期（变成逾期，提醒充值后手动确认），不假装已付
            if (Balance.usesBalance(sub) && !Balance.canPay(sub, amount, balance)) break
            val used = Balance.deduction(sub, amount, balance)
            dao.insertPayment(
                PaymentRecord(
                    subscriptionId = sub.id, billingDay = next.toEpochDay(), amount = amount, currency = sub.currency,
                    auto = true, term = termOf(sub, next), balanceUsed = used,
                ),
            )
            balance = com.subnook.app.data.roundMoney(balance - used)
            next = Billing.next(sub, next)
        }
        return if (next.toEpochDay() == sub.nextBillingDay) sub else sub.copy(nextBillingDay = next.toEpochDay(), balance = balance)
    }

    /** 自动扣款的余额付款，因为余额不足卡在了已经过去的账单日 */
    private fun stuckOnBalance(sub: Subscription, today: LocalDate): Boolean =
        sub.autoRenew && Balance.usesBalance(sub) && LocalDate.ofEpochDay(sub.nextBillingDay) < today

    private fun remind(sub: Subscription, today: LocalDate, timeReached: Boolean): Subscription {
        var out = sub
        val due = LocalDate.ofEpochDay(sub.nextBillingDay)
        if (shouldRemind(sub, today, timeReached)) {
            notifier.upcoming(sub, today)
            out = out.copy(remindedForDay = today.toEpochDay())
        }
        // 手动付款逾期 / 余额不足没扣成：每天提醒一次
        if ((!sub.autoRenew || stuckOnBalance(sub, today)) && sub.remindEnabled && due < today && timeReached &&
            sub.overdueNotifiedDay != today.toEpochDay()
        ) {
            notifier.overdue(sub, today)
            out = out.copy(overdueNotifiedDay = today.toEpochDay())
        }
        return out
    }

    companion object {
        /** 从账单日前 N 天开始，到账单日当天，每天到点提醒一次（remindedForDay 记录最后一次提醒的日期） */
        fun shouldRemind(sub: Subscription, today: LocalDate, timeReached: Boolean): Boolean {
            if (!sub.active || !sub.remindEnabled || !timeReached) return false
            if (sub.remindedForDay == today.toEpochDay()) return false
            val due = LocalDate.ofEpochDay(sub.nextBillingDay)
            return today >= due.minusDays(sub.remindDaysBefore.toLong()) && today <= due
        }
    }

    /**
     * 用户确认付款：记账并推进一个周期。
     * 余额付款会从余额里扣；余额不够时扣到 0，返回还差的钱（用户确认已付，说明差额用别的方式付了）。
     */
    suspend fun confirmPaid(id: Long): Double = mutex.withLock {
        val sub = dao.get(id) ?: return@withLock 0.0
        if (Installments.isFinished(sub)) return@withLock 0.0
        val due = LocalDate.ofEpochDay(sub.nextBillingDay)
        val amount = Billing.amountOn(sub, due)
        val used = Balance.deduction(sub, amount)
        if (!sub.isOneTime || amount > 0.0) {
            dao.insertPayment(
                PaymentRecord(
                    subscriptionId = sub.id, billingDay = sub.nextBillingDay, amount = amount, currency = sub.currency,
                    auto = false, term = termOf(sub, due), balanceUsed = used,
                ),
            )
        }
        val updated = if (sub.isOneTime) {
            // 非周期订阅到期确认后直接归档停用，不再推进周期
            sub.copy(active = false, overdueNotifiedDay = null, balance = com.subnook.app.data.roundMoney(sub.balance - used))
        } else {
            val next = Billing.next(sub)
            Installments.normalize(
                sub.copy(nextBillingDay = next.toEpochDay(), overdueNotifiedDay = null, balance = com.subnook.app.data.roundMoney(sub.balance - used)),
            )
        }
        dao.upsert(updated)
        notifier.cancel(sub.id)
        if (Balance.usesBalance(sub)) com.subnook.app.data.roundMoney(amount - used) else 0.0
    }

    // ---- 余额：和每日处理用同一把锁，避免后台扣款和充值同时改余额时互相覆盖 ----

    suspend fun topUp(subId: Long, amount: Double, day: LocalDate = LocalDate.now()) = mutex.withLock {
        val sub = dao.get(subId) ?: return@withLock
        dao.topUp(com.subnook.app.data.TopUp(subscriptionId = sub.id, day = day.toEpochDay(), amount = amount, currency = sub.currency))
    }

    suspend fun deleteTopUp(t: com.subnook.app.data.TopUp) = mutex.withLock { dao.deleteTopUpWithRevert(t) }

    /** 删除付款记录：余额付款的钱退回余额 */
    suspend fun deletePayment(p: PaymentRecord) = mutex.withLock { dao.deletePaymentWithRefund(p) }

    /** 跳过本期（例如这期没续），不记账直接推进；非周期订阅则直接停用归档 */
    suspend fun skip(id: Long) = mutex.withLock {
        val sub = dao.get(id) ?: return@withLock
        if (Installments.isFinished(sub)) return@withLock
        val updated = if (sub.isOneTime) {
            sub.copy(active = false, overdueNotifiedDay = null)
        } else {
            Installments.normalize(sub.copy(nextBillingDay = Billing.next(sub).toEpochDay(), overdueNotifiedDay = null))
        }
        dao.upsert(updated)
        notifier.cancel(sub.id)
    }

    /** 分期提前还款：记一笔「提前还款」，分期按剩余部分重新计算（或直接还清） */
    suspend fun prepay(id: Long, amount: Double, mode: Installments.PrepayMode, today: LocalDate = LocalDate.now()): Installments.PrepayResult? =
        mutex.withLock {
            val sub = dao.get(id) ?: return@withLock null
            if (!sub.isInstallment || Installments.isFinished(sub)) return@withLock null
            val r = Installments.prepay(sub, amount, mode, today)
            dao.insertPayment(
                PaymentRecord(subscriptionId = sub.id, billingDay = today.toEpochDay(), amount = amount, currency = sub.currency, auto = false,
                    prepay = true, undo = Installments.snapshot(sub)),
            )
            dao.upsert(r.sub)
            notifier.cancel(sub.id)
            r
        }

    sealed interface UndoResult {
        data object Restored : UndoResult
        /** 这笔提前还款之后又记过账：要先删掉后面的记录，否则恢复后进度对不上 */
        data class LaterPayments(val count: Int) : UndoResult
        /** 旧版本（1.2.4 / 1.2.5 调试版）记的提前还款没有快照，只能删记录 */
        data object NoSnapshot : UndoResult
        data object NotFound : UndoResult
    }

    /** 删除一条提前还款记录，并把分期恢复成提前还款之前的样子 */
    suspend fun undoPrepay(paymentId: Long, deleteWithoutSnapshot: Boolean = false): UndoResult = mutex.withLock {
        val p = dao.getPayment(paymentId) ?: return@withLock UndoResult.NotFound
        val sub = dao.get(p.subscriptionId) ?: return@withLock UndoResult.NotFound
        val later = dao.countPaymentsAfter(sub.id, p.recordedAt, p.id)
        if (later > 0) return@withLock UndoResult.LaterPayments(later)
        val restored = p.undo?.let { Installments.restoreFrom(sub, it) }
        if (restored == null) {
            if (deleteWithoutSnapshot) dao.deletePayment(p)
            return@withLock UndoResult.NoSnapshot
        }
        dao.undoPrepay(p, restored)
        UndoResult.Restored
    }

    /** 分期的付款记录写上是第几期（显示用的期数，提前还款后也连续） */
    private fun termOf(sub: Subscription, date: LocalDate): Int? =
        if (sub.isInstallment) Installments.displayTerm(sub, date) else null
}
