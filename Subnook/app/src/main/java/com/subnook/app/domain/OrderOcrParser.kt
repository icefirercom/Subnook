package com.subnook.app.domain

import java.time.LocalDate

/** 订单截图 OCR 解析结果 */
data class OrderOcrResult(
    val orderNumber: String? = null,
    /** 其他候选单号（截图中出现多个长数字串时供用户点选切换） */
    val orderCandidates: List<String> = emptyList(),
    val purchaseChannel: String? = null,
    val price: Double? = null,
    val purchaseDate: LocalDate? = null,
    val serialNumber: String? = null,
    val rawText: String = "",
) {
    val hasAnyField: Boolean
        get() = orderNumber != null || purchaseChannel != null || price != null || purchaseDate != null || serialNumber != null
}

/**
 * 线上订单截图文本解析器（纯 JVM，支持京东、淘宝/天猫、拼多多、Apple 官网、小米、抖音商城、闲鱼与电子发票）。
 */
object OrderOcrParser {

    // 订单号标签（匹配同行或下一行）
    private val ORDER_LABEL_RE = Regex(
        """(?:订单编号|订单号|主订单号|子订单号|交易单号|商户单号|Order\s*(?:No\.?|Number|ID|#))\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9\-]{7,31})""",
        RegexOption.IGNORE_CASE,
    )
    private val ORDER_LABEL_ONLY_RE = Regex(
        """^(?:订单编号|订单号|主订单号|交易单号|商户单号|Order\s*(?:No\.?|Number|ID))\s*[:：]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    // 拼多多典型订单号：240618-123456789012345
    private val PDD_ORDER_RE = Regex("""\b\d{6}-\d{12,18}\b""")
    // 通用长数字订单号（12~26位，排除手机11位号、时间戳等明显干扰）
    private val LONG_DIGITS_RE = Regex("""(?<!\d)\d{12,26}(?!\d)""")
    // Apple 订单号：W + 10 位数字
    private val APPLE_ORDER_RE = Regex("""\bW\d{9,12}\b""")

    // 序列号 / SN / IMEI
    private val SERIAL_RE = Regex(
        """(?:序列号|机身码|硬件码|设备码|S/N|SN|IMEI[12]?)\s*[:：#]?\s*([A-Za-z0-9\-]{6,32})""",
        RegexOption.IGNORE_CASE,
    )
    private val SERIAL_LABEL_ONLY_RE = Regex(
        """^(?:序列号|机身码|硬件码|设备码|S/N|SN|IMEI[12]?)\s*[:：]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // 实付金额关键词行
    private val PAY_AMOUNT_LABEL_RE = Regex(
        """(?:实付款|实际付款|实付金额|实付|应付总额|订单总额|合计支付|合计|总计|价税合计|支付金额|Total)\s*[:：]?\s*[¥￥＄${'$'}]?\s*(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val YUAN_AMOUNT_RE = Regex("""[¥￥]\s*(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?)""")
    private val BARE_AMOUNT_RE = Regex("""^\s*[¥￥＄${'$'}]?\s*(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?)\s*元?\s*$""")

    // 下单/付款时间行
    private val DATE_LABEL_RE = Regex(
        """(?:下单时间|付款时间|支付时间|成交时间|创建时间|购买日期|开票日期|Order\s*Date)\s*[:：]?\s*.*?(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})""",
        RegexOption.IGNORE_CASE,
    )
    private val ANY_DATE_RE = Regex("""(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})""")

    fun parse(rawText: String): OrderOcrResult {
        val normalized = rawText.replace("\r", "")
            .replace(Regex("""(\d)\s*[-－]\s*(\d)"""), "$1-$2")
        val lines = normalized.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return OrderOcrResult(rawText = rawText)

        val candidates = linkedSetOf<String>()

        // 1. 优先找带「订单号/订单编号」标签的行（支持同行和紧邻下一行）
        var primaryOrder: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            val m = ORDER_LABEL_RE.find(line)
            if (m != null) {
                val clean = cleanOrderToken(m.groupValues[1])
                if (isPlausibleOrder(clean)) {
                    if (primaryOrder == null) primaryOrder = clean
                    candidates += clean
                }
            } else if (ORDER_LABEL_ONLY_RE.matches(line) && i + 1 < lines.size) {
                val nextToken = lines[i + 1].split(Regex("""\s+""")).firstOrNull().orEmpty().let(::cleanOrderToken)
                if (isPlausibleOrder(nextToken)) {
                    if (primaryOrder == null) primaryOrder = nextToken
                    candidates += nextToken
                }
            }
        }

        // 2. 先提取序列号 / SN / IMEI（避免把 15 位 IMEI 误当作订单号候选）
        val serial = extractSerial(lines, primaryOrder)

        // 3. 补充正则兜底：拼多多格式、Apple W单号、12-26位长数字单号
        PDD_ORDER_RE.findAll(normalized).forEach { candidates += it.value }
        APPLE_ORDER_RE.findAll(normalized).forEach { candidates += it.value }
        LONG_DIGITS_RE.findAll(normalized).forEach { m ->
            val v = m.value
            // 排除时间戳、已识别的序列号以及已存在单号的子串（如拼多多后半段）
            if (!isTimestampLike(v) && v != serial && candidates.none { it.contains(v) }) candidates += v
        }

        if (primaryOrder == null) primaryOrder = candidates.firstOrNull()

        // 4. 购买渠道特征识别
        val channel = detectChannel(normalized)

        // 5. 实付金额提取（优先带「实付/合计」关键词的行，避免误取优惠券金额）
        val price = extractPrice(lines, normalized)

        // 6. 购买/下单日期提取
        val date = extractDate(lines, normalized)

        return OrderOcrResult(
            orderNumber = primaryOrder,
            orderCandidates = candidates.toList().take(5),
            purchaseChannel = channel,
            price = price,
            purchaseDate = date,
            serialNumber = serial,
            rawText = rawText.trim(),
        )
    }

    private fun extractSerial(lines: List<String>, primaryOrder: String?): String? {
        for (i in lines.indices) {
            val m = SERIAL_RE.find(lines[i])
            if (m != null) {
                val v = cleanOrderToken(m.groupValues[1])
                if (isPlausibleSerial(v, primaryOrder)) return v
            } else if (SERIAL_LABEL_ONLY_RE.matches(lines[i]) && i + 1 < lines.size) {
                val next = lines[i + 1].split(Regex("""\s+""")).firstOrNull().orEmpty().let(::cleanOrderToken)
                if (isPlausibleSerial(next, primaryOrder)) return next
            }
        }
        return null
    }

    private fun isPlausibleSerial(s: String, primaryOrder: String?): Boolean =
        s.length in 6..32 &&
            s != primaryOrder &&
            s.all { it.isLetterOrDigit() || it == '-' } &&
            s.count { it.isDigit() } >= 2

    private fun cleanOrderToken(raw: String): String =
        raw.trim()
            .substringBefore('|')
            .substringBefore('｜')
            .removeSuffix("复制")
            .trimEnd(',', '.', '，', '。', ')', '）', ']')
            .trim()

    private fun isPlausibleOrder(s: String): Boolean {
        if (s.length < 8 || s.length > 32) return false
        if (s.count { it.isDigit() } < 6) return false
        return true
    }

    private fun isTimestampLike(s: String): Boolean =
        s.length == 14 && (s.startsWith("202") || s.startsWith("201"))

    private fun detectChannel(text: String): String? = when {
        text.contains("京东自营") || text.contains("JD自营") -> "京东自营"
        text.contains("京东") || text.contains("JD.COM", ignoreCase = true) -> "京东自营"
        text.contains("天猫") -> "天猫旗舰店"
        text.contains("淘宝") || text.contains("淘金币") -> "天猫旗舰店"
        text.contains("拼多多") || PDD_ORDER_RE.containsMatchIn(text) -> "拼多多"
        text.contains("Apple Store", ignoreCase = true) || text.contains("apple.com", ignoreCase = true) || APPLE_ORDER_RE.containsMatchIn(text) -> "官方商城"
        text.contains("小米商城") || text.contains("华为商城") || text.contains("官方商城") -> "官方商城"
        text.contains("闲鱼") -> "闲鱼/二手"
        text.contains("抖音") -> "抖音商城"
        else -> null
    }

    private fun extractPrice(lines: List<String>, rawText: String): Double? {
        for (i in lines.indices) {
            val m = PAY_AMOUNT_LABEL_RE.find(lines[i])
            if (m != null) {
                parseMoney(m.groupValues[1])?.let { return it }
            }
            // 标签与金额分在相邻两行（常见于电商详情左右对齐 OCR 断行）
            if (lines[i].matches(Regex("""^(?:实付款|实际付款|实付金额|实付|应付总额|合计|总计|价税合计)\s*[:：]?$""")) && i + 1 < lines.size) {
                val nextLine = lines[i + 1]
                YUAN_AMOUNT_RE.find(nextLine)?.let { parseMoney(it.groupValues[1]) }?.let { return it }
                BARE_AMOUNT_RE.find(nextLine)?.let { parseMoney(it.groupValues[1]) }?.let { return it }
            }
        }
        // 兜底：取文中最后一个 ¥ 金额（电商详情页底部通常是实付款）
        return YUAN_AMOUNT_RE.findAll(rawText).mapNotNull { parseMoney(it.groupValues[1]) }.lastOrNull()
    }

    private fun parseMoney(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 && it < 10_000_000.0 }

    private fun extractDate(lines: List<String>, rawText: String): LocalDate? {
        for (i in lines.indices) {
            DATE_LABEL_RE.find(lines[i])?.let { m ->
                safeDate(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it }
            }
            if (lines[i].contains("下单时间") || lines[i].contains("付款时间") || lines[i].contains("支付时间") || lines[i].contains("成交时间")) {
                if (i + 1 < lines.size) {
                    ANY_DATE_RE.find(lines[i + 1])?.let { m ->
                        safeDate(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it }
                    }
                }
            }
        }
        return ANY_DATE_RE.find(rawText)?.let { m ->
            safeDate(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
    }

    private fun safeDate(y: String, m: String, d: String): LocalDate? = runCatching {
        val year = y.toInt()
        val month = m.toInt()
        val day = d.toInt()
        if (year in 2010..2035) LocalDate.of(year, month, day) else null
    }.getOrNull()
}
