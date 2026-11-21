package com.subnook.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Currencies
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Billing
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.PageHeader
import com.subnook.app.ui.components.Pill
import com.subnook.app.ui.components.RoundIconButton
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Kind { PAID, UPCOMING, OVERDUE, WARRANTY }
private data class Event(
    val key: String,
    val name: String,
    val iconType: com.subnook.app.data.IconType,
    val iconValue: String,
    val color: Int,
    val date: LocalDate,
    val amount: Double,
    val currency: String,
    val kind: Kind,
    val sub: Subscription? = null,
    val asset: com.subnook.app.data.Asset? = null,
    val extraLabel: String = "",
)

@Composable
fun CalendarScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onOpenAsset: (Long) -> Unit = {}) {
    val st by vm.state.collectAsState()
    val today = LocalDate.now()
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    var selected by remember { mutableStateOf(today) }
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val payments by remember(monthStr) { vm.observePaymentsBetween(first.toEpochDay(), last.toEpochDay()) }.collectAsState(emptyList())
    val extra = LocalExtra.current

    val events: Map<LocalDate, List<Event>> = remember(st.subs, st.assets, payments, monthStr) {
        val byId = st.subs.associateBy { it.id }
        val list = mutableListOf<Event>()
        // 过去：实际付款记录
        payments.forEach { p ->
            byId[p.subscriptionId]?.let { s ->
                list += Event("s_${s.id}", s.name, s.iconType, s.iconValue, s.color, LocalDate.ofEpochDay(p.billingDay), p.amount, p.currency, Kind.PAID, sub = s)
            }
        }
        st.active.forEach { s ->
            if (s.isLifetime) return@forEach
            val due = LocalDate.ofEpochDay(s.nextBillingDay)
            // 手动付款逾期未确认
            if (due < today && due >= first && due <= last) {
                list += Event("s_${s.id}", s.name, s.iconType, s.iconValue, s.color, due, s.amount, s.currency, Kind.OVERDUE, sub = s)
            }
            // 今天及以后：预测账单
            val from = maxOf(first, today)
            if (from <= last) Billing.upcoming(s, from, last).forEach { d ->
                list += Event("s_${s.id}", s.name, s.iconType, s.iconValue, s.color, d, Billing.amountOn(s, d), s.currency, Kind.UPCOMING, sub = s)
            }
        }
        // 实物资产保修到期日（不计入月度支出合计，仅做日历提醒标记）
        st.assets.filter { it.isOwned }.forEach { a ->
            a.warrantyEndDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { it in first..last }?.let { d ->
                list += Event("a_${a.id}", a.name, a.iconType, a.iconValue, a.color, d, 0.0, a.currency, Kind.WARRANTY, asset = a, extraLabel = "保修到期")
            }
            a.extWarrantyEndDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { it in first..last }?.let { d ->
                val lbl = a.extWarrantyLabel.ifBlank { "延保" } + "到期"
                list += Event("ax_${a.id}", a.name, a.iconType, a.iconValue, a.color, d, 0.0, a.currency, Kind.WARRANTY, asset = a, extraLabel = lbl)
            }
        }
        list.groupBy { it.date }
    }
    val cur = st.settings.displayCurrency
    val all = events.values.flatten()
    val billEvents = all.filter { it.kind != Kind.WARRANTY }
    val monthTotal = billEvents.sumOf { st.rates.convert(it.amount, it.currency, cur) }
    val paidTotal = billEvents.filter { it.kind == Kind.PAID }.sumOf { st.rates.convert(it.amount, it.currency, cur) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            PageHeader("账单日历", subtitle = "${all.size} 笔账单") {
                RoundIconButton(Icons.Rounded.Today, "回到今天", { monthStr = YearMonth.now().toString(); selected = today })
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "合计 ${Currencies.formatList(monthTotal, cur)}" + if (paidTotal > 0) " · 已付 ${Currencies.formatList(paidTotal, cur)}" else "",
                                style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上个月", { monthStr = month.minusMonths(1).toString() }, size = 38.dp)
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下个月", { monthStr = month.plusMonths(1).toString() }, size = 38.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, d ->
                            Text(
                                d, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                                color = if (i >= 5) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        month, today, selected, events,
                        onSelect = { selected = it },
                        onSwipe = { dir -> monthStr = month.plusMonths(dir.toLong()).toString() },
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), style = MaterialTheme.typography.titleSmall)
                if (selected == today) {
                    Spacer(Modifier.width(8.dp))
                    Pill("今天", MaterialTheme.colorScheme.primary)
                }
            }
        }
        val dayEvents = events[selected].orEmpty()
        if (dayEvents.isEmpty()) {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        "这天没有账单", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(22.dp), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(dayEvents) { e ->
            SoftCard(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = { e.sub?.let { onOpen(it.id) } ?: e.asset?.let { onOpenAsset(it.id) } },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubIcon(e.iconType, e.iconValue, e.color, e.name, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        when (e.kind) {
                            Kind.PAID -> Pill("已付款", extra.success)
                            Kind.OVERDUE -> Pill("逾期未确认", extra.danger)
                            Kind.WARRANTY -> Pill(e.extraLabel.ifBlank { "保修到期" }, MaterialTheme.colorScheme.tertiary)
                            Kind.UPCOMING -> {
                                val s = e.sub
                                Pill(
                                    when {
                                        s == null -> ""
                                        s.isOneTime -> "${s.payMethodLabel} 到期"
                                        s.autoRenew -> "${s.payMethodLabel} 自动扣款"
                                        else -> "${s.payMethodLabel} 手动付款"
                                    },
                                    MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (e.kind == Kind.WARRANTY) {
                        Text(
                            e.asset?.purchaseChannel?.ifBlank { e.asset.category.label } ?: "物品",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(Currencies.formatList(e.amount, e.currency), style = MaterialTheme.typography.titleSmall.merge(TabularNums), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(100.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    events: Map<LocalDate, List<Event>>,
    onSelect: (LocalDate) -> Unit,
    onSwipe: (Int) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // 周一开始
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    var dragSum by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.pointerInput(month) {
            detectHorizontalDragGestures(
                onDragStart = { dragSum = 0f },
                onDragEnd = { if (dragSum > 120) onSwipe(-1) else if (dragSum < -120) onSwipe(1) },
                onHorizontalDrag = { _, d -> dragSum += d },
            )
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    if (idx < 0 || idx >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.74f))
                    } else {
                        val d = month.atDay(idx + 1)
                        DayCell(d, d == today, d == selected, events[d].orEmpty(), Modifier.weight(1f)) { onSelect(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, isToday: Boolean, isSelected: Boolean, ev: List<Event>, modifier: Modifier, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val past = d < LocalDate.now()
    Column(
        modifier
            .aspectRatio(0.74f)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.background(extra.accentBrush)
                    ev.isNotEmpty() -> Modifier.background(cs.surfaceContainer)
                    else -> Modifier
                }
            )
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, cs.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${d.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge.merge(TabularNums),
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isSelected -> Color.White
                isToday -> cs.primary
                past -> cs.onSurfaceVariant.copy(alpha = 0.7f)
                else -> cs.onSurface
            },
        )
        Spacer(Modifier.height(4.dp))
        val shown = ev.distinctBy { it.key }
        if (shown.isNotEmpty()) {
            // 图标叠放，最多 3 个
            Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                val n = minOf(shown.size, 3)
                val step = 9
                Box(Modifier.width((20 + (n - 1) * step).dp)) {
                    shown.take(n).forEachIndexed { i, e ->
                        Box(Modifier.offset(x = (i * step).dp)) {
                            SubIcon(e.iconType, e.iconValue, e.color, e.name, size = 20.dp)
                        }
                    }
                }
            }
            val overdue = ev.any { it.kind == Kind.OVERDUE }
            if (shown.size > 3 || overdue) {
                Spacer(Modifier.height(3.dp))
                if (overdue) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else extra.danger))
                else Text("+${shown.size - 3}", style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else cs.onSurfaceVariant)
            }
        }
    }
}
