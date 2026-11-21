package com.subnook.app.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.subnook.app.data.PayMethod
import com.subnook.app.data.cleanCategory
import com.subnook.app.ui.components.AppAlertDialog
import com.subnook.app.ui.theme.LocalExtra

/** 编辑页可选的内置付款方式（订阅 / 分期各一套）；旧数据里的「手动付款」或已停用方式只在正选着它时显示 */
internal fun payMethodChoices(form: Form, disabled: Set<String> = emptySet()): List<PayMethod> {
    val base = if (form.isInstallment) PayMethod.forInstallment else PayMethod.forSubscription
    val builtIn = base.filter { it != PayMethod.OTHER && (it.name !in disabled || form.payMethod == it) }
    return if (form.payMethod in builtIn || form.payMethod == PayMethod.OTHER) builtIn else builtIn + form.payMethod
}

internal fun payMethodLabels(): List<String> = PayMethod.entries.map { it.label }

/** 内置快捷扣款渠道（渠道名 -> 推荐关联的支付平台说明） */
internal val BUILT_IN_PAY_CHANNELS: List<Pair<String, String>> = listOf(
    "微信零钱" to "微信快捷推荐",
    "支付宝余额" to "支付宝快捷推荐",
    "余额宝" to "支付宝快捷推荐",
    "花呗" to "支付宝快捷推荐",
    "Apple ID 余额" to "App Store 快捷推荐",
)

/** 根据一级支付平台，给出第二层「实际扣款渠道」的推荐快捷选项（去重后与用户全局渠道池合并，自动过滤已停用项） */
internal fun suggestedPayChannels(
    method: PayMethod,
    saved: List<String>,
    current: String = "",
    disabled: Set<String> = emptySet(),
): List<String> {
    val contextDefaults = when (method) {
        PayMethod.WECHAT -> listOf("微信零钱")
        PayMethod.ALIPAY -> listOf("支付宝余额", "余额宝", "花呗")
        PayMethod.APP_STORE -> listOf("Apple ID 余额")
        else -> emptyList()
    }
    val cur = cleanCategory(current)
    // 用户自定义卡/账户（如信用卡7727、借记卡2990）对所有平台通用；微信/支付宝把平台原生零钱排最前方便一键点选
    return buildList {
        contextDefaults.forEach { if (it !in disabled || it == cur) add(it) }
        saved.forEach { if ((it !in disabled || it == cur) && it !in this) add(it) }
        if (cur.isNotEmpty() && cur !in this) add(cur)
    }
}

/**
 * 编辑页「付款方式」：
 * - 第一层：支付平台 / 付款方式（Google Pay、微信、支付宝、信用卡等 + 自定义 + 其他）
 * - 第二层：实际扣款渠道（全局复用池，如 信用卡7727、借记卡2990、微信零钱等，选填，再次点击可取消）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PayMethodPicker(
    form: Form,
    custom: List<String>,
    customChannels: List<String>,
    disabledMethods: Set<String> = emptySet(),
    disabledChannels: Set<String> = emptySet(),
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onAddChannel: (String) -> Unit,
    onRemoveChannel: (String) -> Unit,
    askedFor: String?,
    onAsked: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val focus = LocalFocusManager.current
    var removeDlg by remember { mutableStateOf<String?>(null) }
    var askDlg by remember { mutableStateOf<String?>(null) }
    var addChannelDlg by remember { mutableStateOf(false) }
    var removeChannelDlg by remember { mutableStateOf<String?>(null) }
    val note = cleanCategory(form.payMethodNote)
    val customSelected = form.payMethod == PayMethod.OTHER && note in custom

    @Composable
    fun Pill(text: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(11.dp))
                .then(
                    if (selected) Modifier.background(extra.accentBrush)
                    else Modifier.background(cs.surfaceContainer),
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else cs.onSurface)
        }
    }

    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            payMethodChoices(form, disabledMethods).forEach { m ->
                Pill(m.label, form.payMethod == m) { form.payMethod = m; form.payMethodNote = "" }
            }
            custom.filter { it !in disabledMethods || (form.payMethod == PayMethod.OTHER && note == it) }.forEach { name ->
                Pill(name, form.payMethod == PayMethod.OTHER && note == name, onLongClick = { removeDlg = name }) {
                    form.payMethod = PayMethod.OTHER; form.payMethodNote = name
                }
            }
            Pill(PayMethod.OTHER.label, form.payMethod == PayMethod.OTHER && !customSelected) {
                if (form.payMethod != PayMethod.OTHER || customSelected) form.payMethodNote = ""
                form.payMethod = PayMethod.OTHER
            }
        }
        if (form.payMethod == PayMethod.OTHER && !customSelected) {
            Spacer(Modifier.height(10.dp))
            var hadFocus by remember { mutableStateOf(false) }
            TextField(
                form.payMethodNote, { form.payMethodNote = it.replace("\n", "").take(20) },
                Modifier.fillMaxWidth().onFocusChanged { f ->
                    if (hadFocus && !f.isFocused) {
                        val n = cleanCategory(form.payMethodNote)
                        if (needsAskName(n, custom, askedFor, payMethodLabels())) askDlg = n
                    }
                    hadFocus = f.isFocused
                },
                label = { Text("支付平台名称（可不填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        // 第二层：实际扣款渠道（余额方式已有专属预付余额区块，不重复显示）
        if (form.payMethod != PayMethod.BALANCE) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = cs.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CreditCard, null, Modifier.size(16.dp), tint = cs.primary)
                Spacer(Modifier.width(6.dp))
                Text("实际扣款渠道", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (form.payChannel.isBlank()) "选填 · 可跨平台复用" else "再点已选项可取消",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            val channelList = suggestedPayChannels(form.payMethod, customChannels, form.payChannel, disabledChannels)
            val currentChannel = cleanCategory(form.payChannel)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                channelList.forEach { ch ->
                    val sel = currentChannel == ch
                    val removable = ch in customChannels
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .then(
                                if (sel) Modifier.background(cs.primaryContainer).border(1.dp, cs.primary, RoundedCornerShape(11.dp))
                                else Modifier.background(cs.surfaceContainer),
                            )
                            .combinedClickable(
                                onClick = { form.payChannel = if (sel) "" else ch },
                                onLongClick = if (removable) ({ removeChannelDlg = ch }) else null,
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            ch,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) cs.onPrimaryContainer else cs.onSurface,
                        )
                    }
                }
                // 添加新的扣款卡/账户
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .border(1.dp, cs.outline, RoundedCornerShape(11.dp))
                        .clickable { addChannelDlg = true }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(15.dp), tint = cs.primary)
                    Spacer(Modifier.width(3.dp))
                    Text("添加卡/账户", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
            }
        }
    }

    if (addChannelDlg) {
        AddChannelDialog(
            onDismiss = { addChannelDlg = false },
            onAdd = { name ->
                val cleaned = cleanCategory(name)
                if (cleaned.isNotEmpty()) {
                    onAddChannel(cleaned)
                    form.payChannel = cleaned
                }
                addChannelDlg = false
            },
        )
    }

    removeChannelDlg?.let { ch ->
        AppAlertDialog(
            onDismissRequest = { removeChannelDlg = null },
            text = { DialogMessage("从常用扣款渠道移除「$ch」？已保存的订阅不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveChannel(ch)
                    if (cleanCategory(form.payChannel) == ch) form.payChannel = ""
                    removeChannelDlg = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeChannelDlg = null }) { Text("取消") } },
        )
    }

    askDlg?.let { name ->
        AddCategoryDialog(name, what = "付款方式") { add -> if (add) onAddCustom(name); onAsked(name); askDlg = null }
    }
    removeDlg?.let { name ->
        AppAlertDialog(
            onDismissRequest = { removeDlg = null },
            text = { DialogMessage("删除付款方式「$name」？") },
            confirmButton = {
                TextButton(onClick = { onRemoveCustom(name); removeDlg = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeDlg = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddChannelDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val presets = listOf("信用卡", "借记卡", "微信零钱", "支付宝余额")
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加扣款渠道") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    text,
                    { text = it.replace("\n", "").take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("卡号或账户名称") },
                    placeholder = { Text("如：信用卡7727、借记卡2990") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { p ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(cs.surfaceContainer)
                                .clickable { text = p }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(p, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanCategory(text).isNotEmpty(),
                onClick = { onAdd(text) },
            ) { Text("添加并使用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
