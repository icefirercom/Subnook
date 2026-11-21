package com.subnook.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class RateTable(
    /** 1 USD = rates[code] */
    val rates: Map<String, Double>,
    /** 毫秒；0 表示内置兜底汇率 */
    val updatedAt: Long,
) {
    fun convert(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        val f = rates[from] ?: return amount
        val t = rates[to] ?: return amount
        return amount / f * t
    }
}

class RatesRepository(context: Context) {
    private val file = File(context.filesDir, "rates.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _table = MutableStateFlow(load())
    val table: StateFlow<RateTable> = _table

    private fun load(): RateTable = runCatching {
        json.decodeFromString(RateTable.serializer(), file.readText())
    }.getOrElse { RateTable(Currencies.fallbackRates, 0) }

    val isStale: Boolean get() = System.currentTimeMillis() - _table.value.updatedAt > STALE_MS

    /** 拉取最新汇率（open.er-api.com，免费无需 key，每日更新） */
    suspend fun refresh(): Result<RateTable> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = json.parseToJsonElement(body).jsonObject
            check(root["result"]?.jsonPrimitive?.content == "success") { "汇率接口返回失败" }
            val rates = root["rates"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
            val ts = root["time_last_update_unix"]?.jsonPrimitive?.long?.times(1000) ?: System.currentTimeMillis()
            // 记录本地拉取时间，保证 24h 刷新节奏
            val t = RateTable(rates, maxOf(ts, System.currentTimeMillis()))
            file.writeText(json.encodeToString(RateTable.serializer(), t))
            _table.value = t
            t
        }
    }

    suspend fun refreshIfStale() {
        if (isStale) refresh()
    }

    companion object {
        const val API = "https://open.er-api.com/v6/latest/USD"
        const val STALE_MS = 24 * 3600 * 1000L
    }
}
