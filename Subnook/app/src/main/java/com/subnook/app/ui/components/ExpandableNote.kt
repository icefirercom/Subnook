package com.subnook.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 从多行备注中提取「键：值」结构（如 硬件码：ABCD-1234、账号ID: 9527、激活码：XYZ），
 * 方便在备注卡片下方直接一键复制码值，无需手工长按拖选。
 */
fun extractCopyableNoteFields(note: String): List<Pair<String, String>> {
    if (note.isBlank()) return emptyList()
    val re = Regex("""^([^\n:：]{1,12})[:：]\s*(.+)$""")
    return note.lines().mapNotNull { line ->
        val m = re.matchEntire(line.trim()) ?: return@mapNotNull null
        val key = m.groupValues[1].trim()
        val value = m.groupValues[2].trim()
        if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
    }.take(6)
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * 可扩展备注卡片（用于保存 ID 号、硬件码、激活码、账号邮箱等长文本）：
 * - 默认 3 行、输入自动撑高，可点击右上角一键展开为 8~20 行舒适大框
 * - 内置常用字段快捷插入（账号ID / 硬件码 / 激活码 / 邮箱）
 * - 自动识别「键：值」行，生成单行一键复制胶囊
 */
@Composable
fun ExpandableNoteCard(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "记录账号 ID、硬件码、激活码、套餐说明等（支持多行扩展）",
    templates: List<String> = listOf("账号ID：", "硬件码：", "激活码：", "邮箱："),
    onCopied: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val copyables = remember(value) { extractCopyableNoteFields(value) }

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (value.isBlank()) "备注" else "备注 · ${value.length} 字",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        copyToClipboard(ctx, "备注", value)
                        onCopied("已复制全部备注")
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ContentCopy, "复制备注", Modifier.size(14.dp), tint = cs.primary)
                    Spacer(Modifier.width(3.dp))
                    Text("复制", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                    if (expanded) "收起备注框" else "展开备注框",
                    Modifier.size(16.dp),
                    tint = cs.primary,
                )
                Spacer(Modifier.width(2.dp))
                Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = false,
                    minLines = if (expanded) 8 else 3,
                    maxLines = if (expanded) 20 else 8,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainer,
                        unfocusedContainerColor = cs.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )

                // 快捷插入字段标签
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.forEach { tpl ->
                        val label = tpl.trimEnd('：', ':')
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp)).background(cs.surfaceContainer)
                                .clickable {
                                    val prefix = if (value.isBlank() || value.endsWith("\n")) "" else "\n"
                                    onChange(value + prefix + tpl)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text("+ $label", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }

                // 自动提取的「键：值」一键复制胶囊
                if (copyables.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        copyables.forEach { (k, v) ->
                            Row(
                                Modifier.clip(RoundedCornerShape(9.dp)).background(cs.primaryContainer)
                                    .clickable {
                                        copyToClipboard(ctx, k, v)
                                        onCopied("已复制 $k")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(12.dp), tint = cs.onPrimaryContainer)
                                Spacer(Modifier.width(4.dp))
                                Text("复制 $k", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}
