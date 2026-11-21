package com.subnook.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.subnook.app.App
import com.subnook.app.BuildConfig
import com.subnook.app.ui.components.AssetPhotos
import com.subnook.app.ui.components.IconFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 备份文件格式。
 * - version 1：1.0 ~ 1.2 导出的备份；
 * - version 2：1.3 起，新增分期字段、提前还款快照、余额与充值记录；
 * - version 3：1.4 起，新增实物资产（assets）与压缩凭证图（assetPhotos）。
 * 新增字段一律带默认值：旧备份缺这些字段时安全导入。
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
    /** 实物资产档案（1.4 起；旧备份没有这一项） */
    val assets: List<Asset> = emptyList(),
    /** 实物凭证照片：文件名 -> base64(JPEG) */
    val assetPhotos: Map<String, String> = emptyMap(),
)

data class ImportResult(val subs: Int, val payments: Int, val assets: Int = 0)

/** 本地回退快照元信息（保存在 filesDir/snapshots/，支持版本升级/误导入后秒级回退） */
data class LocalSnapshot(
    val fileName: String,
    val createdAt: Long,
    val versionName: String,
    val reason: String,
    val sizeBytes: Long,
) {
    val reasonLabel: String
        get() = when (reason) {
            "pre_import" -> "导入备份前自动保存"
            "pre_cloud" -> "云端恢复前自动保存"
            "pre_upgrade" -> "版本升级前自动保存"
            "pre_rollback" -> "回退前自动保存"
            "manual" -> "手动创建快照"
            else -> reason
        }
}

/** 备份 JSON 的编码 / 解码（不依赖 Android，单元测试里直接用） */
object BackupCodec {
    const val VERSION = 3

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
    private val snapshotDir: File get() = File(context.filesDir, "snapshots").apply { mkdirs() }

    /** 收集当前全部数据（订阅、付款记录、充值记录、实物资产、设置、自定义图标与凭证图） */
    suspend fun snapshot(): BackupFile = withContext(Dispatchers.IO) {
        val subs = app.dao.getAll()
        val assets = app.dao.getAllAssets()
        val icons = buildMap {
            subs.filter { it.iconType == IconType.CUSTOM }.forEach { s ->
                val f = IconFiles.file(context, s.iconValue)
                if (f.exists()) put(s.iconValue, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
            assets.filter { it.iconType == IconType.CUSTOM }.forEach { a ->
                val f = IconFiles.file(context, a.iconValue)
                if (f.exists()) put(a.iconValue, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
        }
        val photos = buildMap {
            assets.flatMap { it.photoList }.distinct().forEach { name ->
                val f = AssetPhotos.file(context, name)
                if (f.exists()) put(name, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
        }
        BackupFile(
            settings = app.settings.get(),
            subscriptions = subs.sortedBy { it.id },
            payments = app.dao.getAllPayments().sortedBy { it.id },
            icons = icons.toSortedMap(),
            topUps = app.dao.getAllTopUps().sortedBy { it.id },
            assets = assets.sortedBy { it.id },
            assetPhotos = photos.toSortedMap(),
        )
    }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(BackupCodec.encode(data).toByteArray())
        }
        data.subscriptions.size + data.assets.size
    }

    /** 导入会覆盖当前全部数据（覆盖前自动保存一份本地回退快照） */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        importText(text, autoSnapshotReason = "pre_import")
    }

    /** 从 JSON 文本恢复（本地文件和 WebDAV 共用），会覆盖当前全部数据 */
    suspend fun importText(text: String, autoSnapshotReason: String? = null): ImportResult = withContext(Dispatchers.IO) {
        // 先校验导入文件合法性，避免坏文件白白生成快照
        val data = BackupCodec.decode(text)
        if (autoSnapshotReason != null) {
            runCatching { createLocalSnapshot(autoSnapshotReason) }
        }
        data.icons.forEach { (name, b64) ->
            IconFiles.file(context, name).apply { parentFile?.mkdirs() }.writeBytes(Base64.decode(b64, Base64.DEFAULT))
        }
        data.assetPhotos.forEach { (name, b64) ->
            AssetPhotos.file(context, name).apply { parentFile?.mkdirs() }.writeBytes(Base64.decode(b64, Base64.DEFAULT))
        }
        // 充值记录必须对应存在的订阅（外键），手工改过的备份里可能有孤儿记录
        val ids = data.subscriptions.map { it.id }.toSet()
        app.dao.replaceAll(
            subs = data.subscriptions,
            payments = data.payments,
            topUps = data.topUps.filter { it.subscriptionId in ids },
            assets = data.assets,
        )
        app.settings.replace(data.settings)
        ImportResult(data.subscriptions.size, data.payments.size, data.assets.size)
    }

    // ---- 本地回退快照（数据时光机）----

    /** 创建一份本地回退快照；[skipIfEmpty] 为 true 时库里没有任何订阅和物品则跳过 */
    suspend fun createLocalSnapshot(
        reason: String,
        versionName: String = BuildConfig.VERSION_NAME,
        skipIfEmpty: Boolean = true,
    ): LocalSnapshot? = withContext(Dispatchers.IO) {
        val snap = snapshot()
        if (skipIfEmpty && snap.subscriptions.isEmpty() && snap.assets.isEmpty()) return@withContext null
        val ts = System.currentTimeMillis()
        val safeVer = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeReason = reason.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val name = "snap-$ts-$safeVer-$safeReason.json"
        val file = File(snapshotDir, name)
        val bytes = BackupCodec.encode(snap).toByteArray()
        file.writeBytes(bytes)
        pruneSnapshots()
        LocalSnapshot(name, ts, safeVer, safeReason, bytes.size.toLong())
    }

    suspend fun listLocalSnapshots(): List<LocalSnapshot> = withContext(Dispatchers.IO) {
        snapshotDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { parseSnapshotFile(it) }
            .sortedByDescending { it.createdAt }
    }

    /** 回退到指定本地快照（若当前内容与目标快照不同，回退前先自动留一份 pre_rollback 快照，确保回退本身也可撤销） */
    suspend fun restoreLocalSnapshot(fileName: String): ImportResult = withContext(Dispatchers.IO) {
        val target = File(snapshotDir, fileName)
        require(target.exists() && target.parentFile?.canonicalPath == snapshotDir.canonicalPath) { "快照文件不存在" }
        val text = target.readText()
        val targetData = BackupCodec.decode(text)
        val cur = snapshot()
        val needGuard = (cur.subscriptions.isNotEmpty() || cur.assets.isNotEmpty()) &&
            BackupCodec.fingerprint(cur) != BackupCodec.fingerprint(targetData)
        importText(text, autoSnapshotReason = if (needGuard) "pre_rollback" else null)
    }

    suspend fun deleteLocalSnapshot(fileName: String) = withContext(Dispatchers.IO) {
        val f = File(snapshotDir, fileName)
        if (f.parentFile?.canonicalPath == snapshotDir.canonicalPath) f.delete()
    }

    private fun pruneSnapshots() {
        val all = snapshotDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .sortedByDescending { parseSnapshotFile(it)?.createdAt ?: it.lastModified() }
        all.drop(MAX_SNAPSHOTS).forEach { runCatching { it.delete() } }
    }

    private fun parseSnapshotFile(f: File): LocalSnapshot? {
        val m = SNAP_RE.matchEntire(f.name) ?: return null
        val ts = m.groupValues[1].toLongOrNull() ?: f.lastModified()
        return LocalSnapshot(
            fileName = f.name,
            createdAt = ts,
            versionName = m.groupValues[2],
            reason = m.groupValues[3],
            sizeBytes = f.length(),
        )
    }

    companion object {
        const val MAX_SNAPSHOTS = 8
        private val SNAP_RE = Regex("""snap-(\d+)-([^-]+)-(.+)\.json""")
    }
}
