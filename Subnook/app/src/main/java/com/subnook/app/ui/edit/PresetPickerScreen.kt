package com.subnook.app.ui.edit

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Category
import com.subnook.app.data.IconType
import com.subnook.app.data.PresetCatalog
import com.subnook.app.ui.components.SubIcon

@Composable
fun PresetPickerScreen(
    customPresets: List<com.subnook.app.data.CustomPreset> = emptyList(),
    disabledPresets: Set<String> = emptySet(),
    onBack: () -> Unit,
    onPick: (key: String?, query: String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val customEnabled = customPresets.filter {
        it.enabled && (q.isBlank() || it.name.contains(q, true) || it.key.contains(q, true))
    }
    val filtered = PresetCatalog.all.filter {
        it.key !in disabledPresets && (q.isBlank() || it.name.contains(q, true) || it.key.contains(q, true))
    }
    val grouped = Category.entries.mapNotNull { c -> filtered.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { c to it } }

    // 每个分区标题在网格里的位置：第 0 项是「自定义订阅」卡片，若有启用的自定义订阅源再加「我的订阅源」区
    val headerIndex: List<Pair<Category, Int>> = remember(grouped, customEnabled) {
        var i = 1 + if (customEnabled.isNotEmpty()) 1 + customEnabled.size else 0
        grouped.map { (c, list) -> (c to i).also { i += 1 + list.size } }
    }
    val gridState = rememberLazyGridState()
    val chipState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 当前滚动到的分区（用于高亮分类标签）
    val current by remember(headerIndex) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            headerIndex.lastOrNull { it.second <= first + 1 }?.first ?: headerIndex.firstOrNull()?.first
        }
    }
    LaunchedEffect(current) {
        val idx = headerIndex.indexOfFirst { it.first == current }
        if (idx >= 0) chipState.animateScrollToItem(maxOf(0, idx - 1))
    }

    val cs = MaterialTheme.colorScheme
    val extra = com.subnook.app.ui.theme.LocalExtra.current
    Scaffold(containerColor = cs.background, topBar = {
        TopAppBar(
            title = { Text("添加订阅", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            androidx.compose.material3.TextField(
                q, { q = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("搜索 Netflix、爱奇艺、紫卡…") }, singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerLowest, unfocusedContainerColor = cs.surfaceContainerLowest,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            // 分类快速跳转
            if (headerIndex.size > 1) {
                LazyRow(
                    state = chipState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(headerIndex, key = { it.first.name }) { (cat, index) ->
                        val sel = cat == current
                        val count = grouped.firstOrNull { it.first == cat }?.second?.size ?: 0
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainerLowest))
                                .clickable { scope.launch { gridState.animateScrollToItem(index) } }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.label, style = MaterialTheme.typography.labelLarge, color = if (sel) androidx.compose.ui.graphics.Color.White else cs.onSurface)
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "$count", style = MaterialTheme.typography.labelSmall,
                                color = if (sel) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(
                GridCells.Adaptive(84.dp),
                Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(20.dp)).background(extra.heroBrush)
                            .clickable { onPick(null, q.trim()) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, null, Modifier.size(26.dp), tint = androidx.compose.ui.graphics.Color.White) }
                        Spacer(Modifier.size(14.dp))
                        Text(if (q.isBlank()) "自定义订阅" else "自定义「$q」", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (customEnabled.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_custom_presets") {
                        Text("我的订阅源", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
                    }
                    items(customEnabled, key = { it.key }) { cp ->
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { onPick(cp.key, q.trim()) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SubIcon(IconType.TEXT, "", cp.color, cp.name, size = 52.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(cp.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
                grouped.forEach { (cat, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_${cat.name}") {
                        Text(cat.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
                    }
                    items(list, key = { it.key }) { p ->
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { onPick(p.key, q.trim()) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SubIcon(IconType.PRESET, p.key, p.color, p.name, size = 52.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
