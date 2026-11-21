package com.subnook.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.subnook.app.App
import com.subnook.app.MainActivity
import com.subnook.app.R
import com.subnook.app.data.Currencies
import com.subnook.app.data.IconType
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.ui.components.IconFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 小组件用到的数据快照 */
private data class WidgetItem(
    val id: Long,
    val name: String,
    val icon: ImageProvider,
    val days: Long,
    val date: String,
    val amount: String,
    val needsConfirm: Boolean,
)

private data class WidgetData(
    val currency: String,
    val monthly: Double,
    val next30: Double,
    val activeCount: Int,
    val items: List<WidgetItem>,
)

class SubnookWidget : GlanceAppWidget() {
    /**
     * 用 Responsive 而不是 Exact：部分桌面（如 ColorOS）上报给小组件的尺寸和实际显示的不一致，
     * 按上报尺寸排版会被截断或留空。Responsive 由桌面按实际大小挑「放得下的最大一份」。
     * 订阅列表本身可以滑动，这些档位只用来决定窄 / 宽布局、紧凑 / 大标题、窄布局是否显示金额。
     */
    override val sizeMode = SizeMode.Responsive(
        setOf(
            // 窄（2×2 等）：宽 110 / 150 × 高 110 / 150 / 200 / 260
            DpSize(110.dp, 110.dp), DpSize(110.dp, 150.dp), DpSize(110.dp, 200.dp), DpSize(110.dp, 260.dp),
            DpSize(150.dp, 110.dp), DpSize(150.dp, 150.dp), DpSize(150.dp, 200.dp), DpSize(150.dp, 260.dp),
            // 宽（4×2 及以上）
            DpSize(200.dp, 110.dp),
            DpSize(200.dp, 140.dp),
            DpSize(200.dp, 176.dp),
            DpSize(200.dp, 220.dp),
            DpSize(200.dp, 300.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = App.get(context)
        // 顺便处理一下到期续费，保证小组件上的日期是最新的
        runCatching { app.processor.run() }
        val initial = load(context)
        // Glance 会话存活期间不会重新执行 provideGlance，所以要订阅数据变化流
        val flow = kotlinx.coroutines.flow.combine(app.dao.observeAll(), app.settings.flow, app.rates.table) { _, _, _ -> Unit }
            .map { load(context) }
            .flowOn(kotlinx.coroutines.Dispatchers.IO)
        provideContent {
            val data by flow.collectAsState(initial)
            Content(data)
        }
    }

    private suspend fun load(context: Context): WidgetData {
        val app = App.get(context)
        val subs = app.dao.getAll()
        val settings = app.settings.get()
        val rates = app.rates.table.value
        val cur = settings.displayCurrency
        val today = LocalDate.now()
        val active = subs.filter { it.active }
        val next30 = active.sumOf { s ->
            rates.convert(Billing.upcomingCost(s, today, today.plusDays(29)), s.currency, cur)
        }
        // 列表可以滑动，多给一些（RemoteViews 有大小限制，不要无限多）；买断制不参与即将到期列表，非周期到期提醒优先靠前
        val items = active.filter { !it.isLifetime }.sortedWith(Billing.pendingComparator).take(30).map { s ->
            val date = LocalDate.ofEpochDay(s.nextBillingDay)
            WidgetItem(
                id = s.id,
                name = s.name,
                icon = iconFor(context, s),
                days = Billing.daysUntil(s, today),
                date = date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M月d日" else "yy年M月d日")),
                amount = if (s.isOneTime && s.amount <= 0.0) "到期" else Currencies.formatList(s.amount, s.currency),
                needsConfirm = Billing.isPayable(s, today),
            )
        }
        return WidgetData(cur, Billing.total(subs, SummaryPeriod.MONTH, rates, cur), next30, active.size, items)
    }

    private fun iconFor(context: Context, s: Subscription): ImageProvider {
        if (s.iconType == IconType.PRESET) {
            val res = IconFiles.presetRes(context, s.iconValue)
            if (res != 0) return ImageProvider(res)
        }
        if (s.iconType == IconType.CUSTOM) {
            runCatching {
                BitmapFactory.decodeFile(IconFiles.file(context, s.iconValue).path)?.let {
                    return ImageProvider(Bitmap.createScaledBitmap(it, 96, 96, true))
                }
            }
        }
        return ImageProvider(letterBitmap(s.name, s.color, if (s.iconType == IconType.TEXT) s.iconValue else ""))
    }

    /** 文字图标：圆角色块 + 首字 */
    private fun letterBitmap(name: String, color: Int, text: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 26f, 26f, p)
        val src = text.ifBlank { name }.trim()
        val letter = src.take(if (src.firstOrNull()?.let { it.code > 0x2E80 } == true) 1 else 2).uppercase().ifBlank { "?" }
        val r = android.graphics.Color.red(color); val g = android.graphics.Color.green(color); val b = android.graphics.Color.blue(color)
        val light = (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.65
        p.color = if (light) 0xFF1C1B1F.toInt() else android.graphics.Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = if (letter.length > 1) 36f else 46f
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(letter, size / 2f, y, p)
        return bmp
    }
}

// ---------- 布局 ----------

private val White = ColorProvider(Color.White)
private val White70 = ColorProvider(Color.White.copy(alpha = 0.72f))
private val White55 = ColorProvider(Color.White.copy(alpha = 0.55f))

@Composable
private fun Content(d: WidgetData) {
    val size = LocalSize.current
    val fontScale = androidx.glance.LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
    val narrow = size.width < 200.dp
    Box(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = if (narrow) 12.dp else 16.dp, vertical = PAD_V.dp),
    ) {
        if (narrow) Small(d, size.width.value, size.height.value, fontScale)
        else Large(d, compactHeader = useCompactHeader(size.height.value, fontScale))
    }
}

private const val PAD_V = 12f
private const val ROW_GAP = 6f
private const val ROW_H = 44f

/**
 * 订阅列表可以上下滑动，铺满标题下面的全部空间。
 * 小组件比较矮时（大标题下面连两行都放不下），换成单行紧凑标题，让列表多露出一些。
 * 文字高度按系统字体缩放估算。
 */
private fun useCompactHeader(heightDp: Float, fontScale: Float): Boolean {
    val inner = heightDp - PAD_V * 2
    val listSpace = inner - 52f * fontScale - ROW_GAP
    return listSpace < ROW_H * 2 + ROW_GAP
}

private const val NARROW_ROW_H = 40f

/**
 * 窄小组件（2×2 等，宽度 < 200dp）：和大尺寸一样用可滑动列表铺满标题下方，不再只放一条、中间空一块。
 * 高度不够时标题压成一行；宽度 ≥ 150dp 时每行右侧显示金额 / 「已付」按钮。
 */
@Composable
private fun Small(d: WidgetData, widthDp: Float, heightDp: Float, fontScale: Float) {
    val big = Currencies.formatList(d.monthly, d.currency)
    val inner = heightDp - PAD_V * 2
    val compact = inner - 62f * fontScale - ROW_GAP < NARROW_ROW_H * 2 + ROW_GAP
    val showAmount = widthDp >= 150f
    Column(GlanceModifier.fillMaxSize()) {
        if (compact) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("每月 ", style = TextStyle(color = White70, fontSize = 11.sp))
                Text(big, style = TextStyle(color = White, fontSize = fitSize(big, 18f, 10), fontWeight = FontWeight.Bold), maxLines = 1)
            }
        } else {
            Text("每月支出", style = TextStyle(color = White70, fontSize = 12.sp))
            Text(big, style = TextStyle(color = White, fontSize = fitSize(big, 22f, 8), fontWeight = FontWeight.Bold), maxLines = 1)
            Text("${d.activeCount} 个订阅", style = TextStyle(color = White55, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(ROW_GAP.dp))
        if (d.items.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Text("点此添加订阅", style = TextStyle(color = White70, fontSize = 12.sp))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(d.items, itemId = { it.id }) { item ->
                    Column(GlanceModifier.fillMaxWidth()) {
                        NarrowRow(item, showAmount)
                        Spacer(GlanceModifier.height(ROW_GAP.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NarrowRow(it: WidgetItem, showAmount: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().height(NARROW_ROW_H.dp).background(ImageProvider(R.drawable.widget_row))
            .padding(horizontal = 7.dp).clickable(openSub(androidx.glance.LocalContext.current, it.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(it.icon, it.name, GlanceModifier.size(26.dp).cornerRadius(8.dp), contentScale = ContentScale.Crop)
        Spacer(GlanceModifier.width(7.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(it.name, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(Billing.dueText(it.days), style = TextStyle(color = dueColor(it.days), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        if (showAmount) {
            Spacer(GlanceModifier.width(4.dp))
            if (it.needsConfirm) {
                Box(
                    GlanceModifier.background(ImageProvider(R.drawable.widget_btn)).padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<ConfirmPaidAction>(actionParametersOf(ConfirmPaidAction.KEY to it.id))),
                ) {
                    Text("已付", style = TextStyle(color = ColorProvider(Color(0xFF165B4C)), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Text(it.amount, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Large(d: WidgetData, compactHeader: Boolean) {
    Column(GlanceModifier.fillMaxSize()) {
        val big = Currencies.formatList(d.monthly, d.currency)
        val next30 = Currencies.formatList(d.next30, d.currency)
        if (compactHeader) {
            // 单行紧凑标题：给订阅列表腾出一行
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("每月 ", style = TextStyle(color = White70, fontSize = 12.sp))
                Text(big, style = TextStyle(color = White, fontSize = fitSize(big, 20f, 10), fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    Text("未来 30 天", style = TextStyle(color = White55, fontSize = 10.sp))
                    Text(next30, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("每月平均支出", style = TextStyle(color = White70, fontSize = 12.sp))
                    Text(big, style = TextStyle(color = White, fontSize = fitSize(big, 26f, 9), fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("未来 30 天", style = TextStyle(color = White55, fontSize = 11.sp))
                    Text(next30, style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.height(ROW_GAP.dp))
        if (d.items.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Text("还没有订阅，点此添加", style = TextStyle(color = White70, fontSize = 13.sp))
            }
        } else {
            // 可滑动列表：放得下几条就显示几条，其余上下滑动查看
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(d.items, itemId = { it.id }) { item ->
                    Column(GlanceModifier.fillMaxWidth()) {
                        ItemRow(item)
                        Spacer(GlanceModifier.height(ROW_GAP.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(it: WidgetItem) {
    val iconSize = 30f
    Row(
        GlanceModifier.fillMaxWidth().height(ROW_H.dp).background(ImageProvider(R.drawable.widget_row))
            .padding(horizontal = 8.dp).clickable(openSub(androidx.glance.LocalContext.current, it.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(it.icon, it.name, GlanceModifier.size(iconSize.dp).cornerRadius((iconSize * 0.3f).dp), contentScale = ContentScale.Crop)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(it.name, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Billing.dueText(it.days), style = TextStyle(color = dueColor(it.days), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(" · ${it.date}", style = TextStyle(color = White55, fontSize = 11.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        if (it.needsConfirm) {
            Box(
                GlanceModifier.background(ImageProvider(R.drawable.widget_btn)).padding(horizontal = 10.dp, vertical = 5.dp)
                    .clickable(actionRunCallback<ConfirmPaidAction>(actionParametersOf(ConfirmPaidAction.KEY to it.id))),
            ) {
                Text("已付", style = TextStyle(color = ColorProvider(Color(0xFF165B4C)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            Text(it.amount, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/** Glance 没有自动缩放字号：金额超过 fitChars 个字符时按比例缩小，避免被截断成 "CN¥3,416..." */
private fun fitSize(text: String, base: Float, fitChars: Int) =
    (if (text.length <= fitChars) base else (base * fitChars / text.length).coerceAtLeast(14f)).sp

private fun dueColor(days: Long): ColorProvider = when {
    days < 0 -> ColorProvider(Color(0xFFFFA4A7))
    days <= 3 -> ColorProvider(Color(0xFFFFD27A))
    else -> White70
}

private fun openSub(context: Context, id: Long) = androidx.glance.appwidget.action.actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_SUB_ID, id)
        // data 不同保证每个条目的 PendingIntent 互不覆盖
        .setData(Uri.parse("subnook://sub/$id"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

/** 小组件上的「已付」按钮 */
class ConfirmPaidAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY] ?: return
        App.get(context).processor.confirmPaid(id)
        SubnookWidget().updateAll(context)
    }

    companion object {
        val KEY = ActionParameters.Key<Long>("sub_id")
    }
}

class SubnookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubnookWidget()
}

object Widgets {
    suspend fun refresh(context: Context) {
        runCatching { SubnookWidget().updateAll(context) }
    }
}
