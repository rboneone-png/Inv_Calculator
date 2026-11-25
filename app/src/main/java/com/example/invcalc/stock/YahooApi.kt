package com.example.invcalc.stock

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 記憶體快取，避免重複下載。 */
object StockCache {
    private val map = HashMap<String, List<Bar>>()
    private val eps = HashMap<String, List<Indicators.EpsPoint>>()
    fun get(symbol: String): List<Bar>? = map[symbol]
    fun put(symbol: String, bars: List<Bar>) { map[symbol] = bars }
    fun getEps(symbol: String): List<Indicators.EpsPoint>? = eps[symbol]
    fun putEps(symbol: String, list: List<Indicators.EpsPoint>) { eps[symbol] = list }
}

object Http {
    /** 需在背景執行緒呼叫。回傳 (HTTP 狀態碼, 內文)。 */
    fun get(url: String, timeoutMs: Int = 20_000): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}

/** Yahoo Finance 非官方 API（免金鑰）。 */
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

    private fun chartUrl(symbol: String, range: String) =
        "https://query1.finance.yahoo.com/v8/finance/chart/" +
            URLEncoder.encode(symbol, "UTF-8") +
            "?range=$range&interval=1d&includeAdjustedClose=true"

    /** 需在背景執行緒呼叫。 */
    fun fetchBlocking(symbol: String, range: String = "10y"): Parsed {
        val (code, body) = Http.get(chartUrl(symbol, range), 30_000)
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
    }

    /** 只為取得名稱：抓最近 5 天資料。 */
    fun fetchNameBlocking(symbol: String): String = fetchBlocking(symbol, "5d").name

    /** 解析 fundamentals-timeseries 的年度 EPS（優先稀釋後 EPS，其次基本 EPS）。 */
    fun parseEps(json: String): List<Indicators.EpsPoint> {
        val ts = JSONObject(json).optJSONObject("timeseries") ?: return emptyList()
        val results = ts.optJSONArray("result") ?: return emptyList()
        val byType = HashMap<String, List<Indicators.EpsPoint>>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val type = item.optJSONObject("meta")?.optJSONArray("type")?.optString(0, "") ?: continue
            val arr = item.optJSONArray(type) ?: continue
            val pts = ArrayList<Indicators.EpsPoint>()
            for (k in 0 until arr.length()) {
                val o = arr.optJSONObject(k) ?: continue
                val day = DateUtil.parse(o.optString("asOfDate", "")) ?: continue
                val raw = o.optJSONObject("reportedValue")?.optDouble("raw", Double.NaN) ?: Double.NaN
                if (raw.isFinite()) pts.add(Indicators.EpsPoint(day, raw))
            }
            byType[type] = pts
        }
        return (byType["annualDilutedEPS"]?.takeIf { it.isNotEmpty() }
            ?: byType["annualBasicEPS"] ?: emptyList()).sortedBy { it.asOfDay }
    }

    /** 需在背景執行緒呼叫。取不到資料時回傳空清單。 */
    fun fetchEpsBlocking(symbol: String): List<Indicators.EpsPoint> {
        val now = System.currentTimeMillis() / 1000
        val url = "https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/" +
            URLEncoder.encode(symbol, "UTF-8") +
            "?type=annualDilutedEPS,annualBasicEPS&period1=946684800&period2=$now"
        val (code, body) = Http.get(url)
        if (code !in 200..299) return emptyList()
        return try { parseEps(body) } catch (e: Exception) { emptyList() }
    }
}
