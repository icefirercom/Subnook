package com.subnook.app

import com.subnook.app.data.RemoteFile
import com.subnook.app.data.WebDavClient
import com.subnook.app.data.WebDavException
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/**
 * 用内存里的假 WebDAV 服务器测试客户端（没有真实账号也能把上传 / 列表 / 下载 / 删除 / 建文件夹走一遍）。
 * [style] 模拟不同服务器的 PROPFIND 响应写法：坚果云式（d: 前缀、相对路径 href）和 Nextcloud 式（D: 前缀、完整 URL href）。
 */
class WebDavClientTest {
    private enum class Style { JIANGUOYUN, NEXTCLOUD }

    private lateinit var server: MockWebServer
    private val files = linkedMapOf<String, ByteArray>()
    private val dirs = mutableSetOf<String>()
    private var style = Style.JIANGUOYUN
    private val user = "me@example.com"
    private val pass = "app-pass 密码"
    private val methods = mutableListOf<String>()

    @Before fun setUp() {
        server = MockWebServer()
        dirs += "/dav/"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/dav/").toString()

    private fun handle(r: RecordedRequest): MockResponse {
        methods += "${r.method} ${r.path}"
        if (r.getHeader("Authorization") != Credentials.basic(user, pass, Charsets.UTF_8)) return MockResponse().setResponseCode(401)
        val path = URLDecoder.decode(r.requestUrl!!.encodedPath, "UTF-8")
        return when (r.method) {
            "PROPFIND" -> {
                if (path !in dirs && path !in files) return MockResponse().setResponseCode(404)
                if (r.getHeader("Depth") == null) return MockResponse().setResponseCode(400)
                MockResponse().setResponseCode(207).setBody(propfind(path, r.getHeader("Depth") == "1"))
            }
            "MKCOL" -> when {
                path in dirs -> MockResponse().setResponseCode(405)
                path.removeSuffix("/").substringBeforeLast('/') + "/" !in dirs -> MockResponse().setResponseCode(409)
                else -> { dirs += path; MockResponse().setResponseCode(201) }
            }
            "PUT" -> {
                val parent = path.substringBeforeLast('/') + "/"
                if (parent !in dirs) MockResponse().setResponseCode(409)
                else { files[path] = r.body.readByteArray(); MockResponse().setResponseCode(201) }
            }
            "GET" -> files[path]?.let { MockResponse().setBody(Buffer().write(it)) } ?: MockResponse().setResponseCode(404)
            "DELETE" -> if (files.remove(path) != null) MockResponse().setResponseCode(204) else MockResponse().setResponseCode(404)
            else -> MockResponse().setResponseCode(405)
        }
    }

    private fun propfind(path: String, depth1: Boolean): String {
        val p = if (style == Style.JIANGUOYUN) "d" else "D"
        fun href(x: String) = if (style == Style.JIANGUOYUN) encode(x) else server.url(encode(x)).toString()
        fun entry(x: String, dir: Boolean, size: Int = 0) = """
            <$p:response><$p:href>${href(x)}</$p:href><$p:propstat><$p:prop>
              ${if (dir) "<$p:resourcetype><$p:collection/></$p:resourcetype>" else "<$p:resourcetype/><$p:getcontentlength>$size</$p:getcontentlength>"}
              <$p:getlastmodified>Thu, 19 Nov 2026 09:50:12 GMT</$p:getlastmodified>
            </$p:prop><$p:status>HTTP/1.1 200 OK</$p:status></$p:propstat></$p:response>"""
        val body = StringBuilder("""<?xml version="1.0" encoding="utf-8"?><$p:multistatus xmlns:$p="DAV:">""")
        body.append(entry(path, path in dirs, files[path]?.size ?: 0))
        if (depth1 && path in dirs) files.filterKeys { it.substringBeforeLast('/') + "/" == path }
            .forEach { (k, v) -> body.append(entry(k, false, v.size)) }
        body.append("</$p:multistatus>")
        return body.toString()
    }

    private fun encode(p: String) = p.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun client(password: String = pass) = WebDavClient(base(), user, password)

    @Test fun connectCreatesFolderOnce() = runBlocking {
        client().connect()
        assertTrue("/dav/Subnook/" in dirs)
        methods.clear()
        client().connect() // 已存在：不再 MKCOL
        assertTrue(methods.none { it.startsWith("MKCOL") })
    }

    @Test fun uploadListDownloadDelete() = runBlocking {
        for (s in Style.entries) {
            style = s
            files.clear()
            val c = client()
            c.upload("subnook-backup-20261118-080000.json", "{\"a\":1}".toByteArray())
            c.upload("subnook-backup-20261119-175012.json", "{\"b\":2}".toByteArray())
            files["/dav/Subnook/notes.txt"] = "x".toByteArray()
            val list = c.list()
            assertEquals("$s", listOf("subnook-backup-20261119-175012.json", "subnook-backup-20261118-080000.json"), list.map { it.name })
            assertEquals(7L, list[0].size)
            assertTrue(list[0].modified > 0)
            assertArrayEquals("{\"b\":2}".toByteArray(), c.download(list[0].name))
            c.delete(list[1].name)
            assertEquals(1, c.list().size)
        }
    }

    @Test fun listWithoutFolderIsEmpty() = runBlocking {
        assertEquals(emptyList<RemoteFile>(), client().list())
    }

    @Test fun wrongPasswordGivesReadableError() = runBlocking {
        try {
            client("wrong").connect()
            fail("应该失败")
        } catch (e: WebDavException) {
            assertTrue(e.message!!, e.message!!.contains("密码"))
        }
    }

    @Test fun unreachableServerGivesReadableError() = runBlocking {
        val url = base()
        server.shutdown()
        try {
            WebDavClient(url, user, pass).connect()
            fail("应该失败")
        } catch (e: WebDavException) {
            assertTrue(e.message!!, e.message!!.isNotBlank())
        }
    }

    @Test fun normalizeUrl() {
        assertEquals("https://dav.jianguoyun.com/dav/", WebDavClient.normalize(" dav.jianguoyun.com/dav ").toString())
        assertEquals("http://192.168.1.2:5005/", WebDavClient.normalize("http://192.168.1.2:5005").toString())
        assertEquals(null, WebDavClient.normalize("  "))
    }

    @Test fun pruneOnlyOwnBackups() {
        val files = (1..12).map { RemoteFile("subnook-backup-202611%02d-120000.json".format(it), 1, 0) } +
            RemoteFile("subnook-backup-2026-11-01.json", 1, 0) // 用户手动放进去的本地导出文件
        val del = WebDavClient.prunable(files, 10).map { it.name }
        assertEquals(listOf("subnook-backup-20261102-120000.json", "subnook-backup-20261101-120000.json"), del)
    }

    @Test fun parseIgnoresDoctype() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><d:multistatus xmlns:d="DAV:"><d:response><d:href>/a/&e;.json</d:href></d:response></d:multistatus>"""
        try {
            val r = WebDavClient.parseMultiStatus(evil)
            assertTrue(r.none { it.name.contains("root") })
        } catch (e: WebDavException) {
            // 直接拒绝含 DOCTYPE 的响应也可以
        }
    }

    @Test fun parseDefaultNamespaceAndEncodedNames() {
        val xml = """<?xml version="1.0"?><multistatus xmlns="DAV:">
            <response><href>/dav/Subnook/</href><propstat><prop><resourcetype><collection/></resourcetype></prop></propstat></response>
            <response><href>/dav/Subnook/subnook-backup-20261119-175012.json</href><propstat><prop><getcontentlength>2048</getcontentlength></prop></propstat></response>
            <response><href>/dav/Subnook/%E5%A4%87%E4%BB%BD%20a+b.json</href></response>
            </multistatus>"""
        val r = WebDavClient.parseMultiStatus(xml)
        assertEquals(listOf("subnook-backup-20261119-175012.json", "备份 a+b.json"), r.map { it.name })
        assertEquals(2048L, r[0].size)
    }
}
