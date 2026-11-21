package com.subnook.app.domain

import com.subnook.app.data.Currencies
import com.subnook.app.data.CycleUnit
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.Subscription
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 分期计算。分期也是一条 [Subscription]（kind = INSTALLMENT）：
 * - 第 1 期的还款日 [Subscription.instStartDay]，之后每个周期一期，共 [Subscription.instTerms] 期；
 * - 「下次付款日期」[Subscription.nextBillingDay] 落在第几期，就是当前期数（已还 = 当前期 - 1），
 *   所以确认付款 / 跳过 / 自动扣款只需要像订阅一样推进下次付款日；
 * - [Subscription.amount] 始终同步为当前这一期的应还金额（列表、通知、小组件直接用）。
 *
 * 金额全部换成最小货币单位（分）用整数算，每期四舍五入（ROUND_HALF_UP），避免浮点误差。
 */
object Installments {

    /** 一期的金额拆分：[payment] = [principal] + [interest]（单位：元，已按币种精度取整） */
    data class Part(val payment: Double, val principal: Double, val interest: Double)

    data class Term(val index: Int, val date: LocalDate, val payment: Double, val principal: Double, val interest: Double)

    data class Summary(
        /** 下次要还的是第几期（1..total；已还清时为 total + 1） */
        val current: Int,
        val total: Int,
        val currentAmount: Double,
        /** 本金 + 手续费 / 利息合计 */
        val totalPayment: Double,
        /** 手续费 / 利息合计（填了首期金额时，按首期金额算出的差额也算在这里） */
        val totalInterest: Double,
        /** 还没还的期数和金额（含当前期） */
        val remainingTerms: Int,
        val remainingAmount: Double,
        val endDate: LocalDate,
    ) {
        val finished: Boolean get() = current > total
        val paidTerms: Int get() = (current - 1).coerceAtMost(total)
    }

    /** 每期利率（小数）：年利率按周期长度折算，每月 = 年利率 / 12 */
    fun periodRate(annualPercent: Double, cycleCount: Int, unit: CycleUnit): Double {
        val years = when (unit) {
            CycleUnit.MONTH -> cycleCount / 12.0
            CycleUnit.YEAR -> cycleCount.toDouble()
            CycleUnit.WEEK -> cycleCount * 7.0 / 365.0
            CycleUnit.DAY -> cycleCount / 365.0
        }
        return annualPercent / 100.0 * years
    }

    /**
     * 计算每期还款额。
     * @param ratePercent 手续费方式是每期费率（%），等额本息 / 等额本金是年利率（%）
     * @param firstOverride 首期金额（账单上的实际数，和计算结果有出入时用），只替换第 1 期
     */
    fun parts(
        principal: Double,
        terms: Int,
        ratePercent: Double,
        method: InstallmentMethod,
        cycleCount: Int = 1,
        cycleUnit: CycleUnit = CycleUnit.MONTH,
        decimals: Int = 2,
        firstOverride: Double? = null,
    ): List<Part> {
        if (terms <= 0 || principal <= 0) return emptyList()
        val scale = BigDecimal.TEN.pow(decimals)
        val p = BigDecimal.valueOf(principal).multiply(scale).setScale(0, RoundingMode.HALF_UP).toLong()
        val rate = BigDecimal.valueOf(ratePercent.coerceAtLeast(0.0)).divide(BigDecimal(100))
        val n = terms.toLong()

        // 每期本金：平均分，除不尽的零头放在首期
        val base = p / n
        val rem = p - base * n
        fun principalOf(i: Int) = base + if (i == 0) rem else 0L

        val minor: List<Pair<Long, Long>> = when (method) {
            InstallmentMethod.INTEREST_FREE -> List(terms) { principalOf(it) to 0L }
            InstallmentMethod.FEE -> {
                val fee = rate.multiply(BigDecimal.valueOf(p)).setScale(0, RoundingMode.HALF_UP).toLong()
                List(terms) { principalOf(it) to fee }
            }
            InstallmentMethod.FEE_UPFRONT -> {
                val fee = rate.multiply(BigDecimal.valueOf(p * n)).setScale(0, RoundingMode.HALF_UP).toLong()
                List(terms) { principalOf(it) to if (it == 0) fee else 0L }
            }
            InstallmentMethod.EQUAL_PAYMENT -> {
                val r = periodRate(ratePercent.coerceAtLeast(0.0), cycleCount, cycleUnit)
                if (r <= 0.0) List(terms) { principalOf(it) to 0L } else {
                    val f = Math.pow(1 + r, terms.toDouble())
                    val pay = roundHalfUp(p * r * f / (f - 1))
                    var balance = p
                    List(terms) { i ->
                        val interest = roundHalfUp(balance * r)
                        // 末期把剩余本金全部还清，消掉每期取整的尾差
                        val pr = if (i == terms - 1) balance else (pay - interest).coerceIn(0L, balance)
                        balance -= pr
                        pr to interest
                    }
                }
            }
            InstallmentMethod.EQUAL_PRINCIPAL -> {
                val r = periodRate(ratePercent.coerceAtLeast(0.0), cycleCount, cycleUnit)
                var balance = p
                List(terms) { i ->
                    val interest = roundHalfUp(balance * r)
                    val pr = principalOf(i)
                    balance -= pr
                    pr to interest
                }
            }
        }
        val unit = scale.toDouble()
        return minor.mapIndexed { i, (pr, int) ->
            if (i == 0 && firstOverride != null) {
                val pay = BigDecimal.valueOf(firstOverride).multiply(scale).setScale(0, RoundingMode.HALF_UP).toLong()
                Part(pay / unit, pr / unit, (pay - pr) / unit)
            } else {
                Part((pr + int) / unit, pr / unit, int / unit)
            }
        }
    }

    /**
     * 实际年化利率（%，IRR 法）：每期利率 r 满足 Σ 第 i 期还款 / (1+r)^i = 本金，年化 = r × 每年期数。
     * 用来提示「每期 0.6% 的手续费其实约等于 13% 年化」。没有利息时返回 null。
     */
    fun annualizedRate(parts: List<Part>, principal: Double, cycleCount: Int, unit: CycleUnit): Double? {
        if (parts.isEmpty() || principal <= 0 || parts.sumOf { it.payment } <= principal + 1e-9) return null
        fun pv(r: Double) = parts.withIndex().sumOf { (i, p) -> p.payment / Math.pow(1 + r, (i + 1).toDouble()) }
        var lo = 0.0
        var hi = 1.0
        repeat(200) {
            val mid = (lo + hi) / 2
            if (pv(mid) > principal) lo = mid else hi = mid
        }
        val perYear = 1.0 / (periodRate(100.0, cycleCount, unit))
        return (lo + hi) / 2 * perYear * 100
    }

    private fun roundHalfUp(x: Double): Long = BigDecimal(x).setScale(0, RoundingMode.HALF_UP).toLong()

    fun parts(sub: Subscription): List<Part> = parts(
        sub.instPrincipal, sub.instTerms, sub.instRate, sub.instMethod,
        sub.cycleCount, sub.cycleUnit, Currencies.decimals(sub.currency), sub.instFirstAmount,
    )

    fun startDate(sub: Subscription): LocalDate = LocalDate.ofEpochDay(sub.instStartDay)

    /** 第 [index] 期（从 1 开始）的还款日 */
    fun termDate(sub: Subscription, index: Int): LocalDate =
        Billing.shift(startDate(sub), sub.cycleCount, sub.cycleUnit, sub.anchorDay, index - 1)

    fun endDate(sub: Subscription): LocalDate = termDate(sub, sub.instTerms.coerceAtLeast(1))

    /** 已知「下次还款是第 [current] 期、日期 [next]」，倒推第 1 期的还款日 */
    fun startFrom(next: LocalDate, current: Int, cycleCount: Int, unit: CycleUnit, anchorDay: Int): LocalDate =
        Billing.shift(next, cycleCount, unit, anchorDay, -(current - 1))

    /** [date] 当天或之后最近的一期是第几期；超过最后一期返回 total + 1 */
    fun termIndex(sub: Subscription, date: LocalDate): Int {
        for (i in 1..sub.instTerms) if (termDate(sub, i) >= date) return i
        return sub.instTerms + 1
    }

    fun currentTerm(sub: Subscription): Int = termIndex(sub, LocalDate.ofEpochDay(sub.nextBillingDay))

    /** 某个还款日应还的金额 */
    fun amountOn(sub: Subscription, date: LocalDate): Double {
        val ps = parts(sub)
        if (ps.isEmpty()) return sub.amount
        val i = termIndex(sub, date).coerceIn(1, ps.size)
        return ps[i - 1].payment
    }

    fun schedule(sub: Subscription): List<Term> = parts(sub).mapIndexed { i, p ->
        Term(i + 1, termDate(sub, i + 1), p.payment, p.principal, p.interest)
    }

    /**
     * 显示用的汇总。期数都加上 [Subscription.instTermOffset]（部分提前还款前已经还完的期数），
     * 总额、手续费 / 利息都加上之前已付的部分，所以提前还款后看到的仍是整笔分期的数字。
     */
    fun summary(sub: Subscription): Summary {
        val offset = sub.instTermOffset
        val total = offset + sub.instTerms
        if (isPaidOff(sub)) {
            return Summary(
                current = total + 1, total = total, currentAmount = 0.0,
                totalPayment = round(sub.instPaidBefore, sub.currency),
                totalInterest = round(sub.instPaidBefore - sub.instPrincipalBefore, sub.currency),
                remainingTerms = 0, remainingAmount = 0.0,
                endDate = LocalDate.ofEpochDay(sub.instPaidOffDay!!),
            )
        }
        val ps = parts(sub)
        val cur = currentTerm(sub)
        val totalPay = sub.instPaidBefore + ps.sumOf { it.payment }
        val remaining = ps.drop(cur - 1)
        return Summary(
            current = offset + cur,
            total = total,
            currentAmount = ps.getOrNull(cur - 1)?.payment ?: 0.0,
            totalPayment = round(totalPay, sub.currency),
            totalInterest = round(totalPay - sub.instPrincipalBefore - sub.instPrincipal, sub.currency),
            remainingTerms = remaining.size,
            remainingAmount = round(remaining.sumOf { it.payment }, sub.currency),
            endDate = endDate(sub),
        )
    }

    /** 显示用的期数：某个还款日是整笔分期的第几期 */
    fun displayTerm(sub: Subscription, date: LocalDate): Int = sub.instTermOffset + termIndex(sub, date)

    fun isPaidOff(sub: Subscription): Boolean = sub.isInstallment && sub.instPaidOffDay != null

    // ---- 提前还款 ----

    enum class PrepayMode(val label: String, val hint: String) {
        PAY_OFF("一次还清", "把剩下的全部还掉，之后不再扣款"),
        LOWER_PAYMENT("减少每期还款", "期数不变，剩余本金重新计算，之后每期还得更少"),
        FEWER_TERMS("缩短还款期数", "每期还款基本不变，提前几期还完，省下更多利息"),
    }

    private val snapshotJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 提前还款前的快照（存在那条提前还款记录上），删除记录时用 [restoreFrom] 恢复 */
    fun snapshot(sub: Subscription): String = snapshotJson.encodeToString(Subscription.serializer(), sub)

    /**
     * 撤销提前还款：把还款计划相关的字段恢复成快照里的样子（期数、本金、日期、金额、是否启用等）；
     * 名称、图标、付款方式、提醒、备注这些在提前还款之后改过的设置保留当前的。
     * 快照无法解析时返回 null。
     */
    fun restoreFrom(current: Subscription, snapshot: String): Subscription? {
        val s = runCatching { snapshotJson.decodeFromString(Subscription.serializer(), snapshot) }.getOrNull() ?: return null
        if (s.id != current.id) return null
        return normalize(
            current.copy(
                kind = s.kind, amount = s.amount, currency = s.currency,
                cycleCount = s.cycleCount, cycleUnit = s.cycleUnit,
                nextBillingDay = s.nextBillingDay, anchorDay = s.anchorDay, active = s.active,
                instPrincipal = s.instPrincipal, instTerms = s.instTerms, instRate = s.instRate,
                instMethod = s.instMethod, instFirstAmount = s.instFirstAmount, instStartDay = s.instStartDay,
                instTermOffset = s.instTermOffset, instPaidBefore = s.instPaidBefore,
                instPrincipalBefore = s.instPrincipalBefore, instPaidOffDay = s.instPaidOffDay,
                remindedForDay = null, overdueNotifiedDay = null,
            ),
        )
    }

    /** 部分提前还款只对按剩余本金计算的方式有意义；信用卡式手续费分期只能一次还清 */
    fun canPartialPrepay(method: InstallmentMethod) = method == InstallmentMethod.INTEREST_FREE ||
        method == InstallmentMethod.EQUAL_PAYMENT || method == InstallmentMethod.EQUAL_PRINCIPAL

    /** 当前期及以后还没还的本金 */
    fun remainingPrincipal(sub: Subscription): Double {
        if (isPaidOff(sub)) return 0.0
        val cur = currentTerm(sub)
        return round(parts(sub).drop(cur - 1).sumOf { it.principal }, sub.currency)
    }

    data class PrepayResult(
        /** 提前还款后的分期（还没保存） */
        val sub: Subscription,
        val paidOff: Boolean,
        /** 和按原计划还完相比少付的钱（可能为负：例如还清时银行收了违约金） */
        val saved: Double,
        /** 部分还款后的新每期金额 / 新的剩余期数 / 新的最后一期 */
        val newPayment: Double = 0.0,
        val newTerms: Int = 0,
        val newEnd: LocalDate? = null,
    )

    /**
     * 计算提前还款。[amount] 是这次实际付的钱：
     * - 一次还清：可以大于剩余本金（有的银行要收剩余手续费或违约金），多出来的算作利息 / 手续费；
     * - 部分还款：全部用来还本金，剩余本金按原方式重新算（减少每期还款 / 缩短期数）。
     * 金额不小于剩余本金时按一次还清处理。
     */
    fun prepay(sub: Subscription, amount: Double, mode: PrepayMode, today: LocalDate): PrepayResult {
        require(sub.isInstallment && !isFinished(sub)) { "这笔分期已经还清了" }
        require(amount > 0) { "请填写还款金额" }
        val ps = parts(sub)
        val cur = currentTerm(sub)
        val paid = ps.take(cur - 1)
        val left = ps.drop(cur - 1)
        val r = remainingPrincipal(sub)
        val leftTotal = left.sumOf { it.payment }
        val base = sub.copy(
            instPaidBefore = round(sub.instPaidBefore + paid.sumOf { it.payment } + amount, sub.currency),
            remindedForDay = null, overdueNotifiedDay = null,
        )
        if (mode == PrepayMode.PAY_OFF || amount >= r - 0.005) {
            val out = base.copy(
                instPrincipalBefore = round(sub.instPrincipalBefore + paid.sumOf { it.principal } + r, sub.currency),
                instPaidOffDay = today.toEpochDay(),
                active = false,
            )
            return PrepayResult(out, paidOff = true, saved = round(leftTotal - amount, sub.currency))
        }
        require(canPartialPrepay(sub.instMethod)) { "这种分期只能一次还清" }
        val newPrincipal = round(r - amount, sub.currency)
        val remainingTerms = left.size
        val decimals = Currencies.decimals(sub.currency)
        fun newParts(m: Int) = parts(newPrincipal, m, sub.instRate, sub.instMethod, sub.cycleCount, sub.cycleUnit, decimals)
        val terms = when (mode) {
            PrepayMode.FEWER_TERMS -> {
                // 每期还款不超过原来的水平，期数尽量少
                val old = (if (cur == 1 && sub.instFirstAmount != null) ps.getOrNull(1) else ps.getOrNull(cur - 1))?.payment ?: 0.0
                (1..remainingTerms).firstOrNull { m -> newParts(m).maxOf { it.payment } <= old + 0.005 } ?: remainingTerms
            }
            else -> remainingTerms
        }
        val start = termDate(sub, cur)
        val out = base.copy(
            instTermOffset = sub.instTermOffset + (cur - 1),
            instPrincipalBefore = round(sub.instPrincipalBefore + paid.sumOf { it.principal } + amount, sub.currency),
            instPrincipal = newPrincipal,
            instTerms = terms,
            instFirstAmount = null,
            instStartDay = start.toEpochDay(),
            nextBillingDay = start.toEpochDay(),
        )
        val np = newParts(terms)
        val normalized = normalize(out)
        return PrepayResult(
            normalized, paidOff = false,
            saved = round(leftTotal - amount - np.sumOf { it.payment }, sub.currency),
            newPayment = np.firstOrNull()?.payment ?: 0.0,
            newTerms = terms,
            newEnd = endDate(normalized),
        )
    }

    private fun round(x: Double, currency: String) =
        BigDecimal.valueOf(x).setScale(Currencies.decimals(currency), RoundingMode.HALF_UP).toDouble()

    /**
     * 保存 / 推进下次付款日之后调用：把金额同步成当前期的应还额；最后一期还完后自动停用（显示为「已还清」）。
     * 普通订阅原样返回。
     */
    fun normalize(sub: Subscription): Subscription {
        if (isPaidOff(sub)) return sub.copy(active = false)
        if (!sub.isInstallment || sub.instTerms <= 0) return sub
        val ps = parts(sub)
        if (ps.isEmpty()) return sub
        val cur = currentTerm(sub)
        val finished = cur > sub.instTerms
        return sub.copy(
            amount = ps[(cur - 1).coerceAtMost(ps.size - 1)].payment,
            active = if (finished) false else sub.active,
        )
    }

    fun isFinished(sub: Subscription): Boolean = sub.isInstallment && (isPaidOff(sub) || currentTerm(sub) > sub.instTerms)

    /** 「第 3/12 期」/「已还清」 */
    fun progressText(sub: Subscription): String {
        val s = summary(sub)
        return when {
            isPaidOff(sub) -> "已提前还清 · 共 ${s.total} 期"
            s.finished -> "已还清 · 共 ${s.total} 期"
            else -> "第 ${s.current}/${s.total} 期"
        }
    }
}
