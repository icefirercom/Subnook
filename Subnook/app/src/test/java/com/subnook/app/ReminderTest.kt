package com.subnook.app

import com.subnook.app.data.Subscription
import com.subnook.app.domain.RenewalProcessor.Companion.shouldRemind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderTest {
    private val due = LocalDate.of(2026, 11, 20)
    private fun sub(days: Int = 3, last: LocalDate? = null) = Subscription(
        name = "t", amount = 1.0, currency = "CNY", nextBillingDay = due.toEpochDay(), anchorDay = 20,
        remindDaysBefore = days, remindedForDay = last?.toEpochDay(),
    )

    @Test fun defaultIsThreeDays() = assertEquals(3, sub().remindDaysBefore)

    @Test fun remindsEveryDayInWindow() {
        val fired = (14..21).map { d -> LocalDate.of(2026, 11, d) }.filter { shouldRemind(sub(), it, true) }.map { it.dayOfMonth }
        assertEquals(listOf(17, 18, 19, 20), fired)
    }

    @Test fun onlyOncePerDay() {
        val d = LocalDate.of(2026, 11, 18)
        assertFalse(shouldRemind(sub(last = d), d, true))
        assertTrue(shouldRemind(sub(last = d.minusDays(1)), d, true))
    }

    @Test fun payableWithinThreeDaysRegardlessOfAutoRenew() {
        val auto = sub()
        val manual = sub().copy(autoRenew = false)
        assertTrue(com.subnook.app.domain.Billing.isPayable(auto, LocalDate.of(2026, 11, 17)))
        assertTrue(com.subnook.app.domain.Billing.isPayable(manual, LocalDate.of(2026, 11, 22)))
        assertFalse(com.subnook.app.domain.Billing.isPayable(auto, LocalDate.of(2026, 11, 16)))
        assertFalse(com.subnook.app.domain.Billing.isPayable(auto.copy(active = false), LocalDate.of(2026, 11, 19)))
    }

    @Test fun waitsForNotifyTime() = assertFalse(shouldRemind(sub(), LocalDate.of(2026, 11, 18), false))

    @Test fun zeroDaysMeansDueDayOnly() {
        val fired = (18..21).map { d -> LocalDate.of(2026, 11, d) }.filter { shouldRemind(sub(days = 0), it, true) }.map { it.dayOfMonth }
        assertEquals(listOf(20), fired)
    }
}
