package com.subnook.app

import android.app.Application
import android.content.Context
import com.subnook.app.data.AppDatabase
import com.subnook.app.data.BackupManager
import com.subnook.app.data.RatesRepository
import com.subnook.app.data.SettingsRepository
import com.subnook.app.domain.RenewalProcessor
import com.subnook.app.notify.Notifier
import com.subnook.app.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce

class App : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db by lazy { AppDatabase.build(this) }
    val dao get() = db.dao()
    val settings by lazy { SettingsRepository(this) }
    val rates by lazy { RatesRepository(this) }
    val notifier by lazy { Notifier(this) }
    val processor by lazy { RenewalProcessor(dao, settings, notifier) }
    val backup by lazy { BackupManager(this) }
    val webDav by lazy { com.subnook.app.data.WebDavSettingsRepository(this) }
    val cloud by lazy { com.subnook.app.data.CloudBackup(this) }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        Scheduler.schedulePeriodicWork(this)
        appScope.launch {
            // 检测到版本变更时自动留存一份本地回退快照（避免升级后发现异常无法回退数据）
            runCatching {
                val prev = settings.lastAppVersionCode()
                val cur = BuildConfig.VERSION_CODE
                if (prev != 0 && prev != cur) {
                    backup.createLocalSnapshot(reason = "pre_upgrade", versionName = "vc$prev")
                }
                if (prev != cur) settings.setLastAppVersionCode(cur)
            }
            Scheduler.runAll(this@App)
            rates.refreshIfStale()
        }
        // 自动云端备份（用户开启后才有）：确保周期任务还在
        appScope.launch {
            if (webDav.get().let { it.autoBackup && it.configured }) com.subnook.app.data.CloudBackup.schedule(this@App, true)
        }
        // 订阅 / 设置 / 汇率任何变化都同步到桌面小组件
        appScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            kotlinx.coroutines.flow.combine(dao.observeAll(), settings.flow, rates.table) { a, b, c -> Triple(a, b, c) }
                .debounce(400L)
                .collect { com.subnook.app.widget.Widgets.refresh(this@App) }
        }
    }

    companion object {
        fun get(context: Context): App = context.applicationContext as App
    }
}
