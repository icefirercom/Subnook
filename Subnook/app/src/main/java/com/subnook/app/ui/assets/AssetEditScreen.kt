package com.subnook.app.ui.assets

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subnook.app.data.Asset
import com.subnook.app.data.AssetStatus
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.IconType
import com.subnook.app.data.PayMethod
import com.subnook.app.data.WarrantyPreset
import com.subnook.app.data.cleanCategory
import com.subnook.app.domain.Assets
import com.subnook.app.domain.OrderOcrResult
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.AppAlertDialog
import com.subnook.app.ui.components.AssetPhotos
import com.subnook.app.ui.components.ChineseDatePickerDialog
import com.subnook.app.ui.components.CurrencyPickerDialog
import com.subnook.app.ui.components.ExpandableNoteCard
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconPickerSheet
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.components.ImageCropDialog
import com.subnook.app.ui.components.OrderOcrScanner
import com.subnook.app.ui.components.PaletteColors
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.components.copyToClipboard
import com.subnook.app.ui.edit.suggestedPayChannels
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DateTarget { PURCHASE, WARRANTY, EXT_WARRANTY, RETIRED }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetEditScreen(
    vm: MainViewModel,
    id: Long,
    onBack: () -> Unit,
    onAddMaintenance: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current

    var original by remember { mutableStateOf<Asset?>(null) }
    var loaded by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var iconType by remember { mutableStateOf(IconType.MATERIAL) }
    var iconValue by remember { mutableStateOf("Smartphone") }
    var color by remember { mutableStateOf(PaletteColors.random()) }
    var category by remember { mutableStateOf(Category.DIGITAL) }
    var price by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(st.settings.displayCurrency) }
    var purchaseDate by remember { mutableStateOf(LocalDate.now()) }
    var purchaseChannel by remember { mutableStateOf("") }
    var payChannel by remember { mutableStateOf("") }
    var warrantyPreset by remember { mutableStateOf(WarrantyPreset.Y1) }
    var warrantyEnd by remember { mutableStateOf<LocalDate?>(LocalDate.now().plusYears(1)) }
    var hasExtWarranty by remember { mutableStateOf(false) }
    var extWarrantyLabel by remember { mutableStateOf("") }
    var extWarrantyEnd by remember { mutableStateOf(LocalDate.now().plusYears(6)) }
    var remind by remember { mutableStateOf(true) }
    var remindDays by remember { mutableStateOf(30) }
    var serialNumber by remember { mutableStateOf("") }
    var orderNumber by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf(AssetStatus.ACTIVE) }
    var retiredDate by remember { mutableStateOf(LocalDate.now()) }
    var soldAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    var iconSheet by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var currencyDlg by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var addPurchaseChannelDlg by remember { mutableStateOf(false) }
    var removePurchaseChannelTarget by remember { mutableStateOf<String?>(null) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    var deleteDlg by remember { mutableStateOf(false) }
    var scanningOcr by remember { mutableStateOf(false) }
    var ocrPending by remember { mutableStateOf<Pair<OrderOcrResult, android.net.Uri>?>(null) }

    val iconPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            iconSheet = false
            cropUri = uri
        }
    }
    val receiptPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { AssetPhotos.saveFromUri(ctx, uri) }
                if (saved != null) photos += saved else vm.toast("图片保存失败")
            }
        }
    }
    val orderOcrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                scanningOcr = true
                runCatching { OrderOcrScanner.scan(ctx, uri) }
                    .onSuccess { res ->
                        scanningOcr = false
                        ocrPending = res to uri
                    }
                    .onFailure {
                        scanningOcr = false
                        vm.toast("订单截图识别失败，请换一张清晰截图")
                    }
            }
        }
    }

    LaunchedEffect(id) {
        if (loaded) return@LaunchedEffect
        if (id > 0) {
            vm.observeAsset(id).first()?.let { a ->
                original = a
                name = a.name
                iconType = a.iconType
                iconValue = a.iconValue
                color = a.color
                category = a.category
                price = if (a.price <= 0.0) "" else a.price.toBigDecimal().stripTrailingZeros().toPlainString()
                currency = a.currency
                purchaseDate = LocalDate.ofEpochDay(a.purchaseDay)
                purchaseChannel = a.purchaseChannel
                payChannel = a.payChannel
                warrantyEnd = a.warrantyEndDay?.let { LocalDate.ofEpochDay(it) }
                warrantyPreset = when (warrantyEnd) {
                    null -> WarrantyPreset.NONE
                    else -> WarrantyPreset.entries.firstOrNull {
                        it.months != null && it.months > 0 && Assets.calcWarrantyEnd(purchaseDate, it.months) == warrantyEnd
                    } ?: WarrantyPreset.CUSTOM
                }
                hasExtWarranty = a.extWarrantyEndDay != null
                extWarrantyLabel = a.extWarrantyLabel
                extWarrantyEnd = a.extWarrantyEndDay?.let { LocalDate.ofEpochDay(it) } ?: purchaseDate.plusYears(6)
                remind = a.remindEnabled
                remindDays = a.remindDaysBefore
                serialNumber = a.serialNumber
                orderNumber = a.orderNumber
                photos.clear()
                photos.addAll(a.photoList)
                status = a.status
                retiredDate = a.retiredDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
                soldAmount = if (a.soldAmount <= 0.0) "" else a.soldAmount.toBigDecimal().stripTrailingZeros().toPlainString()
                note = a.note
            }
        } else {
            currency = st.settings.displayCurrency
        }
        loaded = true
    }

    fun doSave() {
        if (id > 0 && original == null) return
        if (name.isBlank()) {
            showErrors = true
            vm.toast("请填写物品名称")
            return
        }
        val pVal = if (price.isBlank()) 0.0 else (price.replace(",", "").toDoubleOrNull()?.takeIf { it >= 0 } ?: run {
            showErrors = true
            vm.toast("请输入正确的入手价格")
            return
        })
        val sVal = if (status == AssetStatus.SOLD && soldAmount.isNotBlank()) {
            soldAmount.replace(",", "").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        } else 0.0

        val base = original ?: Asset(name = "", purchaseDay = purchaseDate.toEpochDay())
        val updated = base.copy(
            name = name.trim(),
            iconType = iconType,
            iconValue = iconValue,
            color = color,
            category = category,
            price = pVal,
            currency = currency,
            purchaseDay = purchaseDate.toEpochDay(),
            purchaseChannel = purchaseChannel.trim(),
            payChannel = payChannel.trim(),
            warrantyEndDay = warrantyEnd?.toEpochDay(),
            extWarrantyEndDay = if (hasExtWarranty) extWarrantyEnd.toEpochDay() else null,
            extWarrantyLabel = if (hasExtWarranty) extWarrantyLabel.trim().ifBlank { "核心部件/延保" } else "",
            remindEnabled = remind && (warrantyEnd != null || hasExtWarranty),
            remindDaysBefore = remindDays,
            serialNumber = serialNumber.trim(),
            orderNumber = orderNumber.trim(),
            photos = photos.joinToString("\n"),
            status = status,
            retiredDay = if (status == AssetStatus.SOLD || status == AssetStatus.RETIRED) retiredDate.toEpochDay() else null,
            soldAmount = sVal,
            note = note.trim(),
        )
        vm.saveAsset(updated) { onBack() }
    }

    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.CHINA) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "编辑物品" else "新增物品", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (id > 0) IconButton(onClick = { deleteDlg = true }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = extra.danger) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(cs.background).navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = cs.primary.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(extra.accentBrush)
                        .clickable { doSave() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (id > 0) "保存修改" else "添加物品", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 图标与名称
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(interactionSource = null, indication = null) { iconSheet = true }) {
                    Box(Modifier.shadow(16.dp, RoundedCornerShape(24.dp), spotColor = Color(color).copy(alpha = 0.45f))) {
                        SubIcon(iconType, iconValue, color, name.ifBlank { "物" }, size = 84.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(30.dp).clip(CircleShape)
                            .background(cs.background).padding(3.dp).clip(CircleShape).background(extra.accentBrush),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Edit, "更换图标", Modifier.size(15.dp), tint = Color.White) }
                }
                Spacer(Modifier.height(16.dp))
                AssetField(
                    value = name,
                    onChange = { name = it },
                    label = "物品名称（如：iPhone 16 Pro、格力 1.5P 空调）",
                    isError = showErrors && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 从线上订单截图离线识别（订单号 / 渠道 / 金额 / 日期 / SN）
            SoftCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !scanningOcr) {
                            orderOcrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(Icons.Rounded.DocumentScanner, cs.primary, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (scanningOcr) "正在本地离线识别订单…" else "从订单截图智能识别",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (scanningOcr) "图片仅在本机解析，不会上传网络"
                            else "自动提取订单号、渠道、实付金额、下单日期与 SN",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    if (scanningOcr) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.primary)
                    }
                }
            }

            // 使用状态
            Group("使用状态") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).padding(3.dp)) {
                        AssetStatus.entries.forEach { s ->
                            val sel = status == s
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                    .then(if (sel) Modifier.background(extra.accentBrush) else Modifier)
                                    .clickable { status = s }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) Color.White else cs.onSurfaceVariant,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    if (status == AssetStatus.SOLD || status == AssetStatus.RETIRED) {
                        DateRow(
                            label = if (status == AssetStatus.SOLD) "卖出日期" else "报废日期",
                            value = retiredDate.format(dateFmt),
                        ) { dateTarget = DateTarget.RETIRED }
                    }
                    if (status == AssetStatus.SOLD) {
                        AssetField(
                            value = soldAmount,
                            onChange = { soldAmount = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "二手回血金额（$currency）",
                            keyboard = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 购买信息
            Group("购买信息") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetField(
                            value = price,
                            onChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "入手价格",
                            keyboard = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable { currencyDlg = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(Currencies.listSymbol(currency), style = MaterialTheme.typography.titleMedium)
                            Text(Currencies.name(currency), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }

                    DateRow("购买日期", purchaseDate.format(dateFmt)) { dateTarget = DateTarget.PURCHASE }

                    // 购买渠道
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("购买渠道（选填 · 长按可删除自定义）", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        val channels = Assets.availablePurchaseChannels(
                            st.settings.customPurchaseChannels,
                            st.settings.disabledPurchaseChannels.toSet(),
                            purchaseChannel,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            channels.forEach { ch ->
                                val sel = purchaseChannel == ch
                                val canRemove = ch in st.settings.customPurchaseChannels
                                Box(
                                    Modifier.clip(RoundedCornerShape(11.dp))
                                        .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                        .combinedClickable(
                                            onClick = { purchaseChannel = if (sel) "" else ch },
                                            onLongClick = if (canRemove) ({ removePurchaseChannelTarget = ch }) else null,
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(ch, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else cs.onSurface)
                                }
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(11.dp)).border(1.dp, cs.outline, RoundedCornerShape(11.dp))
                                    .clickable { addPurchaseChannelDlg = true }.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(15.dp), tint = cs.primary)
                                Spacer(Modifier.width(3.dp))
                                Text("自定义", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            }
                        }
                    }

                    // 实际付款渠道（复用全局扣款渠道池）
                    val payChannels = suggestedPayChannels(
                        PayMethod.CREDIT_CARD,
                        st.settings.customPayChannels,
                        payChannel,
                        st.settings.disabledPayChannels.toSet(),
                    )
                    if (payChannels.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("付款账户（复用扣款渠道池，选填）", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                payChannels.forEach { ch ->
                                    val sel = payChannel == ch
                                    Box(
                                        Modifier.clip(RoundedCornerShape(11.dp))
                                            .then(
                                                if (sel) Modifier.background(cs.primaryContainer).border(1.dp, cs.primary, RoundedCornerShape(11.dp))
                                                else Modifier.background(cs.surfaceContainer),
                                            )
                                            .clickable { payChannel = if (sel) "" else ch }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                    ) {
                                        Text(ch, style = MaterialTheme.typography.labelMedium, color = if (sel) cs.onPrimaryContainer else cs.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 保修与过保提醒
            Group("保修期与提醒") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("整机保修时长", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WarrantyPreset.entries.forEach { wp ->
                            val sel = warrantyPreset == wp
                            Box(
                                Modifier.clip(RoundedCornerShape(11.dp))
                                    .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                    .clickable {
                                        warrantyPreset = wp
                                        if (wp.months != null) {
                                            warrantyEnd = Assets.calcWarrantyEnd(purchaseDate, wp.months)
                                        } else {
                                            if (warrantyEnd == null) warrantyEnd = purchaseDate.plusYears(1)
                                            dateTarget = DateTarget.WARRANTY
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(wp.label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else cs.onSurface)
                            }
                        }
                    }

                    warrantyEnd?.let { we ->
                        DateRow("保修到期日", we.format(dateFmt)) { dateTarget = DateTarget.WARRANTY }
                    }

                    // 扩展/核心部件保修（如空调压缩机 10 年 / AppleCare+）
                    Row(Modifier.fillMaxWidth().clickable { hasExtWarranty = !hasExtWarranty }, verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Shield, cs.tertiary, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("扩展 / 核心部件保修", style = MaterialTheme.typography.titleSmall)
                            Text("如空调压缩机 10 年、AppleCare+", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        Switch(hasExtWarranty, { hasExtWarranty = it })
                    }
                    if (hasExtWarranty) {
                        AssetField(
                            value = extWarrantyLabel,
                            onChange = { extWarrantyLabel = it.take(20) },
                            label = "延保名称（如：压缩机保修 / AppleCare+）",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DateRow("延保到期日", extWarrantyEnd.format(dateFmt)) { dateTarget = DateTarget.EXT_WARRANTY }
                    }

                    if (warrantyEnd != null || hasExtWarranty) {
                        Row(Modifier.fillMaxWidth().clickable { remind = !remind }, verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.NotificationsActive, extra.warning, size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("过保前提醒", style = MaterialTheme.typography.titleSmall)
                                Text("在提前天数、提前 7 天和过保当天提醒", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            Switch(remind, { remind = it })
                        }
                        if (remind) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(7, 15, 30, 60).forEach { d ->
                                    val sel = remindDays == d
                                    Box(
                                        Modifier.clip(RoundedCornerShape(10.dp))
                                            .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                            .clickable { remindDays = d }
                                            .padding(horizontal = 13.dp, vertical = 8.dp),
                                    ) {
                                        Text("提前${d}天", style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else cs.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 售后凭据（序列号、订单号一键复制 + 压缩凭证图）
            Group("售后凭据与发票图") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetField(
                            value = serialNumber,
                            onChange = { serialNumber = it.take(40) },
                            label = "序列号 / SN / IMEI（可选）",
                            modifier = Modifier.weight(1f),
                        )
                        if (serialNumber.isNotBlank()) {
                            IconButton(onClick = {
                                copyToClipboard(ctx, "序列号", serialNumber.trim())
                                vm.toast("已复制序列号")
                            }) { Icon(Icons.Rounded.ContentCopy, "复制序列号", tint = cs.primary) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetField(
                            value = orderNumber,
                            onChange = { orderNumber = it.take(40) },
                            label = "订单号（可选）",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = !scanningOcr,
                            onClick = {
                                orderOcrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ) { Icon(Icons.Rounded.DocumentScanner, "从截图识别订单号", tint = cs.primary) }
                        if (orderNumber.isNotBlank()) {
                            IconButton(onClick = {
                                copyToClipboard(ctx, "订单号", orderNumber.trim())
                                vm.toast("已复制订单号")
                            }) { Icon(Icons.Rounded.ContentCopy, "复制订单号", tint = cs.primary) }
                        }
                    }

                    Text("发票 / 保修卡 / 实物凭证（最多 3 张，自动压缩存档）", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        photos.forEach { fileName ->
                            val bmp = remember(fileName) {
                                val f = AssetPhotos.file(ctx, fileName)
                                if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                    .background(cs.surfaceContainer)
                                    .clickable { previewPhoto = fileName },
                            ) {
                                if (bmp != null) {
                                    Image(bmp, "凭证图", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable {
                                            photos.remove(fileName)
                                            AssetPhotos.delete(ctx, fileName)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Close, "删除图片", Modifier.size(14.dp), tint = Color.White) }
                            }
                        }
                        if (photos.size < 3) {
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
                                    .background(cs.surfaceContainer.copy(alpha = 0.5f))
                                    .clickable {
                                        receiptPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, "添加凭证图", tint = cs.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text("添加图片", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                                }
                            }
                        }
                        repeat((2 - photos.size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 分类
            Group("分类") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.forAsset.take(8).forEach { c ->
                            val sel = category == c
                            Box(
                                Modifier.clip(RoundedCornerShape(11.dp))
                                    .then(if (sel) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
                                    .clickable { category = c }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(c.label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else cs.onSurface)
                            }
                        }
                    }
                }
            }

            // 可扩展备注（支持硬件码 / 账号ID / 维修记录与一键复制）
            ExpandableNoteCard(
                value = note,
                onChange = { note = it },
                placeholder = "记录硬件码、账号 ID、型号规格、配件清单或维修记录（支持多行扩展）",
                templates = listOf("型号规格：", "硬件码：", "账号ID：", "保修政策：", "维修记录："),
                onCopied = vm::toast,
            )

            // 编辑已有物品：持有折旧概览 + 快捷关联保养/耗材提醒
            if (id > 0) {
                val previewAsset = Asset(
                    id = id, name = name, price = price.toDoubleOrNull() ?: 0.0, currency = currency,
                    purchaseDay = purchaseDate.toEpochDay(), status = status,
                    retiredDay = if (status == AssetStatus.SOLD || status == AssetStatus.RETIRED) retiredDate.toEpochDay() else null,
                    soldAmount = soldAmount.toDoubleOrNull() ?: 0.0,
                )
                Group("持有统计") {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("持有天数", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text("${Assets.usedDays(previewAsset)} 天", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("实际净支出", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(Currencies.formatList(Assets.netCost(previewAsset), currency), style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("日均成本", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text("${Currencies.formatList(Assets.dailyCost(previewAsset), currency)}/天", style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = cs.primary)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onAddMaintenance("${name.trim()} 保养/耗材") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("为它添加周期保养 / 耗材提醒")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (iconSheet) {
        IconPickerSheet(
            color = color, name = name,
            onDismiss = { iconSheet = false },
            onPick = { t, v -> iconType = t; iconValue = v; iconSheet = false },
            onColor = { color = it },
            onPickPhoto = { iconPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
    cropUri?.let { uri ->
        ImageCropDialog(
            uri = uri,
            onDismiss = { cropUri = null },
            onDone = { saved -> iconType = IconType.CUSTOM; iconValue = saved; cropUri = null },
        )
    }
    if (currencyDlg) {
        CurrencyPickerDialog(currency, onDismiss = { currencyDlg = false }) {
            currency = it
            currencyDlg = false
        }
    }
    dateTarget?.let { target ->
        val (initDate, dlgTitle) = when (target) {
            DateTarget.PURCHASE -> purchaseDate to "选择购买日期"
            DateTarget.WARRANTY -> (warrantyEnd ?: purchaseDate.plusYears(1)) to "选择保修到期日"
            DateTarget.EXT_WARRANTY -> extWarrantyEnd to "选择延保到期日"
            DateTarget.RETIRED -> retiredDate to if (status == AssetStatus.SOLD) "选择卖出日期" else "选择报废日期"
        }
        ChineseDatePickerDialog(
            initial = initDate,
            title = dlgTitle,
            onDismiss = { dateTarget = null },
            onConfirm = { picked ->
                when (target) {
                    DateTarget.PURCHASE -> {
                        purchaseDate = picked
                        warrantyPreset.months?.let { m -> warrantyEnd = Assets.calcWarrantyEnd(picked, m) }
                    }
                    DateTarget.WARRANTY -> {
                        warrantyEnd = picked
                        warrantyPreset = WarrantyPreset.CUSTOM
                    }
                    DateTarget.EXT_WARRANTY -> extWarrantyEnd = picked
                    DateTarget.RETIRED -> retiredDate = picked
                }
                dateTarget = null
            },
        )
    }
    if (addPurchaseChannelDlg) {
        var input by remember { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = { addPurchaseChannelDlg = false },
            title = { Text("新增购买渠道") },
            text = {
                AssetField(input, { input = it.take(20) }, "渠道名称（如：Apple 官网、山姆）", modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    enabled = cleanCategory(input).isNotEmpty(),
                    onClick = {
                        val n = cleanCategory(input)
                        vm.addCustomPurchaseChannel(n)
                        purchaseChannel = n
                        addPurchaseChannelDlg = false
                    },
                ) { Text("添加并使用") }
            },
            dismissButton = { TextButton(onClick = { addPurchaseChannelDlg = false }) { Text("取消") } },
        )
    }
    removePurchaseChannelTarget?.let { ch ->
        AppAlertDialog(
            onDismissRequest = { removePurchaseChannelTarget = null },
            title = { Text("移除购买渠道「$ch」？") },
            text = { Text("仅从自定义渠道列表移除，已有物品记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeCustomPurchaseChannel(ch)
                    if (purchaseChannel == ch) purchaseChannel = ""
                    removePurchaseChannelTarget = null
                }) { Text("移除", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { removePurchaseChannelTarget = null }) { Text("取消") } },
        )
    }
    previewPhoto?.let { fileName ->
        val bmp = remember(fileName) {
            val f = AssetPhotos.file(ctx, fileName)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
        }
        Dialog(onDismissRequest = { previewPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { previewPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(bmp, "凭证大图", Modifier.fillMaxWidth().padding(16.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
    ocrPending?.let { (result, uri) ->
        OcrResultDialog(
            result = result,
            canSavePhoto = photos.size < 3,
            onCopyRaw = {
                copyToClipboard(ctx, "识别原文", result.rawText)
                vm.toast("已复制识别原文")
            },
            onDismiss = { ocrPending = null },
            onApply = { orderNo, channel, parsedPrice, parsedDate, serial, savePhoto ->
                var filled = 0
                if (!orderNo.isNullOrBlank()) {
                    orderNumber = orderNo.take(40)
                    filled++
                }
                if (!channel.isNullOrBlank()) {
                    purchaseChannel = channel
                    filled++
                }
                if (parsedPrice != null && parsedPrice > 0.0) {
                    price = parsedPrice.toBigDecimal().stripTrailingZeros().toPlainString()
                    filled++
                }
                if (parsedDate != null) {
                    purchaseDate = parsedDate
                    warrantyPreset.months?.let { m -> warrantyEnd = Assets.calcWarrantyEnd(parsedDate, m) }
                    filled++
                }
                if (!serial.isNullOrBlank()) {
                    serialNumber = serial.take(40)
                    filled++
                }
                ocrPending = null
                if (savePhoto && photos.size < 3) {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { AssetPhotos.saveFromUri(ctx, uri) }
                        if (saved != null && photos.size < 3) photos += saved
                        vm.toast(if (filled > 0) "已填入 $filled 项订单信息并保存凭证图" else "已保存为凭证照片")
                    }
                } else if (filled > 0) {
                    vm.toast("已填入 $filled 项订单信息")
                }
            },
        )
    }
    if (deleteDlg) {
        AppAlertDialog(
            onDismissRequest = { deleteDlg = false },
            title = { Text("删除「$name」？") },
            text = { Text("该物品档案及关联凭证图片将被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let(vm::deleteAsset)
                    deleteDlg = false
                    onBack()
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteDlg = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun OcrResultDialog(
    result: OrderOcrResult,
    canSavePhoto: Boolean,
    onCopyRaw: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (
        orderNo: String?,
        channel: String?,
        price: Double?,
        date: LocalDate?,
        serial: String?,
        savePhoto: Boolean,
    ) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var selectedOrder by remember(result) { mutableStateOf(result.orderNumber) }
    var useOrder by remember(result) { mutableStateOf(result.orderNumber != null) }
    var useChannel by remember(result) { mutableStateOf(result.purchaseChannel != null) }
    var usePrice by remember(result) { mutableStateOf(result.price != null) }
    var useDate by remember(result) { mutableStateOf(result.purchaseDate != null) }
    var useSerial by remember(result) { mutableStateOf(result.serialNumber != null) }
    var savePhoto by remember(result, canSavePhoto) { mutableStateOf(canSavePhoto) }
    var showRaw by remember(result) { mutableStateOf(false) }

    val hasSelection = (useOrder && !selectedOrder.isNullOrBlank()) ||
        (useChannel && result.purchaseChannel != null) ||
        (usePrice && result.price != null) ||
        (useDate && result.purchaseDate != null) ||
        (useSerial && result.serialNumber != null) ||
        (savePhoto && canSavePhoto)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.hasAnyField) "订单截图识别结果" else "未识别到标准订单字段") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!result.hasAnyField) {
                    Text(
                        "未能自动提取订单号或金额，可展开下方识别原文手动复制，或先将截图存为凭证照片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "勾选需要填入的字段（纯本机离线识别，已填内容可随时微调）：",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                }

                if (selectedOrder != null) {
                    OcrFieldCheckRow(
                        checked = useOrder,
                        onCheckedChange = { useOrder = it },
                        label = "订单号",
                        value = selectedOrder!!,
                    )
                    if (result.orderCandidates.size > 1) {
                        Column(Modifier.padding(start = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("检测到多个候选单号，点击切换：", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                result.orderCandidates.forEach { cand ->
                                    val sel = selectedOrder == cand
                                    Box(
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) cs.primaryContainer else cs.surfaceContainer)
                                            .clickable {
                                                selectedOrder = cand
                                                useOrder = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            cand,
                                            style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                                            color = if (sel) cs.onPrimaryContainer else cs.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                result.purchaseChannel?.let { ch ->
                    OcrFieldCheckRow(
                        checked = useChannel,
                        onCheckedChange = { useChannel = it },
                        label = "购买渠道",
                        value = ch,
                    )
                }

                result.price?.let { p ->
                    val formatted = p.toBigDecimal().stripTrailingZeros().toPlainString()
                    OcrFieldCheckRow(
                        checked = usePrice,
                        onCheckedChange = { usePrice = it },
                        label = "实付金额",
                        value = "¥$formatted",
                    )
                }

                result.purchaseDate?.let { d ->
                    OcrFieldCheckRow(
                        checked = useDate,
                        onCheckedChange = { useDate = it },
                        label = "购买日期（联动重算保修期）",
                        value = d.toString(),
                    )
                }

                result.serialNumber?.let { sn ->
                    OcrFieldCheckRow(
                        checked = useSerial,
                        onCheckedChange = { useSerial = it },
                        label = "序列号 / SN",
                        value = sn,
                    )
                }

                if (canSavePhoto) {
                    OcrFieldCheckRow(
                        checked = savePhoto,
                        onCheckedChange = { savePhoto = it },
                        label = "同时保存为凭证照片",
                        value = "自动压缩后存档到售后凭据区",
                    )
                }

                if (result.rawText.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (showRaw) "收起识别原文" else "查看识别原文",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { showRaw = !showRaw }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (showRaw) {
                            Text(
                                "复制原文",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCopyRaw)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (showRaw) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(cs.surfaceContainer).padding(10.dp),
                        ) {
                            Text(
                                result.rawText,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasSelection,
                onClick = {
                    onApply(
                        if (useOrder) selectedOrder else null,
                        if (useChannel) result.purchaseChannel else null,
                        if (usePrice) result.price else null,
                        if (useDate) result.purchaseDate else null,
                        if (useSerial) result.serialNumber else null,
                        savePhoto && canSavePhoto,
                    )
                },
            ) { Text(if (result.hasAnyField) "填入选中项" else "保存凭证图") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun OcrFieldCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    value: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceContainer.copy(alpha = 0.7f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.CalendarMonth, cs.primary, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.outline)
    }
}

@Composable
private fun AssetField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    singleLine: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        isError = isError,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer, errorContainerColor = cs.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}
