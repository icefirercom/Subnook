package com.subnook.app

import com.subnook.app.data.BackupCodec
import com.subnook.app.data.BackupFile
import com.subnook.app.data.Category
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.SortOrder
import com.subnook.app.data.SubKind
import com.subnook.app.data.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧版本导出的备份必须能导入新版本 */
class BackupCompatTest {

    /** 1.2.0 导出的格式（version 1，有 sortIndex，没有分期字段） */
    private val v120 = """
        {"app":"Subnook","version":1,"exportedAt":1763460000000,
         "settings":{"displayCurrency":"CNY","notifyHour":9,"notifyMinute":0,"period":"MONTH","sort":"MANUAL","sortAsc":true,"theme":"SYSTEM"},
         "subscriptions":[
          {"id":1,"name":"爱奇艺VIP","iconType":"PRESET","iconValue":"iqiyi","color":-16728570,"amount":25,"currency":"CNY","cycleCount":1,"cycleUnit":"MONTH","nextBillingDay":20776,"anchorDay":19,"payMethod":"WECHAT","autoRenew":false,"category":"VIDEO","presetKey":"iqiyi","createdAt":1762000000000,"sortIndex":0},
          {"id":3,"name":"Netflix","iconType":"PRESET","iconValue":"netflix","color":-1767148,"amount":15.49,"currency":"USD","cycleCount":1,"cycleUnit":"MONTH","nextBillingDay":20780,"anchorDay":23,"payMethod":"CREDIT_CARD","autoRenew":true,"category":"VIDEO","remindEnabled":true,"remindDaysBefore":3,"active":true,"note":"","presetKey":"netflix","remindedForDay":null,"overdueNotifiedDay":null,"createdAt":1762172800000,"sortIndex":2}
         ],
         "payments":[{"id":1,"subscriptionId":1,"billingDay":20746,"amount":25,"currency":"CNY","auto":false,"recordedAt":1763000000000}],
         "icons":{}}
    """.trimIndent()

    /** 1.0 时代（改名前、没有 sortIndex / sortAsc / icons） */
    private val v100 = """
        {"app":"${"Sub" + "Keeper"}","version":1,"exportedAt":1760000000000,
         "settings":{"displayCurrency":"USD","notifyHour":8,"notifyMinute":30,"period":"YEAR","sort":"AMOUNT","theme":"DARK"},
         "subscriptions":[{"id":7,"name":"Spotify","amount":11.99,"currency":"USD","nextBillingDay":20800,"anchorDay":5}],
         "payments":[]}
    """.trimIndent()

    @Test fun importsBackupFrom120() {
        val b = BackupCodec.decode(v120)
        assertEquals(2, b.subscriptions.size)
        assertEquals(1, b.payments.size)
        assertEquals(SortOrder.MANUAL, b.settings.sort)
        b.subscriptions.forEach { s ->
            assertEquals(SubKind.SUBSCRIPTION, s.kind)
            assertEquals(0, s.instTerms)
            assertNull(s.instFirstAmount)
        }
        val netflix = b.subscriptions.first { it.id == 3L }
        assertEquals(15.49, netflix.amount, 0.0)
        assertEquals(2, netflix.sortIndex)
    }

    @Test fun importsBackupFrom100UnderOldName() {
        val b = BackupCodec.decode(v100)
        val s = b.subscriptions.single()
        assertEquals("Spotify", s.name)
        assertEquals(0, s.sortIndex)
        assertEquals(Category.OTHER, s.category)
        assertEquals(SubKind.SUBSCRIPTION, s.kind)
        assertNull(b.settings.sortAsc)
        assertTrue(b.icons.isEmpty())
    }

    @Test fun toleratesUnknownFieldsAndEnumValues() {
        // 以后的版本可能多出字段或新的分类：不认识的字段忽略，不认识的分类退回「其他」
        val future = v120.replace("\"category\":\"VIDEO\",\"presetKey\":\"iqiyi\"", "\"category\":\"SPACESHIP\",\"presetKey\":\"iqiyi\",\"futureField\":{\"x\":1}")
        val s = BackupCodec.decode(future).subscriptions.first { it.id == 1L }
        assertEquals(Category.OTHER, s.category)
    }

    @Test fun installmentRoundTrip() {
        val inst = Subscription(
            id = 9, name = "手机分期", amount = 536.0, currency = "CNY", nextBillingDay = 20800, anchorDay = 15,
            kind = SubKind.INSTALLMENT, instPrincipal = 6000.0, instTerms = 12, instRate = 0.6,
            instMethod = InstallmentMethod.FEE_UPFRONT, instFirstAmount = 940.0, instStartDay = 20740,
            instTermOffset = 3, instPaidBefore = 1900.5, instPrincipalBefore = 1500.0, instPaidOffDay = 20790,
        )
        val pay = com.subnook.app.data.PaymentRecord(id = 5, subscriptionId = 9, billingDay = 20790, amount = 1000.0, currency = "CNY", auto = false, term = 4, prepay = true, undo = "{\"name\":\"x\"}")
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(inst), payments = listOf(pay))))
        assertEquals(inst, back.subscriptions.single())
        assertEquals(pay, back.payments.single())
    }

    @Test fun balanceAndTopUpsRoundTrip() {
        val s = Subscription(id = 3, name = "话费", amount = 30.0, currency = "CNY", nextBillingDay = 20800, anchorDay = 1,
            payMethod = com.subnook.app.data.PayMethod.BALANCE, balance = 45.5)
        val pay = com.subnook.app.data.PaymentRecord(id = 1, subscriptionId = 3, billingDay = 20770, amount = 30.0, currency = "CNY", auto = true, balanceUsed = 30.0)
        val top = com.subnook.app.data.TopUp(id = 2, subscriptionId = 3, day = 20760, amount = 100.0, currency = "CNY", recordedAt = 1)
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(s), payments = listOf(pay), topUps = listOf(top))))
        assertEquals(s, back.subscriptions.single())
        assertEquals(pay, back.payments.single())
        assertEquals(top, back.topUps.single())
        // 旧备份没有余额和充值记录
        val old = BackupCodec.decode(v120)
        assertTrue(old.topUps.isEmpty())
        assertTrue(old.subscriptions.all { it.balance == 0.0 })
        assertTrue(old.payments.all { it.balanceUsed == 0.0 })
    }

    @Test fun removedInstallmentCategoryBecomesOther() {
        // 1.2.1～1.2.10 调试版导出的备份里可能有已经去掉的「分期还款」分类
        val dev = v120.replace("\"category\":\"VIDEO\",\"presetKey\":\"iqiyi\"", "\"category\":\"INSTALLMENT\",\"presetKey\":\"iqiyi\"")
        assertEquals(Category.OTHER, BackupCodec.decode(dev).subscriptions.first { it.id == 1L }.category)
    }

    @Test fun otherNotesRoundTripAndLabels() {
        val s = Subscription(
            id = 4, name = "x", amount = 1.0, currency = "CNY", nextBillingDay = 20800, anchorDay = 1,
            payMethod = com.subnook.app.data.PayMethod.OTHER, payMethodNote = " 云闪付 ",
            category = Category.OTHER, categoryNote = "宠物",
        )
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(s)))).subscriptions.single()
        assertEquals(s, back)
        assertEquals("云闪付", back.payMethodLabel)
        assertEquals("宠物", back.categoryLabel)
        // 不是「其他」时忽略填的名称；「其他」没填时显示「其他」
        assertEquals("支付宝", back.copy(payMethod = com.subnook.app.data.PayMethod.ALIPAY).payMethodLabel)
        assertEquals("其他", back.copy(categoryNote = "").categoryLabel)
        // 旧备份没有这三个字段
        assertTrue(BackupCodec.decode(v120).subscriptions.all { it.payMethodNote.isEmpty() && it.categoryNote.isEmpty() && it.payChannel.isEmpty() })
        // 扣款渠道字段往返
        val ch = s.copy(payMethod = com.subnook.app.data.PayMethod.GOOGLE_PLAY, payChannel = "信用卡7727")
        val chBack = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(ch)))).subscriptions.single()
        assertEquals("信用卡7727", chBack.payChannel)
        assertEquals("Google Pay · 信用卡7727", chBack.payMethodLabel)
    }

    @Test fun brokenInstallmentFallsBackToSubscription() {
        val bad = Subscription(name = "x", amount = 10.0, currency = "CNY", nextBillingDay = 20800, anchorDay = 1, kind = SubKind.INSTALLMENT)
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(bad))))
        assertEquals(SubKind.SUBSCRIPTION, back.subscriptions.single().kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherApps() {
        BackupCodec.decode("""{"app":"Other","subscriptions":[],"payments":[]}""")
    }

    @Test fun lifetimeNoExpiryRoundTripAndCompat() {
        val buyout = Subscription(
            id = 12, name = "Typora 买断", amount = 89.0, currency = "CNY",
            nextBillingDay = 20800, anchorDay = 1, kind = SubKind.ONE_TIME,
            autoRenew = false, noExpiry = true, note = "硬件码：ABCD-1234\n账号ID：ice@example.com",
        )
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(subscriptions = listOf(buyout)))).subscriptions.single()
        assertEquals(buyout, back)
        assertTrue(back.noExpiry)
        assertTrue(back.isLifetime)
        // 旧备份缺省为 false，保证向后兼容
        assertTrue(BackupCodec.decode(v120).subscriptions.all { !it.noExpiry && !it.isLifetime })
        assertFalse(BackupCodec.decode(v100).subscriptions.single().noExpiry)
    }

    @Test fun fingerprintIgnoresExportTime() {
        val a = BackupCodec.decode(v120)
        assertEquals(BackupCodec.fingerprint(a), BackupCodec.fingerprint(a.copy(exportedAt = 1)))
    }
}
