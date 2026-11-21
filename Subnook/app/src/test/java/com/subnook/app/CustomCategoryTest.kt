package com.subnook.app

import com.subnook.app.data.AppSettings
import com.subnook.app.data.BackupCodec
import com.subnook.app.data.BackupFile
import com.subnook.app.data.cleanCategory
import com.subnook.app.ui.edit.needsAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCategoryTest {
    @Test fun askOnlyForNewNames() {
        val custom = listOf("宠物")
        assertTrue(needsAsk("健身", custom, askedFor = null))
        assertFalse(needsAsk("宠物", custom, null)) // 已经在列表里
        assertFalse(needsAsk("健身", custom, askedFor = "健身")) // 这次已经问过
        assertFalse(needsAsk("", custom, null))
        assertFalse(needsAsk("影视", custom, null)) // 和内置分类重名
    }

    @Test fun cleanName() {
        assertEquals("宠物 用品", cleanCategory("  宠物\n用品 "))
        assertEquals(20, cleanCategory("一".repeat(30)).length)
    }

    @Test fun editPageHasNoManualPayMethod() {
        val f = com.subnook.app.ui.edit.Form()
        f.payMethod = com.subnook.app.data.PayMethod.ALIPAY
        val choices = com.subnook.app.ui.edit.payMethodChoices(f)
        assertFalse(com.subnook.app.data.PayMethod.MANUAL in choices)
        assertFalse(com.subnook.app.data.PayMethod.OTHER in choices) // 「其他」单独放在最后
        // 旧数据正选着「手动付款」时照常显示，改选别的之后就没了
        f.payMethod = com.subnook.app.data.PayMethod.MANUAL
        assertTrue(com.subnook.app.data.PayMethod.MANUAL in com.subnook.app.ui.edit.payMethodChoices(f))
        // 分期没有应用商店
        f.payMethod = com.subnook.app.data.PayMethod.HUABEI
        f.kind = com.subnook.app.data.SubKind.INSTALLMENT
        assertFalse(com.subnook.app.data.PayMethod.GOOGLE_PLAY in com.subnook.app.ui.edit.payMethodChoices(f))
    }

    @Test fun askForPayMethodName() {
        val labels = com.subnook.app.ui.edit.payMethodLabels()
        assertTrue(com.subnook.app.ui.edit.needsAskName("云闪付", emptyList(), null, labels))
        assertFalse(com.subnook.app.ui.edit.needsAskName("支付宝", emptyList(), null, labels))
        assertFalse(com.subnook.app.ui.edit.needsAskName("云闪付", listOf("云闪付"), null, labels))
    }

    @Test fun customCategoriesInBackup() {
        val s = AppSettings(
            customCategories = listOf("宠物", "健身"),
            customPayMethods = listOf("云闪付"),
            customPayChannels = listOf("信用卡7727", "借记卡2990"),
        )
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(settings = s)))
        assertEquals(listOf("宠物", "健身"), back.settings.customCategories)
        assertEquals(listOf("云闪付"), back.settings.customPayMethods)
        assertEquals(listOf("信用卡7727", "借记卡2990"), back.settings.customPayChannels)
        // 旧备份的设置里没有这一项
        val old = BackupCodec.decode("""{"app":"Subnook","settings":{"displayCurrency":"CNY"},"subscriptions":[],"payments":[]}""")
        assertTrue(old.settings.customCategories.isEmpty())
        assertTrue(old.settings.customPayChannels.isEmpty())
    }

    @Test fun payChannelLabelsAndSuggestions() {
        val base = com.subnook.app.data.Subscription(
            name = "YouTube", amount = 13.99, currency = "USD", nextBillingDay = 20800, anchorDay = 1,
            payMethod = com.subnook.app.data.PayMethod.GOOGLE_PLAY, payChannel = "信用卡7727",
        )
        assertEquals("Google Pay", base.payPlatformLabel)
        assertEquals("Google Pay · 信用卡7727", base.payMethodLabel)
        assertEquals("信用卡7727", base.effectiveChannelLabel)

        // 直扣信用卡且渠道名已写明「信用卡」时，避免显示「信用卡 · 信用卡7727」
        val directCard = base.copy(payMethod = com.subnook.app.data.PayMethod.CREDIT_CARD)
        assertEquals("信用卡7727", directCard.payMethodLabel)

        // 微信下优先推荐微信零钱，再去重追加全局卡池
        val suggestions = com.subnook.app.ui.edit.suggestedPayChannels(
            com.subnook.app.data.PayMethod.WECHAT,
            listOf("信用卡7727", "微信零钱", "借记卡2990"),
        )
        assertEquals(listOf("微信零钱", "信用卡7727", "借记卡2990"), suggestions)

        // 停用某个渠道后从推荐中隐藏，但若当前订阅正选着它则保留显示
        val filtered = com.subnook.app.ui.edit.suggestedPayChannels(
            com.subnook.app.data.PayMethod.WECHAT,
            listOf("信用卡7727", "借记卡2990"),
            current = "借记卡2990",
            disabled = setOf("微信零钱", "借记卡2990"),
        )
        assertEquals(listOf("信用卡7727", "借记卡2990"), filtered)
    }

    @Test fun disabledMethodsAndCustomPresetsInBackup() {
        val f = com.subnook.app.ui.edit.Form()
        f.payMethod = com.subnook.app.data.PayMethod.ALIPAY
        val enabled = com.subnook.app.ui.edit.payMethodChoices(f, disabled = setOf("PAYPAL", "ALIPAY"))
        // PAYPAL 未选中且已停用 -> 隐藏；ALIPAY 正在被当前订阅使用 -> 保留显示
        assertFalse(com.subnook.app.data.PayMethod.PAYPAL in enabled)
        assertTrue(com.subnook.app.data.PayMethod.ALIPAY in enabled)

        val cp = com.subnook.app.data.CustomPreset(
            key = "custom_1", name = "自建 VPS", price = 5.0, currency = "USD",
            payMethod = com.subnook.app.data.PayMethod.GOOGLE_PLAY, payChannel = "信用卡7727", oneTime = true, enabled = true,
        )
        val settings = AppSettings(
            disabledPayMethods = listOf("PAYPAL"),
            disabledPayChannels = listOf("花呗"),
            disabledPresets = listOf("netflix"),
            customPresets = listOf(cp),
        )
        val back = BackupCodec.decode(BackupCodec.encode(BackupFile(settings = settings))).settings
        assertEquals(listOf("PAYPAL"), back.disabledPayMethods)
        assertEquals(listOf("花呗"), back.disabledPayChannels)
        assertEquals(listOf("netflix"), back.disabledPresets)
        assertEquals(listOf(cp), back.customPresets)
    }
}
