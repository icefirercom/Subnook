package com.subnook.app

import com.subnook.app.domain.OrderOcrParser
import com.subnook.app.ui.components.extractCopyableNoteFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrderOcrParserTest {

    @Test fun parsesJdOrderScreenshot() {
        val raw = """
            已完成
            京东自营旗舰店
            Apple iPhone 16 Pro 256GB 沙漠色钛金属
            商品总额 ¥8999.00
            优惠券 -¥500.00
            实付款：¥8499.00
            订单编号：295847392018 复制
            下单时间：2026-09-20 10:12:33
            序列号：F2LZN123ABCD
        """.trimIndent()

        val res = OrderOcrParser.parse(raw)
        assertTrue(res.hasAnyField)
        assertEquals("295847392018", res.orderNumber)
        assertEquals("京东自营", res.purchaseChannel)
        assertEquals(8499.0, res.price!!, 1e-6)
        assertEquals(LocalDate.of(2026, 9, 20), res.purchaseDate)
        assertEquals("F2LZN123ABCD", res.serialNumber)
    }

    @Test fun parsesTaobaoMultilineLabelAndValue() {
        val raw = """
            天猫官方旗舰店
            格力 1.5匹 新一级能效挂机空调
            商品总价 ¥3299.00
            实付款
            2799.50
            订单编号
            2219485739201847293|复制
            成交时间
            2026/06/18 20:01:05
        """.trimIndent()

        val res = OrderOcrParser.parse(raw)
        assertEquals("2219485739201847293", res.orderNumber)
        assertEquals("天猫旗舰店", res.purchaseChannel)
        assertEquals(2799.5, res.price!!, 1e-6)
        assertEquals(LocalDate.of(2026, 6, 18), res.purchaseDate)
    }

    @Test fun parsesPddHyphenatedOrderWithoutDuplicateSuffix() {
        val raw = """
            拼多多百亿补贴
            Sony WH-1000XM5 降噪耳机
            实付金额 ¥1,699.00
            订单号：240618 - 123456789012345
            付款时间：2026年6月18日 08:30
        """.trimIndent()

        val res = OrderOcrParser.parse(raw)
        assertEquals("240618-123456789012345", res.orderNumber)
        assertEquals(listOf("240618-123456789012345"), res.orderCandidates)
        assertEquals("拼多多", res.purchaseChannel)
        assertEquals(1699.0, res.price!!, 1e-6)
        assertEquals(LocalDate.of(2026, 6, 18), res.purchaseDate)
    }

    @Test fun parsesAppleStoreAndExcludesImeiFromOrderCandidates() {
        val raw = """
            Apple Store 订单详情
            Order Number: W1234567890
            Order Date: 2026-10-05
            Total: ¥7999.00
            IMEI1: 356789123456789
        """.trimIndent()

        val res = OrderOcrParser.parse(raw)
        assertEquals("W1234567890", res.orderNumber)
        assertEquals("官方商城", res.purchaseChannel)
        assertEquals(7999.0, res.price!!, 1e-6)
        assertEquals(LocalDate.of(2026, 10, 5), res.purchaseDate)
        assertEquals("356789123456789", res.serialNumber)
        assertFalse(res.orderCandidates.contains("356789123456789"))
    }

    @Test fun emptyOrUnrelatedTextReturnsNoFields() {
        val res = OrderOcrParser.parse("今天天气不错\n去散步了")
        assertFalse(res.hasAnyField)
        assertNull(res.orderNumber)
        assertNull(res.price)
    }

    @Test fun extractsCopyableKeyValueLinesFromNotes() {
        val note = """
            账号ID：icefirer_01
            硬件码: HW-9928-ABCD
            普通说明文字没有冒号
            激活码：XXXX-YYYY-ZZZZ
            空值行：
        """.trimIndent()

        val fields = extractCopyableNoteFields(note)
        assertEquals(
            listOf(
                "账号ID" to "icefirer_01",
                "硬件码" to "HW-9928-ABCD",
                "激活码" to "XXXX-YYYY-ZZZZ",
            ),
            fields,
        )
    }
}
