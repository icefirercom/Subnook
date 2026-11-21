package com.subnook.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subnook.app.data.PayMethod
import com.subnook.app.data.cleanCategory
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.AppAlertDialog
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.edit.BUILT_IN_PAY_CHANNELS
import com.subnook.app.ui.theme.LocalExtra

@Composable
fun PayManageScreen(vm: MainViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0=扣款方式(平台), 1=实际扣款渠道

    var addMethodDlg by remember { mutableStateOf(false) }
    var renameMethodTarget by remember { mutableStateOf<String?>(null) }
    var deleteMethodTarget by remember { mutableStateOf<String?>(null) }

    var addChannelDlg by remember { mutableStateOf(false) }
    var renameChannelTarget by remember { mutableStateOf<String?>(null) }
    var deleteChannelTarget by remember { mutableStateOf<String?>(null) }

    val disabledMethods = st.settings.disabledPayMethods.toSet()
    val disabledChannels = st.settings.disabledPayChannels.toSet()

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("扣款方式与渠道", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { if (tab == 0) addMethodDlg = true else addChannelDlg = true }) {
                        Icon(Icons.Rounded.Add, if (tab == 0) "新增扣款方式" else "新增扣款渠道", tint = cs.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部两段切换
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("支付平台（一级）", "实际扣款渠道（二级）").forEachIndexed { idx, label ->
                    val sel = tab == idx
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (sel) Modifier.background(extra.accentBrush) else Modifier)
                            .clickable { tab = idx }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Color.White else cs.onSurfaceVariant,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (tab == 0) {
                // ---- 一级：扣款方式 / 支付平台 ----
                AddBannerRow("新增自定义支付平台", "如：云闪付、美区礼品卡等") { addMethodDlg = true }

                if (st.settings.customPayMethods.isNotEmpty()) {
                    Group("自定义支付平台 · ${st.settings.customPayMethods.size}") {
                        st.settings.customPayMethods.forEachIndexed { i, name ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                            ManageSwitchRow(
                                title = name,
                                subtitle = null,
                                checked = name !in disabledMethods,
                                onChecked = { vm.setPayMethodEnabled(name, it) },
                                onEdit = { renameMethodTarget = name },
                                onDelete = { deleteMethodTarget = name },
                            )
                        }
                    }
                }

                val subBuiltIns = PayMethod.forSubscription.filter { it != PayMethod.OTHER }
                Group("订阅常用平台") {
                    subBuiltIns.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                        ManageSwitchRow(
                            title = m.label,
                            subtitle = if (m == PayMethod.BALANCE) "预付余额钱包模式" else null,
                            checked = m.name !in disabledMethods,
                            onChecked = { vm.setPayMethodEnabled(m.name, it) },
                        )
                    }
                }

                val instBuiltIns = PayMethod.forInstallment.filter { it != PayMethod.OTHER && it !in subBuiltIns }
                Group("分期常用平台") {
                    instBuiltIns.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                        ManageSwitchRow(
                            title = m.label,
                            subtitle = null,
                            checked = m.name !in disabledMethods,
                            onChecked = { vm.setPayMethodEnabled(m.name, it) },
                        )
                    }
                }
            } else {
                // ---- 二级：实际扣款渠道 ----
                AddBannerRow("新增扣款渠道", "如：信用卡7727、借记卡2990（跨平台复用）") { addChannelDlg = true }

                Group("我的卡与账户 · ${st.settings.customPayChannels.size}") {
                    if (st.settings.customPayChannels.isEmpty()) {
                        Text(
                            "暂无自定义扣款渠道，点击上方添加常用信用卡、借记卡或钱包账户。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        st.settings.customPayChannels.forEachIndexed { i, ch ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                            ManageSwitchRow(
                                title = ch,
                                subtitle = "全局复用",
                                checked = ch !in disabledChannels,
                                onChecked = { vm.setPayChannelEnabled(ch, it) },
                                onEdit = { renameChannelTarget = ch },
                                onDelete = { deleteChannelTarget = ch },
                            )
                        }
                    }
                }

                Group("内置快捷渠道") {
                    BUILT_IN_PAY_CHANNELS.forEachIndexed { i, (ch, hint) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
                        ManageSwitchRow(
                            title = ch,
                            subtitle = hint,
                            checked = ch !in disabledChannels,
                            onChecked = { vm.setPayChannelEnabled(ch, it) },
                        )
                    }
                }
            }

            Text(
                "停用的选项不会出现在新建订阅界面；已有订阅如果正在使用，编辑时仍会保留显示，不影响历史数据。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (addMethodDlg) {
        NameInputDialog(
            title = "新增自定义支付平台",
            label = "平台名称",
            placeholder = "如：云闪付、Steam 钱包",
            initial = "",
            confirmText = "添加",
            onDismiss = { addMethodDlg = false },
            onConfirm = {
                vm.addCustomPayMethod(it)
                addMethodDlg = false
            },
        )
    }

    renameMethodTarget?.let { old ->
        NameInputDialog(
            title = "修改支付平台名称",
            label = "平台名称",
            placeholder = "",
            initial = old,
            confirmText = "保存",
            onDismiss = { renameMethodTarget = null },
            onConfirm = {
                vm.renameCustomPayMethod(old, it)
                renameMethodTarget = null
            },
        )
    }

    deleteMethodTarget?.let { name ->
        AppAlertDialog(
            onDismissRequest = { deleteMethodTarget = null },
            title = { Text("删除「$name」？") },
            text = { Text("仅从自定义平台列表中移除，已有订阅不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.removeCustomPayMethod(name); deleteMethodTarget = null }) {
                    Text("删除", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteMethodTarget = null }) { Text("取消") } },
        )
    }

    if (addChannelDlg) {
        NameInputDialog(
            title = "新增扣款渠道",
            label = "卡号或账户名称",
            placeholder = "如：信用卡7727、借记卡2990",
            initial = "",
            presets = listOf("信用卡", "借记卡", "微信零钱", "支付宝余额"),
            confirmText = "添加",
            onDismiss = { addChannelDlg = false },
            onConfirm = {
                vm.addCustomPayChannel(it)
                addChannelDlg = false
            },
        )
    }

    renameChannelTarget?.let { old ->
        NameInputDialog(
            title = "修改扣款渠道名称",
            label = "卡号或账户名称",
            placeholder = "",
            initial = old,
            confirmText = "保存",
            onDismiss = { renameChannelTarget = null },
            onConfirm = {
                vm.renameCustomPayChannel(old, it)
                renameChannelTarget = null
            },
        )
    }

    deleteChannelTarget?.let { name ->
        AppAlertDialog(
            onDismissRequest = { deleteChannelTarget = null },
            title = { Text("删除「$name」？") },
            text = { Text("仅从常用渠道池移除，已有订阅的记录不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.removeCustomPayChannel(name); deleteChannelTarget = null }) {
                    Text("删除", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteChannelTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun AddBannerRow(title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(extra.heroBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun ManageSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (checked) cs.onSurface else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, "修改", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.DeleteOutline, "删除", Modifier.size(18.dp), tint = cs.error.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.width(4.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    placeholder: String,
    initial: String,
    presets: List<String> = emptyList(),
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember(initial) { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    text,
                    { text = it.replace("\n", "").take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { p ->
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp)).background(cs.surfaceContainer)
                                    .clickable { text = p }.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(p, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cleanCategory(text).isNotEmpty(), onClick = { onConfirm(cleanCategory(text)) }) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
