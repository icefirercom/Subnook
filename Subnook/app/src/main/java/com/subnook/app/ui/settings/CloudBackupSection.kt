package com.subnook.app.ui.settings

import com.subnook.app.ui.components.AppAlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.subnook.app.data.CloudBackup
import com.subnook.app.data.RemoteFile
import com.subnook.app.data.WebDavClient
import com.subnook.app.data.WebDavConfig
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 设置页「备份」分组里的云端备份（WebDAV）几行：账号、立即备份、从云端恢复、每天自动备份 */
@Composable
internal fun CloudBackupRows(vm: MainViewModel) {
    val cfg by vm.webDav.collectAsState()
    var accountDlg by remember { mutableStateOf(false) }
    var restoreDlg by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    // 接在「导出 / 导入备份」后面，同一个「备份」分组
    Divider()
    Item(
        Icons.Rounded.Cloud, Color(0xFF0EA5E9), "云端备份（WebDAV）",
        if (cfg.configured) "${cfg.user} · ${host(cfg.url)}" else "未设置",
    ) { accountDlg = true }
    if (cfg.configured) {
        Divider()
        Item(
            Icons.Rounded.CloudUpload, Color(0xFF14B8A6), "立即备份到云端",
            when {
                backingUp -> "正在上传…"
                cfg.lastBackupAt > 0 -> "上次 ${time(cfg.lastBackupAt)}"
                else -> null
            },
        ) {
            if (backingUp) return@Item
            backingUp = true
            vm.cloudBackup { backingUp = false }
        }
        Divider()
        Item(Icons.Rounded.CloudDownload, Color(0xFF3B82F6), "从云端恢复") { restoreDlg = true }
        Divider()
        Row(
            Modifier.fillMaxWidth().clickable { vm.setAutoBackup(!cfg.autoBackup) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Rounded.CloudSync, Color(0xFF8B5CF6))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("每天自动备份", style = MaterialTheme.typography.titleSmall)
                if (cfg.autoBackup && cfg.lastError.isNotBlank()) {
                    Text("上次失败：${cfg.lastError}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(cfg.autoBackup, { vm.setAutoBackup(it) })
        }
    }

    if (accountDlg) AccountDialog(vm, cfg) { accountDlg = false }
    if (restoreDlg) RestoreDialog(vm) { restoreDlg = false }
}

private fun host(url: String) = WebDavClient.normalize(url)?.host ?: url

private fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

@Composable
private fun AccountDialog(vm: MainViewModel, cfg: WebDavConfig, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(cfg.url.ifBlank { "" }) }
    var user by remember { mutableStateOf(cfg.user) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    AppAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("WebDAV 账号") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    url, { url = it.trim(); error = null }, label = { Text("服务器地址") }, singleLine = true,
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(user, { user = it; error = null }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text("密码") }, singleLine = true,
                    placeholder = { if (cfg.hasPassword) Text("已保存，不修改可留空") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (url.startsWith("http://", true)) {
                    Text("http 地址不加密", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.warning)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                if (cfg.configured) {
                    TextButton(onClick = { vm.clearWebDav(); onDismiss() }, enabled = !busy) { Text("移除账号", color = cs.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && url.isNotBlank() && user.isNotBlank() && (password.isNotEmpty() || cfg.hasPassword),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        vm.saveWebDav(url, user, password.ifEmpty { null })
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("测试并保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun RestoreDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<RemoteFile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<RemoteFile?>(null) }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) {
        vm.cloudList().onSuccess { files = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从云端恢复") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val list = files
                when {
                    error != null -> Text("读取失败：$error", color = cs.error)
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在读取云端备份…")
                    }
                    list.isEmpty() -> Text("云端还没有备份。", color = cs.onSurfaceVariant)
                    else -> list.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                        Column(Modifier.fillMaxWidth().clickable { confirm = f }.padding(vertical = 10.dp)) {
                            Text(label(f), style = MaterialTheme.typography.titleSmall)
                            Text(
                                (if (i == 0) "最新 · " else "") + "%.1f KB".format(f.size / 1024.0),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
    confirm?.let { f ->
        AppAlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("恢复这份备份？") },
            text = { Text("${label(f)} 的备份将覆盖当前全部数据，无法撤销。") },
            confirmButton = {
                TextButton(onClick = { vm.cloudRestore(f.name); confirm = null; onDismiss() }) {
                    Text("覆盖恢复", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
}

/** subnook-backup-20261119-175012.json → 2026年11月19日 17:50 */
private fun label(f: RemoteFile): String {
    val stamp = f.name.removePrefix(WebDavClient.PREFIX).removeSuffix(".json")
    return runCatching {
        LocalDateTime.parse(stamp, DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
    }.getOrElse { if (f.modified > 0) time(f.modified) else f.name }
}
