package com.subnook.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 历史记录列表（付款记录、充值记录等）：最多同时显示 [maxVisible] 行，多出来的在框里上下滑动，
 * 不会把整个页面无限拉长。每行固定 [rowHeight] 高，这样框的高度刚好是整数行。
 */
@Composable
fun <T> CappedList(
    items: List<T>,
    rowHeight: Dp,
    maxVisible: Int = 3,
    dividerStart: Dp = 16.dp,
    key: ((T) -> Any)? = null,
    /** 一开始滚到第几行（例如分期明细直接显示当前期） */
    startAt: Int = 0,
    /** 「共 N 条」里的量词 */
    unit: String = "条",
    row: @Composable (T) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val divider = 1.dp
    val initial = with(androidx.compose.ui.platform.LocalDensity.current) { ((rowHeight + divider) * startAt.coerceAtLeast(0)).roundToPx() }
    val scroll = rememberScrollState(initial)
    val barColor = cs.outline.copy(alpha = 0.45f)
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = rowHeight * maxVisible + divider * (maxVisible - 1))
            // 滚动条：内容超出时在右侧画一条细圆角条，长度按可见比例，位置跟着滚动
            // （放在 verticalScroll 前面，画在可见区域的坐标里，不跟内容一起滚走）
            .drawWithContent {
                drawContent()
                if (scroll.maxValue > 0 && scroll.maxValue < Int.MAX_VALUE) {
                    val view = size.height
                    val total = view + scroll.maxValue
                    val barH = (view * view / total).coerceAtLeast(24.dp.toPx())
                    val y = (view - barH) * scroll.value / scroll.maxValue
                    val w = 3.dp.toPx()
                    drawRoundRect(
                        barColor, topLeft = Offset(size.width - w - 2.dp.toPx(), y),
                        size = Size(w, barH), cornerRadius = CornerRadius(w / 2),
                    )
                }
            }
            .verticalScroll(scroll),
    ) {
        items.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = dividerStart), thickness = divider, color = cs.outlineVariant)
            if (key != null) {
                androidx.compose.runtime.key(key(t)) { Box(Modifier.fillMaxWidth().height(rowHeight)) { row(t) } }
            } else {
                Box(Modifier.fillMaxWidth().height(rowHeight)) { row(t) }
            }
        }
    }
}
