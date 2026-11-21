package com.subnook.app.domain

import com.subnook.app.data.PayMethod
import com.subnook.app.data.Subscription
import java.time.LocalDate

/**
 * 余额能覆盖的期数，以及第一次覆盖不了、需要充值的账单日。
 * 移植自 PR #3（作者 wanwuzhishi），改成按每期实际金额计算，分期也适用。
 */
data class BalanceForecast(
    /** 余额还能付几期 */
    val periods: Int,
    /** 第一次余额不够付的账单日；null = 分期剩下的期数全都够付，不用再充值 */
    val rechargeOn: LocalDate?,
    /** 付下一期还差多少（余额够付下一期时为 0） */
    val shortfall: Double,
    /** 到 [rechargeOn] 那一期时，还要再充多少才够付 */
    val rechargeAmount: Double = 0.0,
    /** 期数太多，已按上限截断 */
    val capped: Boolean = false,
) {
    val coversAll: Boolean get() = rechargeOn == null
}

object Balance {
    /** 上限约 100 年（按月），避免余额很大时把日期算到离谱的年份 */
    const val MAX_PERIODS = 1200
    private const val EPSILON = 1e-6

    fun usesBalance(sub: Subscription) = sub.payMethod == PayMethod.BALANCE

    /**
     * 从下次付款日开始，依次用余额付每一期（分期按还款计划，每期金额可能不同），
     * 直到余额不够、分期还完或达到上限。金额无效时返回 null。
     */
    fun forecast(sub: Subscription): BalanceForecast? {
        if (sub.isInstallment && Installments.isFinished(sub)) return BalanceForecast(0, null, 0.0)
        if (!sub.isInstallment && sub.amount <= 0.0) return null
        if (sub.cycleCount <= 0) return null
        val end = if (sub.isInstallment) Installments.endDate(sub) else null
        var left = sub.balance.coerceAtLeast(0.0)
        var d = LocalDate.ofEpochDay(sub.nextBillingDay)
        var periods = 0
        var shortfall = 0.0
        while (periods < MAX_PERIODS) {
            if (end != null && d > end) return BalanceForecast(periods, null, shortfall)
            val amt = Billing.amountOn(sub, d)
            if (amt <= 0.0) return null
            if (left + EPSILON < amt) {
                if (periods == 0) shortfall = amt - left
                return BalanceForecast(periods, d, roundMoney(shortfall), rechargeAmount = roundMoney(amt - left))
            }
            left -= amt
            periods++
            d = Billing.next(sub, d)
        }
        return BalanceForecast(periods, d, 0.0, capped = true)
    }

    /** 这一期要从余额扣多少：余额付款才扣，最多扣到 0 */
    fun deduction(sub: Subscription, amount: Double, balance: Double = sub.balance): Double =
        if (usesBalance(sub)) roundMoney(minOf(balance.coerceAtLeast(0.0), amount)) else 0.0

    /** 余额够不够付这一期 */
    fun canPay(sub: Subscription, amount: Double, balance: Double = sub.balance): Boolean =
        balance + EPSILON >= amount

    fun periodsText(forecast: BalanceForecast): String = when {
        forecast.capped -> "$MAX_PERIODS+ 期"
        forecast.coversAll -> "剩下 ${forecast.periods} 期全部"
        else -> "${forecast.periods} 期"
    }

    /** 账单日文案：今年省略年份 */
    fun dateText(date: LocalDate, today: LocalDate = Billing.today()): String =
        if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日"
        else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

    private fun roundMoney(v: Double) = com.subnook.app.data.roundMoney(v)
}
