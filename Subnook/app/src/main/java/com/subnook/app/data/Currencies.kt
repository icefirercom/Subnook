package com.subnook.app.data

data class CurrencyInfo(val code: String, val name: String, val symbol: String)

object Currencies {
    val list = listOf(
        CurrencyInfo("CNY", "人民币", "¥"),
        CurrencyInfo("USD", "美元", "$"),
        CurrencyInfo("EUR", "欧元", "€"),
        CurrencyInfo("HKD", "港币", "HK$"),
        CurrencyInfo("JPY", "日元", "JP¥"),
        CurrencyInfo("GBP", "英镑", "£"),
        CurrencyInfo("TWD", "新台币", "NT$"),
        CurrencyInfo("KRW", "韩元", "₩"),
        CurrencyInfo("SGD", "新加坡元", "S$"),
        CurrencyInfo("MOP", "澳门元", "MOP$"),
        CurrencyInfo("AUD", "澳元", "A$"),
        CurrencyInfo("CAD", "加元", "C$"),
        CurrencyInfo("CHF", "瑞士法郎", "CHF"),
        CurrencyInfo("NZD", "新西兰元", "NZ$"),
        CurrencyInfo("INR", "印度卢比", "₹"),
        CurrencyInfo("RUB", "俄罗斯卢布", "₽"),
        CurrencyInfo("TRY", "土耳其里拉", "₺"),
        CurrencyInfo("THB", "泰铢", "฿"),
        CurrencyInfo("MYR", "马来西亚林吉特", "RM"),
        CurrencyInfo("PHP", "菲律宾比索", "₱"),
        CurrencyInfo("VND", "越南盾", "₫"),
        CurrencyInfo("IDR", "印尼盾", "Rp"),
        CurrencyInfo("ARS", "阿根廷比索", "AR$"),
        CurrencyInfo("BRL", "巴西雷亚尔", "R$"),
        CurrencyInfo("MXN", "墨西哥比索", "MX$"),
        CurrencyInfo("NGN", "尼日利亚奈拉", "₦"),
        CurrencyInfo("EGP", "埃及镑", "E£"),
        CurrencyInfo("PKR", "巴基斯坦卢比", "₨"),
        CurrencyInfo("UAH", "乌克兰格里夫纳", "₴"),
        CurrencyInfo("KZT", "哈萨克斯坦坚戈", "₸"),
        CurrencyInfo("HUF", "匈牙利福林", "Ft"),
    )
    private val byCode = list.associateBy { it.code }

    fun symbol(code: String) = byCode[code]?.symbol ?: "$code "
    fun name(code: String) = byCode[code]?.name ?: code

    /** 0 位小数的币种 */
    private val noDecimals = setOf("JPY", "KRW", "VND", "IDR", "HUF")

    /** 金额的小数位数（分期计算按这个精度四舍五入） */
    fun decimals(code: String): Int = if (code in noDecimals) 0 else 2

    fun format(amount: Double, code: String): String = formatWith(symbol(code), amount, code)

    private fun formatWith(symbol: String, amount: Double, code: String): String {
        // 整数金额不显示小数（CN¥3,000 而不是 CN¥3,000.00），有小数的才保留两位
        val cents = kotlin.math.round(amount * 100).toLong()
        val whole = cents % 100 == 0L
        val digits = if (code in noDecimals || whole) 0 else 2
        val sym = symbol.trim()
        // 以字母结尾的符号（CHF、RM、Rp、Ft）和数字之间留个空格
        val sep = if (sym.lastOrNull()?.isLetter() == true) " " else ""
        return sym + sep + "%,.${digits}f".format(amount)
    }

    /** 带币种代码，例如 "CNY ¥25.00"、"USD $15.49" */
    fun formatCode(amount: Double, code: String): String = code + " " + format(amount, code)

    /** 仅首页订阅列表用：¥/$ 加地区前缀，不再另外显示代码，例如 "CN¥25.00"、"US$20.00"、"JP¥3,000" */
    private val listSymbols = mapOf("CNY" to "CN¥", "USD" to "US$")

    fun listSymbol(code: String): String = (listSymbols[code] ?: symbol(code)).trim()

    fun formatList(amount: Double, code: String): String = formatWith(listSymbol(code), amount, code)

    /** 离线兜底汇率（1 USD = x），首次联网后会被实时汇率覆盖 */
    val fallbackRates: Map<String, Double> = mapOf(
        "USD" to 1.0, "CNY" to 7.12, "EUR" to 0.86, "HKD" to 7.78, "JPY" to 152.0, "GBP" to 0.76,
        "TWD" to 30.5, "KRW" to 1430.0, "SGD" to 1.30, "MOP" to 8.01, "AUD" to 1.53, "CAD" to 1.40,
        "CHF" to 0.80, "NZD" to 1.74, "INR" to 88.5, "RUB" to 81.0, "TRY" to 42.0, "THB" to 32.5,
        "MYR" to 4.20, "PHP" to 58.5, "VND" to 26300.0, "IDR" to 16600.0, "ARS" to 1450.0,
        "BRL" to 5.35, "MXN" to 18.4, "NGN" to 1460.0, "EGP" to 47.5, "PKR" to 282.0, "UAH" to 41.8,
        "KZT" to 525.0, "HUF" to 335.0,
    )
}
