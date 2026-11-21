package com.subnook.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.subnook.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Scheduler {

    /** 安排下一次每日检查闹钟（用户设定的提醒时间） */
    suspend fun scheduleDailyAlarm(context: Context) {
        val s = App.get(context).settings.get()
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(s.notifyHour, s.notifyMinute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** 兜底的周期任务：每 6 小时处理一次续费/提醒；每 24 小时刷新汇率 */
    fun schedulePeriodicWork(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "renewal", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RenewalWorker>(6, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "rates", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RatesWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    suspend fun runAll(context: Context) {
        App.get(context).processor.run()
        scheduleDailyAlarm(context)
    }
}

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async { Scheduler.runAll(context) }
}

/**
 * 开机、更新应用、改时间/时区、授予精确闹钟权限后重新安排；
 * 同时立即重绘桌面小组件——否则更新 App 后桌面会一直显示旧版本的画面，直到下次打开 App 或每小时的定时刷新。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Scheduler.schedulePeriodicWork(context)
        Scheduler.runAll(context)
        com.subnook.app.widget.Widgets.refresh(context)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        async { App.get(context).processor.confirmPaid(id) }
    }

    companion object {
        const val ACTION_CONFIRM = "com.subnook.app.CONFIRM_PAID"
        const val EXTRA_ID = "id"
    }
}

class RenewalWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Scheduler.runAll(applicationContext)
        return Result.success()
    }
}

class RatesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (App.get(applicationContext).rates.refresh().isSuccess) Result.success() else Result.retry()
}
