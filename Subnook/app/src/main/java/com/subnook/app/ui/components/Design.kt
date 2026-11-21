package com.subnook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subnook.app.ui.theme.LocalExtra

/** 暖纸账本卡片：细腻 1dp 纸感描边 + 克制的柔投影 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = LocalExtra.current
    Column(
        modifier
            .then(
                if (extra.dark) Modifier
                else Modifier.shadow(6.dp, shape, ambientColor = extra.cardShadow, spotColor = extra.cardShadow),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** 大标题页头：标题 + 副标题 + 右侧操作 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        actions()
    }
}

/** 圆形浅色图标按钮 */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** 小标题 + 计数胶囊 */
@Composable
fun SectionHeader(title: String, count: Int? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 彩色小胶囊 */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(CircleShape).background(if (filled) color else color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else color,
        )
    }
}

/** 设置页用的彩色方形图标 */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.56f), tint = tint)
    }
}

/** 分组卡片 + 组标题 */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 6.dp),
            )
        }
        SoftCard(Modifier.fillMaxWidth(), content = content)
    }
}

/** 状态栏遮罩：内容滚到顶部时不和状态栏图标重叠 */
@Composable
fun StatusBarScrim() {
    Box(
        Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
    )
}

data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** 悬浮胶囊底部导航 */
@Composable
fun FloatingNavBar(items: List<NavItem>, current: String?, onSelect: (String) -> Unit) {
    val extra = LocalExtra.current
    val shape = RoundedCornerShape(30.dp)
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier.fillMaxWidth()
            // 底部渐隐，让悬浮栏和后面滚动的内容分开
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.92f), bg)))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .then(
                    if (extra.dark) Modifier
                    else Modifier.shadow(16.dp, shape, ambientColor = extra.cardShadow, spotColor = Color(0xFF1A2420).copy(alpha = 0.16f))
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                val sel = item.route == current
                Row(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .then(if (sel) Modifier.background(extra.accentBrush) else Modifier)
                        .clickable { onSelect(item.route) }
                        .padding(horizontal = if (sel) 18.dp else 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (sel) item.selectedIcon else item.icon, item.label, Modifier.size(22.dp),
                        tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sel) {
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}
