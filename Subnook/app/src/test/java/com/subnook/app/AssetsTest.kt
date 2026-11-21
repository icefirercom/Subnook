package com.subnook.app

import com.subnook.app.data.Asset
import com.subnook.app.data.AssetStatus
import com.subnook.app.data.BackupCodec
import com.subnook.app.data.BackupFile
import com.subnook.app.data.Category
import com.subnook.app.data.RateTable
import com.subnook.app.domain.Assets
import com.subnook.app.domain.WarrantyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssetsTest {
    private val today = LocalDate.of(2026, 11, 21)

    @Test fun warrantyCalculationAndStates() {
        val buy = LocalDate.of(2025, 12, 1)
        assertNull(Assets.calcWarrantyEnd(buy, 0))
        assertEquals(LocalDate.of(2026, 12, 1), Assets.calcWarrantyEnd(buy, 12))
        assertEquals(LocalDate.of(2031, 12, 1), Assets.calcWarrantyEnd(buy, 72))

        val soon = Asset(name = "手机", price = 6000.0, purchaseDay = buy.toEpochDay(), warrantyEndDay = LocalDate.of(2026, 12, 1).toEpochDay(), remindDaysBefore = 30)
        assertEquals(10L, Assets.warrantyDaysLeft(soon, today))
        assertEquals(WarrantyState.EXPIRING_SOON, Assets.warrantyState(soon, today))
        assertTrue(Assets.isExpiringSoon(soon, today))

        val expired = soon.copy(warrantyEndDay = today.minusDays(1).toEpochDay())
        assertEquals(WarrantyState.EXPIRED, Assets.warrantyState(expired, today))
        assertFalse(Assets.isExpiringSoon(expired, today))
    }

    @Test fun dailyCostAndSoldNetCost() {
        val buy = today.minusDays(99) // 含当天共 100 天
        val phone = Asset(name = "iPhone", price = 5000.0, purchaseDay = buy.toEpochDay())
        assertEquals(100L, Assets.usedDays(phone, today))
        assertEquals(50.0, Assets.dailyCost(phone, today), 1e-6)

        // 已出二手回血 2000，净支出 3000，日均 30
        val sold = phone.copy(status = AssetStatus.SOLD, retiredDay = today.toEpochDay(), soldAmount = 2000.0)
        assertEquals(3000.0, Assets.netCost(sold), 1e-6)
        assertEquals(30.0, Assets.dailyCost(sold, today), 1e-6)
        assertFalse(sold.isOwned)
    }

    @Test fun warrantyReminderFiresOnlyOnMilestones() {
        val end = LocalDate.of(2026, 12, 21)
        val ac = Asset(
            name = "格力空调", price = 2699.0, category = Category.APPLIANCE,
            purchaseDay = LocalDate.of(2020, 12, 21).toEpochDay(),
            warrantyEndDay = end.toEpochDay(),
            remindEnabled = true, remindDaysBefore = 30,
        )
        // 提前 30 天（11月21日）触发，提前 29 天不重复轰炸，提前 7 天触发，当天触发，过保次日不触发
        assertTrue(Assets.shouldRemindWarranty(ac, end.minusDays(30), true))
        assertFalse(Assets.shouldRemindWarranty(ac, end.minusDays(29), true))
        assertTrue(Assets.shouldRemindWarranty(ac, end.minusDays(7), true))
        assertTrue(Assets.shouldRemindWarranty(ac, end, true))
        assertFalse(Assets.shouldRemindWarranty(ac, end.plusDays(1), true))
        // 同一天已提醒不再重复
        assertFalse(Assets.shouldRemindWarranty(ac.copy(remindedForDay = end.toEpochDay()), end, true))
        // 已退役不提醒
        assertFalse(Assets.shouldRemindWarranty(ac.copy(status = AssetStatus.SOLD), end, true))
    }

    @Test fun purchaseChannelsAndCurrencyTotals() {
        val list = Assets.availablePurchaseChannels(
            custom = listOf("山姆", "京东自营"),
            disabled = setOf("拼多多"),
            current = "拼多多",
        )
        assertTrue("山姆" in list)
        assertTrue("拼多多" in list) // 当前选中项即使已停用仍可见
        assertEquals(list.distinct().size, list.size)

        val rates = RateTable(mapOf("USD" to 1.0, "CNY" to 7.0), 1)
        val a1 = Asset(name = "A", price = 100.0, currency = "USD", purchaseDay = today.toEpochDay())
        val a2 = Asset(name = "B", price = 300.0, currency = "CNY", purchaseDay = today.toEpochDay())
        val a3 = Asset(name = "C", price = 999.0, currency = "CNY", purchaseDay = today.toEpochDay(), status = AssetStatus.SOLD)
        assertEquals(1000.0, Assets.totalOwnedValue(listOf(a1, a2, a3), rates, "CNY"), 1e-6)
    }

    @Test fun assetBackupRoundTrip() {
        val asset = Asset(
            id = 5, name = "格力云佳 1.5P", iconValue = "AcUnit", category = Category.APPLIANCE,
            price = 2699.0, currency = "CNY", purchaseDay = LocalDate.of(2024, 6, 18).toEpochDay(),
            purchaseChannel = "京东自营", payChannel = "信用卡7727",
            warrantyEndDay = LocalDate.of(2030, 6, 18).toEpochDay(),
            extWarrantyEndDay = LocalDate.of(2034, 6, 18).toEpochDay(),
            extWarrantyLabel = "压缩机保修", serialNumber = "SN123456", orderNumber = "JD20240618",
            photos = "ap_1.jpg",
        )
        val file = BackupFile(assets = listOf(asset), assetPhotos = mapOf("ap_1.jpg" to "AQID"))
        val back = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals(listOf(asset), back.assets)
        assertEquals(mapOf("ap_1.jpg" to "AQID"), back.assetPhotos)
    }
}
