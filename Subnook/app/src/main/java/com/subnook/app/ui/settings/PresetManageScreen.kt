package com.subnook.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.CustomPreset
import com.subnook.app.data.CycleUnit
import com.subnook.app.data.IconType
import com.subnook.app.data.PayMethod
import com.subnook.app.data.Preset
import com.subnook.app.data.PresetCatalog
import com.subnook.app.data.cleanCategory
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.AppAlertDialog
import com.subnook.app.ui.components.ColorRow
import com.subnook.app.ui.components.CurrencyPickerDialog
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.PaletteColors
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.edit.suggestedPayChannels
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums

@Composable
fun PresetManageScreen(vm: MainViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val disabledBuiltIn = st.settings.disabledPresets.toSet()
    val customPresets = st.settings.customPresets

    var q by remember { mutableStateOf("") }
    var editingPreset by remember { mutableStateOf<CustomPreset?>(null) }
    var creatingPreset by remember { mutableStateOf(false) }
    var deletingPreset by remember { mutableStateOf<CustomPreset?>(null) }
    val expandedCats = remember { mutableStateMapOf<Category, Boolean>() }

    val builtInAll = PresetCatalog.all
    val builtInEnabledCount = builtInAll.count { it.key !in disabledBuiltIn }
    val customEnabledCount = customPresets.count { it.enabled }

    val groupedBuiltIn = remember(builtInAll) {
        Category.entries.mapNotNull { c ->
            builtInAll.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { c to it }
        }
    }

    val matchedCustom = customPresets.filter {
        q.isBlank() || it.name.contains(q, true) || it.category.label.contains(q, true)
    }
    val matchedBuiltIn = remember(q, builtInAll) {
        if (q.isBlank()) emptyList()
        else builtInAll.filter { it.name.contains(q, true) || it.key.contains(q, true) || it.category.label.contains(q, true) }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("订阅源管理", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { creatingPreset = true }) {
                        Icon(Icons.Rounded.Add, "新增自定义订阅源", tint = cs.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                TextField(
                    q, { q = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("搜索订阅源名称或分类…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerLowest,
                        unfocusedContainerColor = cs.surfaceContainerLowest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)),
                )
            }

            item(key = "summary") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "内置已启用 $builtInEnabledCount/${builtInAll.size} · 自定义 $customEnabledCount/${customPresets.size}",
                        style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "全启用",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setBuiltInPresetsEnabled(builtInAll.map { it.key }, true) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        "全停用",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setBuiltInPresetsEnabled(builtInAll.map { it.key }, false) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (q.isBlank()) {
                item(key = "add_custom") {
                    AddBannerRow("新增自定义订阅源", "预设常用名称、金额、周期或非周期及默认扣款渠道") {
                        creatingPreset = true
                    }
                }
            }

            // ---- 自定义订阅源 ----
            if (matchedCustom.isNotEmpty() || q.isBlank()) {
                item(key = "custom_group") {
                    Group("我的自定义订阅源 · ${matchedCustom.size}") {
                        if (matchedCustom.isEmpty()) {
                            Text(
                                "还没有自定义订阅源，点击上方按钮添加，新建订阅时即可一键套用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            matchedCustom.forEachIndexed { i, cp ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                                CustomPresetRow(
                                    cp = cp,
                                    onClick = { editingPreset = cp },
                                    onToggle = { vm.setCustomPresetEnabled(cp.key, it) },
                                    onDelete = { deletingPreset = cp },
                                )
                            }
                        }
                    }
                }
            }

            // ---- 内置订阅源 ----
            if (q.isNotBlank()) {
                item(key = "search_title") {
                    Text(
                        "匹配的内置订阅源 · ${matchedBuiltIn.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
                if (matchedBuiltIn.isEmpty()) {
                    item(key = "no_match") {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Text("没有匹配的内置订阅源", Modifier.padding(20.dp), color = cs.onSurfaceVariant)
                        }
                    }
                } else {
                    items(matchedBuiltIn, key = { "b_${it.key}" }) { p ->
                        SoftCard(Modifier.fillMaxWidth()) {
                            BuiltInPresetRow(p, p.key !in disabledBuiltIn) { vm.setBuiltInPresetEnabled(p.key, it) }
                        }
                    }
                }
            } else {
                item(key = "builtin_header") {
                    Text(
                        "内置订阅源（按分类控制启停）",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
                groupedBuiltIn.forEach { (cat, list) ->
                    val enabledInCat = list.count { it.key !in disabledBuiltIn }
                    val expanded = expandedCats[cat] == true
                    item(key = "cat_${cat.name}") {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedCats[cat] = !expanded }
                                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cat.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "已启用 $enabledInCat / ${list.size}",
                                        style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = enabledInCat > 0,
                                    onCheckedChange = { on -> vm.setBuiltInPresetsEnabled(list.map { it.key }, on) },
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    if (expanded) "收起" else "展开",
                                    tint = cs.onSurfaceVariant,
                                )
                            }
                            if (expanded) {
                                list.forEach { p ->
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                                    BuiltInPresetRow(p, p.key !in disabledBuiltIn) { vm.setBuiltInPresetEnabled(p.key, it) }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_pad") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    if (creatingPreset) {
        CustomPresetEditorDialog(
            initial = null,
            defaultCurrency = st.settings.displayCurrency,
            customChannels = st.settings.customPayChannels,
            disabledChannels = st.settings.disabledPayChannels.toSet(),
            onDismiss = { creatingPreset = false },
            onSave = {
                vm.upsertCustomPreset(it)
                creatingPreset = false
                vm.toast("已添加订阅源「${it.name}」")
            },
        )
    }

    editingPreset?.let { cp ->
        CustomPresetEditorDialog(
            initial = cp,
            defaultCurrency = st.settings.displayCurrency,
            customChannels = st.settings.customPayChannels,
            disabledChannels = st.settings.disabledPayChannels.toSet(),
            onDismiss = { editingPreset = null },
            onSave = {
                vm.upsertCustomPreset(it)
                editingPreset = null
                vm.toast("已保存订阅源「${it.name}」")
            },
        )
    }

    deletingPreset?.let { cp ->
        AppAlertDialog(
            onDismissRequest = { deletingPreset = null },
            title = { Text("删除订阅源「${cp.name}」？") },
            text = { Text("仅删除此自定义模板，已创建的订阅记录不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCustomPreset(cp.key); deletingPreset = null }) {
                    Text("删除", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingPreset = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CustomPresetRow(
    cp: CustomPreset,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubIcon(IconType.TEXT, "", cp.color, cp.name, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                cp.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (cp.enabled) cs.onSurface else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val cycleStr = if (cp.oneTime) "非周期" else if (cp.cycleCount == 1) "每${cp.cycleUnit.label}" else "每${cp.cycleCount}${cp.cycleUnit.label}"
            val priceStr = if (cp.price > 0.0) " · ${Currencies.formatList(cp.price, cp.currency)}" else ""
            val payStr = if (cp.payChannel.isNotBlank()) " · ${cp.payMethod.label}·${cp.payChannel}" else " · ${cp.payMethod.label}"
            Text(
                "${cp.category.label} · $cycleStr$priceStr$payStr",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.DeleteOutline, "删除", Modifier.size(18.dp), tint = cs.error.copy(alpha = 0.85f))
        }
        Switch(checked = cp.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun BuiltInPresetRow(p: Preset, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!enabled) }.padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubIcon(IconType.PRESET, p.key, p.color, p.name, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) cs.onSurface else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val cycleStr = if (p.cycleCount == 1) "每${p.cycleUnit.label}" else "每${p.cycleCount}${p.cycleUnit.label}"
            Text(
                "${Currencies.formatList(p.price, p.currency)} / $cycleStr",
                style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                color = cs.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun CustomPresetEditorDialog(
    initial: CustomPreset?,
    defaultCurrency: String,
    customChannels: List<String>,
    disabledChannels: Set<String>,
    onDismiss: () -> Unit,
    onSave: (CustomPreset) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var oneTime by remember { mutableStateOf(initial?.oneTime ?: false) }
    var price by remember {
        mutableStateOf(
            if (initial == null || initial.price <= 0.0) ""
            else initial.price.toBigDecimal().stripTrailingZeros().toPlainString(),
        )
    }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var cycleCount by remember { mutableStateOf((initial?.cycleCount ?: 1).toString()) }
    var cycleUnit by remember { mutableStateOf(initial?.cycleUnit ?: CycleUnit.MONTH) }
    var category by remember { mutableStateOf(initial?.category ?: Category.OTHER) }
    var payMethod by remember { mutableStateOf(initial?.payMethod ?: PayMethod.ALIPAY) }
    var payChannel by remember { mutableStateOf(initial?.payChannel ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: PaletteColors.random()) }
    var pickCurrency by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增自定义订阅源" else "编辑自定义订阅源") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    name, { name = it.replace("\n", "").take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("订阅源名称") },
                    placeholder = { Text("如：VPS 主机、网盘会员") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )

                // 周期 / 非周期
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).padding(3.dp)) {
                    listOf(false to "周期订阅", true to "非周期订阅").forEach { (isOne, label) ->
                        val sel = oneTime == isOne
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .then(if (sel) Modifier.background(extra.accentBrush) else Modifier)
                                .clickable { oneTime = isOne }.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else cs.onSurfaceVariant)
                        }
                    }
                }

                // 金额与币种
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        price, { price = it.filter { c -> c.isDigit() || c == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("参考金额（可选）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                            .clickable { pickCurrency = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Text(Currencies.listSymbol(currency), style = MaterialTheme.typography.titleSmall)
                    }
                }

                if (!oneTime) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("每", style = MaterialTheme.typography.labelLarge)
                        TextField(
                            cycleCount, { cycleCount = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.width(64.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainer).padding(3.dp)) {
                            CycleUnit.entries.forEach { u ->
                                val sel = cycleUnit == u
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .then(if (sel) Modifier.background(cs.surfaceContainerLowest) else Modifier)
                                        .clickable { cycleUnit = u }.padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(u.label, style = MaterialTheme.typography.labelMedium, color = if (sel) cs.primary else cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 分类
                Text("分类", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        val sel = category == c
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                .clickable { category = c }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(c.label, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else cs.onSurface)
                        }
                    }
                }

                // 默认支付平台
                Text("默认支付平台", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PayMethod.forSubscription.forEach { m ->
                        val sel = payMethod == m
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                .clickable { payMethod = m }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(m.label, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else cs.onSurface)
                        }
                    }
                }

                // 默认扣款渠道（可选）
                if (payMethod != PayMethod.BALANCE) {
                    val channels = suggestedPayChannels(payMethod, customChannels, payChannel, disabledChannels)
                    if (channels.isNotEmpty()) {
                        Text("默认扣款渠道（可选）", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            channels.forEach { ch ->
                                val sel = payChannel == ch
                                Box(
                                    Modifier.clip(RoundedCornerShape(9.dp))
                                        .then(
                                            if (sel) Modifier.background(cs.primaryContainer).border(1.dp, cs.primary, RoundedCornerShape(9.dp))
                                            else Modifier.background(cs.surfaceContainer),
                                        )
                                        .clickable { payChannel = if (sel) "" else ch }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(ch, style = MaterialTheme.typography.labelSmall, color = if (sel) cs.onPrimaryContainer else cs.onSurface)
                                }
                            }
                        }
                    }
                }

                // 图标颜色
                Text("图标底色", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                ColorRow(color) { color = it }
            }
        },
        confirmButton = {
            val cleaned = cleanCategory(name)
            TextButton(
                enabled = cleaned.isNotEmpty(),
                onClick = {
                    val cnt = if (oneTime) 1 else (cycleCount.toIntOrNull()?.coerceIn(1, 999) ?: 1)
                    val amt = price.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val key = initial?.key ?: "custom_${System.currentTimeMillis()}"
                    onSave(
                        CustomPreset(
                            key = key,
                            name = cleaned,
                            category = category,
                            colorLong = color.toLong() and 0xFFFFFFFFL,
                            price = amt,
                            currency = currency,
                            cycleCount = cnt,
                            cycleUnit = if (oneTime) CycleUnit.MONTH else cycleUnit,
                            payMethod = payMethod,
                            payChannel = if (payMethod == PayMethod.BALANCE) "" else payChannel.trim(),
                            oneTime = oneTime,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (pickCurrency) {
        CurrencyPickerDialog(currency, onDismiss = { pickCurrency = false }) {
            currency = it
            pickCurrency = false
        }
    }
}
