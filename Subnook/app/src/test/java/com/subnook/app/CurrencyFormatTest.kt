package com.subnook.app

import com.subnook.app.data.Currencies
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatTest {
    @Test fun regionSymbols() {
        assertEquals("CN¥25", Currencies.formatList(25.0, "CNY"))
        assertEquals("US$20", Currencies.formatList(20.0, "USD"))
        assertEquals("JP¥960", Currencies.formatList(960.0, "JPY"))
        assertEquals("HK$128", Currencies.formatList(128.0, "HKD"))
        assertEquals("€5", Currencies.formatList(5.0, "EUR"))
        assertEquals("CHF 12", Currencies.formatList(12.0, "CHF"))
    }

    @Test fun decimalsOnlyWhenNeeded() {
        assertEquals("CN¥3,000", Currencies.formatList(3000.0, "CNY"))
        assertEquals("US$15.49", Currencies.formatList(15.49, "USD"))
        assertEquals("CN¥133.59", Currencies.formatList(133.591, "CNY"))
        assertEquals("CN¥80.10", Currencies.formatList(80.1, "CNY"))
        assertEquals("CN¥100", Currencies.formatList(99.999, "CNY"))
        assertEquals("¥3,267.42", Currencies.format(3267.42, "CNY"))
        assertEquals("¥0", Currencies.format(0.0, "CNY"))
    }

    @Test fun forintLikeOtherLetterSymbols() {
        assertEquals("Ft 2,990", Currencies.format(2990.0, "HUF"))
        assertEquals("Ft 2,990", Currencies.formatList(2990.4, "HUF"))
    }
}
