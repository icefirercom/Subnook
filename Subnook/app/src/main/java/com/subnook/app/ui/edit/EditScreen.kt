package com.subnook.app.ui.edit

import com.subnook.app.ui.components.AppAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.components.Pill
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.CycleUnit
import com.subnook.app.data.IconType
import com.subnook.app.data.PayMethod
import com.subnook.app.data.PresetCatalog
import com.subnook.app.data.Subscription
import com.subnook.app.data.SubKind
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.domain.Installments
import com.subnook.app.domain.Billing
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.CurrencyPickerDialog
import com.subnook.app.ui.components.IconFiles
import com.subnook.app.ui.components.IconPickerSheet
import com.subnook.app.ui.components.PaletteColors
import com.subnook.app.ui.components.SubIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

internal class Form {
    var name by mutableStateOf("")
    var iconType by mutableStateOf(IconType.TEXT)
    var iconValue by mutableStateOf("")
    var color by mutableStateOf(PaletteColors[12])
    var amount by mutableStateOf("")
    var currency by mutableStateOf("CNY")
    var cycleCount by mutableStateOf("1")
    var cycleUnit by mutableStateOf(CycleUnit.MONTH)
    var next by mutableStateOf(LocalDate.now().plusMonths(1))
    var payMethod by mutableStateOf(PayMethod.ALIPAY)
    var autoRenew by mutableStateOf(true)
    var category by mutableStateOf(Category.OTHER)
    var remind by mutableStateOf(true)
    var remindDays by mutableStateOf(3)
    var active by mutableStateOf(true)
    var note by mutableStateOf("")
    var presetKey by mutableStateOf<String?>(null)
    /** 选「其他」时填的名称（可以不填） */
    var payMethodNote by mutableStateOf("")
    /** 实际扣款渠道（如 信用卡7727、借记卡2990、微信零钱，可跨平台复用） */
    var payChannel by mutableStateOf("")
    var categoryNote by mutableStateOf("")
    var original by mutableStateOf<Subscription?>(null)
    var loaded by mutableStateOf(false)

    // ---- 分期 ----
    var kind by mutableStateOf(SubKind.SUBSCRIPTION)
    var instPrincipal by mutableStateOf("")
    var instTerms by mutableStateOf("12")
    var instRate by mutableStateOf("")
    var instMethod by mutableStateOf(InstallmentMethod.INTEREST_FREE)
    var instFirst by mutableStateOf("")
    /** 下次还款是第几期 */
    var instCurrent by mutableStateOf("1")
    /** 非周期订阅：是否买断制（无结束时间） */
    var noExpiry by mutableStateOf(false)
    val isInstallment get() = kind == SubKind.INSTALLMENT
    val isOneTime get() = kind == SubKind.ONE_TIME
    val isLifetime get() = isOneTime && noExpiry

    // ---- 余额付款 ----
    var balance by mutableStateOf("")
    /** 用户改过余额输入框；没改过时保存用数据库里最新的余额（后台可能已经扣过款） */
    var balanceTouched by mutableStateOf(false)
    fun balanceValue(): Double? = if (balance.isBlank()) 0.0 else balance.num()?.takeIf { it >= 0 }
    /** 充值 / 删除记录后，同步输入框里显示的余额（不算用户改动） */
    fun adjustBalance(delta: Double) {
        val v = ((balanceValue() ?: original?.balance ?: 0.0) + delta).coerceAtLeast(0.0)
        balance = com.subnook.app.data.roundMoney(v).plain()
    }

    fun startInstallment(displayCurrency: String) {
        kind = SubKind.INSTALLMENT
        category = Category.OTHER
        payMethod = PayMethod.CREDIT_CARD; autoRenew = true
        iconType = IconType.MATERIAL; iconValue = "CreditCard"
        color = PaletteColors.random()
        currency = displayCurrency
    }

    fun load(s: Subscription) {
        original = s
        name = s.name; iconType = s.iconType; iconValue = s.iconValue; color = s.color
        amount = s.amount.plain(); currency = s.currency
        cycleCount = s.cycleCount.toString(); cycleUnit = s.cycleUnit; next = LocalDate.ofEpochDay(s.nextBillingDay)
        payMethod = s.payMethod; autoRenew = s.autoRenew; category = s.category
        remind = s.remindEnabled; remindDays = s.remindDaysBefore; active = s.active; note = s.note; presetKey = s.presetKey
        payMethodNote = s.payMethodNote; payChannel = s.payChannel; categoryNote = s.categoryNote
        kind = s.kind
        noExpiry = s.noExpiry
        balance = if (s.balance == 0.0) "" else s.balance.plain(); balanceTouched = false
        if (s.isInstallment) {
            instPrincipal = s.instPrincipal.plain(); instTerms = s.instTerms.toString()
            instRate = if (s.instRate == 0.0) "" else s.instRate.plain(); instMethod = s.instMethod
            instFirst = s.instFirstAmount?.plain().orEmpty()
            instCurrent = Installments.currentTerm(s).toString()
            // 已还清的分期是自动停用的；如果用户把期数改回去，应该重新启用
            if (Installments.isFinished(s) && !Installments.isPaidOff(s)) active = true
        }
    }

    /**
     * 编辑页开着时，后台可能扣了款、充了值、推进了账单日。用户没动过的这几项跟着数据库走，
     * 否则保存时会用打开页面时的旧值覆盖（比如把账单日改回已扣过的那天，下一轮又扣一次）。
     */
    fun syncFrom(s: Subscription) {
        val o = original ?: return
        if (o == s) return
        if (next == LocalDate.ofEpochDay(o.nextBillingDay)) next = LocalDate.ofEpochDay(s.nextBillingDay)
        if (s.isInstallment && o.isInstallment && instCurrent == Installments.currentTerm(o).toString()) {
            instCurrent = Installments.currentTerm(s).toString()
        }
        if (!balanceTouched) balance = if (s.balance == 0.0) "" else s.balance.plain()
        original = s
    }

    private fun Double.plain() = toBigDecimal().stripTrailingZeros().toPlainString()
    private fun String.num() = replace(",", "").trim().toDoubleOrNull()

    fun principalValue() = instPrincipal.num()?.takeIf { it > 0 }
    fun termsValue() = instTerms.toIntOrNull()?.takeIf { it in 1..600 }
    fun currentValue() = instCurrent.toIntOrNull()?.takeIf { c -> c >= 1 && c <= (termsValue() ?: 0) + 1 }
    fun rateValue() = if (instMethod.rateLabel == null || instRate.isBlank()) 0.0 else instRate.num()?.takeIf { it >= 0 }

    fun applyPreset(key: String, hasImage: Boolean) {
        val p = PresetCatalog.all.firstOrNull { it.key == key } ?: return
        presetKey = p.key
        name = p.name
        iconType = if (hasImage) IconType.PRESET else IconType.MATERIAL
        iconValue = if (hasImage) p.key else p.material
        color = p.color
        amount = p.price.toBigDecimal().stripTrailingZeros().toPlainString()
        currency = p.currency
        cycleCount = p.cycleCount.toString(); cycleUnit = p.cycleUnit
        next = Billing.shift(LocalDate.now(), p.cycleCount, p.cycleUnit, LocalDate.now().dayOfMonth)
        // 预设里的「手动付款」（房租、保险等）：付款方式记成「其他」，关掉自动续费就是手动
        payMethod = if (p.payMethod == PayMethod.MANUAL) PayMethod.OTHER else p.payMethod; autoRenew = p.payMethod.defaultAuto
        category = p.category
    }

    fun applyCustomPreset(cp: com.subnook.app.data.CustomPreset) {
        presetKey = cp.key
        name = cp.name
        iconType = IconType.TEXT
        iconValue = ""
        color = cp.color
        kind = if (cp.oneTime) SubKind.ONE_TIME else SubKind.SUBSCRIPTION
        amount = if (cp.oneTime && cp.price <= 0.0) "" else cp.price.toBigDecimal().stripTrailingZeros().toPlainString()
        currency = cp.currency
        cycleCount = cp.cycleCount.toString()
        cycleUnit = cp.cycleUnit
        next = if (cp.oneTime) LocalDate.now().plusMonths(1)
        else Billing.shift(LocalDate.now(), cp.cycleCount, cp.cycleUnit, LocalDate.now().dayOfMonth)
        payMethod = if (cp.payMethod == PayMethod.MANUAL) PayMethod.OTHER else cp.payMethod
        payChannel = cp.payChannel
        autoRenew = if (cp.oneTime) false else cp.payMethod.defaultAuto
        category = cp.category
    }

    /** [requireName] = false：给预览用（名称还没填也能算） */
    fun build(requireName: Boolean = true): Subscription? {
        val cnt = if (isOneTime) 1 else cycleCount.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
        if (requireName && name.isBlank()) return null
        val o = original
        val dateChanged = o == null || o.nextBillingDay != next.toEpochDay()
        val base = o ?: Subscription(name = "", amount = 0.0, currency = "", nextBillingDay = 0, anchorDay = 1)
        val anchor = if (o == null || dateChanged || o.cycleUnit != cycleUnit) next.dayOfMonth else o.anchorDay
        val bal = (if (balanceTouched || o == null) balanceValue() else o.balance) ?: return null
        if (isInstallment) {
            val principal = principalValue() ?: return null
            val terms = termsValue() ?: return null
            val current = currentValue() ?: return null
            val rate = rateValue() ?: return null
            val first = if (instFirst.isBlank()) null else (instFirst.num()?.takeIf { it > 0 } ?: return null)
            return Installments.normalize(
                base.copy(
                    name = name.trim(), iconType = iconType, iconValue = iconValue, color = color,
                    currency = currency, cycleCount = cnt, cycleUnit = cycleUnit,
                    nextBillingDay = next.toEpochDay(), anchorDay = anchor,
                    payMethod = payMethod, autoRenew = autoRenew, category = category,
                    remindEnabled = remind, remindDaysBefore = remindDays, active = active, note = note.trim(),
                    presetKey = null,
                    remindedForDay = o?.remindedForDay,
                    overdueNotifiedDay = if (dateChanged) null else o?.overdueNotifiedDay,
                    kind = SubKind.INSTALLMENT,
                    instPrincipal = principal, instTerms = terms, instRate = rate, instMethod = instMethod,
                    instFirstAmount = first,
                    instStartDay = Installments.startFrom(next, current, cnt, cycleUnit, anchor).toEpochDay(),
                    balance = bal,
                    payMethodNote = payMethodNote.trim(), categoryNote = categoryNote.trim(),
                    payChannel = if (payMethod == PayMethod.BALANCE) "" else payChannel.trim(),
                ),
            )
        }
        val amt = if (isOneTime && amount.isBlank()) 0.0 else (amount.replace(",", "").toDoubleOrNull() ?: return null)
        return base.copy(
            name = name.trim(), iconType = iconType, iconValue = iconValue, color = color,
            amount = amt, currency = currency, cycleCount = cnt, cycleUnit = if (isOneTime) CycleUnit.MONTH else cycleUnit,
            nextBillingDay = next.toEpochDay(),
            anchorDay = anchor,
            payMethod = payMethod, autoRenew = if (isOneTime) false else autoRenew, category = category,
            remindEnabled = if (isLifetime) false else remind, remindDaysBefore = remindDays, active = active, note = note.trim(),
            presetKey = presetKey,
            kind = kind,
            noExpiry = if (isOneTime) noExpiry else false,
            remindedForDay = o?.remindedForDay,
            overdueNotifiedDay = if (dateChanged) null else o?.overdueNotifiedDay,
            balance = bal,
            payMethodNote = payMethodNote.trim(), categoryNote = categoryNote.trim(),
            payChannel = if (payMethod == PayMethod.BALANCE) "" else payChannel.trim(),
        )
    }
}

/** 表单存进 Bundle（original 不存，恢复后从数据库重新读） */
private val FormSaver = androidx.compose.runtime.saveable.listSaver<Form, Any?>(
    save = { f ->
        listOf(
            f.name, f.iconType.name, f.iconValue, f.color, f.amount, f.currency, f.cycleCount, f.cycleUnit.name,
            f.next.toEpochDay(), f.payMethod.name, f.autoRenew, f.category.name, f.remind, f.remindDays, f.active,
            f.note, f.presetKey, f.loaded,
            f.kind.name, f.instPrincipal, f.instTerms, f.instRate, f.instMethod.name, f.instFirst, f.instCurrent,
            f.balance, f.balanceTouched, f.payMethodNote, f.categoryNote, f.payChannel, f.noExpiry,
        )
    },
    restore = { l ->
        Form().apply {
            name = l[0] as String; iconType = IconType.valueOf(l[1] as String); iconValue = l[2] as String
            color = l[3] as Int; amount = l[4] as String; currency = l[5] as String; cycleCount = l[6] as String
            cycleUnit = CycleUnit.valueOf(l[7] as String); next = LocalDate.ofEpochDay(l[8] as Long)
            payMethod = PayMethod.valueOf(l[9] as String); autoRenew = l[10] as Boolean; category = Category.valueOf(l[11] as String)
            remind = l[12] as Boolean; remindDays = l[13] as Int; active = l[14] as Boolean
            note = l[15] as String; presetKey = l[16] as String?; loaded = l[17] as Boolean
            kind = SubKind.valueOf(l[18] as String); instPrincipal = l[19] as String; instTerms = l[20] as String
            instRate = l[21] as String; instMethod = InstallmentMethod.valueOf(l[22] as String)
            instFirst = l[23] as String; instCurrent = l[24] as String
            balance = l[25] as String; balanceTouched = l[26] as Boolean
            payMethodNote = l[27] as String; categoryNote = l[28] as String
            payChannel = l.getOrNull(29) as? String ?: ""
            noExpiry = l.getOrNull(30) as? Boolean ?: false
        }
    },
)

@Composable
fun EditScreen(vm: MainViewModel, id: Long, presetKey: String?, onBack: () -> Unit, prefillName: String = "", installment: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 表单状态可保存：去相册选图时 Activity 可能被系统回收重建，回来后填写的内容和选图结果都不能丢
    val form = rememberSaveable(saver = FormSaver) { Form() }
    val st by vm.state.collectAsState()
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    var iconSheet by rememberSaveable { mutableStateOf(false) }
    var cropUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val photo = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            iconSheet = false
            cropUri = uri
        }
    }
    var currencyDlg by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    var deleteDlg by remember { mutableStateOf(false) }
    var prepayDlg by remember { mutableStateOf(false) }
    var undoRecord by remember { mutableStateOf<com.subnook.app.data.PaymentRecord?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id, presetKey) {
        if (form.loaded) {
            // 从保存的状态恢复：只补回原始记录，不覆盖用户已改的内容
            if (id > 0 && form.original == null) form.original = vm.observeSub(id).first()
            return@LaunchedEffect
        }
        if (id > 0) {
            vm.observeSub(id).first()?.let(form::load)
        } else if (presetKey != null) {
            val customPreset = vm.state.value.settings.customPresets.firstOrNull { it.key == presetKey }
            if (customPreset != null) form.applyCustomPreset(customPreset)
            else form.applyPreset(presetKey, IconFiles.presetRes(context, presetKey) != 0)
        } else {
            form.currency = vm.state.value.settings.displayCurrency
            // 自定义订阅 / 分期：图标颜色随机，不总是同一个绿色
            form.color = PaletteColors.random()
            if (installment) form.startInstallment(vm.state.value.settings.displayCurrency)
            // 从搜索框带过来的名称
            form.name = prefillName
        }
        form.loaded = true
    }
    // 后台扣款 / 充值 / 推进账单日后，表单里用户没改过的字段跟着更新（见 Form.syncFrom）
    LaunchedEffect(id) {
        if (id <= 0) return@LaunchedEffect
        vm.observeSub(id).collect { s -> if (s != null && form.loaded) form.syncFrom(s) }
    }
    val paymentsFlow = remember(id) { if (id > 0) vm.observePayments(id) else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val payments by paymentsFlow.collectAsState(emptyList())
    val topUpsFlow = remember(id) { if (id > 0) vm.observeTopUps(id) else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val topUps by topUpsFlow.collectAsState(emptyList())

    // 「其他」填的分类名：这次编辑里已经问过要不要加入分类列表的名称（不重复问）
    var askedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var askedPayMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var saveAsk by remember { mutableStateOf<String?>(null) }
    var savePayAsk by remember { mutableStateOf<String?>(null) }
    fun doSave() {
        // 编辑已有订阅时，原始记录还没读出来就先不保存，免得被当成新订阅
        if (id > 0 && form.original == null) return
        val s = form.build()
        if (s == null) {
            showErrors = true
            vm.toast(
                when {
                    form.isInstallment -> "请填写名称、分期金额、期数和正确的当前期数"
                    form.isOneTime -> "请填写名称和正确的金额"
                    else -> "请填写名称、金额和正确的周期"
                },
            )
            return
        }
        // 填了新分类名但还没问过（没离开输入框就点了保存）：先问再保存
        val n = com.subnook.app.data.cleanCategory(form.categoryNote)
        if (form.category == Category.OTHER && needsAsk(n, st.settings.customCategories, askedCategory)) {
            saveAsk = n
            return
        }
        val pn = com.subnook.app.data.cleanCategory(form.payMethodNote)
        if (form.payMethod == PayMethod.OTHER && needsAskName(pn, st.settings.customPayMethods, askedPayMethod, payMethodLabels())) {
            savePayAsk = pn
            return
        }
        vm.save(s, keepDbBalance = !form.balanceTouched)
        onBack()
    }
    val save = { doSave() }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    val what = if (form.isInstallment) "分期" else "订阅"
                    Text(if (id > 0) "编辑$what" else "新建$what", style = MaterialTheme.typography.titleLarge)
                },
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
                    Modifier.fillMaxWidth().height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = cs.primary.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(16.dp)).background(extra.accentBrush).clickable { save() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (id > 0) "保存修改" else if (form.isInstallment) "添加分期" else "添加订阅",
                        style = MaterialTheme.typography.titleMedium, color = Color.White,
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 图标 + 名称
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(interactionSource = null, indication = null) { iconSheet = true }) {
                    Box(
                        Modifier.shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Color(form.color).copy(alpha = 0.6f), ambientColor = Color(form.color).copy(alpha = 0.3f)),
                    ) {
                        SubIcon(form.iconType, form.iconValue, form.color, form.name.ifBlank { "订" }, size = 88.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(30.dp).clip(CircleShape)
                            .background(cs.background).padding(3.dp).clip(CircleShape).background(extra.accentBrush),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Edit, "更换图标", Modifier.size(15.dp), tint = Color.White) }
                }
                Spacer(Modifier.height(18.dp))
                Field(
                    form.name, { form.name = it }, if (form.isInstallment) "名称（如：手机分期）" else "订阅名称",
                    isError = showErrors && form.name.isBlank(), modifier = Modifier.fillMaxWidth(),
                )
            }

            // 「启用中」放在最前面；关掉后下面的内容都收起来。已经提前还清的分期没有开关，内容照常显示（要能撤销提前还款）
            val paidOff = form.original?.let { Installments.isPaidOff(it) } == true
            if (paidOff) {
                SoftCardRow { Pill("已提前还清", extra.success) }
            } else {
                SoftCardRow {
                    SwitchRow(Icons.Rounded.PowerSettingsNew, cs.primary, "启用中", null, form.active) { form.active = it }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = form.active || paidOff) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (form.isInstallment) {
                InstallmentFields(form, showErrors, onCurrency = { currencyDlg = true }, onDate = { dateDlg = true })
                InstallmentPlan(form)
            } else Group(if (form.isOneTime) "到期与金额" else "金额与周期") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Segmented(
                        listOf(SubKind.SUBSCRIPTION, SubKind.ONE_TIME),
                        form.kind,
                        { if (it == SubKind.ONE_TIME) "非周期订阅" else "周期订阅" },
                        Modifier.fillMaxWidth(),
                    ) { k ->
                        form.kind = k
                        if (k == SubKind.ONE_TIME) form.autoRenew = false
                        else form.noExpiry = false
                    }
                    if (form.isOneTime) {
                        SwitchRow(
                            androidx.compose.material.icons.Icons.Rounded.AllInclusive,
                            extra.success,
                            "无结束时间（买断制）",
                            "永久有效，不触发到期倒计时与提醒",
                            form.noExpiry,
                        ) { on ->
                            form.noExpiry = on
                            if (on && form.original == null) form.next = LocalDate.now()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(
                            form.amount, { form.amount = it.filter { c -> c.isDigit() || c == '.' } },
                            if (form.isOneTime) "金额（可选）" else "付款金额",
                            keyboard = KeyboardType.Decimal,
                            isError = showErrors && !(form.isOneTime && form.amount.isBlank()) && form.amount.toDoubleOrNull() == null,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable { currencyDlg = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(Currencies.listSymbol(form.currency), style = MaterialTheme.typography.titleMedium)
                            Text(Currencies.name(form.currency), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    if (!form.isOneTime) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("每", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Field(
                                form.cycleCount, { form.cycleCount = it.filter(Char::isDigit).take(3) }, null,
                                keyboard = KeyboardType.Number,
                                isError = showErrors && (form.cycleCount.toIntOrNull() ?: 0) < 1,
                                modifier = Modifier.width(64.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            )
                            Spacer(Modifier.width(10.dp))
                            Segmented(CycleUnit.entries, form.cycleUnit, { it.label }, Modifier.weight(1f)) { form.cycleUnit = it }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable { dateDlg = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(Icons.Rounded.CalendarMonth, cs.primary, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    form.isLifetime -> "购买日期（参考）"
                                    form.isOneTime -> "到期时间"
                                    else -> "下次付款日期"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Text(form.next.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", java.util.Locale.CHINA)), style = MaterialTheme.typography.titleSmall)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.outline)
                    }
                }
            }

            Group(if (form.isOneTime) "扣款方式" else "付款方式") {
                Column(Modifier.padding(16.dp)) {
                    // 分期只列能分期的方式（花呗、白条、信用卡等）；手动 / 自动只看下面的开关
                    PayMethodPicker(
                        form = form,
                        custom = st.settings.customPayMethods,
                        customChannels = st.settings.customPayChannels,
                        disabledMethods = st.settings.disabledPayMethods.toSet(),
                        disabledChannels = st.settings.disabledPayChannels.toSet(),
                        onAddCustom = vm::addCustomPayMethod,
                        onRemoveCustom = vm::removeCustomPayMethod,
                        onAddChannel = vm::addCustomPayChannel,
                        onRemoveChannel = vm::removeCustomPayChannel,
                        askedFor = askedPayMethod,
                        onAsked = { askedPayMethod = it },
                    )
                    if (!form.isOneTime) {
                        Spacer(Modifier.height(14.dp))
                        SwitchRow(
                            Icons.Rounded.Autorenew, extra.success, if (form.isInstallment) "自动扣款" else "自动续费",
                            null, form.autoRenew,
                        ) { form.autoRenew = it }
                    }
                }
            }

            if (form.payMethod == PayMethod.BALANCE) {
                BalanceGroup(
                    form, topUps,
                    onCurrency = { currencyDlg = true },
                    onTopUp = if (id > 0 && form.original != null) { amount -> form.original?.let { vm.topUp(it, amount) }; form.adjustBalance(amount) } else null,
                    onDeleteTopUp = { t -> vm.deleteTopUp(t); form.adjustBalance(-t.amount) },
                )
            }

            Group("分类") {
                CategoryPicker(
                    form, st.settings.customCategories,
                    onAddCustom = vm::addCustomCategory, onRemoveCustom = vm::removeCustomCategory,
                    askedFor = askedCategory, onAsked = { askedCategory = it },
                )
            }

            if (!form.isLifetime) Group("提醒") {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        Icons.Rounded.NotificationsActive, extra.warning,
                        if (form.isOneTime) "到期提醒" else "扣款提醒",
                        null, form.remind,
                    ) { form.remind = it }
                    if (form.remind) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("提前天数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StepButton("−", enabled = form.remindDays > 0) { form.remindDays-- }
                            Text(
                                if (form.remindDays == 0) "当天" else "${form.remindDays} 天",
                                style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.width(64.dp),
                            )
                            StepButton("+", enabled = form.remindDays < 30) { form.remindDays++ }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 7, 14).forEach { d -> Chip("${d}天", form.remindDays == d) { form.remindDays = d } }
                        }
                    }
                }
            }

            com.subnook.app.ui.components.ExpandableNoteCard(
                value = form.note,
                onChange = { form.note = it },
                onCopied = vm::toast,
            )

            if (id > 0) {
                form.build()?.let { s ->
                    val cur = st.settings.displayCurrency
                    if (!s.isInstallment && !s.isOneTime) Group("折合") {
                        Row(Modifier.padding(16.dp)) {
                            listOf(SummaryPeriod.WEEK, SummaryPeriod.MONTH, SummaryPeriod.YEAR).forEach { p ->
                                Column(Modifier.weight(1f)) {
                                    Text(p.unitLabel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    Text(
                                        Currencies.formatList(st.rates.convert(Billing.periodCost(s, p), s.currency, cur), cur),
                                        style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                                    )
                                }
                            }
                        }
                    }
                    if (!s.autoRenew && !s.isLifetime && s.active && !Installments.isFinished(s)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vm.skip(s); onBack() }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(if (s.isOneTime) "不再提醒" else "跳过本期")
                            }
                            Button(onClick = { vm.confirmPaid(s); onBack() }, Modifier.weight(1.4f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(if (s.isOneTime) "确认到期" else "本期已付款")
                            }
                        }
                    }
                }
                // 提前还款：按数据库里保存的分期算（不是表单里还没保存的改动）
                val stored = form.original
                if (stored != null && stored.isInstallment && stored.active && !Installments.isFinished(stored)) {
                    OutlinedButton(
                        onClick = { prepayDlg = true }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    ) { Text("提前还款") }
                }
                Group("付款记录 · ${payments.size}") {
                    if (payments.isEmpty()) {
                        Text(
                            "暂无记录",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                        )
                    }
                    // 最多显示 3 条，多的在框里滑动
                    com.subnook.app.ui.components.CappedList(payments, rowHeight = 60.dp, key = { it.id }) { p ->
                        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Currencies.formatList(p.amount, p.currency), style = MaterialTheme.typography.titleSmall.merge(TabularNums))
                                Text(
                                    LocalDate.ofEpochDay(p.billingDay).let { d ->
                                        val o = form.original
                                        d.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) + when {
                                            p.prepay -> ""
                                            o != null && o.isInstallment -> " · 第 ${p.term ?: Installments.displayTerm(o, d)} 期"
                                            else -> ""
                                        } + if (p.balanceUsed > 0) " · 余额扣 ${Currencies.formatList(p.balanceUsed, p.currency)}" else ""
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            when {
                                p.prepay -> Pill(
                                    if (form.original?.instPaidOffDay == p.billingDay) "提前还清" else "提前还款", extra.warning,
                                )
                                else -> Pill(if (p.auto) "自动" else "手动确认", if (p.auto) extra.success else cs.primary)
                            }
                            IconButton(onClick = {
                                if (p.prepay) undoRecord = p
                                else {
                                    // 余额付款的记录：钱退回余额，输入框也同步
                                    if (p.balanceUsed > 0) form.adjustBalance(p.balanceUsed)
                                    vm.deletePayment(p)
                                }
                            }) { Icon(Icons.Rounded.Close, "删除记录", Modifier.size(18.dp), tint = cs.outline) }
                        }
                    }
                }
            }
            }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (iconSheet) {
        IconPickerSheet(
            color = form.color, name = form.name,
            onDismiss = { iconSheet = false },
            onPick = { t, v -> form.iconType = t; form.iconValue = v; iconSheet = false },
            onColor = { form.color = it },
            onPickPhoto = {
                photo.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
    cropUri?.let { uri ->
        com.subnook.app.ui.components.ImageCropDialog(
            uri,
            onDismiss = { cropUri = null },
            onDone = { name -> form.iconType = IconType.CUSTOM; form.iconValue = name; cropUri = null },
        )
    }
    if (currencyDlg) CurrencyPickerDialog(form.currency, { currencyDlg = false }) { form.currency = it; currencyDlg = false }
    if (dateDlg) {
        com.subnook.app.ui.components.ChineseDatePickerDialog(
            initial = form.next,
            title = when {
                form.isLifetime -> "选择购买日期"
                form.isOneTime -> "选择到期时间"
                form.isInstallment -> "选择下次还款日"
                else -> "选择下次付款日期"
            },
            onDismiss = { dateDlg = false },
            onConfirm = { picked ->
                form.next = picked
                dateDlg = false
            },
        )
    }
    saveAsk?.let { name ->
        val answer = { add: Boolean ->
            if (add) vm.addCustomCategory(name)
            askedCategory = name
            saveAsk = null
            doSave()
        }
        AddCategoryDialog(name) { add -> answer(add) }
    }
    savePayAsk?.let { name ->
        AddCategoryDialog(name, what = "付款方式") { add ->
            if (add) vm.addCustomPayMethod(name)
            askedPayMethod = name
            savePayAsk = null
            doSave()
        }
    }
    undoRecord?.let { p ->
        AppAlertDialog(
            onDismissRequest = { undoRecord = null },
            title = { Text("撤销这笔提前还款？") },
            text = {
                Text(
                    "删除这条 ${Currencies.formatList(p.amount, p.currency)} 的记录，分期恢复到提前还款前。",
                )
            },
            confirmButton = {
                TextButton(onClick = { undoRecord = null; vm.undoPrepay(p) { onBack() } }) {
                    Text("撤销并恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { undoRecord = null }) { Text("取消") } },
        )
    }
    if (prepayDlg) form.original?.let { o ->
        PrepayDialog(o, onDismiss = { prepayDlg = false }) { amount, mode ->
            prepayDlg = false
            vm.prepay(o, amount, mode) { onBack() }
        }
    }
    if (deleteDlg) {
        AppAlertDialog(
            onDismissRequest = { deleteDlg = false },
            title = { Text("删除「${form.name}」？") },
            text = { Text("付款记录会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { form.original?.let(vm::delete); deleteDlg = false; onBack() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDlg = false }) { Text("取消") } },
        )
    }
}

/** 填充式输入框（无描边，浅灰底） */
@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    singleLine: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value, onChange, modifier,
        label = label?.let { { Text(it) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        isError = isError, textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer, errorContainerColor = cs.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(cs.surfaceContainerLowest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.outline)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else cs.onSurface)
    }
}

@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(4.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .then(if (sel) Modifier.shadow(3.dp, RoundedCornerShape(11.dp)).background(cs.surfaceContainerLowest) else Modifier)
                    .clickable { onSelect(o) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (sel) cs.primary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!desc.isNullOrBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, onChange)
    }
}

/** 白色圆角卡片里放一行（「启用中」开关用） */
@Composable
private fun SoftCardRow(content: @Composable () -> Unit) {
    com.subnook.app.ui.components.SoftCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}
