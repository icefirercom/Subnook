package com.subnook.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.subnook.app.App
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * WebDAV 云端备份：手动备份 / 恢复，以及可选的每天自动备份。
 * 只在用户配置了 WebDAV 后才会联网；文件名 subnook-backup-yyyyMMdd-HHmmss.json，云端只保留最近 [KEEP] 份。
 */
class CloudBackup(private val context: Context) {
    private val app get() = App.get(context)
    private val repo get() = app.webDav

    private suspend fun client(): WebDavClient {
        val c = repo.get()
        if (c.url.isBlank() || c.user.isBlank()) throw WebDavException("还没有设置 WebDAV 账号")
        val pw = repo.password() ?: throw WebDavException("密码已失效，请重新填写 WebDAV 密码")
        return WebDavClient(c.url, c.user, pw)
    }

    /** 测试并保存账号：能登录才保存。[password] 为 null 表示沿用已保存的密码 */
    suspend fun saveAccount(url: String, user: String, password: String?) {
        val pw = password ?: repo.password() ?: throw WebDavException("请填写密码")
        val normalized = WebDavClient.normalize(url)?.toString() ?: throw WebDavException("服务器地址不正确")
        WebDavClient(normalized, user.trim(), pw).connect()
        repo.saveAccount(normalized, user.trim(), password)
    }

    /**
     * 上传一份备份。[onlyIfChanged] 为 true（自动备份）时，内容和上次备份一样就跳过。
     * @return 上传的文件名；跳过时返回 null
     */
    suspend fun backup(onlyIfChanged: Boolean = false): String? {
        val data = app.backup.snapshot()
        val hash = BackupCodec.fingerprint(data)
        if (onlyIfChanged && hash == repo.lastHash()) return null
        val client = client()
        val name = WebDavClient.PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json"
        client.upload(name, BackupCodec.encode(data).toByteArray())
        repo.markBackedUp(System.currentTimeMillis(), hash)
        // 清理旧备份（只删本 App 生成的文件）：失败不影响本次备份结果
        runCatching { WebDavClient.prunable(client.list(), KEEP).forEach { client.delete(it.name) } }
        return name
    }

    suspend fun list(): List<RemoteFile> = client().list()

    suspend fun restore(name: String): ImportResult {
        val text = client().download(name).decodeToString()
        val r = app.backup.importText(text)
        // 刚从云端恢复的数据和云端一致，下次自动备份不用重复上传
        runCatching { repo.markBackedUp(repo.get().lastBackupAt, BackupCodec.fingerprint(app.backup.snapshot())) }
        return r
    }

    companion object {
        const val KEEP = 10
        private const val WORK = "webdav_backup"

        /** 按设置开启 / 关闭每天自动备份（需要联网时才执行） */
        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!on) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = App.get(applicationContext)
        val cfg = app.webDav.get()
        if (!cfg.autoBackup || !cfg.configured) return Result.success()
        return runCatching { app.cloud.backup(onlyIfChanged = true) }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                app.webDav.markError(it.message ?: it.javaClass.simpleName)
                if (runAttemptCount < 3) Result.retry() else Result.success()
            },
        )
    }
}
