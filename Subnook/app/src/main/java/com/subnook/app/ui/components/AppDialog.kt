package com.subnook.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全 App 统一的弹窗样式（参数和 Material 的 AlertDialog 一样）。
 * Material 默认标题是 24sp 粗体，太大太重；这里标题 18sp 半粗、正文 14sp 常规，白底大圆角。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { content ->
            { ProvideTextStyle(t.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)) { content() } }
        },
        text = text?.let { content ->
            { ProvideTextStyle(t.bodyMedium.copy(lineHeight = 21.sp)) { content() } }
        },
        shape = RoundedCornerShape(26.dp),
        containerColor = cs.surfaceContainerLowest,
        iconContentColor = cs.primary,
        titleContentColor = cs.onSurface,
        textContentColor = cs.onSurfaceVariant,
        tonalElevation = 0.dp,
    )
}
