package com.subnook.app.ui.edit

import com.subnook.app.ui.components.AppAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Currencies
import com.subnook.app.data.TopUp
import com.subnook.app.domain.Balance
import com.subnook.app.domain.Billing
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 编辑页「余额」分组（付款方式选「余额」时显示）：当前余额、能付几期 / 哪天前要充值、充值和充值记录。
 * 预测部分移植自 PR #3（作者 wanwuzhishi）；充值记录是后加的。
 * @param onTopUp 已保存的订阅才能充值；新建时为 null，直接填初始余额即可
 */
@Composable
internal fun BalanceGroup(
    form: Form,
    topUps: List<TopUp>,
    onCurrency: () -> Unit,
    onTopUp: ((Double) -> Unit)?,
    onDeleteTopUp: (TopUp) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var topUpDlg by remember { mutableStateOf(false) }
    Group("余额") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    form.balance,
                    { form.balance = it.filter { c -> c.isDigit() || c == '.' }; form.balanceTouched = true },
                    "当前余额",
                    keyboard = KeyboardType.Decimal,
                    isError = form.balanceValue() == null,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable(onClick = onCurrency)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Currencies.listSymbol(form.currency), style = MaterialTheme.typography.titleMedium)
                    Text(Currencies.name(form.currency), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            BalanceForecastCard(form)
            if (onTopUp != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(alpha = 0.10f))
                        .clickable { topUpDlg = true }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = cs.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("充值", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
        }
        if (topUps.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant)
            Text(
                "充值记录 · ${topUps.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
            )
            // 最多显示 3 条，多的在框里滑动
            com.subnook.app.ui.components.CappedList(topUps, rowHeight = 56.dp, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().fillMaxHeight().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("+" + Currencies.formatList(t.amount, t.currency), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = LocalExtra.current.success)
                        Text(
                            LocalDate.ofEpochDay(t.day).format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDeleteTopUp(t) }) { Icon(Icons.Rounded.Close, "删除充值记录", Modifier.size(18.dp), tint = cs.outline) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    if (topUpDlg && onTopUp != null) {
        TopUpDialog(form.currency, onDismiss = { topUpDlg = false }) { amount -> topUpDlg = false; onTopUp(amount) }
    }
}

/** 按表单当前内容预测：余额还能付几期、哪天之前要充值 */
@Composable
private fun BalanceForecastCard(form: Form) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val today = Billing.today()
    val preview = form.build(requireName = false)
    // 金额还没填好就不显示
    val forecast = preview?.let { Balance.forecast(it) } ?: return
    val c = form.currency
    val title = when {
        forecast.coversAll -> "余额够付${Balance.periodsText(forecast)}"
        forecast.periods == 0 -> "余额不足 1 期"
        else -> "余额可用 ${Balance.periodsText(forecast)}"
    }
    val desc = when {
        forecast.coversAll -> ""
        forecast.periods == 0 -> "还差 ${Currencies.formatList(forecast.shortfall, c)}"
        else -> "${Balance.dateText(forecast.rechargeOn!!, today)}前需充 ${Currencies.formatList(forecast.rechargeAmount, c)}"
    }
    val tint: Color = when {
        forecast.periods == 0 && !forecast.coversAll -> extra.danger
        forecast.periods == 1 && !forecast.coversAll -> extra.warning
        else -> extra.success
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AccountBalanceWallet, tint, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TopUpDialog(currency: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toDoubleOrNull()?.takeIf { it > 0 }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("充值") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("充值金额（${Currencies.listSymbol(currency)}）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { onConfirm(amount!!) }) { Text("充值") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
