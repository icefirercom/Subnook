package com.subnook.app.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Currencies
import com.subnook.app.data.CycleUnit
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.SubKind
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Installments
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.format.DateTimeFormatter

private fun String.decimalOnly() = filter { c -> c.isDigit() || c == '.' }

/** 分期：金额、期数、周期、计息方式、费率、首期金额、下次还款日和当前期数 */
@Composable
internal fun InstallmentFields(form: Form, showErrors: Boolean, onCurrency: () -> Unit, onDate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val center = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
    val prepaid = form.original?.takeIf { it.isInstallment && it.instTermOffset > 0 }
    Group("分期信息") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    form.instPrincipal, { form.instPrincipal = it.decimalOnly() }, if (prepaid != null) "剩余本金（提前还款后）" else "分期总金额（本金）",
                    keyboard = KeyboardType.Decimal,
                    isError = showErrors && form.principalValue() == null,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable(onClick = onCurrency)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Currencies.listSymbol(form.currency), style = MaterialTheme.typography.titleMedium)
                    Text(Currencies.name(form.currency), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Field(
                    form.instTerms, { form.instTerms = it.filter(Char::isDigit).take(3) }, null,
                    keyboard = KeyboardType.Number,
                    isError = showErrors && form.termsValue() == null,
                    modifier = Modifier.width(72.dp), textStyle = center,
                )
                Spacer(Modifier.width(8.dp))
                Text("期", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                // 常用期数：平分剩下的宽度，窄屏上也不会把「24」挤成两行
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12, 24).forEach { n ->
                        TermChip("$n", form.instTerms == "$n", Modifier.weight(1f)) { form.instTerms = "$n" }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Field(
                    form.cycleCount, { form.cycleCount = it.filter(Char::isDigit).take(3) }, null,
                    keyboard = KeyboardType.Number,
                    isError = showErrors && (form.cycleCount.toIntOrNull() ?: 0) < 1,
                    modifier = Modifier.width(64.dp), textStyle = center,
                )
                Spacer(Modifier.width(10.dp))
                Segmented(CycleUnit.entries, form.cycleUnit, { it.label }, Modifier.weight(1f)) { form.cycleUnit = it }
                Spacer(Modifier.width(8.dp))
                Text("一期", style = MaterialTheme.typography.titleSmall)
            }

            Column {
                Text("计息方式", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstallmentMethod.entries.forEach { m -> Chip(m.label, form.instMethod == m) { form.instMethod = m } }
                }
            }
            form.instMethod.rateLabel?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(
                        form.instRate, { form.instRate = it.decimalOnly() }, label,
                        keyboard = KeyboardType.Decimal,
                        isError = showErrors && form.rateValue() == null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("%", style = MaterialTheme.typography.titleMedium)
                }
            }
            Field(
                form.instFirst, { form.instFirst = it.decimalOnly() }, "首期金额（可选）",
                    keyboard = KeyboardType.Decimal,
                    isError = showErrors && form.instFirst.isNotBlank() && (form.instFirst.toDoubleOrNull() ?: 0.0) <= 0.0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable(onClick = onDate)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Rounded.CalendarMonth, cs.primary, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("下次还款日期", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(
                        form.next.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", java.util.Locale.CHINA)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.outline)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("这次还第", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Field(
                    form.instCurrent, { form.instCurrent = it.filter(Char::isDigit).take(3) }, null,
                    keyboard = KeyboardType.Number,
                    isError = showErrors && form.currentValue() == null,
                    modifier = Modifier.width(72.dp), textStyle = center,
                )
                Spacer(Modifier.width(8.dp))
                Text("期", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                val cur = form.currentValue()
                val terms = form.termsValue()
                Text(
                    when {
                        cur == null || terms == null -> "填 1 ~ ${terms ?: "期数"}"
                        cur > terms -> "已全部还清"
                        else -> "已还 ${cur - 1} 期"
                    },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** 按表单实时算出的还款计划：本期、剩余、总额、手续费 / 利息、每期明细 */
@Composable
internal fun InstallmentPlan(form: Form) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val principal = form.principalValue()
    val terms = form.termsValue()
    val current = form.currentValue()
    val rate = form.rateValue()
    val cnt = form.cycleCount.toIntOrNull()?.takeIf { it in 1..999 }
    // 还没填完整就不显示这一块
    if (principal == null || terms == null || current == null || rate == null || cnt == null) return
    Group("还款计划") {
        val anchor = form.next.dayOfMonth
        val sub = Subscription(
            name = "", amount = 0.0, currency = form.currency, cycleCount = cnt, cycleUnit = form.cycleUnit,
            nextBillingDay = form.next.toEpochDay(), anchorDay = anchor,
            kind = SubKind.INSTALLMENT, instPrincipal = principal, instTerms = terms, instRate = rate,
            instMethod = form.instMethod, instFirstAmount = form.instFirst.toDoubleOrNull()?.takeIf { it > 0 },
            instStartDay = Installments.startFrom(form.next, current, cnt, form.cycleUnit, anchor).toEpochDay(),
        )
        // 提前还款过的分期：带上之前已还的期数和金额，显示的是整笔分期的数字
        val o = form.original?.takeIf { it.isInstallment }
        val full = if (o == null) sub else sub.copy(
            instTermOffset = o.instTermOffset, instPaidBefore = o.instPaidBefore,
            instPrincipalBefore = o.instPrincipalBefore, instPaidOffDay = o.instPaidOffDay,
        )
        val offset = full.instTermOffset
        val parts = Installments.parts(sub)
        val sched = Installments.schedule(sub)
        val sum = Installments.summary(full)
        fun money(v: Double) = Currencies.formatList(v, form.currency)
        if (Installments.isPaidOff(full)) {
            Column(Modifier.padding(16.dp)) {
                Text("已于 ${sum.endDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} 提前还清", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row {
                    PlanStat("实际共还", money(sum.totalPayment), Modifier.weight(1f))
                    PlanStat("手续费 / 利息", money(sum.totalInterest), Modifier.weight(1f))
                }
            }
            return@Group
        }
        val interestName = when (form.instMethod) {
            InstallmentMethod.EQUAL_PAYMENT, InstallmentMethod.EQUAL_PRINCIPAL -> "利息"
            else -> "手续费"
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (sum.finished) "已全部还清" else "本期应还（第 ${sum.current}/${sum.total} 期）",
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    )
                    Text(
                        money(if (sum.finished) sum.totalPayment else sum.currentAmount),
                        style = MaterialTheme.typography.headlineSmall.merge(TabularNums), fontWeight = FontWeight.Bold,
                    )
                }
                if (!sum.finished) Column(horizontalAlignment = Alignment.End) {
                    Text("剩余 ${sum.remainingTerms} 期", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(money(sum.remainingAmount), style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                }
            }
            Spacer(Modifier.height(14.dp))
            // 两列两行：金额大（如 10 万的贷款）时三列会把数字截断
            Row {
                PlanStat("还款总额", money(sum.totalPayment), Modifier.weight(1f))
                PlanStat("${interestName}合计", money(sum.totalInterest), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                PlanStat("第 ${offset + 1} 期", sched.first().date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), Modifier.weight(1f))
                PlanStat("最后一期", sum.endDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), Modifier.weight(1f))
            }
            // 手续费方式：提示折合的实际年化（每期 0.6% 看着少，年化其实约 13%）
            if (form.instMethod == InstallmentMethod.FEE || form.instMethod == InstallmentMethod.FEE_UPFRONT) {
                Installments.annualizedRate(parts, principal, cnt, form.cycleUnit)?.let { apr ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "年化约 ${"%.2f".format(apr)}%",
                        style = MaterialTheme.typography.bodySmall, color = extra.warning,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "收起每期明细" else "查看每期明细（${sched.size} 期）",
                    style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.weight(1f),
                )
                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = cs.primary)
            }
            // 最多显示 5 期，多的在框里滑动；打开时直接停在当前期附近
            if (expanded) com.subnook.app.ui.components.CappedList(
                sched, rowHeight = 52.dp, maxVisible = 5, dividerStart = 8.dp, key = { it.index }, unit = "期",
                startAt = (sum.current - offset - 2).coerceIn(0, (sched.size - 5).coerceAtLeast(0)),
            ) { t0 ->
                val t = t0.copy(index = t0.index + offset)
                val isCur = t.index == sum.current
                val paid = t.index < sum.current
                val color = if (paid) cs.outline else cs.onSurface
                Row(
                    Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .then(if (isCur) Modifier.background(cs.primary.copy(alpha = 0.10f)) else Modifier)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${t.index}", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = color, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.bodyMedium, color = color)
                        if (t.interest != 0.0) Text(
                            "本金 ${money(t.principal)} + $interestName ${money(t.interest)}",
                            style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = cs.onSurfaceVariant,
                        )
                    }
                    Text(
                        money(t.payment) + if (paid) " 已还" else "",
                        style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = color,
                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TermChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (selected) androidx.compose.ui.graphics.Color.White else cs.onSurface,
        )
    }
}

@Composable
private fun PlanStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(TabularNums), maxLines = 1)
    }
}
