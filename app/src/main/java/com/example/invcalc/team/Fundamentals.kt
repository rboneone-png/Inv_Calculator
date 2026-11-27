package com.example.invcalc.team

import com.example.invcalc.stock.DateUtil
import com.example.invcalc.stock.Http
import org.json.JSONObject
import java.net.URLEncoder

/** 一個會計年度的財報摘要（缺資料的欄位為 null）。 */
class FundYear(
    val asOfDay: Long,
    val revenue: Double?,
    val netIncome: Double?,
    val equity: Double?,
    val debt: Double?,
    val fcf: Double?,
    val eps: Double?
)

/** Yahoo Finance fundamentals-timeseries（非官方、免金鑰）年度財報。 */
object Fundamentals {
    private val TYPES = listOf(
        "annualTotalRevenue", "annualNetIncome", "annualStockholdersEquity",
        "annualTotalDebt", "annualFreeCashFlow", "annualDilutedEPS", "annualBasicEPS"
    )

    fun parse(json: String): List<FundYear> {
        val results = JSONObject(json).optJSONObject("timeseries")?.optJSONArray("result") ?: return emptyList()
        val byType = HashMap<String, HashMap<Long, Double>>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val type = item.optJSONObject("meta")?.optJSONArray("type")?.optString(0, "") ?: continue
            val arr = item.optJSONArray(type) ?: continue
            val m = HashMap<Long, Double>()
            for (k in 0 until arr.length()) {
                val o = arr.optJSONObject(k) ?: continue
                val day = DateUtil.parse(o.optString("asOfDate", "")) ?: continue
                val raw = o.optJSONObject("reportedValue")?.optDouble("raw", Double.NaN) ?: Double.NaN
                if (raw.isFinite()) m[day] = raw
            }
            byType[type] = m
        }
        val days = byType.values.flatMap { it.keys }.toSortedSet()
        val years = days.map { d ->
            FundYear(
                d,
                byType["annualTotalRevenue"]?.get(d),
                byType["annualNetIncome"]?.get(d),
                byType["annualStockholdersEquity"]?.get(d),
                byType["annualTotalDebt"]?.get(d),
                byType["annualFreeCashFlow"]?.get(d),
                byType["annualDilutedEPS"]?.get(d) ?: byType["annualBasicEPS"]?.get(d)
            )
        }
        return years.filter {
            it.revenue != null || it.netIncome != null || it.equity != null || it.debt != null || it.fcf != null || it.eps != null
        }.takeLast(6)
    }

    /** 需在背景執行緒呼叫；取不到資料時回傳空清單。 */
    fun fetchBlocking(symbol: String): List<FundYear> {
        val now = System.currentTimeMillis() / 1000
        val url = "https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/" +
            URLEncoder.encode(symbol, "UTF-8") +
            "?type=" + TYPES.joinToString(",") + "&period1=946684800&period2=$now"
        return try {
            val (code, body) = Http.get(url)
            if (code in 200..299) parse(body) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
