package com.subnook.app.ui.assets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnook.app.data.Asset
import com.subnook.app.data.AssetStatus
import com.subnook.app.data.Currencies
import com.subnook.app.domain.Assets
import com.subnook.app.domain.WarrantyState
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.UiState
import com.subnook.app.ui.components.CurrencyPickerDialog
import com.subnook.app.ui.components.PageHeader
import com.subnook.app.ui.components.Pill
import com.subnook.app.ui.components.SectionHeader
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.LocalDate

private enum class AssetFilter(val label: String, val test: (Asset) -> Boolean) {
    OWNED("在用", { it.isOwned }),
    ALL("全部", { true }),
    RETIRED("退役", { !it.isOwned }),
}

private enum class AssetSort(val label: String, val short: String) {
    WARRANTY("过保日期", "保修"),
    PURCHASE("入手日期", "购买日"),
    PRICE("入手价格", "价格"),
    DAILY("日均成本", "日均"),
}

@Composable
fun AssetsScreen(
    vm: MainViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val st by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val today = LocalDate.now()
    val cur = st.settings.displayCurrency

    var filter by rememberSaveable { mutableStateOf(AssetFilter.OWNED) }
    var sort by rememberSaveable { mutableStateOf(AssetSort.WARRANTY) }
    var sortMenu by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }

    val expiring = remember(st.assets, today) {
        st.assets.filter { Assets.isExpiringSoon(it, today) }
            .sortedBy { Assets.nearestWarrantyDay(it, today) }
    }

    val shown = remember(st.assets, filter, sort, st.rates, cur, today) {
        val base = st.assets.filter(filter.test)
        when (sort) {
            AssetSort.WARRANTY -> base.sortedWith(
                compareBy<Asset> { !it.isOwned }
                    .thenBy { Assets.nearestWarrantyDay(it, today) }
                    .thenByDescending { it.purchaseDay },
            )
            AssetSort.PURCHASE -> base.sortedByDescending { it.purchaseDay }
            AssetSort.PRICE -> base.sortedByDescending { st.rates.convert(it.price, it.currency, cur) }
            AssetSort.DAILY -> base.sortedByDescending { st.rates.convert(Assets.dailyCost(it, today), it.currency, cur) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(cs.background),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val ownedCount = st.assets.count { it.isOwned }
            PageHeader(
                title = "我的物品",
                subtitle = if (st.assets.isEmpty()) "记录数码家电、保修期与日均成本" else "在用 $ownedCount 件 · 共 ${st.assets.size} 件",
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, spotColor = Color(0xFF165B4C).copy(alpha = 0.45f))
                        .clip(CircleShape)
                        .background(extra.accentBrush)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "添加物品", tint = Color.White) }
            }
        }

        item { AssetHeroCard(st, today, onCurrency = { pickCurrency = true }) }

        if (expiring.isNotEmpty()) {
            item { SectionHeader("即将过保", expiring.size) }
            items(expiring, key = { "exp_${it.id}" }) { a ->
                ExpiringCard(a, today) { onOpen(a.id) }
            }
        }

        if (st.loaded && st.assets.isEmpty()) {
            item { AssetEmptyHint(onAdd) }
        } else if (st.assets.isNotEmpty()) {
            item(key = "toolbar") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            Modifier
                                .height(34.dp)
                                .clip(CircleShape)
                                .background(cs.surfaceContainerHigh)
                                .clickable { sortMenu = true }
                                .padding(start = 12.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sort.short, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Rounded.SwapVert, "排序", Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                        }
                        DropdownMenu(
                            sortMenu, onDismissRequest = { sortMenu = false },
                            shape = RoundedCornerShape(16.dp), containerColor = cs.surfaceContainerLowest,
                        ) {
                            AssetSort.entries.forEach { s ->
                                val sel = s == sort
                                DropdownMenuItem(
                                    text = { Text(s.label, color = if (sel) cs.primary else cs.onSurface, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) },
                                    trailingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = if (sel) cs.primary else Color.Transparent) },
                                    onClick = { sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    AssetFilterSlider(filter) { filter = it }
                }
            }

            if (shown.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text(
                            "暂无「${filter.label}」状态的物品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { a ->
                    Box(Modifier.alpha(if (a.isOwned) 1f else 0.65f)) {
                        AssetCard(a, st, today) { onOpen(a.id) }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(96.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (pickCurrency) {
        CurrencyPickerDialog(cur, onDismiss = { pickCurrency = false }) {
            vm.setCurrency(it)
            pickCurrency = false
        }
    }
}

@Composable
private fun AssetFilterSlider(selected: AssetFilter, onSelect: (AssetFilter) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val segW = 52.dp
    val index = AssetFilter.entries.indexOf(selected)
    val offset by animateDpAsState(segW * index, label = "asset_filter")
    Box(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(3.dp)) {
        Box(Modifier.offset(x = offset).width(segW).height(28.dp).clip(CircleShape).background(extra.accentBrush))
        Row {
            AssetFilter.entries.forEach { f ->
                val sel = f == selected
                Box(
                    Modifier.width(segW).height(28.dp).clip(CircleShape).clickable { onSelect(f) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        f.label, style = MaterialTheme.typography.labelMedium,
                        color = if (sel) Color.White else cs.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetHeroCard(st: UiState, today: LocalDate, onCurrency: () -> Unit) {
    val extra = LocalExtra.current
    val cur = st.settings.displayCurrency
    val owned = st.assets.filter { it.isOwned }
    val totalVal = Assets.totalOwnedValue(st.assets, st.rates, cur)
    val totalDaily = Assets.totalOwnedDailyCost(st.assets, st.rates, cur, today)
    val inWarranty = owned.count {
        val ws = Assets.warrantyState(it, today)
        ws == WarrantyState.ACTIVE || ws == WarrantyState.EXPIRING_SOON
    }
    val shape = RoundedCornerShape(24.dp)
    val white = Color.White
    val warmGold = Color(0xFFE8CBA0)

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color(0xFF133C34), spotColor = Color(0xFF133C34).copy(alpha = if (extra.dark) 0f else 0.32f))
            .clip(shape)
            .background(extra.heroBrush),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                Brush.radialGradient(listOf(warmGold.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.6f),
                radius = size.width * 0.6f, center = Offset(size.width * 0.92f, 0f),
            )
            drawCircle(
                color = warmGold.copy(alpha = 0.10f),
                radius = size.width * 0.44f,
                center = Offset(size.width * 0.95f, size.height * 0.12f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("在用物品总价值", style = MaterialTheme.typography.labelLarge, color = white.copy(alpha = 0.78f))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(white.copy(alpha = 0.16f)).clickable(onClick = onCurrency)
                        .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$cur · ${Currencies.name(cur)}", style = MaterialTheme.typography.labelLarge, color = white)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = white)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Currencies.format(totalVal, cur),
                style = MaterialTheme.typography.displayMedium.merge(TabularNums).copy(fontSize = 40.sp),
                color = white, maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            Row {
                HeroStat("整体日均", "${Currencies.format(totalDaily, cur)}/天", Modifier.weight(1.1f))
                HeroStat("保修期内", "$inWarranty 件", Modifier.weight(0.9f))
                HeroStat("在用件数", "${owned.size} 件", Modifier.weight(0.75f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExpiringCard(a: Asset, today: LocalDate, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val days = Assets.warrantyDaysLeft(a, today)
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SubIcon(a.iconType, a.iconValue, a.color, a.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(Assets.warrantyBadgeText(a, today), if (days != null && days <= 7) extra.danger else extra.warning)
                    if (a.purchaseChannel.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(a.purchaseChannel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp), tint = extra.warning)
        }
    }
}

@Composable
private fun AssetCard(a: Asset, st: UiState, today: LocalDate, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val cur = st.settings.displayCurrency
    val ws = Assets.warrantyState(a, today)
    val daily = Assets.dailyCost(a, today)
    val showCost = if (a.status == AssetStatus.SOLD) Assets.netCost(a) else a.price

    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SubIcon(a.iconType, a.iconValue, a.color, a.name, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    if (a.purchaseChannel.isNotBlank()) add(a.purchaseChannel)
                    add(Assets.usedText(a, today))
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (a.status != AssetStatus.ACTIVE) {
                        Pill(a.status.label, if (a.status == AssetStatus.IDLE) cs.secondary else cs.outline)
                    }
                    when (ws) {
                        WarrantyState.ACTIVE -> Pill(Assets.warrantyBadgeText(a, today), extra.success)
                        WarrantyState.EXPIRING_SOON -> Pill(Assets.warrantyBadgeText(a, today), extra.warning)
                        WarrantyState.EXPIRED -> Pill("已过保", cs.outline)
                        WarrantyState.NONE -> if (a.status == AssetStatus.ACTIVE) Pill(a.category.label, cs.primary)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Currencies.formatList(showCost, a.currency),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Currencies.formatList(daily, a.currency)}/天",
                    style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                    color = cs.primary,
                )
                if (a.currency != cur && showCost > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "≈ " + Currencies.formatList(st.rates.convert(showCost, a.currency, cur), cur),
                        style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                        color = cs.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetEmptyHint(onAdd: () -> Unit) {
    val extra = LocalExtra.current
    SoftCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(extra.accentBrush), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DevicesOther, null, Modifier.size(32.dp), tint = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("还没有实物档案", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "记录手机、空调、电脑等购买渠道、保修期、SN 码与发票凭证，过保前自动提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.clip(CircleShape).background(extra.accentBrush).clickable(onClick = onAdd).padding(horizontal = 22.dp, vertical = 11.dp),
            ) { Text("添加第一件物品", style = MaterialTheme.typography.labelLarge, color = Color.White) }
        }
    }
}
