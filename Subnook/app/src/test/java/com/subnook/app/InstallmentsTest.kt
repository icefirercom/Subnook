package com.subnook.app

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.SubKind
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Billing
import com.subnook.app.domain.Installments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InstallmentsTest {
    private fun pays(principal: Double, n: Int, rate: Double, m: InstallmentMethod, decimals: Int = 2, first: Double? = null) =
        Installments.parts(principal, n, rate, m, decimals = decimals, firstOverride = first).map { it.payment }

    @Test fun interestFreeEvenSplit() {
        assertEquals(List(12) { 500.0 }, pays(6000.0, 12, 0.0, InstallmentMethod.INTEREST_FREE))
    }

    @Test fun interestFreeRemainderGoesToFirstTerm() {
        assertEquals(listOf(333.34, 333.33, 333.33), pays(1000.0, 3, 0.0, InstallmentMethod.INTEREST_FREE))
    }

    @Test fun noDecimalCurrencyRoundsToWholeUnits() {
        assertEquals(listOf(3334.0, 3333.0, 3333.0), pays(10000.0, 3, 0.0, InstallmentMethod.INTEREST_FREE, decimals = 0))
    }

    @Test fun feePerTerm() {
        // 6000 分 12 期，每期手续费 0.6%：每期 500 + 36
        val ps = Installments.parts(6000.0, 12, 0.6, InstallmentMethod.FEE)
        assertTrue(ps.all { it.payment == 536.0 && it.principal == 500.0 && it.interest == 36.0 })
    }

    @Test fun feeUpfront() {
        val p = pays(6000.0, 12, 0.6, InstallmentMethod.FEE_UPFRONT)
        assertEquals(932.0, p[0], 0.0)
        assertTrue(p.drop(1).all { it == 500.0 })
    }

    @Test fun equalPaymentMatchesBankFormula() {
        // 10 万元、1 年、年利率 4.35% 等额本息：月供 8530.99
        val ps = Installments.parts(100000.0, 12, 4.35, InstallmentMethod.EQUAL_PAYMENT)
        assertEquals(8530.99, ps[0].payment, 0.0)
        assertEquals(100000.0, ps.sumOf { it.principal }, 1e-6)
        val interest = ps.sumOf { it.interest }
        assertTrue("interest=$interest", interest in 2371.0..2373.5)
        // 除末期外每期相同
        assertTrue(ps.dropLast(1).all { it.payment == 8530.99 })
    }

    @Test fun equalPrincipalDecreases() {
        // 12 万、12 期、年利率 6%（每月 0.5%）
        val p = pays(120000.0, 12, 6.0, InstallmentMethod.EQUAL_PRINCIPAL)
        assertEquals(10600.0, p.first(), 0.0)
        assertEquals(10050.0, p.last(), 0.0)
    }

    @Test fun firstAmountOverride() {
        val p = pays(6000.0, 12, 0.6, InstallmentMethod.FEE, first = 540.5)
        assertEquals(540.5, p[0], 0.0)
        assertEquals(536.0, p[1], 0.0)
    }

    @Test fun annualizedRateOfMonthlyFee() {
        val ps = Installments.parts(6000.0, 12, 0.6, InstallmentMethod.FEE)
        val apr = Installments.annualizedRate(ps, 6000.0, 1, CycleUnit.MONTH)!!
        assertTrue("apr=$apr", apr in 12.5..13.5)
        assertEquals(null, Installments.annualizedRate(Installments.parts(6000.0, 12, 0.0, InstallmentMethod.INTEREST_FREE), 6000.0, 1, CycleUnit.MONTH))
    }

    // ---- 日期 / 期数 ----

    private fun phone(next: LocalDate, current: Int, terms: Int = 12) = Subscription(
        name = "手机", amount = 0.0, currency = "CNY", nextBillingDay = next.toEpochDay(), anchorDay = next.dayOfMonth,
        kind = SubKind.INSTALLMENT, instPrincipal = 6000.0, instTerms = terms, instRate = 0.6, instMethod = InstallmentMethod.FEE,
        instStartDay = Installments.startFrom(next, current, 1, CycleUnit.MONTH, next.dayOfMonth).toEpochDay(),
    )

    @Test fun startDateFromCurrentTerm() {
        val s = phone(LocalDate.of(2026, 12, 15), current = 3)
        assertEquals(LocalDate.of(2026, 10, 15), Installments.startDate(s))
        assertEquals(3, Installments.currentTerm(s))
        assertEquals(LocalDate.of(2027, 9, 15), Installments.endDate(s))
    }

    @Test fun normalizeSyncsAmountAndFinishes() {
        val s = Installments.normalize(phone(LocalDate.of(2026, 12, 15), current = 3))
        assertEquals(536.0, s.amount, 0.0)
        assertTrue(s.active)
        // 推进到最后一期之后：自动停用
        val done = Installments.normalize(s.copy(nextBillingDay = LocalDate.of(2027, 10, 15).toEpochDay()))
        assertFalse(done.active)
        assertTrue(Installments.isFinished(done))
        assertEquals(0.0, Installments.summary(done).remainingAmount, 0.0)
    }

    @Test fun occurrencesStopAtLastTerm() {
        val s = phone(LocalDate.of(2026, 12, 15), current = 11)
        val dates = Billing.upcoming(s, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15)), dates)
        assertEquals(536.0 * 2, Billing.upcomingCost(s, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 31)), 1e-9)
    }

    @Test fun summaryRemaining() {
        val sum = Installments.summary(phone(LocalDate.of(2026, 12, 15), current = 3))
        assertEquals(10, sum.remainingTerms)
        assertEquals(5360.0, sum.remainingAmount, 0.0)
        assertEquals(6432.0, sum.totalPayment, 0.0)
        assertEquals(432.0, sum.totalInterest, 0.0)
        assertEquals(2, sum.paidTerms)
    }

    // ---- 提前还款 ----

    private val today = LocalDate.of(2026, 12, 1)

    @Test fun payOffEarly() {
        val s = Installments.normalize(phone(LocalDate.of(2026, 12, 15), current = 3))
        assertEquals(5000.0, Installments.remainingPrincipal(s), 0.0)
        val r = Installments.prepay(s, 5000.0, Installments.PrepayMode.PAY_OFF, today)
        assertTrue(r.paidOff)
        assertEquals(360.0, r.saved, 1e-9) // 剩余 10 期手续费
        val done = Installments.normalize(r.sub)
        assertFalse(done.active)
        assertTrue(Installments.isFinished(done))
        val sum = Installments.summary(done)
        assertEquals(12, sum.total)
        assertEquals(536.0 * 2 + 5000, sum.totalPayment, 1e-9)
        assertEquals(72.0, sum.totalInterest, 1e-9)
        assertEquals(today, sum.endDate)
        assertEquals(emptyList<LocalDate>(), Billing.upcoming(done, today, today.plusYears(2)))
    }

    @Test fun payOffWithPenaltyCountsAsFee() {
        val s = phone(LocalDate.of(2026, 12, 15), current = 3)
        val r = Installments.prepay(s, 5100.0, Installments.PrepayMode.PAY_OFF, today)
        assertEquals(172.0, Installments.summary(r.sub).totalInterest, 1e-9)
        assertEquals(260.0, r.saved, 1e-9)
    }

    private fun freePhone(current: Int) = Installments.normalize(
        phone(LocalDate.of(2026, 12, 15), current).copy(instPrincipal = 1200.0, instRate = 0.0, instMethod = InstallmentMethod.INTEREST_FREE),
    )

    @Test fun partialLowerPayment() {
        val s = freePhone(current = 4) // 每期 100，已还 3 期
        val r = Installments.prepay(s, 300.0, Installments.PrepayMode.LOWER_PAYMENT, today)
        assertFalse(r.paidOff)
        assertEquals(9, r.newTerms)
        val n = r.sub
        assertEquals(3, n.instTermOffset)
        assertEquals(600.0, n.instPrincipal, 0.0)
        assertEquals(66.72, n.amount, 0.0) // 600 ÷ 9，零头放首期
        val sum = Installments.summary(n)
        assertEquals(4, sum.current)
        assertEquals(12, sum.total)
        assertEquals(1200.0, sum.totalPayment, 1e-9)
        assertEquals(0.0, sum.totalInterest, 1e-9)
        assertEquals(LocalDate.of(2026, 12, 15), Installments.termDate(n, 1))
        assertEquals(4, Installments.displayTerm(n, LocalDate.of(2026, 12, 15)))
        assertEquals(Installments.endDate(s), Installments.endDate(n))
    }

    @Test fun partialFewerTerms() {
        val s = freePhone(current = 4)
        val r = Installments.prepay(s, 300.0, Installments.PrepayMode.FEWER_TERMS, today)
        assertEquals(6, r.newTerms) // 每期仍是 100，少还 3 期
        assertEquals(100.0, r.newPayment, 0.0)
        assertEquals(9, Installments.summary(r.sub).total)
        assertEquals(LocalDate.of(2027, 5, 15), Installments.endDate(r.sub))
    }

    @Test fun fewerTermsSavesInterestOnLoan() {
        val next = LocalDate.of(2026, 12, 15)
        val loan = Installments.normalize(
            phone(next, current = 1).copy(instPrincipal = 100000.0, instRate = 4.35, instMethod = InstallmentMethod.EQUAL_PAYMENT),
        )
        val r = Installments.prepay(loan, 50000.0, Installments.PrepayMode.FEWER_TERMS, today)
        assertEquals(6, r.newTerms)
        assertTrue(r.newPayment <= 8530.99)
        assertTrue("saved=${r.saved}", r.saved > 0)
        val lower = Installments.prepay(loan, 50000.0, Installments.PrepayMode.LOWER_PAYMENT, today)
        assertEquals(12, lower.newTerms)
        assertTrue(lower.newPayment < r.newPayment)
        assertTrue(r.saved > lower.saved) // 缩短期数省的利息更多
    }

    @Test(expected = IllegalArgumentException::class)
    fun feeInstallmentCannotPartiallyPrepay() {
        Installments.prepay(phone(LocalDate.of(2026, 12, 15), current = 3), 1000.0, Installments.PrepayMode.LOWER_PAYMENT, today)
    }

    @Test fun partialAmountAboveRemainingPaysOff() {
        val r = Installments.prepay(freePhone(current = 4), 900.0, Installments.PrepayMode.LOWER_PAYMENT, today)
        assertTrue(r.paidOff)
    }

    // ---- 撤销提前还款 ----

    @Test fun undoPartialPrepayRestoresPlan() {
        val s = freePhone(current = 4).copy(id = 7)
        val snap = Installments.snapshot(s)
        val after = Installments.prepay(s, 300.0, Installments.PrepayMode.FEWER_TERMS, today).sub
            .copy(name = "改过名字", note = "备注")
        val back = Installments.restoreFrom(after, snap)!!
        assertEquals("改过名字", back.name) // 之后改的设置保留
        assertEquals("备注", back.note)
        assertEquals(s.copy(name = "改过名字", note = "备注", remindedForDay = null, overdueNotifiedDay = null), back)
        assertEquals(Installments.summary(s), Installments.summary(back))
    }

    @Test fun undoPayOffReactivates() {
        val s = Installments.normalize(phone(LocalDate.of(2026, 12, 15), current = 3)).copy(id = 8)
        val paidOff = Installments.normalize(Installments.prepay(s, 5000.0, Installments.PrepayMode.PAY_OFF, today).sub)
        assertFalse(paidOff.active)
        val back = Installments.restoreFrom(paidOff, Installments.snapshot(s))!!
        assertTrue(back.active)
        assertFalse(Installments.isFinished(back))
        assertEquals(3, Installments.summary(back).current)
        assertEquals(536.0, back.amount, 0.0)
    }

    @Test fun undoRejectsOtherSnapshot() {
        val a = freePhone(current = 4).copy(id = 1)
        val b = freePhone(current = 4).copy(id = 2)
        assertEquals(null, Installments.restoreFrom(a, Installments.snapshot(b)))
        assertEquals(null, Installments.restoreFrom(a, "not json"))
    }

    @Test fun monthEndAnchorForInstallment() {
        val s = phone(LocalDate.of(2026, 1, 31), current = 1, terms = 3)
        assertEquals(LocalDate.of(2026, 2, 28), Installments.termDate(s, 2))
        assertEquals(LocalDate.of(2026, 3, 31), Installments.termDate(s, 3))
    }
}
