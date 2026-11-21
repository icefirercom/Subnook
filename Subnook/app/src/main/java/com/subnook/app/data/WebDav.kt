package com.subnook.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.IOException
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** WebDAV 上的一个备份文件 */
data class RemoteFile(val name: String, val size: Long, val modified: Long)

class WebDavException(msg: String, cause: Throwable? = null) : IOException(msg, cause)

/**
 * 极简 WebDAV 客户端：只用到 PROPFIND / MKCOL / PUT / GET / DELETE。
 * 备份放在 [baseUrl] 下的 `Subnook/` 文件夹，例如坚果云 https://dav.jianguoyun.com/dav/Subnook/。
 * 不依赖 Android API（XML 用 javax 解析），单元测试里可以直接配合 MockWebServer 跑。
 */
class WebDavClient(baseUrl: String, user: String, password: String, private val http: OkHttpClient = sharedHttp) {
    private val base: HttpUrl = normalize(baseUrl) ?: throw WebDavException("服务器地址不正确")
    private val folder: HttpUrl = base.newBuilder().addPathSegment(FOLDER).addPathSegment("").build()
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", auth)
        .header("User-Agent", "Subnook-Android")

    private fun <T> call(req: Request, block: (Response) -> T): T = try {
        http.newCall(req).execute().use(block)
    } catch (e: WebDavException) {
        throw e
    } catch (e: IOException) {
        throw WebDavException(networkMessage(e), e)
    }

    private fun networkMessage(e: IOException): String = when (e) {
        is java.net.UnknownHostException -> "找不到服务器，请检查地址和网络"
        is java.net.SocketTimeoutException -> "连接超时，请稍后再试"
        is javax.net.ssl.SSLException -> "安全连接失败（证书或 https 设置有问题）"
        is java.net.ConnectException -> "无法连接到服务器"
        else -> "连接失败：${e.message ?: e.javaClass.simpleName}"
    }

    private fun fail(r: Response, action: String): Nothing = throw WebDavException(
        when (r.code) {
            401 -> "账号或密码错误（坚果云等需要填写「应用密码」，不是登录密码）"
            403 -> "没有权限（$action）"
            404, 409 -> "路径不存在，请检查服务器地址"
            405 -> "服务器不支持此操作（$action），请确认填的是 WebDAV 地址"
            423 -> "文件被锁定，请稍后再试"
            429, 503 -> "请求太频繁或服务器繁忙，请稍后再试"
            507 -> "云端空间不足"
            else -> "$action 失败：HTTP ${r.code}"
        },
    )

    /** 检查账号能否登录，并确保 Subnook 文件夹存在 */
    suspend fun connect() = withContext(Dispatchers.IO) {
        call(propfind(base, 0)) { r -> if (r.code != 207 && !r.isSuccessful) fail(r, "登录") }
        ensureFolder()
    }

    private fun ensureFolder() {
        // 先看文件夹在不在：有的服务器对已存在的文件夹 MKCOL 会返回奇怪的状态码
        val exists = call(propfind(folder, 0)) { r -> r.code == 207 || r.isSuccessful }
        if (exists) return
        call(request(folder).method("MKCOL", null).build()) { r ->
            // 201 新建成功；405 已存在（并发创建 / 服务器差异）
            if (!r.isSuccessful && r.code != 405) fail(r, "创建文件夹")
        }
    }

    suspend fun upload(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        ensureFolder()
        val body = bytes.toRequestBody("application/json; charset=utf-8".toMediaType())
        call(request(fileUrl(name)).put(body).build()) { r -> if (!r.isSuccessful) fail(r, "上传") }
    }

    suspend fun download(name: String): ByteArray = withContext(Dispatchers.IO) {
        call(request(fileUrl(name)).get().build()) { r ->
            if (!r.isSuccessful) fail(r, "下载")
            r.body?.bytes()?.takeIf { it.isNotEmpty() } ?: throw WebDavException("下载的文件是空的")
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        call(request(fileUrl(name)).delete().build()) { r -> if (!r.isSuccessful && r.code != 404) fail(r, "删除") }
    }

    /** 列出 Subnook 文件夹里的备份文件，新的在前 */
    suspend fun list(): List<RemoteFile> = withContext(Dispatchers.IO) {
        val xml = call(propfind(folder, 1)) { r ->
            if (r.code == 404) return@call null
            if (r.code != 207 && !r.isSuccessful) fail(r, "读取文件列表")
            r.body?.string().orEmpty()
        } ?: return@withContext emptyList()
        parseMultiStatus(xml)
            .filter { it.name.startsWith(PREFIX) && it.name.endsWith(".json") }
            .sortedWith(compareByDescending<RemoteFile> { it.name }.thenByDescending { it.modified })
    }

    private fun propfind(url: HttpUrl, depth: Int): Request {
        val xml = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""
        return request(url).header("Depth", depth.toString())
            .method("PROPFIND", xml.toRequestBody("application/xml; charset=utf-8".toMediaType())).build()
    }

    private fun fileUrl(name: String) = folder.newBuilder().addPathSegment(name).build()

    companion object {
        const val FOLDER = "Subnook"
        const val PREFIX = "subnook-backup-"

        /** 本 App 上传的备份文件名：subnook-backup-yyyyMMdd-HHmmss.json，只清理这种文件 */
        private val OWN_FILE = Regex("""subnook-backup-\d{8}-\d{6}\.json""")

        fun isOwnBackup(name: String) = OWN_FILE.matches(name)

        /** 自动清理：只删本 App 生成的备份，保留最新 [keep] 份；用户自己放进去的文件不动 */
        fun prunable(files: List<RemoteFile>, keep: Int): List<RemoteFile> =
            files.filter { isOwnBackup(it.name) }.sortedByDescending { it.name }.drop(keep)

        private val sharedHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        /** 补上 https://、去掉空格，保证以 / 结尾 */
        fun normalize(input: String): HttpUrl? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
            if (!s.endsWith("/")) s += "/"
            return s.toHttpUrlOrNull()
        }

        /**
         * 解析 PROPFIND 的 207 multistatus，只取文件（跳过文件夹）。
         * 按元素本地名匹配、不管命名空间前缀（d: / D: / 默认命名空间都行）；不解析 DTD，防 XXE。
         */
        fun parseMultiStatus(xml: String): List<RemoteFile> {
            if (xml.isBlank()) return emptyList()
            val f = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val doc = try {
                f.newDocumentBuilder().parse(xml.byteInputStream())
            } catch (e: Exception) {
                throw WebDavException("服务器返回的文件列表无法解析", e)
            }
            return elements(doc.documentElement, "response").mapNotNull { resp ->
                val href = elements(resp, "href").firstOrNull()?.textContent?.trim().orEmpty()
                if (href.isEmpty() || href.endsWith("/")) return@mapNotNull null
                if (elements(resp, "collection").isNotEmpty()) return@mapNotNull null
                val name = runCatching { URLDecoder.decode(href.substringAfterLast('/').replace("+", "%2B"), "UTF-8") }
                    .getOrDefault(href.substringAfterLast('/'))
                val size = elements(resp, "getcontentlength").firstOrNull()?.textContent?.trim()?.toLongOrNull() ?: 0L
                val modified = elements(resp, "getlastmodified").firstOrNull()?.textContent?.trim()?.let { t ->
                    runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                } ?: 0L
                RemoteFile(name, size, modified)
            }
        }

        private fun localName(n: Node): String = (n.localName ?: n.nodeName).substringAfter(':')

        /** [root] 下所有本地名为 [name] 的元素（不含 root 自己） */
        private fun elements(root: Element, name: String): List<Element> {
            val out = mutableListOf<Element>()
            fun walk(n: Node) {
                val children = n.childNodes
                for (i in 0 until children.length) {
                    val c = children.item(i)
                    if (c.nodeType == Node.ELEMENT_NODE) {
                        if (localName(c).equals(name, ignoreCase = true)) out += c as Element
                        walk(c)
                    }
                }
            }
            walk(root)
            return out
        }
    }
}
