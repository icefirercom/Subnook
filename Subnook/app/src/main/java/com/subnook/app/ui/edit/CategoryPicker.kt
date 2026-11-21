package com.subnook.app.ui.edit

import com.subnook.app.ui.components.AppAlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Category
import com.subnook.app.data.cleanCategory
import com.subnook.app.ui.theme.LocalExtra

/** 常用分类：默认只显示这些，其余收进「更多」 */
private val CommonForSubscription = listOf(
    Category.VIDEO, Category.MUSIC, Category.AI, Category.CLOUD, Category.TOOLS, Category.EDU, Category.GAME, Category.SHOPPING,
)
private val CommonForInstallment = listOf(
    Category.DIGITAL, Category.APPLIANCE, Category.CAR, Category.HOUSING, Category.EDU, Category.HEALTH,
)

/**
 * 编辑页「分类」：常用分类 + 用户自己加的分类 + 三角下拉箭头（展开其余分类和「其他」）。
 * 自定义分类存成 OTHER + categoryNote；选「其他」填了名称后，问一次要不要加入分类列表（不直接加）。
 * 长按自定义分类可以删除（已经用了它的订阅不受影响）。
 * @param askToAdd 返回 true 表示需要弹窗询问（名称有效、还不在列表里、没问过）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryPicker(
    form: Form,
    custom: List<String>,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    askedFor: String?,
    onAsked: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val focus = LocalFocusManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var removeDlg by remember { mutableStateOf<String?>(null) }
    var askDlg by remember { mutableStateOf<String?>(null) }

    val common = if (form.isInstallment) CommonForInstallment else CommonForSubscription
    val all = if (form.isInstallment) Category.forInstallment else Category.entries
    val note = cleanCategory(form.categoryNote)
    val customSelected = form.category == Category.OTHER && note in custom
    // 收起时：常用 + 当前选中的（哪怕不在常用里）
    val shownBuiltIn = (if (expanded) all else (common + form.category).distinct()).filter { it != Category.OTHER }

    fun maybeAsk() {
        val n = cleanCategory(form.categoryNote)
        if (form.category == Category.OTHER && needsAsk(n, custom, askedFor)) askDlg = n
    }

    @Composable
    fun Pill(text: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp))
                .then(if (selected) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else cs.onSurface)
        }
    }

    Column(Modifier.padding(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            shownBuiltIn.forEach { c ->
                Pill(c.label, form.category == c) { form.category = c; form.categoryNote = "" }
            }
            custom.forEach { name ->
                Pill(name, form.category == Category.OTHER && note == name, onLongClick = { removeDlg = name }) {
                    form.category = Category.OTHER; form.categoryNote = name
                }
            }
            // 「其他」收在下拉里；正在用「其他」时收起也显示，免得看不到自己选了什么
            val otherSelected = form.category == Category.OTHER && !customSelected
            if (expanded || otherSelected) {
                Pill(Category.OTHER.label, otherSelected) {
                    if (form.category != Category.OTHER || customSelected) form.categoryNote = ""
                    form.category = Category.OTHER
                }
            }
            // 三角下拉箭头：展开 / 收起其余分类
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "more")
            Box(
                Modifier.size(width = 44.dp, height = 38.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                    .combinedClickable(onClick = { expanded = !expanded }),
                contentAlignment = Alignment.Center,
            ) {
                val arrowColor = cs.onSurfaceVariant
                androidx.compose.foundation.Canvas(Modifier.size(width = 12.dp, height = 7.dp).graphicsLayer { rotationZ = rotation }) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
                    }
                    drawPath(p, arrowColor)
                }
            }
        }
        if (form.category == Category.OTHER && !customSelected) {
            Spacer(Modifier.height(10.dp))
            var hadFocus by remember { mutableStateOf(false) }
            TextField(
                form.categoryNote, { form.categoryNote = it.replace("\n", "").take(20) },
                Modifier.fillMaxWidth().onFocusChanged { f ->
                    // 输入完（离开输入框）时问一次
                    if (hadFocus && !f.isFocused) maybeAsk()
                    hadFocus = f.isFocused
                },
                label = { Text("分类名称（可不填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }

    askDlg?.let { name ->
        AddCategoryDialog(name) { add -> if (add) onAddCustom(name); onAsked(name); askDlg = null }
    }
    removeDlg?.let { name ->
        AppAlertDialog(
            onDismissRequest = { removeDlg = null },
            text = { DialogMessage("删除分类「$name」？") },
            confirmButton = {
                TextButton(onClick = { onRemoveCustom(name); removeDlg = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeDlg = null }) { Text("取消") } },
        )
    }
}

/** 名称有效、不是内置分类、不在自定义列表里、这次编辑还没问过 */
internal fun needsAsk(name: String, custom: List<String>, askedFor: String?): Boolean =
    needsAskName(name, custom, askedFor, Category.entries.map { it.label })

/** 通用版：名称有效、和内置选项不重名、不在自定义列表里、这次编辑还没问过 */
internal fun needsAskName(name: String, custom: List<String>, askedFor: String?, builtInLabels: List<String>): Boolean =
    name.isNotEmpty() && name != askedFor && name !in custom && name !in builtInLabels

/** 弹窗里的一句话：正文字号、常规字重（不用 AlertDialog 又大又粗的标题） */
@Composable
internal fun DialogMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
}

/** 询问是否把「其他」里填的名称加入预设（[what] = 分类 / 付款方式） */
@Composable
internal fun AddCategoryDialog(name: String, what: String = "分类", onAnswer: (add: Boolean) -> Unit) {
    AppAlertDialog(
        onDismissRequest = { onAnswer(false) },
        text = { DialogMessage("把「$name」加入$what？") },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("加入") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("不用") } },
    )
}
