package com.example.invcalc.stock

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 記憶體快取，避免重複下載。 */
object StockCache {
    private val map = HashMap<String, List<Bar>>()
    fun get(symbol: String): List<Bar>? = map[symbol]
    fun put(symbol: String, bars: List<Bar>) { map[symbol] = bars }
}

/** Yahoo Finance 非官方 chart API（免金鑰）。 */
object YahooApi {
    class Parsed(val name: String, val bars: List<Bar>)

    fun parse(json: String): Parsed {
        val chart = JSONObject(json).getJSONObject("chart")
        if (!chart.isNull("error")) {
            val desc = chart.getJSONObject("error").optString("description", "")
            throw IllegalArgumentException(if (desc.isBlank()) "查無此股票" else desc)
        }
        if (chart.isNull("result")) throw IllegalArgumentException("查無此股票")
        val result = chart.getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val name = meta.optString("longName", "").ifBlank {
            meta.optString("shortName", "").ifBlank { meta.optString("symbol", "") }
        }
        val offset = meta.optLong("gmtoffset", 0L)
        val ts = result.optJSONArray("timestamp") ?: throw IllegalArgumentException("查無價格資料")
        val ind = result.getJSONObject("indicators")
        val quote = ind.getJSONArray("quote").getJSONObject(0)
        val close = quote.getJSONArray("close")
        val vol = quote.optJSONArray("volume")
        val adj = ind.optJSONArray("adjclose")?.optJSONObject(0)?.optJSONArray("adjclose")

        val bars = ArrayList<Bar>()
        for (i in 0 until ts.length()) {
            if (close.isNull(i)) continue
            val c = close.getDouble(i)
            val a = if (adj != null && i < adj.length() && !adj.isNull(i)) adj.getDouble(i) else c
            val v = if (vol != null && i < vol.length() && !vol.isNull(i)) vol.getDouble(i) else 0.0
            val day = Math.floorDiv(ts.getLong(i) + offset, 86400L)
            val bar = Bar(day, c, a, v)
            if (bars.isNotEmpty() && bars.last().epochDay == day) bars[bars.size - 1] = bar else bars.add(bar)
        }
        if (bars.size < 2) throw IllegalArgumentException("價格資料不足")
        return Parsed(name, bars)
    }

    /** 需在背景執行緒呼叫。 */
    fun fetchBlocking(symbol: String): Parsed {
        val url = URL(
            "https://query1.finance.yahoo.com/v8/finance/chart/" +
                URLEncoder.encode(symbol, "UTF-8") +
                "?range=10y&interval=1d&includeAdjustedClose=true"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code in 200..299) return parse(body)
            val msg = try {
                parse(body)
                null
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                null
            }
            throw IllegalStateException(msg ?: "連線失敗（HTTP $code）")
        } finally {
            conn.disconnect()
        }
    }
}
