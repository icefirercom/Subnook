package com.subnook.app.domain

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.RateTable
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Billing {

    fun today(): LocalDate = LocalDate.now()

    fun LocalDate.withAnchor(anchorDay: Int): LocalDate =
        withDayOfMonth(minOf(anchorDay, lengthOfMonth()))

    /** date 往后推 n 个周期（n 可为负） */
    fun shift(date: LocalDate, count: Int, unit: CycleUnit, anchorDay: Int, n: Int = 1): LocalDate {
        val steps = count.toLong() * n
        return when (unit) {
            CycleUnit.DAY -> date.plusDays(steps)
            CycleUnit.WEEK -> date.plusWeeks(steps)
            CycleUnit.MONTH -> date.plusMonths(steps).withAnchor(anchorDay)
            CycleUnit.YEAR -> date.plusYears(steps).withAnchor(anchorDay)
        }
    }

    fun next(sub: Subscription, from: LocalDate = LocalDate.ofEpochDay(sub.nextBillingDay)): LocalDate =
        shift(from, sub.cycleCount, sub.cycleUnit, sub.anchorDay, 1)

    fun prev(sub: Subscription, from: LocalDate): LocalDate =
        shift(from, sub.cycleCount, sub.cycleUnit, sub.anchorDay, -1)

    /** 订阅在 [from, to] 区间内的所有账单日（基于下次付款日向前/向后推算） */
    fun occurrences(sub: Subscription, fromDate: LocalDate, toDate: LocalDate): List<LocalDate> {
        if (sub.isLifetime) return emptyList()
        if (sub.isOneTime) {
            val d = LocalDate.ofEpochDay(sub.nextBillingDay)
            return if (d in fromDate..toDate) listOf(d) else emptyList()
        }
        var from = fromDate
        var to = toDate
        // 分期只有第 1 期到最后一期之间的还款日
        if (sub.isInstallment) {
            if (Installments.isPaidOff(sub)) return emptyList()
            from = maxOf(from, Installments.startDate(sub))
            to = minOf(to, Installments.endDate(sub))
            if (from > to) return emptyList()
        }
        val out = mutableListOf<LocalDate>()
        val base = LocalDate.ofEpochDay(sub.nextBillingDay)
        var d = base
        var guard = 0
        while (d >= from && guard++ < 5000) {
            if (d <= to) out += d
            d = prev(sub, d)
        }
        d = next(sub, base)
        guard = 0
        while (d <= to && guard++ < 5000) {
            if (d >= from) out += d
            d = next(sub, d)
        }
        return out.sorted()
    }

    /**
     * 还没付的账单日：只从「下次付款日期」往后算。
     * 下次付款日之前的周期要么已付（有付款记录），要么跳过了，不能再当成待付。
     */
    fun upcoming(sub: Subscription, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrences(sub, maxOf(from, LocalDate.ofEpochDay(sub.nextBillingDay)), to)

    /** 某个账单日要付的金额：订阅每期一样，分期按还款计划 */
    fun amountOn(sub: Subscription, date: LocalDate): Double =
        if (sub.isInstallment) Installments.amountOn(sub, date) else sub.amount

    /** [from, to] 内还没付的账单合计（原币种） */
    fun upcomingCost(sub: Subscription, from: LocalDate, to: LocalDate): Double =
        upcoming(sub, from, to).sumOf { amountOn(sub, it) }

    /** 折算为每年花费（原币种） */
    fun yearlyCost(sub: Subscription): Double {
        val cycleDays = sub.cycleCount * sub.cycleUnit.approxDays
        return sub.amount * CycleUnit.YEAR.approxDays / cycleDays
    }

    fun periodCost(sub: Subscription, period: SummaryPeriod): Double {
        if (sub.isOneTime) return 0.0
        // 月/年周期按精确比例，避免 30 元/月 显示成 29.99
        val perYear = when (sub.cycleUnit) {
            CycleUnit.MONTH -> sub.amount * 12.0 / sub.cycleCount
            CycleUnit.YEAR -> sub.amount / sub.cycleCount
            else -> yearlyCost(sub)
        }
        return when (period) {
            SummaryPeriod.YEAR -> perYear
            SummaryPeriod.MONTH -> perYear / 12.0
            SummaryPeriod.WEEK -> perYear * 7.0 / CycleUnit.YEAR.approxDays
        }
    }

    fun total(subs: List<Subscription>, period: SummaryPeriod, rates: RateTable, currency: String): Double =
        subs.filter { it.active }.sumOf { rates.convert(periodCost(it, period), it.currency, currency) }

    /** 进入「待付款」的天数窗口：3 天内到期（含已逾期） */
    const val PAYABLE_DAYS = 3L

    /** 是否显示在「待付款」里并提供「已付款」按钮（买断制无结束时间不进入待付款） */
    fun isPayable(sub: Subscription, today: LocalDate = today()): Boolean =
        sub.active && !sub.isLifetime && daysUntil(sub, today) <= PAYABLE_DAYS

    fun daysUntil(sub: Subscription, today: LocalDate = today()): Long =
        ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(sub.nextBillingDay))

    /** 待付款/到期提醒排序：非周期订阅优先于周期订阅，同类按到期日近 → 远 */
    val pendingComparator: Comparator<Subscription> =
        compareBy<Subscription> { !it.isOneTime }.thenBy { it.nextBillingDay }

    fun cycleText(sub: Subscription): String = when {
        sub.isLifetime -> "买断"
        sub.isOneTime -> "非周期"
        sub.cycleCount == 1 -> "每${sub.cycleUnit.label}"
        else -> "每${sub.cycleCount}${sub.cycleUnit.label}"
    }

    fun dueText(days: Long): String = when {
        days < -1 -> "已逾期 ${-days} 天"
        days == -1L -> "昨天到期"
        days == 0L -> "今天"
        days == 1L -> "明天"
        days == 2L -> "后天"
        else -> "$days 天后"
    }
}
