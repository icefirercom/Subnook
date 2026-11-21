package com.subnook.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.subnook.app.App
import com.subnook.app.ui.components.IconFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份文件格式。
 * - version 1：1.0 ~ 1.2 导出的备份；
 * - version 2：1.3 起，订阅里多了分期字段（kind / inst*），付款记录多了 term / prepay / undo / balanceUsed，订阅多了 balance，新增 topUps。
 * 新增字段一律带默认值：旧备份缺这些字段时按默认值（普通订阅）导入。
 */
@Serializable
data class BackupFile(
    val app: String = "Subnook",
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val subscriptions: List<Subscription> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    /** 自定义图标：文件名 -> base64(PNG) */
    val icons: Map<String, String> = emptyMap(),
    /** 余额充值记录（1.3 起；旧备份没有这一项） */
    val topUps: List<TopUp> = emptyList(),
)

data class ImportResult(val subs: Int, val payments: Int)

/** 备份 JSON 的编码 / 解码（不依赖 Android，单元测试里直接用） */
object BackupCodec {
    const val VERSION = 2

    /** 能识别的备份来源：当前名称 + 改名前的旧名称 */
    private val APP_IDS = setOf("Subnook", "Sub" + "Keeper")

    /**
     * ignoreUnknownKeys：更新版本导出的备份里可能有本版本不认识的字段；
     * coerceInputValues：不认识的枚举值（例如更新版本新加的分类）退回该字段的默认值，而不是整份导入失败。
     */
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(data: BackupFile): String = json.encodeToString(BackupFile.serializer(), data)

    fun decode(text: String): BackupFile {
        val data = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("文件内容无法识别（${it.message?.take(80)}）", it) }
        require(data.app in APP_IDS) { "不是本应用的备份文件" }
        // 旧版本没有分期：kind 缺省为 SUBSCRIPTION；分期字段不完整的记录当普通订阅处理，避免算出 0 元
        val subs = data.subscriptions.map { s ->
            if (s.isInstallment && (s.instTerms <= 0 || s.instPrincipal <= 0.0)) s.copy(kind = SubKind.SUBSCRIPTION) else s
        }
        return data.copy(subscriptions = subs)
    }

    /** 比较两份备份内容是否相同（忽略导出时间），用于自动备份时跳过没变化的数据 */
    fun fingerprint(data: BackupFile): String {
        val bytes = encode(data.copy(exportedAt = 0)).toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return md.joinToString("") { "%02x".format(it) }
    }
}

class BackupManager(private val context: Context) {
    private val app get() = App.get(context)

    /** 收集当前全部数据（订阅、付款记录、设置、自定义图标） */
    suspend fun snapshot(): BackupFile = withContext(Dispatchers.IO) {
        val subs = app.dao.getAll()
        val icons = subs.filter { it.iconType == IconType.CUSTOM }.mapNotNull { s ->
            val f = IconFiles.file(context, s.iconValue)
            if (f.exists()) s.iconValue to Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) else null
        }.toMap()
        BackupFile(
            settings = app.settings.get(),
            subscriptions = subs.sortedBy { it.id },
            payments = app.dao.getAllPayments().sortedBy { it.id },
            icons = icons.toSortedMap(),
            topUps = app.dao.getAllTopUps().sortedBy { it.id },
        )
    }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(BackupCodec.encode(data).toByteArray())
        }
        data.subscriptions.size
    }

    /** 导入会覆盖当前全部数据 */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        importText(text)
    }

    /** 从 JSON 文本恢复（本地文件和 WebDAV 共用），会覆盖当前全部数据 */
    suspend fun importText(text: String): ImportResult = withContext(Dispatchers.IO) {
        val data = BackupCodec.decode(text)
        data.icons.forEach { (name, b64) ->
            IconFiles.file(context, name).apply { parentFile?.mkdirs() }.writeBytes(Base64.decode(b64, Base64.DEFAULT))
        }
        // 充值记录必须对应存在的订阅（外键），手工改过的备份里可能有孤儿记录
        val ids = data.subscriptions.map { it.id }.toSet()
        app.dao.replaceAll(data.subscriptions, data.payments, data.topUps.filter { it.subscriptionId in ids })
        app.settings.replace(data.settings)
        ImportResult(data.subscriptions.size, data.payments.size)
    }
}
