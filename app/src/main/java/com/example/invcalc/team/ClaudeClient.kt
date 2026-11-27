package com.example.invcalc.team

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Claude API 呼叫（選用）。AI 只負責把「程式已算好的數字」用白話文解讀，
 * 不負責計算；未填 API Key 時整個團隊仍可完整運作。
 */
object ClaudeClient {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val VERSION = "2023-06-01"

    const val SYSTEM_STOCK =
        "你是「AI 金融分析團隊」的文字撰寫員。使用者會提供一檔股票由程式計算好的分析數據與評分。" +
            "規則：1) 所有數字、評分、評級、價位都已由程式算好，你不得重新計算、修改或編造任何數字，只能引用提供的數字；" +
            "2) 用白話繁體中文解讀，避免艱澀術語，必要時簡短解釋；3) 輸出 Markdown，依序使用這些標題：" +
            "「## 📈 技術分析師」「## 🧾 財報分析師」「## 🌐 市場趨勢分析師」「## 🛡️ 風險分析師」「## 🧠 投資組長總結」，" +
            "每段 2–4 句；組長總結要說明評級、部位上限、支撐／壓力／停損價位的意思，以及最需要留意的風險與可以觀察的訊號；" +
            "4) 資料標示「資料不足」的部分直接說明無法判斷；5) 最後加一行「本內容僅供參考，不構成投資建議。」"

    const val SYSTEM_OVERALL =
        "你是「AI 金融分析團隊」的投資組長。使用者會提供多檔股票由程式計算好的排名比較表。" +
            "規則：只能引用表中的數字，不得重新計算或編造；用白話繁體中文寫 3–6 句比較總結，" +
            "說明排名靠前與靠後的主要原因、各檔適合的持有期間與需要留意的風險；輸出 Markdown。" +
            "最後加一行「本內容僅供參考，不構成投資建議。」"

    fun buildRequest(model: String, system: String, user: String, maxTokens: Int = 2048): String {
        val msg = JSONObject().put("role", "user").put("content", user)
        return JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", JSONArray().put(msg))
            .toString()
    }

    /** 取出回應文字；API 回傳錯誤時丟出含訊息的例外。 */
    fun parseResponse(json: String): String {
        val o = JSONObject(json)
        if (o.has("error")) {
            val msg = o.optJSONObject("error")?.optString("message", "") ?: ""
            throw IllegalStateException(if (msg.isBlank()) "Claude API 回傳錯誤" else msg)
        }
        val arr = o.optJSONArray("content") ?: throw IllegalStateException("Claude 回應沒有內容")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") sb.append(item.optString("text"))
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) throw IllegalStateException("Claude 回應是空的")
        return text
    }

    /** 需在背景執行緒呼叫。 */
    fun complete(apiKey: String, model: String, system: String, user: String, maxTokens: Int = 2048): String {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey.trim())
            conn.setRequestProperty("anthropic-version", VERSION)
            conn.outputStream.use { it.write(buildRequest(model.trim(), system, user, maxTokens).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code in 200..299) return parseResponse(body)
            val msg = try {
                parseResponse(body)
                null
            } catch (e: IllegalStateException) {
                e.message
            } catch (e: Exception) {
                null
            }
            throw IllegalStateException(
                when (code) {
                    401 -> "API Key 無效（HTTP 401）"
                    429 -> "請求過於頻繁或額度不足（HTTP 429）：" + (msg ?: "")
                    else -> msg ?: "Claude API 連線失敗（HTTP $code）"
                }
            )
        } finally {
            conn.disconnect()
        }
    }
}
