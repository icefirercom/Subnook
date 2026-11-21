package com.subnook.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subnook.app.MainActivity
import com.subnook.app.R
import com.subnook.app.data.Currencies
import com.subnook.app.data.IconType
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Billing
import com.subnook.app.ui.components.IconFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class Notifier(private val context: Context) {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_BILL, "账单提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "订阅扣款前提醒、手动付款逾期提醒"
            }
        )
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33

    fun upcoming(sub: Subscription, today: LocalDate) {
        val due = LocalDate.ofEpochDay(sub.nextBillingDay)
        val days = ChronoUnit.DAYS.between(today, due)
        val whenText = when (days) {
            0L -> "今天"
            1L -> "明天"
            else -> "${days}天后（${due.format(DateTimeFormatter.ofPattern("M月d日"))}）"
        }
        val amount = Billing.amountOn(sub, due)
        if (sub.isOneTime) {
            val lowBalance = com.subnook.app.domain.Balance.usesBalance(sub) && amount > 0.0 && !com.subnook.app.domain.Balance.canPay(sub, amount)
            val title = "${sub.name} ${whenText}到期"
            val text = when {
                lowBalance -> "到期提醒（${sub.payMethodLabel}）· 需 ${Currencies.formatList(amount, sub.currency)}，余额只有 ${Currencies.formatList(sub.balance, sub.currency)}"
                amount > 0.0 -> "到期提醒 · ${sub.payMethodLabel} · ${Currencies.formatList(amount, sub.currency)}"
                else -> "到期提醒 · ${sub.payMethodLabel}"
            }
            post(sub, title, text, withConfirm = days <= Billing.PAYABLE_DAYS)
            return
        }
        // 余额付款且余额不够：提醒先充值
        val lowBalance = com.subnook.app.domain.Balance.usesBalance(sub) && !com.subnook.app.domain.Balance.canPay(sub, amount)
        val action = when {
            lowBalance -> "要扣款，余额不足"
            sub.autoRenew -> "将自动扣款（${sub.payMethodLabel}）"
            else -> "需要手动付款（${sub.payMethodLabel}）"
        }
        val inst = if (sub.isInstallment) com.subnook.app.domain.Installments.progressText(sub) + " · " else ""
        val text = if (lowBalance) {
            "${inst}需 ${Currencies.formatList(amount, sub.currency)}，余额只有 ${Currencies.formatList(sub.balance, sub.currency)}，请先充值"
        } else {
            "${inst}金额 ${Currencies.formatList(amount, sub.currency)}"
        }
        post(sub, "${sub.name} $whenText$action", text, withConfirm = days <= Billing.PAYABLE_DAYS)
    }

    fun overdue(sub: Subscription, today: LocalDate) {
        val due = LocalDate.ofEpochDay(sub.nextBillingDay)
        val days = ChronoUnit.DAYS.between(due, today)
        val amount = Billing.amountOn(sub, due)
        val dueText = due.format(DateTimeFormatter.ofPattern("M月d日"))
        if (sub.isOneTime) {
            val amtText = if (amount > 0.0) " · ${Currencies.formatList(amount, sub.currency)}" else ""
            post(sub, "${sub.name} 已到期 $days 天", "到期日 $dueText · ${sub.payMethodLabel}$amtText，处理后点「已处理」", withConfirm = true)
            return
        }
        if (sub.autoRenew && com.subnook.app.domain.Balance.usesBalance(sub)) {
            post(
                sub, "${sub.name} 余额不足，已逾期 $days 天",
                "账单日 $dueText 需 ${Currencies.formatList(amount, sub.currency)}，余额 ${Currencies.formatList(sub.balance, sub.currency)}；充值后点「已付款」",
                withConfirm = true,
            )
            return
        }
        post(sub, "${sub.name} 已逾期 $days 天未确认付款", "账单日 $dueText · ${Currencies.formatList(amount, sub.currency)}，付完后点「已付款」", withConfirm = true)
    }

    private fun post(sub: Subscription, title: String, text: String, withConfirm: Boolean) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context, sub.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SUB_ID, sub.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CH_BILL)
            .setSmallIcon(R.drawable.ic_stat_bill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(sub.color)
            .setLargeIcon(largeIcon(sub))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 非周期订阅的到期提醒排序优先于周期订阅
            .setSortKey((if (sub.isOneTime) "0_" else "1_") + "%010d_%06d".format(sub.nextBillingDay.coerceAtLeast(0L), sub.id))
        if (withConfirm) {
            val confirm = PendingIntent.getBroadcast(
                context, sub.id.toInt(),
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_CONFIRM).putExtra(ActionReceiver.EXTRA_ID, sub.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, if (sub.isOneTime) "已处理" else "已付款", confirm)
        }
        try {
            NotificationManagerCompat.from(context).notify(notifId(sub.id), b.build())
        } catch (_: SecurityException) {
        }
    }

    private fun largeIcon(sub: Subscription): Bitmap? = runCatching {
        when (sub.iconType) {
            IconType.PRESET -> {
                val id = IconFiles.presetRes(context, sub.iconValue)
                if (id != 0) BitmapFactory.decodeResource(context.resources, id) else null
            }
            IconType.CUSTOM -> BitmapFactory.decodeFile(IconFiles.file(context, sub.iconValue).path)
            else -> null
        }
    }.getOrNull()

    fun cancel(subId: Long) = NotificationManagerCompat.from(context).cancel(notifId(subId))

    private fun notifId(subId: Long) = 10_000 + subId.toInt()

    companion object {
        const val CH_BILL = "bill"
    }
}
