package com.subnook.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class SummaryPeriod(val label: String, val unitLabel: String) {
    WEEK("周", "每周"), MONTH("月", "每月"), YEAR("年", "每年")
}

/**
 * 首页订阅列表排序。AMOUNT / NAME / NEXT_DATE 是旧版本就有的名字，保存在设置和备份里，不能改名。
 * [defaultAsc]：刚选中这个排序时默认的方向；[ascHint] / [descHint]：正序 / 倒序的含义说明。
 */
enum class SortOrder(val label: String, val short: String, val defaultAsc: Boolean, val ascHint: String, val descHint: String) {
    NEXT_DATE("下次付款日期", "日期", true, "近 → 远", "远 → 近"),
    AMOUNT("月均金额", "金额", false, "低 → 高", "高 → 低"),
    NAME("名称", "名称", true, "A → Z", "Z → A"),
    CATEGORY("分类", "分类", true, "影视 → 其他", "其他 → 影视"),
    CREATED("添加时间", "添加", false, "旧 → 新", "新 → 旧"),
    /** 长按拖动排出来的顺序，没有正序 / 倒序 */
    MANUAL("手动排序", "手动", true, "", ""),
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

@Serializable
data class AppSettings(
    val displayCurrency: String = "CNY",
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val period: SummaryPeriod = SummaryPeriod.MONTH,
    val sort: SortOrder = SortOrder.NEXT_DATE,
    /** null = 用该排序的默认方向（旧版本没有这个设置） */
    val sortAsc: Boolean? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 用户自己加的分类（选「其他」时填的名称，确认后加入），订阅里存成 OTHER + categoryNote */
    val customCategories: List<String> = emptyList(),
    /** 用户自己加的付款方式（选「其他」时填的名称，确认后加入），订阅里存成 OTHER + payMethodNote */
    val customPayMethods: List<String> = emptyList(),
    /** 全局复用的实际扣款渠道池（如 信用卡7727、借记卡2990、微信零钱等） */
    val customPayChannels: List<String> = emptyList(),
    /** 停用的支付平台（内置存 PayMethod.name，自定义存名称），停用后编辑页隐藏（已有订阅仍保留可见） */
    val disabledPayMethods: List<String> = emptyList(),
    /** 停用的扣款渠道名称（内置推荐或自定义渠道），停用后编辑页隐藏 */
    val disabledPayChannels: List<String> = emptyList(),
    /** 停用的内置订阅源 key（PresetCatalog.all 中的 key） */
    val disabledPresets: List<String> = emptyList(),
    /** 用户自定义的订阅源列表（支持新增、编辑、启停、删除） */
    val customPresets: List<CustomPreset> = emptyList(),
) {
    val sortAscending: Boolean get() = sortAsc ?: sort.defaultAsc
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val currency = stringPreferencesKey("display_currency")
        val hour = intPreferencesKey("notify_hour")
        val minute = intPreferencesKey("notify_minute")
        val period = stringPreferencesKey("period")
        val sort = stringPreferencesKey("sort")
        val sortAsc = booleanPreferencesKey("sort_asc")
        val theme = stringPreferencesKey("theme")
        /** 用户自己加的分类，一行一个 */
        val customCategories = stringPreferencesKey("custom_categories")
        val customPayMethods = stringPreferencesKey("custom_pay_methods")
        val customPayChannels = stringPreferencesKey("custom_pay_channels")
        val disabledPayMethods = stringPreferencesKey("disabled_pay_methods")
        val disabledPayChannels = stringPreferencesKey("disabled_pay_channels")
        val disabledPresets = stringPreferencesKey("disabled_presets")
        val customPresets = stringPreferencesKey("custom_presets")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            displayCurrency = p[K.currency] ?: "CNY",
            notifyHour = p[K.hour] ?: 9,
            notifyMinute = p[K.minute] ?: 0,
            period = p[K.period]?.let { runCatching { SummaryPeriod.valueOf(it) }.getOrNull() } ?: SummaryPeriod.MONTH,
            sort = p[K.sort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.NEXT_DATE,
            sortAsc = p[K.sortAsc],
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            customCategories = parseCategories(p[K.customCategories]),
            customPayMethods = parseCategories(p[K.customPayMethods]),
            customPayChannels = parseCategories(p[K.customPayChannels]),
            disabledPayMethods = parseCategories(p[K.disabledPayMethods]),
            disabledPayChannels = parseCategories(p[K.disabledPayChannels]),
            disabledPresets = parseCategories(p[K.disabledPresets]),
            customPresets = parseCustomPresets(p[K.customPresets]),
        )
    }

    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[K.theme] = t.name }

    /** 自定义分类：加在最后，重名不重复加 */
    suspend fun addCustomCategory(name: String) = context.dataStore.edit {
        val list = parseCategories(it[K.customCategories])
        val n = cleanCategory(name)
        if (n.isNotEmpty() && n !in list) it[K.customCategories] = (list + n).joinToString(SEP)
    }

    suspend fun removeCustomCategory(name: String) = context.dataStore.edit {
        it[K.customCategories] = (parseCategories(it[K.customCategories]) - name).joinToString(SEP)
    }

    suspend fun addCustomPayMethod(name: String) = context.dataStore.edit {
        val list = parseCategories(it[K.customPayMethods])
        val n = cleanCategory(name)
        if (n.isNotEmpty() && n !in list) it[K.customPayMethods] = (list + n).joinToString(SEP)
    }

    suspend fun removeCustomPayMethod(name: String) = context.dataStore.edit {
        it[K.customPayMethods] = (parseCategories(it[K.customPayMethods]) - name).joinToString(SEP)
        it[K.disabledPayMethods] = (parseCategories(it[K.disabledPayMethods]) - name).joinToString(SEP)
    }

    suspend fun renameCustomPayMethod(oldName: String, newName: String) = context.dataStore.edit {
        val n = cleanCategory(newName)
        if (n.isEmpty() || n == oldName) return@edit
        val list = parseCategories(it[K.customPayMethods])
        if (n in list) return@edit
        it[K.customPayMethods] = list.map { item -> if (item == oldName) n else item }.joinToString(SEP)
        val dis = parseCategories(it[K.disabledPayMethods])
        if (oldName in dis) it[K.disabledPayMethods] = dis.map { item -> if (item == oldName) n else item }.joinToString(SEP)
    }

    suspend fun setPayMethodEnabled(id: String, enabled: Boolean) = context.dataStore.edit {
        val cur = parseCategories(it[K.disabledPayMethods])
        val next = if (enabled) cur - id else (cur + id).distinct()
        it[K.disabledPayMethods] = next.joinToString(SEP)
    }

    suspend fun addCustomPayChannel(name: String) = context.dataStore.edit {
        val list = parseCategories(it[K.customPayChannels])
        val n = cleanCategory(name)
        if (n.isNotEmpty() && n !in list) it[K.customPayChannels] = (list + n).joinToString(SEP)
    }

    suspend fun removeCustomPayChannel(name: String) = context.dataStore.edit {
        it[K.customPayChannels] = (parseCategories(it[K.customPayChannels]) - name).joinToString(SEP)
        it[K.disabledPayChannels] = (parseCategories(it[K.disabledPayChannels]) - name).joinToString(SEP)
    }

    suspend fun renameCustomPayChannel(oldName: String, newName: String) = context.dataStore.edit {
        val n = cleanCategory(newName)
        if (n.isEmpty() || n == oldName) return@edit
        val list = parseCategories(it[K.customPayChannels])
        if (n in list) return@edit
        it[K.customPayChannels] = list.map { item -> if (item == oldName) n else item }.joinToString(SEP)
        val dis = parseCategories(it[K.disabledPayChannels])
        if (oldName in dis) it[K.disabledPayChannels] = dis.map { item -> if (item == oldName) n else item }.joinToString(SEP)
    }

    suspend fun setPayChannelEnabled(name: String, enabled: Boolean) = context.dataStore.edit {
        val cur = parseCategories(it[K.disabledPayChannels])
        val next = if (enabled) cur - name else (cur + name).distinct()
        it[K.disabledPayChannels] = next.joinToString(SEP)
    }

    suspend fun setBuiltInPresetEnabled(key: String, enabled: Boolean) = context.dataStore.edit {
        val cur = parseCategories(it[K.disabledPresets])
        val next = if (enabled) cur - key else (cur + key).distinct()
        it[K.disabledPresets] = next.joinToString(SEP)
    }

    suspend fun setBuiltInPresetsEnabled(keys: Collection<String>, enabled: Boolean) = context.dataStore.edit {
        val cur = parseCategories(it[K.disabledPresets]).toMutableSet()
        if (enabled) cur.removeAll(keys.toSet()) else cur.addAll(keys)
        it[K.disabledPresets] = cur.joinToString(SEP)
    }

    suspend fun upsertCustomPreset(preset: CustomPreset) = context.dataStore.edit {
        val list = parseCustomPresets(it[K.customPresets]).toMutableList()
        val idx = list.indexOfFirst { p -> p.key == preset.key }
        if (idx >= 0) list[idx] = preset else list.add(0, preset)
        it[K.customPresets] = encodeCustomPresets(list)
    }

    suspend fun setCustomPresetEnabled(key: String, enabled: Boolean) = context.dataStore.edit {
        val list = parseCustomPresets(it[K.customPresets]).map { p -> if (p.key == key) p.copy(enabled = enabled) else p }
        it[K.customPresets] = encodeCustomPresets(list)
    }

    suspend fun deleteCustomPreset(key: String) = context.dataStore.edit {
        val list = parseCustomPresets(it[K.customPresets]).filterNot { p -> p.key == key }
        it[K.customPresets] = encodeCustomPresets(list)
    }

    suspend fun get(): AppSettings = flow.first()

    suspend fun setCurrency(c: String) = context.dataStore.edit { it[K.currency] = c }
    suspend fun setNotifyTime(h: Int, m: Int) = context.dataStore.edit { it[K.hour] = h; it[K.minute] = m }
    suspend fun setPeriod(p: SummaryPeriod) = context.dataStore.edit { it[K.period] = p.name }
    suspend fun setSort(s: SortOrder, asc: Boolean) = context.dataStore.edit { it[K.sort] = s.name; it[K.sortAsc] = asc }

    suspend fun replace(s: AppSettings) = context.dataStore.edit {
        it[K.currency] = s.displayCurrency
        it[K.hour] = s.notifyHour
        it[K.minute] = s.notifyMinute
        it[K.period] = s.period.name
        it[K.sort] = s.sort.name
        s.sortAsc?.let { v -> it[K.sortAsc] = v } ?: it.remove(K.sortAsc)
        it[K.theme] = s.theme.name
        it[K.customCategories] = s.customCategories.joinToString(SEP)
        it[K.customPayMethods] = s.customPayMethods.joinToString(SEP)
        it[K.customPayChannels] = s.customPayChannels.joinToString(SEP)
        it[K.disabledPayMethods] = s.disabledPayMethods.joinToString(SEP)
        it[K.disabledPayChannels] = s.disabledPayChannels.joinToString(SEP)
        it[K.disabledPresets] = s.disabledPresets.joinToString(SEP)
        it[K.customPresets] = encodeCustomPresets(s.customPresets)
    }
}

private val presetJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
private val presetListSer = ListSerializer(CustomPreset.serializer())

private fun parseCustomPresets(raw: String?): List<CustomPreset> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { presetJson.decodeFromString(presetListSer, raw) }.getOrDefault(emptyList())

private fun encodeCustomPresets(list: List<CustomPreset>): String =
    presetJson.encodeToString(presetListSer, list)

private fun parseCategories(raw: String?): List<String> =
    raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** 分类名：去掉换行和首尾空格，最多 20 个字 */
fun cleanCategory(name: String): String = name.replace('\n', ' ').trim().take(20)

private const val SEP = "\n"
