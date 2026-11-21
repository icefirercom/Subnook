package com.subnook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private fun DayOfWeek.zhShort(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/**
 * 中文习惯日期选择弹窗：
 * - 纯中文文案（不受系统语言影响）
 * - 周一起始（一 二 三 四 五 六 日），固定 6 行高度切换月份不跳动
 * - 支持左右滑动切月、点击年月直接展开「年份 + 12个月」快选
 * - 顶部提供快捷日期（今天 / 1个月 / 3个月 / 半年 / 1年），选订阅或到期日一步到位
 */
@Composable
fun ChineseDatePickerDialog(
    initial: LocalDate,
    title: String = "选择日期",
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val today = remember { LocalDate.now() }
    var selected by remember(initial) { mutableStateOf(initial) }
    var month by remember(initial) { mutableStateOf(YearMonth.from(initial)) }
    var pickingYm by remember { mutableStateOf(false) }
    var pickerYear by remember(initial) { mutableIntStateOf(initial.year) }

    fun jumpTo(date: LocalDate) {
        selected = date
        month = YearMonth.from(date)
        pickerYear = date.year
        pickingYm = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(shape)
                .background(cs.surfaceContainerLowest)
                .border(1.dp, cs.outlineVariant, shape)
                .padding(18.dp),
        ) {
            // 顶部标题与已选日期
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日 ${selected.dayOfWeek.zhShort()}",
                        style = MaterialTheme.typography.titleLarge.merge(TabularNums),
                        color = cs.onSurface,
                    )
                }
                if (selected != today) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.primaryContainer)
                            .clickable { jumpTo(today) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("今天", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 快捷日期胶囊（以今天为基准）
            val shortcuts = remember(today) {
                listOf(
                    "今天" to today,
                    "1个月后" to today.plusMonths(1),
                    "3个月后" to today.plusMonths(3),
                    "半年后" to today.plusMonths(6),
                    "1年后" to today.plusYears(1),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shortcuts.forEach { (label, target) ->
                    val hit = selected == target
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hit) cs.primaryContainer else cs.surfaceContainer)
                            .clickable { jumpTo(target) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hit) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            fontWeight = if (hit) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = cs.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // 年月控制栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceContainer)
                        .clickable {
                            pickerYear = month.year
                            pickingYm = !pickingYm
                        }
                        .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (pickingYm) "${pickerYear}年" else "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleSmall.merge(TabularNums),
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (pickingYm) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "切换年月",
                        modifier = Modifier.size(18.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (pickingYm) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一年", { pickerYear-- }, size = 34.dp)
                    Spacer(Modifier.width(6.dp))
                    RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一年", { pickerYear++ }, size = 34.dp)
                } else {
                    RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上个月", { month = month.minusMonths(1) }, size = 34.dp)
                    Spacer(Modifier.width(6.dp))
                    RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下个月", { month = month.plusMonths(1) }, size = 34.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (pickingYm) {
                // 快速选年份 + 12 个月网格
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val baseYear = today.year
                        (baseYear - 1..baseYear + 3).forEach { y ->
                            val sel = y == pickerYear
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) cs.primary else cs.surfaceContainer)
                                    .clickable { pickerYear = y }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$y",
                                    style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                                    color = if (sel) cs.onPrimary else cs.onSurface,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    for (r in 0 until 3) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until 4) {
                                val m = r * 4 + c + 1
                                val ym = YearMonth.of(pickerYear, m)
                                val isCur = ym == month
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (isCur) Modifier.background(extra.accentBrush)
                                            else Modifier.background(cs.surfaceContainer),
                                        )
                                        .clickable {
                                            month = ym
                                            val clampedDay = minOf(selected.dayOfMonth, ym.lengthOfMonth())
                                            selected = ym.atDay(clampedDay)
                                            pickingYm = false
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${m}月",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCur) Color.White else cs.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 星期表头（周一起始）
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, d ->
                        Text(
                            d,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i >= 5) cs.tertiary else cs.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 固定 6 行日历网格，支持左右滑动切月
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value - 1
                val daysInMonth = month.lengthOfMonth()
                var dragSum by remember { mutableFloatStateOf(0f) }

                Column(
                    Modifier.pointerInput(month) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onDragEnd = {
                                if (dragSum > 100f) month = month.minusMonths(1)
                                else if (dragSum < -100f) month = month.plusMonths(1)
                            },
                            onHorizontalDrag = { _, delta -> dragSum += delta },
                        )
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (row in 0 until 6) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (col in 0 until 7) {
                                val dayNum = row * 7 + col - lead + 1
                                if (dayNum !in 1..daysInMonth) {
                                    Box(Modifier.weight(1f).aspectRatio(1.1f))
                                } else {
                                    val date = month.atDay(dayNum)
                                    val isSel = date == selected
                                    val isToday = date == today
                                    val isWeekend = col >= 5
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .then(
                                                when {
                                                    isSel -> Modifier.background(extra.accentBrush)
                                                    isToday -> Modifier.border(1.5.dp, cs.primary, CircleShape)
                                                    else -> Modifier
                                                },
                                            )
                                            .clickable { selected = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$dayNum",
                                            style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                                            fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSel -> Color.White
                                                isToday -> cs.primary
                                                isWeekend -> cs.tertiary
                                                else -> cs.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = cs.outlineVariant)
            Spacer(Modifier.height(6.dp))

            // 底部按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(extra.accentBrush)
                        .clickable { onConfirm(selected) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                ) {
                    Text("确定", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}
