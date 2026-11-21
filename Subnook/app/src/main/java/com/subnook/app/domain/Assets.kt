package com.subnook.app.domain

import com.subnook.app.data.Asset
import com.subnook.app.data.AssetStatus
import com.subnook.app.data.RateTable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WarrantyState { NONE, ACTIVE, EXPIRING_SOON, EXPIRED }

/** 实物资产与保修期纯 JVM 领域计算逻辑 */
object Assets {
    /** 内置常见购买渠道（全局可复用、支持设置中启停） */
    val BUILT_IN_PURCHASE_CHANNELS: List<String> = listOf(
        "京东自营", "天猫旗舰店", "拼多多", "官方商城", "线下门店", "闲鱼/二手",
    )

    fun availablePurchaseChannels(custom: List<String>, disabled: Set<String>, current: String = ""): List<String> {
        val cur = current.trim()
        return buildList {
            BUILT_IN_PURCHASE_CHANNELS.forEach { if (it !in disabled || it == cur) add(it) }
            custom.forEach { if ((it !in disabled || it == cur) && it !in this) add(it) }
            if (cur.isNotEmpty() && cur !in this) add(cur)
        }
    }

    /** 按购买日期和保修月数推算保修到期日（0 表示无保修） */
    fun calcWarrantyEnd(purchaseDate: LocalDate, months: Int): LocalDate? =
        if (months <= 0) null else purchaseDate.plusMonths(months.toLong())

    /** 已持有/使用天数（已卖出或报废算到退役日，至少为 1 天） */
    fun usedDays(asset: Asset, today: LocalDate = LocalDate.now()): Long {
        val start = LocalDate.ofEpochDay(asset.purchaseDay)
        val end = if (!asset.isOwned && asset.retiredDay != null) LocalDate.ofEpochDay(asset.retiredDay) else today
        return (ChronoUnit.DAYS.between(start, end) + 1L).coerceAtLeast(1L)
    }

    /** 实际净支出（已出二手时扣减回血金额） */
    fun netCost(asset: Asset): Double =
        if (asset.status == AssetStatus.SOLD) (asset.price - asset.soldAmount).coerceAtLeast(0.0)
        else asset.price.coerceAtLeast(0.0)

    /** 日均使用成本（原币种） */
    fun dailyCost(asset: Asset, today: LocalDate = LocalDate.now()): Double =
        netCost(asset) / usedDays(asset, today).toDouble()

    /** 主保修剩余天数（负数表示已过保，null 表示无保修） */
    fun warrantyDaysLeft(asset: Asset, today: LocalDate = LocalDate.now()): Long? =
        asset.warrantyEndDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }

    /** 扩展/核心部件保修剩余天数 */
    fun extWarrantyDaysLeft(asset: Asset, today: LocalDate = LocalDate.now()): Long? =
        asset.extWarrantyEndDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }

    fun warrantyState(asset: Asset, today: LocalDate = LocalDate.now()): WarrantyState {
        val d = warrantyDaysLeft(asset, today) ?: return WarrantyState.NONE
        val warnWindow = asset.remindDaysBefore.coerceAtLeast(7).toLong()
        return when {
            d < 0 -> WarrantyState.EXPIRED
            d <= warnWindow -> WarrantyState.EXPIRING_SOON
            else -> WarrantyState.ACTIVE
        }
    }

    /** 是否属于「即将过保」（持有中，且主保修或扩展保修在提醒窗口内且未过期） */
    fun isExpiringSoon(asset: Asset, today: LocalDate = LocalDate.now()): Boolean {
        if (!asset.isOwned) return false
        val win = asset.remindDaysBefore.coerceAtLeast(1).toLong()
        val mainHit = warrantyDaysLeft(asset, today)?.let { it in 0..win } == true
        val extHit = extWarrantyDaysLeft(asset, today)?.let { it in 0..win } == true
        return mainHit || extHit
    }

    /**
     * 过保提醒判定（不骚扰设计）：
     * 仅对在用/闲置物品，在提前 N 天、提前 7 天（若 N>=7）和过保当天触发提醒，每天最多一次，过保后不再反复推送。
     */
    fun shouldRemindWarranty(asset: Asset, today: LocalDate, timeReached: Boolean): Boolean {
        if (!asset.isOwned || !asset.remindEnabled || !timeReached) return false
        if (asset.remindedForDay == today.toEpochDay()) return false
        val milestones = buildSet {
            add(asset.remindDaysBefore.coerceAtLeast(0).toLong())
            if (asset.remindDaysBefore >= 7) add(7L)
            add(0L)
        }
        val mainDays = warrantyDaysLeft(asset, today)
        val extDays = extWarrantyDaysLeft(asset, today)
        return (mainDays != null && mainDays in milestones) || (extDays != null && extDays in milestones)
    }

    /** 即将过保排序：按最近过保日近 → 远 */
    fun nearestWarrantyDay(asset: Asset, today: LocalDate = LocalDate.now()): Long {
        val t = today.toEpochDay()
        val candidates = listOfNotNull(asset.warrantyEndDay, asset.extWarrantyEndDay).filter { it >= t }
        return candidates.minOrNull() ?: Long.MAX_VALUE
    }

    /** 保修状态胶囊文案 */
    fun warrantyBadgeText(asset: Asset, today: LocalDate = LocalDate.now()): String {
        val d = warrantyDaysLeft(asset, today) ?: return "无保修"
        return when {
            d < 0 -> "已过保"
            d == 0L -> "今天过保"
            d <= 30L -> "${d}天后过保"
            d >= 365L -> {
                val y = d / 365
                val m = (d % 365) / 30
                if (m > 0) "保修剩${y}年${m}个月" else "保修剩${y}年"
            }
            else -> "保修剩${d}天"
        }
    }

    /** 持有时长文案 */
    fun usedText(asset: Asset, today: LocalDate = LocalDate.now()): String {
        val days = usedDays(asset, today)
        val prefix = if (asset.isOwned) "已用" else "曾用"
        return when {
            days >= 365L -> {
                val y = days / 365
                val m = (days % 365) / 30
                if (m > 0) "${prefix} ${y}年${m}个月" else "${prefix} ${y}年"
            }
            else -> "$prefix $days 天"
        }
    }

    /** 统计在用物品总价值（换算为显示币种） */
    fun totalOwnedValue(assets: List<Asset>, rates: RateTable, currency: String): Double =
        assets.filter { it.isOwned }.sumOf { rates.convert(it.price, it.currency, currency) }

    /** 统计在用物品总日均折旧（换算为显示币种） */
    fun totalOwnedDailyCost(assets: List<Asset>, rates: RateTable, currency: String, today: LocalDate = LocalDate.now()): Double =
        assets.filter { it.isOwned }.sumOf { rates.convert(dailyCost(it, today), it.currency, currency) }
}
