package com.example.invcalc.team

import com.example.invcalc.stock.Http
import org.json.JSONObject
import java.net.URLEncoder

class NewsItem(val title: String, val publisher: String, val time: Long)

/** 新聞情緒：score 介於 -1（偏空）到 +1（偏多）；classified 為有明確情緒的則數。 */
class Sentiment(val score: Double, val positive: Int, val negative: Int, val classified: Int, val total: Int)

/** 以內建關鍵字詞庫計算情緒（由程式計算，可驗證；非 AI 判讀）。 */
object News {
    private val POS_EN = listOf(
        "surge", "surges", "soar", "soars", "rally", "rallies", "beat", "beats", "record", "upgrade", "upgrades",
        "raises", "raised", "outperform", "strong", "growth", "profit", "profits", "gain", "gains", "jump", "jumps",
        "bullish", "boost", "boosts", "rebound", "climb", "climbs", "optimistic", "exceed", "exceeds", "breakthrough", "approval"
    )
    private val NEG_EN = listOf(
        "plunge", "plunges", "slump", "slumps", "tumble", "tumbles", "misses", "missed", "downgrade", "downgrades",
        "cuts", "lawsuit", "probe", "investigation", "fraud", "recall", "weak", "loss", "losses", "decline", "declines",
        "drop", "drops", "falls", "bearish", "warning", "layoffs", "bankruptcy", "crash", "selloff", "sell-off", "slide", "slides"
    )
    private val POS_ZH = listOf("大漲", "漲停", "創高", "創新高", "看好", "上修", "調升", "獲利", "成長", "買進", "利多", "突破", "優於預期", "轉盈", "增持")
    private val NEG_ZH = listOf("大跌", "跌停", "下修", "調降", "虧損", "利空", "衰退", "賣出", "減持", "警訊", "裁員", "破產", "調查", "訴訟", "重挫", "下殺")

    private fun enRegex(words: List<String>) =
        Regex("\\b(" + words.joinToString("|") { Regex.escape(it) } + ")\\b", RegexOption.IGNORE_CASE)

    private val posRe = enRegex(POS_EN)
    private val negRe = enRegex(NEG_EN)

    private fun countHits(title: String, re: Regex, zh: List<String>): Int =
        re.findAll(title).count() + zh.count { w -> title.contains(w) }

    fun sentiment(items: List<NewsItem>): Sentiment? {
        if (items.isEmpty()) return null
        var sum = 0.0
        var classified = 0
        var pos = 0
        var neg = 0
        for (it in items) {
            val p = countHits(it.title, posRe, POS_ZH)
            val n = countHits(it.title, negRe, NEG_ZH)
            if (p + n == 0) continue
            classified++
            val s = (p - n).toDouble() / (p + n)
            sum += s
            if (s > 0) pos++ else if (s < 0) neg++
        }
        val score = if (classified == 0) 0.0 else sum / classified
        return Sentiment(score, pos, neg, classified, items.size)
    }

    fun parse(json: String): List<NewsItem> {
        val arr = JSONObject(json).optJSONArray("news") ?: return emptyList()
        val out = ArrayList<NewsItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            if (title.isEmpty()) continue
            out.add(NewsItem(title, o.optString("publisher", ""), o.optLong("providerPublishTime", 0L)))
        }
        return out
    }

    /** 需在背景執行緒呼叫；失敗時回傳空清單。 */
    fun fetchBlocking(symbol: String): List<NewsItem> {
        val url = "https://query1.finance.yahoo.com/v1/finance/search?q=" +
            URLEncoder.encode(symbol, "UTF-8") + "&quotesCount=0&newsCount=15"
        return try {
            val (code, body) = Http.get(url)
            if (code in 200..299) parse(body) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun label(s: Sentiment): String = when {
        s.classified == 0 -> "中性（無明確多空字眼）"
        s.score > 0.25 -> "偏多"
        s.score < -0.25 -> "偏空"
        else -> "中性"
    }
}
