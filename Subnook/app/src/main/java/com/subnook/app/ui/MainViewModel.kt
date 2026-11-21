package com.subnook.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subnook.app.App
import com.subnook.app.data.AppSettings
import com.subnook.app.data.PaymentRecord
import com.subnook.app.data.RateTable
import com.subnook.app.data.SortOrder
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.notify.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val subs: List<Subscription> = emptyList(),
    val assets: List<com.subnook.app.data.Asset> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val rates: RateTable = RateTable(emptyMap(), 0),
    val loaded: Boolean = false,
) {
    val active get() = subs.filter { it.active }
    val inactive get() = subs.filter { !it.active }
    // 首页把订阅和分期分成两个区域；统计、紫色卡片、日历仍然把两者合在一起算
    val activeSubs get() = active.filter { !it.isInstallment }
    val activeInstallments get() = active.filter { it.isInstallment }

    fun total(period: SummaryPeriod = settings.period) = Billing.total(subs, period, rates, settings.displayCurrency)

    /** 首页「订阅中」的顺序（不含分期） */
    fun sortedActive(): List<Subscription> = sorted(activeSubs)
    /** 首页「分期还款」：和订阅用同一个排序设置 */
    fun sortedInstallments(): List<Subscription> = sorted(activeInstallments)
    fun sortedInactive(): List<Subscription> = sorted(inactive)

    private fun sorted(list: List<Subscription>): List<Subscription> {
        fun monthly(s: Subscription) = rates.convert(Billing.periodCost(s, SummaryPeriod.MONTH), s.currency, settings.displayCurrency)
        // 中文名按拼音排，英文不区分大小写
        val byName = compareBy(nameCollator) { s: Subscription -> s.name.trim() }
        val key: Comparator<Subscription> = when (settings.sort) {
            SortOrder.MANUAL -> return list.sortedWith(compareBy<Subscription> { it.sortIndex }.thenBy { it.id })
            SortOrder.NEXT_DATE -> compareBy { if (it.isLifetime) Long.MAX_VALUE else it.nextBillingDay }
            SortOrder.AMOUNT -> compareBy { monthly(it) }
            SortOrder.NAME -> byName
            SortOrder.CATEGORY -> compareBy { it.category.ordinal }
            SortOrder.CREATED -> compareBy<Subscription> { it.createdAt }.thenBy { it.id }
        }
        // 正序 / 倒序只作用于主排序键；值相同时始终按名称正序，顺序稳定
        return list.sortedWith((if (settings.sortAscending) key else key.reversed()).then(byName))
    }

    private companion object {
        val nameCollator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.CHINA).apply {
            strength = java.text.Collator.SECONDARY
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<UiState> = combine(a.dao.observeAll(), a.dao.observeAllAssets(), a.settings.flow, a.rates.table) { subs, assets, s, r ->
        UiState(subs, assets, s, r, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun observeSub(id: Long): Flow<Subscription?> = a.dao.observe(id)
    fun observeAsset(id: Long): Flow<com.subnook.app.data.Asset?> = a.dao.observeAsset(id)
    fun observePayments(id: Long): Flow<List<PaymentRecord>> = a.dao.observePayments(id)
    fun observePaymentsBetween(from: Long, to: Long) = a.dao.observePaymentsBetween(from, to)
    fun observeAllPayments() = a.dao.observeAllPayments()

    fun toast(msg: String) { _messages.tryEmit(msg) }

    /**
     * 保存。[keepDbBalance]：用户没改过余额输入框时为 true——编辑页开着的时候后台可能已经扣过款 / 充过值，
     * 这时用数据库里最新的余额，不用表单打开时的旧值覆盖。
     */
    fun save(sub: Subscription, keepDbBalance: Boolean = false, onDone: () -> Unit = {}) = viewModelScope.launch {
        var s = com.subnook.app.domain.Installments.normalize(sub)
        if (keepDbBalance && s.id != 0L) a.dao.get(s.id)?.let { s = s.copy(balance = it.balance) }
        // 新订阅排在手动顺序的最后
        a.dao.upsert(if (s.id == 0L) s.copy(sortIndex = a.dao.maxSortIndex() + 1) else s)
        Scheduler.runAll(a)
        onDone()
    }

    fun delete(sub: Subscription) = viewModelScope.launch {
        a.dao.delete(sub)
        a.notifier.cancel(sub.id)
        toast("已删除「${sub.name}」")
    }

    fun deletePayment(p: PaymentRecord) = viewModelScope.launch {
        a.processor.deletePayment(p)
        if (p.balanceUsed > 0) toast("已删除记录，${com.subnook.app.data.Currencies.formatList(p.balanceUsed, p.currency)} 已退回余额")
    }

    // ---- 自定义分类 / 付款方式 / 扣款渠道 / 订阅源 ----
    fun addCustomCategory(name: String) = viewModelScope.launch { a.settings.addCustomCategory(name) }
    fun removeCustomCategory(name: String) = viewModelScope.launch { a.settings.removeCustomCategory(name) }
    fun addCustomPayMethod(name: String) = viewModelScope.launch { a.settings.addCustomPayMethod(name) }
    fun removeCustomPayMethod(name: String) = viewModelScope.launch { a.settings.removeCustomPayMethod(name) }
    fun renameCustomPayMethod(oldName: String, newName: String) = viewModelScope.launch { a.settings.renameCustomPayMethod(oldName, newName) }
    fun setPayMethodEnabled(id: String, enabled: Boolean) = viewModelScope.launch { a.settings.setPayMethodEnabled(id, enabled) }
    fun addCustomPayChannel(name: String) = viewModelScope.launch { a.settings.addCustomPayChannel(name) }
    fun removeCustomPayChannel(name: String) = viewModelScope.launch { a.settings.removeCustomPayChannel(name) }
    fun renameCustomPayChannel(oldName: String, newName: String) = viewModelScope.launch { a.settings.renameCustomPayChannel(oldName, newName) }
    fun setPayChannelEnabled(name: String, enabled: Boolean) = viewModelScope.launch { a.settings.setPayChannelEnabled(name, enabled) }
    fun setBuiltInPresetEnabled(key: String, enabled: Boolean) = viewModelScope.launch { a.settings.setBuiltInPresetEnabled(key, enabled) }
    fun setBuiltInPresetsEnabled(keys: Collection<String>, enabled: Boolean) = viewModelScope.launch { a.settings.setBuiltInPresetsEnabled(keys, enabled) }
    fun upsertCustomPreset(preset: com.subnook.app.data.CustomPreset) = viewModelScope.launch { a.settings.upsertCustomPreset(preset) }
    fun setCustomPresetEnabled(key: String, enabled: Boolean) = viewModelScope.launch { a.settings.setCustomPresetEnabled(key, enabled) }
    fun deleteCustomPreset(key: String) = viewModelScope.launch { a.settings.deleteCustomPreset(key) }
    fun addCustomPurchaseChannel(name: String) = viewModelScope.launch { a.settings.addCustomPurchaseChannel(name) }
    fun removeCustomPurchaseChannel(name: String) = viewModelScope.launch { a.settings.removeCustomPurchaseChannel(name) }
    fun setPurchaseChannelEnabled(name: String, enabled: Boolean) = viewModelScope.launch { a.settings.setPurchaseChannelEnabled(name, enabled) }

    // ---- 实物资产管理 ----
    fun saveAsset(asset: com.subnook.app.data.Asset, onDone: () -> Unit = {}) = viewModelScope.launch {
        a.dao.upsertAsset(asset)
        Scheduler.runAll(a)
        onDone()
    }

    fun deleteAsset(asset: com.subnook.app.data.Asset) = viewModelScope.launch {
        asset.photoList.forEach { com.subnook.app.ui.components.AssetPhotos.delete(a, it) }
        a.dao.deleteAsset(asset)
        a.notifier.cancelAsset(asset.id)
        toast("已删除「${asset.name}」")
    }

    // ---- 余额充值 ----
    fun observeTopUps(id: Long) = a.dao.observeTopUps(id)

    fun topUp(sub: Subscription, amount: Double) = viewModelScope.launch {
        a.processor.topUp(sub.id, amount)
        // 卡在余额不足的自动扣款：充值后马上再处理一次，够了就自动补扣
        Scheduler.runAll(a)
        toast("已充值 ${com.subnook.app.data.Currencies.formatList(amount, sub.currency)}")
    }

    fun deleteTopUp(t: com.subnook.app.data.TopUp) = viewModelScope.launch {
        a.processor.deleteTopUp(t)
        toast("已删除充值记录，余额已减回")
    }

    fun confirmPaid(sub: Subscription) = viewModelScope.launch {
        val short = a.processor.confirmPaid(sub.id)
        if (short > 0.005) {
            toast("余额不够，已扣完余额；还差 ${com.subnook.app.data.Currencies.formatList(short, sub.currency)} 按其他方式付款")
        } else if (sub.isOneTime) {
            toast("已确认「${sub.name}」到期并归档")
        } else if (sub.isInstallment) {
            val after = a.dao.get(sub.id)
            toast(
                if (after != null && com.subnook.app.domain.Installments.isFinished(after)) "已还完最后一期，「${sub.name}」已还清"
                else "已记录第 ${com.subnook.app.domain.Installments.summary(sub).current} 期还款",
            )
        } else {
            toast("已记录付款，下次账单日已更新")
        }
    }

    fun prepay(sub: Subscription, amount: Double, mode: com.subnook.app.domain.Installments.PrepayMode, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            runCatching { a.processor.prepay(sub.id, amount, mode) }
                .onSuccess { r ->
                    toast(
                        when {
                            r == null -> "这笔分期已经还清了"
                            r.paidOff -> "已提前还清「${sub.name}」"
                            else -> "已记录提前还款，之后每期 " +
                                com.subnook.app.data.Currencies.formatList(r.newPayment, sub.currency) + "，还剩 ${r.newTerms} 期"
                        },
                    )
                    Scheduler.runAll(a)
                    onDone()
                }
                .onFailure { toast("提前还款失败：${it.message}") }
        }

    /** 删除提前还款记录并恢复分期。[onRestored] 在恢复成功后调用（编辑页据此关闭，避免旧表单覆盖恢复的数据） */
    fun undoPrepay(p: PaymentRecord, onRestored: () -> Unit) = viewModelScope.launch {
        val r = runCatching { a.processor.undoPrepay(p.id) }.getOrElse {
            toast("撤销失败：${it.message}")
            return@launch
        }
        when (r) {
            com.subnook.app.domain.RenewalProcessor.UndoResult.Restored -> {
                toast("已撤销提前还款，分期已恢复")
                Scheduler.runAll(a)
                onRestored()
            }
            is com.subnook.app.domain.RenewalProcessor.UndoResult.LaterPayments ->
                toast("这笔提前还款之后还有 ${r.count} 条付款记录，请先删除它们再撤销")
            com.subnook.app.domain.RenewalProcessor.UndoResult.NoSnapshot -> {
                // 旧调试版本记的提前还款没有快照：只删记录
                a.processor.undoPrepay(p.id, deleteWithoutSnapshot = true)
                toast("这条记录没有还款前的数据，只删除了记录，分期需要手动改回")
            }
            com.subnook.app.domain.RenewalProcessor.UndoResult.NotFound -> toast("记录已不存在")
        }
    }

    fun skip(sub: Subscription) = viewModelScope.launch {
        a.processor.skip(sub.id)
        toast(if (sub.isOneTime) "已停用「${sub.name}」到期提醒" else "已跳过本期")
    }

    fun setPeriod(p: SummaryPeriod) = viewModelScope.launch { a.settings.setPeriod(p) }
    fun setCurrency(c: String) = viewModelScope.launch { a.settings.setCurrency(c) }
    /** 选排序方式和方向；从其他方式切到手动排序时，把当前看到的顺序存为手动顺序的起点 */
    fun setSort(s: SortOrder, asc: Boolean) = viewModelScope.launch {
        val st = state.value
        if (s == SortOrder.MANUAL && st.settings.sort != SortOrder.MANUAL) {
            a.dao.setManualOrder(st.sortedActive().map { it.id } + st.sortedInstallments().map { it.id } + st.sortedInactive().map { it.id })
        }
        a.settings.setSort(s, asc)
    }

    /** 拖动排序结束：[subIds] / [instIds] 是启用中订阅、分期各自的新顺序，并切到手动排序 */
    fun reorder(subIds: List<Long>, instIds: List<Long>) = viewModelScope.launch {
        a.dao.setManualOrder(subIds + instIds + state.value.sortedInactive().map { it.id })
        a.settings.setSort(SortOrder.MANUAL, true)
    }
    fun setTheme(t: com.subnook.app.data.ThemeMode) = viewModelScope.launch { a.settings.setTheme(t) }
    fun setNotifyTime(h: Int, m: Int) = viewModelScope.launch {
        a.settings.setNotifyTime(h, m)
        Scheduler.scheduleDailyAlarm(a)
    }

    fun refreshRates() = viewModelScope.launch {
        a.rates.refresh()
            .onSuccess { toast("汇率已更新") }
            .onFailure { toast("汇率更新失败：${it.message ?: it.javaClass.simpleName}") }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { a.backup.export(uri) }
            .onSuccess { toast("已导出 $it 条记录（含订阅与物品）") }
            .onFailure { toast("导出失败：${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { a.backup.import(uri) }
            .onSuccess {
                Scheduler.runAll(a)
                val extra = if (it.assets > 0) "、${it.assets} 件物品" else ""
                toast("已导入 ${it.subs} 个订阅、${it.payments} 条付款记录$extra（原数据已自动存快照）")
            }
            .onFailure { toast("导入失败：${it.message}") }
    }

    // ---- 本地回退快照 ----
    suspend fun listLocalSnapshots(): List<com.subnook.app.data.LocalSnapshot> = a.backup.listLocalSnapshots()

    fun createLocalSnapshot(onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { a.backup.createLocalSnapshot(reason = "manual", skipIfEmpty = false) }
            .onSuccess { toast("已创建本地回退快照"); onDone() }
            .onFailure { toast("创建快照失败：${it.message}") }
    }

    fun restoreLocalSnapshot(fileName: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { a.backup.restoreLocalSnapshot(fileName) }
            .onSuccess {
                Scheduler.runAll(a)
                val extra = if (it.assets > 0) "、${it.assets} 件物品" else ""
                toast("已回退至快照：${it.subs} 个订阅$extra")
                onDone()
            }
            .onFailure { toast("回退失败：${it.message}") }
    }

    fun deleteLocalSnapshot(fileName: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { a.backup.deleteLocalSnapshot(fileName) }
            .onSuccess { onDone() }
    }

    // ---- WebDAV 云端备份 ----
    val webDav: StateFlow<com.subnook.app.data.WebDavConfig> =
        a.webDav.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.subnook.app.data.WebDavConfig())

    /** 测试连接成功才保存；[password] 为 null 表示不改密码 */
    suspend fun saveWebDav(url: String, user: String, password: String?): Result<Unit> =
        runCatching { a.cloud.saveAccount(url, user, password) }
            .onSuccess { toast("连接成功，已保存") }

    fun clearWebDav() = viewModelScope.launch {
        a.webDav.clear()
        com.subnook.app.data.CloudBackup.schedule(a, false)
        toast("已移除 WebDAV 账号")
    }

    fun setAutoBackup(on: Boolean) = viewModelScope.launch {
        a.webDav.setAutoBackup(on)
        com.subnook.app.data.CloudBackup.schedule(a, on)
    }

    /** 在 ViewModel 里跑，离开设置页也不会中断上传 */
    fun cloudBackup(onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { a.cloud.backup() }
            .onSuccess { toast("已备份到云端") }
            .onFailure { toast("云端备份失败：${it.message}") }
        onDone()
    }

    suspend fun cloudList(): Result<List<com.subnook.app.data.RemoteFile>> = runCatching { a.cloud.list() }

    fun cloudRestore(name: String) = viewModelScope.launch {
        runCatching { a.cloud.restore(name) }
            .onSuccess {
                Scheduler.runAll(a)
                val extra = if (it.assets > 0) "、${it.assets} 件物品" else ""
                toast("已从云端恢复 ${it.subs} 个项目、${it.payments} 条记录$extra（原数据已存本地快照）")
            }
            .onFailure { toast("恢复失败：${it.message}") }
    }

    fun onResume() = viewModelScope.launch {
        Scheduler.runAll(a)
        a.rates.refreshIfStale()
    }
}
