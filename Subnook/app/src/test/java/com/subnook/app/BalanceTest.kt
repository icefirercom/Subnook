package com.subnook.app

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.PayMethod
import com.subnook.app.data.SubKind
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Balance
import com.subnook.app.domain.Installments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 余额付款预测。前 8 个测试移植自 PR #3（作者 wanwuzhishi），后面是分期 / 扣款相关的补充 */
class BalanceTest {
    private fun sub(
        next: LocalDate,
        amount: Double,
        balance: Double,
        count: Int = 1,
        unit: CycleUnit = CycleUnit.MONTH,
    ) = Subscription(
        name = "t", amount = amount, currency = "CNY", cycleCount = count, cycleUnit = unit,
        nextBillingDay = next.toEpochDay(), anchorDay = next.dayOfMonth,
        payMethod = PayMethod.BALANCE, balance = balance,
    )

    @Test fun exactMultipleCoversWholePeriods() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 90.0))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2027, 2, 20), f.rechargeOn)
        assertEquals(0.0, f.shortfall, 1e-9)
        assertEquals(30.0, f.rechargeAmount, 1e-9)
    }

    @Test fun partialPeriodNeedsRechargeAfterCoveredOnes() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 100.0))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2027, 2, 20), f.rechargeOn)
        assertEquals(20.0, f.rechargeAmount, 1e-9) // 届时还剩 10，再充 20 才够
    }

    @Test fun insufficientBalanceNeedsRechargeOnNextBill() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 10.0))!!
        assertEquals(0, f.periods)
        assertEquals(LocalDate.of(2026, 11, 20), f.rechargeOn)
        assertEquals(20.0, f.shortfall, 1e-9)
    }

    @Test fun weeklyCycle() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 10.0, balance = 35.0, unit = CycleUnit.WEEK))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2026, 12, 11), f.rechargeOn)
    }

    @Test fun yearlyCycle() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 100.0, balance = 250.0, unit = CycleUnit.YEAR))!!
        assertEquals(2, f.periods)
        assertEquals(LocalDate.of(2028, 11, 20), f.rechargeOn)
    }

    @Test fun floatingPointDoesNotLoseAPeriod() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 0.1, balance = 0.3))!!
        assertEquals(3, f.periods)
    }

    @Test fun noForecastWithoutAmount() {
        assertNull(Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 0.0, balance = 100.0)))
    }

    @Test fun veryLargeBalanceIsCapped() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 1.0, balance = 1_000_000.0))!!
        assertEquals(Balance.MAX_PERIODS, f.periods)
        assertTrue(f.capped)
    }

    // ---- 补充：分期按每期实际金额、还完就不用再充 ----

    private fun installment(method: InstallmentMethod, rate: Double, balance: Double, terms: Int = 12, current: Int = 1): Subscription {
        val next = LocalDate.of(2026, 12, 15)
        return Installments.normalize(
            Subscription(
                name = "phone", amount = 0.0, currency = "CNY", nextBillingDay = next.toEpochDay(), anchorDay = 15,
                payMethod = PayMethod.BALANCE, balance = balance,
                kind = SubKind.INSTALLMENT, instPrincipal = 6000.0, instTerms = terms, instRate = rate, instMethod = method,
                instStartDay = Installments.startFrom(next, current, 1, CycleUnit.MONTH, 15).toEpochDay(),
            ),
        )
    }

    @Test fun installmentUsesPerTermAmounts() {
        // 首期收手续费：第 1 期 932，之后每期 500
        val f = Balance.forecast(installment(InstallmentMethod.FEE_UPFRONT, 0.6, balance = 1500.0))!!
        assertEquals(2, f.periods) // 932 + 500
        assertEquals(LocalDate.of(2027, 2, 15), f.rechargeOn)
        assertEquals(432.0, f.rechargeAmount, 1e-9) // 剩 68，再充 432
    }

    @Test fun installmentFullyCovered() {
        val f = Balance.forecast(installment(InstallmentMethod.INTEREST_FREE, 0.0, balance = 2000.0, current = 10))!!
        assertTrue(f.coversAll) // 剩 3 期 × 500
        assertEquals(3, f.periods)
        assertNull(f.rechargeOn)
    }

    @Test fun deductionOnlyForBalancePayments() {
        val s = sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 20.0)
        assertEquals(20.0, Balance.deduction(s, 30.0), 0.0) // 最多扣到 0
        assertFalse(Balance.canPay(s, 30.0))
        assertTrue(Balance.canPay(s, 20.0))
        assertEquals(0.0, Balance.deduction(s.copy(payMethod = PayMethod.ALIPAY), 30.0), 0.0)
    }
}
