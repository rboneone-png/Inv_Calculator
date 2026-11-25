package com.example.invcalc.stock

import org.json.JSONArray
import java.util.Locale

/** 台股中文簡稱：來自證交所（上市）與櫃買中心（上櫃）的公開資料。 */
object TwNames {
    class Match(val symbol: String, val name: String)

    private val TSE_URLS = listOf(
        "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL",
        "https://openapi.twse.com.tw/v1/opendata/t187ap03_L"
    )
    private val OTC_URLS = listOf(
        "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes",
        "https://www.tpex.org.tw/openapi/v1/mopsfin_t187ap03_O"
    )

    private var tseCache: Map<String, String>? = null
    private var otcCache: Map<String, String>? = null

    /**
     * 解析「代號 → 名稱」表。欄位名稱依特徵判斷：
     * 代號欄含 Code／代號；名稱欄優先含 Abbreviation／簡稱，其次 Name。
     */
    fun parseTable(json: String): Map<String, String> {
        val arr = JSONArray(json)
        if (arr.length() == 0) return emptyMap()
        val keys = arr.getJSONObject(0).keys().asSequence().toList()
        val codeKey = keys.firstOrNull { it.contains("Code", true) || it.contains("代號") } ?: return emptyMap()
        val nameKey = keys.firstOrNull { it.contains("Abbreviation", true) || it.contains("簡稱") }
            ?: keys.firstOrNull { it.equals("Name", true) }
            ?: keys.firstOrNull { it.contains("Name", true) }
            ?: return emptyMap()
        val out = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString(codeKey, "").trim().uppercase(Locale.US)
            val name = o.optString(nameKey, "").trim()
            if (code.isNotEmpty() && name.isNotEmpty()) out[code] = name
        }
        return out
    }

    private fun load(urls: List<String>): Map<String, String> {
        for (u in urls) {
            try {
                val (code, body) = Http.get(u)
                if (code !in 200..299) continue
                val m = parseTable(body)
                if (m.isNotEmpty()) return m
            } catch (e: Exception) {
                // 試下一個來源
            }
        }
        return emptyMap()
    }

    @Synchronized
    private fun tse(): Map<String, String> =
        tseCache ?: load(TSE_URLS).also { if (it.isNotEmpty()) tseCache = it }

    @Synchronized
    private fun otc(): Map<String, String> =
        otcCache ?: load(OTC_URLS).also { if (it.isNotEmpty()) otcCache = it }

    /**
     * 以輸入的台股代號查中文簡稱（需在背景執行緒呼叫）。
     * 只輸入數字時，先查上市、再查上櫃，並回傳正確的 .TW／.TWO 代號。
     */
    fun lookup(input: String): Match? {
        val raw = input.trim().uppercase(Locale.US)
        return when {
            raw.endsWith(".TWO") -> raw.removeSuffix(".TWO").let { c -> otc()[c]?.let { Match("$c.TWO", it) } }
            raw.endsWith(".TW") -> raw.removeSuffix(".TW").let { c -> tse()[c]?.let { Match("$c.TW", it) } }
            SymbolUtil.isTaiwanCode(raw) ->
                tse()[raw]?.let { Match("$raw.TW", it) } ?: otc()[raw]?.let { Match("$raw.TWO", it) }
            else -> null
        }
    }
}
