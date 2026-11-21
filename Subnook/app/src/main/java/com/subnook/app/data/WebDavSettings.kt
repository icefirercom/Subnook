package com.subnook.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** WebDAV 账号。密码只在 [WebDavSettingsRepository.password] 里解密取出，不放进这个对象，也不进备份文件 */
data class WebDavConfig(
    val url: String = "",
    val user: String = "",
    val hasPassword: Boolean = false,
    /** 每天自动备份（有变化才上传） */
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0,
    /** 最近一次自动备份失败的原因，成功后清空 */
    val lastError: String = "",
) {
    val configured: Boolean get() = url.isNotBlank() && user.isNotBlank() && hasPassword
}

private val Context.webDavStore by preferencesDataStore("webdav")

/**
 * WebDAV 设置单独存一个 DataStore：不属于 [AppSettings]，导出的备份 JSON 里不会带上账号密码。
 * 密码用 Android Keystore 里的 AES 密钥加密后保存（密钥不能导出，卸载 App 后失效）。
 */
class WebDavSettingsRepository(private val context: Context) {
    private object K {
        val url = stringPreferencesKey("url")
        val user = stringPreferencesKey("user")
        val password = stringPreferencesKey("password_enc")
        val auto = booleanPreferencesKey("auto_backup")
        val lastAt = longPreferencesKey("last_backup_at")
        val lastHash = stringPreferencesKey("last_backup_hash")
        val lastError = stringPreferencesKey("last_error")
    }

    val flow: Flow<WebDavConfig> = context.webDavStore.data.map { p ->
        WebDavConfig(
            url = p[K.url].orEmpty(),
            user = p[K.user].orEmpty(),
            hasPassword = !p[K.password].isNullOrEmpty(),
            autoBackup = p[K.auto] ?: false,
            lastBackupAt = p[K.lastAt] ?: 0,
            lastError = p[K.lastError].orEmpty(),
        )
    }

    suspend fun get(): WebDavConfig = flow.first()

    suspend fun password(): String? =
        context.webDavStore.data.first()[K.password]?.let { runCatching { Crypto.decrypt(it) }.getOrNull() }

    suspend fun lastHash(): String? = context.webDavStore.data.first()[K.lastHash]

    /** [password] 为 null 表示不改密码 */
    suspend fun saveAccount(url: String, user: String, password: String?) = context.webDavStore.edit {
        val changed = it[K.url] != url || it[K.user] != user
        it[K.url] = url
        it[K.user] = user
        if (password != null) it[K.password] = Crypto.encrypt(password)
        // 换了服务器 / 账号：下次自动备份不能因为「内容没变」而跳过
        if (changed || password != null) it.remove(K.lastHash)
        it.remove(K.lastError)
    }

    suspend fun clear() = context.webDavStore.edit { it.clear() }

    suspend fun setAutoBackup(on: Boolean) = context.webDavStore.edit { it[K.auto] = on }

    suspend fun markBackedUp(at: Long, hash: String) = context.webDavStore.edit {
        it[K.lastAt] = at
        it[K.lastHash] = hash
        it.remove(K.lastError)
    }

    suspend fun markError(msg: String) = context.webDavStore.edit { it[K.lastError] = msg }

    private object Crypto {
        private const val ALIAS = "subnook_webdav"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }

        fun encrypt(plain: String): String {
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = c.iv + c.doFinal(plain.toByteArray())
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun decrypt(enc: String): String {
            val raw = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            return c.doFinal(raw, 12, raw.size - 12).decodeToString()
        }
    }
}
