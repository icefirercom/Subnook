package com.subnook.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.mutableStateOf
import com.subnook.app.ui.components.CurrencyPickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.PayMethod
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.UiState
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.LocalDate
import java.time.YearMonth

/** 统计图表配色：低饱和矿物与植物色系，契合暖纸墨绿账本风 */
private val ChartColors = listOf(
    Color(0xFF165B4C), Color(0xFFC26D2D), Color(0xFF2B6CB0), Color(0xFFC8433A), Color(0xFF2E8B7A),
    Color(0xFF8C5A3C), Color(0xFF6B8E23), Color(0xFFD9822B), Color(0xFF4A6FA5), Color(0xFF9C4A5E),
    Color(0xFF5F7161), Color(0xFFB5832F), Color(0xFF3A7D8C), Color(0xFF7B6B5B),
)

private data class Slice(val label: String, val value: Double, val color: Color, val count: Int)

@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val st by vm.state.collectAsState()
    val payments by vm.observeAllPayments().collectAsState(emptyList())
    val cs = MaterialTheme.colorScheme
    val cur = st.settings.displayCurrency
    val active = st.active
    var pickCurrency by remember { mutableStateOf(false) }
    var groupByChannel by remember { mutableStateOf(true) }
    fun monthly(s: Subscription) = st.rates.convert(Billing.periodCost(s, SummaryPeriod.MONTH), s.currency, cur)
    val monthTotal = active.sumOf(::monthly)

    // 分类
    val byCategory = active.groupBy { it.categoryLabel }.map { (c, l) -> c to l }
        .sortedByDescending { (_, l) -> l.sumOf(::monthly) }
        .mapIndexed { i, (c, l) -> Slice(c, l.sumOf(::monthly), ChartColors[i % ChartColors.size], l.size) }

    // 未来 12 个月实际要付
    val today = LocalDate.now()
    val months = (0 until 12).map { YearMonth.from(today).plusMonths(it.toLong()) }
    val perMonth = months.map { ym ->
        val from = maxOf(ym.atDay(1), today)
        val to = ym.atEndOfMonth()
        active.sumOf { s -> st.rates.convert(Billing.upcomingCost(s, from, to), s.currency, cur) }
    }

    // 已付
    val byId = st.subs.associateBy { it.id }
    val paidThisYear = payments.filter { LocalDate.ofEpochDay(it.billingDay).year == today.year }
        .sumOf { st.rates.convert(it.amount, it.currency, cur) }
    val paidAll = payments.sumOf { st.rates.convert(it.amount, it.currency, cur) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("支出统计", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PeriodTiles(st, onCurrency = { pickCurrency = true }) }

            if (active.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text("还没有启用中的订阅，添加后这里会显示统计。", Modifier.padding(24.dp), color = cs.onSurfaceVariant)
                    }
                }
                return@LazyColumn
            }

            item {
                Group("按分类（每月）") {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Donut(byCategory, Modifier.size(128.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${byCategory.size}", style = MaterialTheme.typography.headlineSmall)
                                    Text("个分类", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(18.dp))
                            byCategory.firstOrNull()?.let { top ->
                                Column(Modifier.weight(1f)) {
                                    Text("花得最多", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(top.color))
                                        Spacer(Modifier.width(6.dp))
                                        Text(top.label, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "每月 ${Currencies.format(top.value, cur)} · ${top.count} 个",
                                        style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = cs.onSurfaceVariant,
                                    )
                                    Text(
                                        "占 ${if (monthTotal > 0) kotlin.math.round(top.value / monthTotal * 100).toInt() else 0}%",
                                        style = MaterialTheme.typography.headlineSmall, color = top.color,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            byCategory.forEach { s -> LegendRow(s, monthTotal, cur) }
                        }
                    }
                }
            }

            item {
                Group("未来 12 个月实际待付") {
                    Column(Modifier.padding(16.dp)) {
                        val sum = perMonth.sum()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Currencies.format(sum, cur), style = MaterialTheme.typography.headlineSmall.merge(TabularNums))
                            Spacer(Modifier.width(8.dp))
                            Text("合计 · 最高 ${months[perMonth.indexOf(perMonth.max())].monthValue} 月", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        MonthBars(months, perMonth, cur)
                    }
                }
            }

            item {
                Group("花费排行（每月）") {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        active.sortedByDescending(::monthly).take(10).forEachIndexed { i, s ->
                            RankRow(i + 1, s, monthly(s), monthTotal, cur) { onOpen(s.id) }
                        }
                    }
                }
            }

            item {
                val hasChannel = active.any { it.payChannelLabel.isNotEmpty() }
                val useChannel = hasChannel && groupByChannel
                val byMethod = active.groupBy { if (useChannel) it.effectiveChannelLabel else it.payPlatformLabel }
                    .map { (m, l) -> m to l }.sortedByDescending { (_, l) -> l.sumOf(::monthly) }
                    .mapIndexed { i, (m, l) -> Slice(m, l.sumOf(::monthly), ChartColors[(i + 1) % ChartColors.size], l.size) }
                Group(if (useChannel) "按实际扣款渠道（每月）" else "按支付平台（每月）") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasChannel) {
                            Row(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainer).padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                listOf(true to "按扣款渠道", false to "按支付平台").forEach { (isCh, label) ->
                                    val sel = groupByChannel == isCh
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                            .then(if (sel) Modifier.background(cs.surfaceContainerLowest) else Modifier)
                                            .clickable { groupByChannel = isCh }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (sel) cs.primary else cs.onSurfaceVariant,
                                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                        StackBar(byMethod)
                        byMethod.forEach { s -> LegendRow(s, monthTotal, cur) }
                    }
                }
            }

            // 有分期时：订阅和分期各占多少（两者都已计入上面的总额和各项统计）
            if (active.any { it.isInstallment }) item {
                val subs = active.filter { !it.isInstallment }
                val inst = active.filter { it.isInstallment }
                val parts = listOf(
                    Slice("订阅", subs.sumOf(::monthly), ChartColors[0], subs.size),
                    Slice("分期还款", inst.sumOf(::monthly), ChartColors[7], inst.size),
                ).filter { it.count > 0 }
                val left = inst.sumOf { st.rates.convert(com.subnook.app.domain.Installments.summary(it).remainingAmount, it.currency, cur) }
                Group("订阅 / 分期（每月）") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StackBar(parts)
                        parts.forEach { s -> LegendRow(s, monthTotal, cur) }
                        Text(
                            "分期剩余待还 ${Currencies.format(left, cur)}",
                            style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                val auto = active.filter { it.autoRenew }
                val manual = active.filter { !it.autoRenew }
                val extra = LocalExtra.current
                val parts = listOf(
                    Slice("自动续费", auto.sumOf(::monthly), extra.success, auto.size),
                    Slice("手动付款", manual.sumOf(::monthly), extra.warning, manual.size),
                ).filter { it.count > 0 }
                Group("自动 / 手动（每月）") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StackBar(parts)
                        parts.forEach { s -> LegendRow(s, monthTotal, cur) }
                    }
                }
            }

            item {
                val byCur = active.groupBy { it.currency }.map { (c, l) -> c to l }
                    .sortedByDescending { (_, l) -> l.sumOf(::monthly) }
                Group("按原始币种（每月）") {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        byCur.forEach { (c, l) ->
                            val orig = l.sumOf { Billing.periodCost(it, SummaryPeriod.MONTH) }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                // 只有这一栏显示币种代码，方便区分 ¥(CNY) 和 JP¥ 等
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                                ) { Text(c, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${Currencies.name(c)} · ${l.size} 个", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Currencies.format(orig, c), style = MaterialTheme.typography.titleSmall.merge(TabularNums))
                                    if (c != cur) Text("≈ " + Currencies.format(l.sumOf(::monthly), cur), style = MaterialTheme.typography.labelSmall, color = cs.outline)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Group("已付款记录") {
                    Row(Modifier.padding(16.dp)) {
                        StatCell("今年已付", Currencies.format(paidThisYear, cur), Modifier.weight(1f))
                        StatCell("累计已付", Currencies.format(paidAll, cur), Modifier.weight(1f))
                        StatCell("付款笔数", "${payments.size}", Modifier.weight(0.7f))
                    }
                    if (payments.isNotEmpty()) {
                        // 最多显示 3 条，多的在框里滑动（以前只显示最近 5 条）
                        val recent = payments.filter { byId.containsKey(it.subscriptionId) }
                        com.subnook.app.ui.components.CappedList(recent, rowHeight = 44.dp, key = { it.id }) { p ->
                            val s = byId.getValue(p.subscriptionId)
                            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                SubIcon(s.iconType, s.iconValue, s.color, s.name, size = 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    LocalDate.ofEpochDay(p.billingDay).let { "${it.monthValue}月${it.dayOfMonth}日  " },
                                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                                )
                                Text(Currencies.format(p.amount, p.currency), style = MaterialTheme.typography.labelLarge.merge(TabularNums))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    if (pickCurrency) {
        CurrencyPickerDialog(cur, onDismiss = { pickCurrency = false }) {
            vm.setCurrency(it); pickCurrency = false
        }
    }
}

@Composable
private fun PeriodTiles(st: UiState, onCurrency: () -> Unit) {
    val extra = LocalExtra.current
    val cur = st.settings.displayCurrency
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(extra.heroBrush).padding(18.dp)) {
        Column {
            // 每月作为主数字；每周/每年/每天放到下面一排。币种在右上角切换（与首页紫色卡片一致），金额里不再带代码
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每月平均支出", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onCurrency)
                        .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$cur · ${Currencies.name(cur)}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Currencies.format(st.total(SummaryPeriod.MONTH), cur),
                style = MaterialTheme.typography.headlineMedium.merge(TabularNums), color = Color.White, maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                listOf(
                    "每周" to st.total(SummaryPeriod.WEEK),
                    "每年" to st.total(SummaryPeriod.YEAR),
                    "每天" to st.total(SummaryPeriod.YEAR) / 365.2425,
                ).forEachIndexed { i, (label, v) ->
                    Column(Modifier.weight(if (i == 1) 1.25f else 1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
                        Text(
                            Currencies.format(v, cur),
                            style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(slices: List<Slice>, modifier: Modifier, center: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices.size) { anim.animateTo(1f, tween(700)) }
    val track = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: return@Canvas
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { s ->
                val sweep = (s.value / total * 360f).toFloat() * anim.value
                drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

@Composable
private fun LegendRow(s: Slice, total: Double, cur: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
        Spacer(Modifier.width(8.dp))
        Text(
            if (s.count > 0) "${s.label}  ·  ${s.count} 个" else s.label,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${if (total > 0) kotlin.math.round(s.value / total * 100).toInt() else 0}%",
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp), textAlign = TextAlign.End,
        )
        Text(
            Currencies.format(s.value, cur), style = MaterialTheme.typography.labelLarge.merge(TabularNums),
            modifier = Modifier.width(92.dp), textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

@Composable
private fun StackBar(parts: List<Slice>) {
    val total = parts.sumOf { it.value }
    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (total > 0) parts.forEach { s ->
            val w = (s.value / total).toFloat()
            if (w > 0f) Box(Modifier.weight(w).fillMaxHeight().background(s.color))
        }
    }
}

@Composable
private fun MonthBars(months: List<YearMonth>, values: List<Double>, cur: String) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    var selected by androidx.compose.runtime.remember { mutableIntStateOf(values.indexOf(values.max())) }
    Column {
        Text(
            "${months[selected].year}年${months[selected].monthValue}月：${Currencies.format(values[selected], cur)}",
            style = MaterialTheme.typography.labelLarge, color = cs.primary,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val frac = (v / max).toFloat().coerceAtLeast(0.03f)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) { selected = i },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .then(if (i == selected) Modifier.background(extra.accentBrush) else Modifier.background(cs.primary.copy(alpha = 0.22f))),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            months.forEachIndexed { i, m ->
                Text(
                    "${m.monthValue}", Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (i == selected) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (i == selected) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, s: Subscription, value: Double, total: Double, cur: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val frac = if (total > 0) (value / total).toFloat() else 0f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.width(22.dp))
        SubIcon(s.iconType, s.iconValue, s.color, s.name, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Currencies.format(value, cur), style = MaterialTheme.typography.labelLarge.merge(TabularNums))
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(cs.surfaceContainer)) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).fillMaxHeight().clip(CircleShape).background(cs.primary.copy(alpha = 0.75f)))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Suppress("unused") private val keepImports = listOf(Category.OTHER, PayMethod.OTHER)
