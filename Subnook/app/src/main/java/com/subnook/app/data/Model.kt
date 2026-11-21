package com.subnook.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class CycleUnit(val label: String, val approxDays: Double) {
    DAY("天", 1.0),
    WEEK("周", 7.0),
    MONTH("月", 365.2425 / 12.0),
    YEAR("年", 365.2425),
}

enum class PayMethod(val label: String, val defaultAuto: Boolean) {
    GOOGLE_PLAY("Google Pay", true),
    APP_STORE("App Store", true),
    WECHAT("微信", true),
    ALIPAY("支付宝", true),
    CREDIT_CARD("信用卡", true),
    PAYPAL("PayPal", true),
    BANK_CARD("银行卡", true),
    /** 预付余额（话费、游戏点券、平台钱包等）：记账时从订阅的余额里扣 */
    BALANCE("余额", true),
    MANUAL("手动付款", false),
    OTHER("其他", false),
    // ---- 分期常用（只列持牌机构 / 大平台的正规消费分期，不放网贷）----
    HUABEI("花呗", true),
    JD_BAITIAO("京东白条", true),
    WECHAT_FENFU("微信分付", true),
    MEITUAN_YUEFU("美团月付", true),
    DOUYIN_YUEFU("抖音月付", true),
    BANK_LOAN("银行贷款", true),
    ;

    companion object {
        /** 订阅能选的付款方式（顺序即界面上的顺序） */
        // 「手动付款」不列：手动 / 自动由「自动续费」开关决定，和用什么方式付款无关（旧数据里的 MANUAL 照常显示、统计）
        val forSubscription = listOf(GOOGLE_PLAY, APP_STORE, WECHAT, ALIPAY, CREDIT_CARD, PAYPAL, BANK_CARD, BALANCE, OTHER)

        /** 分期能选的付款方式：应用商店这类分不了期的不列 */
        val forInstallment = listOf(CREDIT_CARD, HUABEI, JD_BAITIAO, WECHAT_FENFU, MEITUAN_YUEFU, DOUYIN_YUEFU, BANK_LOAN, OTHER)
    }
}

/**
 * 订阅和分期共用一套分类（统计页按同一套分类汇总）。
 * 旧的「分期还款」分类已去掉（DB v7 迁移成「其他」），换成几个分期常见的分类。
 */
enum class Category(val label: String) {
    VIDEO("影视"),
    MUSIC("音乐音频"),
    AI("AI"),
    CLOUD("云存储"),
    TOOLS("工具效率"),
    EDU("学习阅读"),
    SHOPPING("购物外卖"),
    SOCIAL("社交"),
    GAME("游戏"),
    VPN("网络"),
    TELECOM("通信运营商"),
    NEWS("新闻资讯"),
    LIFE("生活"),
    DIGITAL("数码产品"),
    APPLIANCE("家电家居"),
    CAR("汽车出行"),
    HOUSING("住房装修"),
    HEALTH("医疗健康"),
    OTHER("其他"),
    ;

    companion object {
        /** 分期的分类：常见的排前面，其余照常可选 */
        val forInstallment: List<Category> = listOf(DIGITAL, APPLIANCE, CAR, HOUSING, EDU, HEALTH, SHOPPING, LIFE).let { first ->
            first + entries.filter { it !in first && it != OTHER } + OTHER
        }
    }
}

/** 记录类型：普通周期订阅 / 非周期订阅（固定到期） / 分期付款 */
enum class SubKind { SUBSCRIPTION, ONE_TIME, INSTALLMENT }

/**
 * 分期的计息方式。[rateLabel] 是费率输入框的名字，null 表示不需要填费率。
 * 金额按最小货币单位（分；无小数的币种按 1）计算，每期四舍五入，零头放在首期，等额本息的尾差放在末期。
 */
enum class InstallmentMethod(val label: String, val rateLabel: String?, val hint: String) {
    INTEREST_FREE("免息", null, "本金平均分到每期，除不尽的零头算在首期"),
    FEE("每期手续费", "每期费率", "信用卡、花呗、白条常见：每期还 本金÷期数 + 本金×每期费率"),
    FEE_UPFRONT("首期收手续费", "每期费率", "手续费（本金×每期费率×期数）在首期一次收取，之后每期只还本金"),
    EQUAL_PAYMENT("等额本息", "年利率", "贷款常见：每期还款额相同，按剩余本金计息，末期多退少补"),
    EQUAL_PRINCIPAL("等额本金", "年利率", "每期还相同本金 + 剩余本金的利息，越还越少"),
}

/** 图标类型：PRESET=内置品牌图(value=预设key) / MATERIAL=设计图标(value=图标名) / CUSTOM=相册图片(value=文件名) / TEXT=文字(value=文字) */
enum class IconType { PRESET, MATERIAL, CUSTOM, TEXT }

@Serializable
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconType: IconType = IconType.TEXT,
    val iconValue: String = "",
    val color: Int = 0xFF00897B.toInt(),
    val amount: Double,
    val currency: String,
    val cycleCount: Int = 1,
    val cycleUnit: CycleUnit = CycleUnit.MONTH,
    /** 下次付款日期 (LocalDate.toEpochDay) */
    val nextBillingDay: Long,
    /** 账单日锚点(1..31)，用于按月/按年计算时保持月末日期不漂移 */
    val anchorDay: Int,
    val payMethod: PayMethod = PayMethod.OTHER,
    val autoRenew: Boolean = true,
    val category: Category = Category.OTHER,
    val remindEnabled: Boolean = true,
    val remindDaysBefore: Int = 3,
    val active: Boolean = true,
    val note: String = "",
    val presetKey: String? = null,
    /** 最后一次发送"即将扣款"提醒的日期（每天最多提醒一次） */
    val remindedForDay: Long? = null,
    /** 手动付款逾期提醒最后发送的日期 */
    val overdueNotifiedDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 手动排序的位置，越小越靠前（DB v2 新增） */
    @ColumnInfo(defaultValue = "0") val sortIndex: Int = 0,
    // ---- DB v3 新增：分期付款。旧备份没有这些字段，按默认值导入就是普通订阅 ----
    @ColumnInfo(defaultValue = "SUBSCRIPTION") val kind: SubKind = SubKind.SUBSCRIPTION,
    /** 分期本金（分期总金额，不含手续费 / 利息） */
    @ColumnInfo(defaultValue = "0") val instPrincipal: Double = 0.0,
    /** 总期数 */
    @ColumnInfo(defaultValue = "0") val instTerms: Int = 0,
    /** 费率（百分数）：手续费方式是每期费率，等额本息 / 等额本金是年利率 */
    @ColumnInfo(defaultValue = "0") val instRate: Double = 0.0,
    @ColumnInfo(defaultValue = "FEE") val instMethod: InstallmentMethod = InstallmentMethod.FEE,
    /** 首期金额（和计算结果对不上时由用户填写账单上的数），null = 按计算 */
    val instFirstAmount: Double? = null,
    /** 第 1 期的还款日 (LocalDate.toEpochDay) */
    @ColumnInfo(defaultValue = "0") val instStartDay: Long = 0,
    // ---- DB v4 新增：提前还款 ----
    // 部分提前还款后，上面的 inst* 字段改成「剩余部分重新计算的新计划」，之前已经还掉的记在下面三个字段里
    /** 之前的计划里已经还完的期数（显示「第 N 期」时加上） */
    @ColumnInfo(defaultValue = "0") val instTermOffset: Int = 0,
    /** 之前已经付出的钱（按期还款 + 提前还款） */
    @ColumnInfo(defaultValue = "0") val instPaidBefore: Double = 0.0,
    /** 之前已经还掉的本金 */
    @ColumnInfo(defaultValue = "0") val instPrincipalBefore: Double = 0.0,
    /** 一次性提前还清的日期，null = 没有提前还清 */
    val instPaidOffDay: Long? = null,
    // ---- DB v6 新增：余额付款（移植自 PR #3，作者 wanwuzhishi）----
    /** 余额付款的剩余余额，币种与订阅相同 */
    @ColumnInfo(defaultValue = "0") val balance: Double = 0.0,
    // ---- DB v7 新增：选「其他」时用户自己填的名称（可以不填）----
    @ColumnInfo(defaultValue = "") val payMethodNote: String = "",
    @ColumnInfo(defaultValue = "") val categoryNote: String = "",
    // ---- DB v8 新增：实际扣款渠道（如 信用卡7727、借记卡2990、微信零钱等，可跨支付平台复用）----
    @ColumnInfo(defaultValue = "") val payChannel: String = "",
) {
    val isInstallment: Boolean get() = kind == SubKind.INSTALLMENT
    val isOneTime: Boolean get() = kind == SubKind.ONE_TIME

    /** 支付平台名称（一级，如 Google Pay / 微信 / 支付宝 / 自定义名称） */
    val payPlatformLabel: String
        get() = if (payMethod == PayMethod.OTHER && payMethodNote.isNotBlank()) payMethodNote.trim() else payMethod.label

    /** 实际扣款渠道（二级，如 信用卡7727、借记卡2990、微信零钱），未填为空 */
    val payChannelLabel: String get() = payChannel.trim()

    /** 统计按实际扣款账户汇总时的名称：填了渠道按渠道聚合，没填退回平台名称 */
    val effectiveChannelLabel: String get() = payChannelLabel.ifBlank { payPlatformLabel }

    /** 显示用的完整付款方式：平台 + 实际扣款渠道（直扣卡且渠道已写明卡类型时不重复前缀） */
    val payMethodLabel: String
        get() {
            val p = payPlatformLabel
            val c = payChannelLabel
            if (c.isEmpty() || c == p) return p
            val redundantCard =
                (payMethod == PayMethod.CREDIT_CARD && c.contains("信用卡")) ||
                    (payMethod == PayMethod.BANK_CARD && (c.contains("借记卡") || c.contains("银行卡") || c.contains("储蓄卡")))
            return if (redundantCard) c else "$p · $c"
        }

    /** 显示 / 统计用的分类：「其他」且填了名称时用填的名称 */
    val categoryLabel: String
        get() = if (category == Category.OTHER && categoryNote.isNotBlank()) categoryNote.trim() else category.label
}

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = Subscription::class,
        parentColumns = ["id"],
        childColumns = ["subscriptionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("subscriptionId"), Index("billingDay")],
)
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    /** 该笔对应的账单日 */
    val billingDay: Long,
    val amount: Double,
    val currency: String,
    /** true=自动续费自动记账，false=手动确认 */
    val auto: Boolean,
    val recordedAt: Long = System.currentTimeMillis(),
    /** 分期：这笔是第几期（DB v4 新增，旧记录为 null） */
    val term: Int? = null,
    /** 分期：提前还款（DB v4 新增） */
    @ColumnInfo(defaultValue = "0") val prepay: Boolean = false,
    /** 这笔付款从订阅余额里扣掉的钱；0 = 不是余额付款（DB v6 新增）。删除记录时退回余额 */
    @ColumnInfo(defaultValue = "0") val balanceUsed: Double = 0.0,
    /** 提前还款前的分期快照（Subscription 的 JSON，DB v5 新增），删除这条记录时用来恢复 */
    val undo: String? = null,
)

/** 余额充值记录（DB v6 新增）。不算支出（支出在扣款时记），删除时从余额里减回去 */
@Serializable
@Entity(
    tableName = "topups",
    foreignKeys = [ForeignKey(
        entity = Subscription::class,
        parentColumns = ["id"],
        childColumns = ["subscriptionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("subscriptionId")],
)
data class TopUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    /** 充值日期 (LocalDate.toEpochDay) */
    val day: Long,
    val amount: Double,
    val currency: String,
    val recordedAt: Long = System.currentTimeMillis(),
)

data class Preset(
    val key: String,
    val name: String,
    val category: Category,
    val colorLong: Long,
    val price: Double,
    val currency: String,
    val cycleCount: Int,
    val cycleUnit: CycleUnit,
    val payMethod: PayMethod,
    val material: String,
) {
    val color: Int get() = colorLong.toInt()
}

/** 用户自定义订阅源（随 AppSettings 持久化并同步进备份） */
@Serializable
data class CustomPreset(
    val key: String,
    val name: String,
    val category: Category = Category.OTHER,
    val colorLong: Long = 0xFF165B4C,
    val price: Double = 0.0,
    val currency: String = "CNY",
    val cycleCount: Int = 1,
    val cycleUnit: CycleUnit = CycleUnit.MONTH,
    val payMethod: PayMethod = PayMethod.ALIPAY,
    val payChannel: String = "",
    val oneTime: Boolean = false,
    val enabled: Boolean = true,
) {
    val color: Int get() = colorLong.toInt()
}
