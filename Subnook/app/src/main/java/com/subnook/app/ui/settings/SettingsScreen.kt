package com.subnook.app.ui.settings

import com.subnook.app.ui.components.AppAlertDialog
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.components.PageHeader
import com.subnook.app.ui.components.Pill
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.data.ThemeMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.subnook.app.BuildConfig
import com.subnook.app.data.Currencies
import com.subnook.app.data.UpdateChecker
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.subnook.app.notify.Scheduler
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.CurrencyPickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onManagePay: () -> Unit = {},
    onManagePresets: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    var currencyDlg by remember { mutableStateOf(false) }
    var timeDlg by remember { mutableStateOf(false) }
    var aboutDlg by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    // 回到页面时刷新权限状态
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

    val notifGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactOk = remember(tick) { Scheduler.canExact(ctx) }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }

    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    val openUrl: (String) -> Unit = { url ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.toast("没有可以打开链接的浏览器") }
    }

    val notifLauncher =rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    // 导出和导入的文件选择器都默认打开「下载」文件夹，存和找在同一个地方
    val exportLauncher = rememberLauncherForActivityResult(ExportToDownloads()) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(OpenFromDownloads()) { uri -> importUri = uri }

    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader("设置", subtitle = "Subnook ${BuildConfig.VERSION_NAME}")

        Group("外观") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Palette, Color(0xFFEC4899))
                Spacer(Modifier.width(14.dp))
                Text("主题", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).padding(3.dp)) {
                    ThemeMode.entries.forEach { m ->
                        val sel = st.settings.theme == m
                        Text(
                            m.label, style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Color.White else cs.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .then(if (sel) Modifier.background(extra.accentBrush) else Modifier)
                                .clickable { vm.setTheme(m) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Group("桌面小组件") {
            Item(Icons.Rounded.Widgets, Color(0xFF8B5CF6), "添加到桌面") {
                val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                val provider = android.content.ComponentName(ctx, com.subnook.app.widget.SubnookWidgetReceiver::class.java)
                if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(provider, null, null)
                // ColorOS 等系统可能静默拒绝一键添加，给出手动方法
                vm.toast("没有弹窗的话：长按桌面空白处 → 卡片 → 插件 → Subnook")
            }
        }

        Group("统计与汇率") {
            Item(Icons.Rounded.CurrencyExchange, Color(0xFF165B4C), "显示币种", "${Currencies.name(st.settings.displayCurrency)} · ${Currencies.listSymbol(st.settings.displayCurrency)}") { currencyDlg = true }
            Divider()
            val rateText = if (st.rates.updatedAt == 0L) "内置参考汇率" else
                "更新于 " + Instant.ofEpochMilli(st.rates.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
            Item(Icons.Rounded.Sync, Color(0xFF2B6CB0), "立即更新汇率", rateText) { vm.refreshRates() }
        }

        Group("定制管理") {
            val disabledMethods = st.settings.disabledPayMethods.toSet()
            val disabledChannels = st.settings.disabledPayChannels.toSet()
            val allBuiltInMethods = (com.subnook.app.data.PayMethod.forSubscription + com.subnook.app.data.PayMethod.forInstallment)
                .distinct().filter { it != com.subnook.app.data.PayMethod.OTHER }
            val enabledMethods = allBuiltInMethods.count { it.name !in disabledMethods } +
                st.settings.customPayMethods.count { it !in disabledMethods }
            val allChannels = (com.subnook.app.ui.edit.BUILT_IN_PAY_CHANNELS.map { it.first } + st.settings.customPayChannels).distinct()
            val enabledChannels = allChannels.count { it !in disabledChannels }
            Item(
                Icons.Rounded.CreditCard, Color(0xFF165B4C), "扣款方式与渠道",
                "已启用 $enabledMethods 个平台 · $enabledChannels 个渠道",
            ) { onManagePay() }
            Divider()
            val builtInTotal = com.subnook.app.data.PresetCatalog.all.size
            val builtInEnabled = com.subnook.app.data.PresetCatalog.all.count { it.key !in st.settings.disabledPresets }
            val customCount = st.settings.customPresets.size
            Item(
                Icons.Rounded.Tune, Color(0xFFC26D2D), "订阅源管理",
                "内置 $builtInEnabled/$builtInTotal · 自定义 $customCount 个",
            ) { onManagePresets() }
        }

        Group("提醒") {
            Item(Icons.Rounded.Schedule, Color(0xFFF59E0B), "提醒时间", "每天 %02d:%02d".format(st.settings.notifyHour, st.settings.notifyMinute)) { timeDlg = true }
            Divider()
            Item(Icons.Rounded.Notifications, Color(0xFFEF4444), "通知权限", ok = notifGranted) {
                if (Build.VERSION.SDK_INT >= 33 && !notifGranted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            Divider()
            Item(Icons.Rounded.Alarm, Color(0xFF8B5CF6), "准时提醒", ok = exactOk) {
                if (Build.VERSION.SDK_INT >= 31) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Divider()
            Item(Icons.Rounded.BatteryChargingFull, Color(0xFF10B981), "忽略电池优化", ok = batteryOk) {
                if (!batteryOk) ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                else ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        Group("备份") {
            Item(Icons.Rounded.Upload, Color(0xFF14B8A6), "导出备份") {
                exportLauncher.launch("subnook-backup-${LocalDate.now()}.json")
            }
            Divider()
            Item(Icons.Rounded.Download, Color(0xFF3B82F6), "导入备份") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            CloudBackupRows(vm)
        }

        Group("关于") {
            Item(Icons.Rounded.Info, Color(0xFF165B4C), "Subnook · 个人定制版", "版本 ${BuildConfig.VERSION_NAME} · ${UpdateChecker.OWNER}") {
                aboutDlg = true
            }
            Divider()
            Item(Icons.Rounded.Code, Color(0xFF24292F), "我的 GitHub 仓库", UpdateChecker.REPO_URL.removePrefix("https://")) {
                openUrl(UpdateChecker.REPO_URL)
            }
            Divider()
            Item(
                Icons.Rounded.SystemUpdate, Color(0xFF1D8A5E), "检查更新",
                if (checking) "正在检查…" else "从 ${UpdateChecker.OWNER} 仓库检查",
            ) {
                if (checking) return@Item
                checking = true
                scope.launch {
                    runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }
                        .onSuccess { updateResult = it }
                        .onFailure { vm.toast("检查更新失败：${it.message ?: it.javaClass.simpleName}") }
                    checking = false
                }
            }
        }

        Text(
            run {
                val inst = st.subs.count { it.isInstallment }
                val count = if (inst > 0) "${st.subs.size - inst} 个订阅、$inst 个分期" else "${st.subs.size} 个订阅"
                count
            },
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(100.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    if (currencyDlg) CurrencyPickerDialog(st.settings.displayCurrency, { currencyDlg = false }) { vm.setCurrency(it); currencyDlg = false }
    if (timeDlg) {
        val tp = rememberTimePickerState(st.settings.notifyHour, st.settings.notifyMinute, is24Hour = true)
        AppAlertDialog(
            onDismissRequest = { timeDlg = false },
            title = { Text("提醒时间") },
            text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = { vm.setNotifyTime(tp.hour, tp.minute); timeDlg = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { timeDlg = false }) { Text("取消") } },
        )
    }
    if (aboutDlg) {
        AppAlertDialog(
            onDismissRequest = { aboutDlg = false },
            icon = { IconTile(Icons.Rounded.Info, Color(0xFF165B4C), size = 44.dp) },
            title = { Text("Subnook · 个人定制版") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.titleSmall)
                    Text("定制维护：${UpdateChecker.OWNER}", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                    HorizontalDivider(color = cs.outlineVariant)
                    Text("上游项目与协议说明", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Text(
                        "本软件基于开源项目 capllen/Subnook 进行个人非商业定制修改。",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    Text(
                        "Required Notice: Copyright (c) 2026 capllen (https://github.com/capllen/Subnook)",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    )
                    Text(
                        "遵循 PolyForm Noncommercial 1.0.0 许可证：允许个人、学习与研究等非商业用途及修改分发，禁止任何商业用途。",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { openUrl(UpdateChecker.REPO_URL); aboutDlg = false }) { Text("我的仓库") } },
            dismissButton = { TextButton(onClick = { openUrl(UpdateChecker.UPSTREAM_URL); aboutDlg = false }) { Text("上游仓库") } },
        )
    }
    updateResult?.let { r ->
        val dismiss = { updateResult = null }
        when (r) {
            is UpdateChecker.Result.NewVersion -> AppAlertDialog(
                onDismissRequest = dismiss,
                title = { Text("发现新版本 ${r.release.version}") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.release.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(r.release.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openUrl(r.release.url); dismiss() }) { Text("前往下载") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("以后再说") } },
            )
            is UpdateChecker.Result.UpToDate -> AppAlertDialog(
                onDismissRequest = dismiss,
                title = { Text("已是最新版本") },
                text = { Text("当前版本 ${BuildConfig.VERSION_NAME}") },
                confirmButton = { TextButton(onClick = dismiss) { Text("好的") } },
            )
            UpdateChecker.Result.NoRelease -> AppAlertDialog(
                onDismissRequest = dismiss,
                title = { Text("暂无发布版本") },
                text = { Text("GitHub 上还没有发布版本。") },
                confirmButton = { TextButton(onClick = { openUrl(UpdateChecker.RELEASES_URL); dismiss() }) { Text("打开仓库") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } },
            )
        }
    }
    importUri?.let { uri ->
        AppAlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("导入并覆盖？") },
            text = { Text("当前全部数据将被备份文件替换，无法撤销。") },
            confirmButton = { TextButton(onClick = { vm.import(uri); importUri = null }) { Text("覆盖导入", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun Divider() = HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun Item(icon: ImageVector, tint: Color, title: String, desc: String? = null, ok: Boolean? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            // 第二行只放状态（已设置的值、上次时间等），不放说明文字
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (ok) {
            true -> Pill("已开启", LocalExtra.current.success)
            false -> Pill("去开启", LocalExtra.current.warning)
            null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** 系统文件选择器里「下载」文件夹的位置（主存储 /Download） */
private val DOWNLOADS_URI: Uri =
    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")

private class ExportToDownloads : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: android.content.Context, input: String): Intent =
        super.createIntent(context, input).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
}

private class OpenFromDownloads : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
}
