package com.subnook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Currencies
import com.subnook.app.data.IconType
import com.subnook.app.data.PresetCatalog

@Composable
fun CurrencyPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("选择币种") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val list = Currencies.list.filter {
                    q.isBlank() || it.code.contains(q, true) || it.name.contains(q)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.code }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(c.code) }.padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 只显示 CN¥ 这类符号 + 中文名，不再重复 CNY 代码（搜索仍可按代码匹配）
                            Text(Currencies.listSymbol(c.code), fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                            Text(c.name, modifier = Modifier.weight(1f))
                            if (c.code == current) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
    )
}

/** 设计图标底色：刻意避开紫/靛/蓝，免得和首页紫色卡片撞色 */
val PaletteColors = listOf(
    0xFFE53935, 0xFFFF6B6B, 0xFFD81B60, 0xFFF06292, 0xFFF4511E, 0xFFFB8C00, 0xFFFFB300, 0xFFFDD835,
    0xFFC0CA33, 0xFF7CB342, 0xFF43A047, 0xFF2E7D32, 0xFF00897B, 0xFF26A69A, 0xFF00ACC1, 0xFF6D4C41,
    0xFFA1887F, 0xFF757575, 0xFF455A64, 0xFF212121,
).map { it.toInt() }

@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteColors.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** 图标选择：品牌预设 / 设计图标 / 相册 / 文字 */
@Composable
fun IconPickerSheet(
    color: Int,
    name: String,
    onDismiss: () -> Unit,
    onPick: (IconType, String) -> Unit,
    onColor: (Int) -> Unit,
    // 相册选图的启动器放在调用方（始终在组合里），避免底部弹窗被关掉 / Activity 重建后收不到选图结果
    onPickPhoto: () -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("品牌图标", "设计图标", "文字/相册").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> {
                    OutlinedTextField(q, { q = it }, label = { Text("搜索品牌") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val list = PresetCatalog.all.filter {
                        IconFiles.presetRes(context, it.key) != 0 && (q.isBlank() || it.name.contains(q, true) || it.key.contains(q, true))
                    }
                    LazyVerticalGrid(GridCells.Adaptive(68.dp), Modifier.height(420.dp)) {
                        items(list, key = { it.key }) { p ->
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(IconType.PRESET, p.key) }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                SubIcon(IconType.PRESET, p.key, p.color, p.name, size = 48.dp)
                                Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                1 -> {
                    Text("背景颜色", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    ColorRow(color, onColor)
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.height(340.dp)) {
                        items(DesignIcons.all, key = { it.first }) { (n, _) ->
                            Box(Modifier.padding(5.dp).clickable { onPick(IconType.MATERIAL, n) }) {
                                SubIcon(IconType.MATERIAL, n, color, name, size = 46.dp)
                            }
                        }
                    }
                }
                else -> {
                    Text("文字图标", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    ColorRow(color, onColor)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubIcon(IconType.TEXT, "", color, name.ifBlank { "订" }, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { onPick(IconType.TEXT, "") }) { Text("使用文字图标") }
                    }
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = onPickPhoto) {
                        Icon(Icons.Rounded.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("从相册选择图片（可裁剪）")
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}
