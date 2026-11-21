package com.subnook.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

/** 手动检查更新与版本历史：读取 GitHub Releases。只在用户主动点击时调用，不在启动时自动检查。 */
object UpdateChecker {
    const val OWNER = "icefirercom"
    const val REPO_URL = "https://github.com/icefirercom/Subnook"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val UPSTREAM_URL = "https://github.com/capllen/Subnook"
    private const val API = "https://api.github.com/repos/icefirercom/Subnook/releases/latest"
    private const val API_LIST = "https://api.github.com/repos/icefirercom/Subnook/releases?per_page=10"

    data class Release(
        val version: String,
        val name: String,
        val notes: String,
        val url: String,
        val publishedAt: String = "",
        val apkUrl: String? = null,
    )

    sealed interface Result {
        data class NewVersion(val release: Release) : Result
        data class UpToDate(val latest: String) : Result
        /** 仓库还没有发布任何 Release */
        data object NoRelease : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Subnook-Android")
            val code = conn.responseCode
            if (code == 404) return@withContext Result.NoRelease
            if (code == 403 || code == 429) error("请求太频繁，请稍后再试")
            if (code !in 200..299) error("服务器返回 $code")
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: FileNotFoundException) {
                return@withContext Result.NoRelease
            }
            val root = Json.parseToJsonElement(body).jsonObject
            val release = parseRelease(root)
            if (compare(release.version, normalize(currentVersion)) > 0) Result.NewVersion(release)
            else Result.UpToDate(release.version)
        } finally {
            conn.disconnect()
        }
    }

    /** 获取历史发布版本列表（方便用户查看更新记录或在需要时下载历史 APK 回退） */
    suspend fun listReleases(): List<Release> = withContext(Dispatchers.IO) {
        val conn = URL(API_LIST).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Subnook-Android")
            val code = conn.responseCode
            if (code == 404) return@withContext emptyList()
            if (code == 403 || code == 429) error("请求太频繁，请稍后再试")
            if (code !in 200..299) error("服务器返回 $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body).jsonArray.map { parseRelease(it.jsonObject) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(root: kotlinx.serialization.json.JsonObject): Release {
        val tag = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
        val apk = root["assets"]?.jsonArray?.firstOrNull {
            it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
        }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
        return Release(
            version = normalize(tag),
            name = root["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tag,
            notes = root["body"]?.jsonPrimitive?.content.orEmpty().trim(),
            url = root["html_url"]?.jsonPrimitive?.content ?: RELEASES_URL,
            publishedAt = root["published_at"]?.jsonPrimitive?.content.orEmpty().take(10),
            apkUrl = apk,
        )
    }

    /** "v1.2.0" / "1.2.0-debug" -> "1.2.0" */
    fun normalize(v: String): String = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')

    /** 按「主.功能.修复」逐段比较数字，缺的段当 0 */
    fun compare(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
