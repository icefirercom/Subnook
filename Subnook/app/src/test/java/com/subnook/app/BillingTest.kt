package com.subnook.app

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.RateTable
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.domain.RenewalProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillingTest {
    private fun sub(next: LocalDate, count: Int = 1, unit: CycleUnit = CycleUnit.MONTH, amount: Double = 30.0, cur: String = "CNY") =
        Subscription(name = "t", amount = amount, currency = cur, cycleCount = count, cycleUnit = unit,
            nextBillingDay = next.toEpochDay(), anchorDay = next.dayOfMonth)

    @Test fun monthEndAnchorDoesNotDrift() {
        val s = sub(LocalDate.of(2026, 1, 31))
        val feb = Billing.next(s)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), Billing.next(s, feb))
    }

    @Test fun leapDayYearly() {
        val s = sub(LocalDate.of(2028, 2, 29), unit = CycleUnit.YEAR)
        assertEquals(LocalDate.of(2029, 2, 28), Billing.next(s))
    }

    @Test fun occurrencesInMonth() {
        val s = sub(LocalDate.of(2026, 11, 20), unit = CycleUnit.WEEK)
        val occ = Billing.occurrences(s, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(6, 13, 20, 27), occ.map { it.dayOfMonth })
    }

    @Test fun upcomingSkipsCyclesBeforeNextBillingDay() {
        // 11 月 18 日那期已提前确认付款，下次账单日推到 12 月 18 日：11 月里不该再有待付
        val s = sub(LocalDate.of(2026, 12, 18))
        assertEquals(emptyList<LocalDate>(), Billing.upcoming(s, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 30)))
        assertEquals(listOf(LocalDate.of(2026, 12, 18)), Billing.upcoming(s, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 12, 31)))
        // 下次账单日在区间内时照常返回
        val t = sub(LocalDate.of(2026, 11, 20), unit = CycleUnit.WEEK)
        assertEquals(listOf(20, 27), Billing.upcoming(t, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 30)).map { it.dayOfMonth })
    }

    @Test fun periodCosts() {
        val m = sub(LocalDate.now(), amount = 30.0)
        assertEquals(30.0, Billing.periodCost(m, SummaryPeriod.MONTH), 1e-9)
        assertEquals(360.0, Billing.periodCost(m, SummaryPeriod.YEAR), 1e-9)
        val y = sub(LocalDate.now(), unit = CycleUnit.YEAR, amount = 120.0)
        assertEquals(10.0, Billing.periodCost(y, SummaryPeriod.MONTH), 1e-9)
        val q = sub(LocalDate.now(), count = 3, amount = 90.0)
        assertEquals(30.0, Billing.periodCost(q, SummaryPeriod.MONTH), 1e-9)
    }

    @Test fun currencyConversion() {
        val r = RateTable(mapOf("USD" to 1.0, "CNY" to 7.0, "EUR" to 0.5), 1)
        assertEquals(70.0, r.convert(10.0, "USD", "CNY"), 1e-9)
        assertEquals(14.0, r.convert(1.0, "EUR", "CNY"), 1e-9)
        val total = Billing.total(listOf(sub(LocalDate.now(), amount = 10.0, cur = "USD"), sub(LocalDate.now(), amount = 7.0)), SummaryPeriod.MONTH, r, "CNY")
        assertEquals(77.0, total, 1e-9)
    }

    @Test fun oneTimeSubscriptionBehaviorAndPriority() {
        val due = LocalDate.of(2026, 11, 20)
        val oneTime = sub(due, amount = 99.0).copy(kind = com.subnook.app.data.SubKind.ONE_TIME, autoRenew = false)
        // 非周期订阅不计入周/月/年均摊支出，只在其到期日出现一次
        assertEquals(0.0, Billing.periodCost(oneTime, SummaryPeriod.MONTH), 1e-9)
        assertEquals("非周期", Billing.cycleText(oneTime))
        assertEquals(listOf(due), Billing.occurrences(oneTime, due.minusMonths(2), due.plusMonths(2)))
        assertEquals(emptyList<LocalDate>(), Billing.occurrences(oneTime, due.plusDays(1), due.plusMonths(2)))

        // 待提醒排序：非周期订阅优先于周期订阅（即使周期订阅到期日更早）
        val earlierRecurring = sub(due.minusDays(1))
        val sorted = listOf(earlierRecurring, oneTime).sortedWith(Billing.pendingComparator)
        assertEquals(listOf(oneTime, earlierRecurring), sorted)
    }

    @Test fun lifetimeOneTimeHasNoExpiryOrReminder() {
        val bought = LocalDate.of(2026, 5, 1)
        val buyout = sub(bought, amount = 298.0).copy(
            kind = com.subnook.app.data.SubKind.ONE_TIME,
            autoRenew = false,
            noExpiry = true,
            remindEnabled = true,
            remindDaysBefore = 7,
        )
        assertTrue(buyout.isLifetime)
        assertEquals("买断", Billing.cycleText(buyout))
        assertEquals(0.0, Billing.periodCost(buyout, SummaryPeriod.MONTH), 1e-9)
        assertEquals(emptyList<LocalDate>(), Billing.occurrences(buyout, bought.minusYears(1), bought.plusYears(1)))
        assertFalse(Billing.isPayable(buyout, bought))
        assertFalse(Billing.isPayable(buyout, bought.plusDays(10)))
        assertFalse(RenewalProcessor.shouldRemind(buyout, bought, timeReached = true))
        assertFalse(RenewalProcessor.shouldRemind(buyout, bought.minusDays(1), timeReached = true))
    }
}
