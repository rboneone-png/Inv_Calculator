package com.example.invcalc.qqqi

import com.example.invcalc.stock.Http
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/** 一筆配息。epochDay 為配息（除息）日。 */
class Div(val epochDay: Long, val amount: Double)

class ChartInfo(val price: Double?, val dividends: List<Div>)

/** 股價、配息與匯率資料來源：Yahoo Finance（非官方、免金鑰）。 */
object QqqiApi {
    const val DEFAULT_FX = 32.0
    const val AVG_COUNT = 6

    fun parse(json: String): ChartInfo {
        val chart = JSONObject(json).getJSONObject("chart")
        if (!chart.isNull("error")) {
            val desc = chart.getJSONObject("error").optString("description", "")
            throw IllegalArgumentException(if (desc.isBlank()) "查無資料" else desc)
        }
        if (chart.isNull("result")) throw IllegalArgumentException("查無資料")
        val result = chart.getJSONArray("result").getJSONObject(0)

        // 股價：優先用即時價，否則取最後一筆有效收盤價
        var price = result.optJSONObject("meta")?.optDouble("regularMarketPrice", Double.NaN) ?: Double.NaN
        if (!price.isFinite() || price <= 0) {
            price = Double.NaN
            val close = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
            if (close != null) {
                for (i in close.length() - 1 downTo 0) {
                    if (!close.isNull(i) && close.optDouble(i, Double.NaN) > 0) {
                        price = close.getDouble(i)
                        break
                    }
                }
            }
        }

        // 配息
        val divs = ArrayList<Div>()
        val ev = result.optJSONObject("events")?.optJSONObject("dividends")
        if (ev != null) {
            val keys = ev.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = ev.optJSONObject(k) ?: continue
                val amount = o.optDouble("amount", Double.NaN)
                val ts = o.optLong("date", k.toLongOrNull() ?: 0L)
                if (amount.isFinite() && amount > 0 && ts > 0) divs.add(Div(Math.floorDiv(ts, 86400L), amount))
            }
        }
        divs.sortBy { it.epochDay }
        return ChartInfo(if (price.isFinite() && price > 0) price else null, divs)
    }

    private fun chartUrl(symbol: String, range: String, events: Boolean) =
        "https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(symbol, "UTF-8") +
            "?range=$range&interval=1d" + (if (events) "&events=div" else "")

    private fun fetch(symbol: String, range: String, events: Boolean): ChartInfo {
        val (code, body) = Http.get(chartUrl(symbol, range, events))
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

    /** QQQI 股價與近一年配息。需在背景執行緒呼叫。 */
    fun fetchQqqi(): ChartInfo = fetch("QQQI", "1y", true)

    /** 美元兌台幣即時匯率（Yahoo 代號 TWD=X）。取不到或不合理時丟出例外。 */
    fun fetchUsdTwd(): Double {
        val p = fetch("TWD=X", "5d", false).price ?: throw IllegalStateException("查無匯率")
        if (p !in 20.0..50.0) throw IllegalStateException("匯率數值不合理：$p")
        return p
    }

    /** 最近 n 次配息（依日期排序後取最後 n 筆）。 */
    fun lastDividends(divs: List<Div>, n: Int = AVG_COUNT): List<Div> = divs.sortedBy { it.epochDay }.takeLast(n)

    /** 月配息平均值＝最近 n 次（QQQI 為月配息，約 6 個月）配息的平均。沒有配息資料時回傳 null。 */
    fun averageDividend(divs: List<Div>, n: Int = AVG_COUNT): Double? {
        val last = lastDividends(divs, n)
        if (last.isEmpty()) return null
        return last.fold(BigDecimal.ZERO) { acc, d -> acc.add(BigDecimal.valueOf(d.amount)) }
            .divide(BigDecimal(last.size), 10, RoundingMode.HALF_UP).toDouble()
    }
}

/**
 * 股息試算（以 BigDecimal 計算，避免浮點誤差影響「捨去」與「四捨五入」）。
 *  C = A ÷ H ÷ B，小數點捨去
 *  E = C × D
 *  F = E × H，小數點四捨五入
 *  G = F × 12，小數點四捨五入
 */
object QqqiCalc {
    class Result(
        val shares: Long,          // C 股數
        val monthlyUsd: Double,    // E 月股息收入（USD）
        val monthlyNt: Long,       // F 月股息收入（NT）
        val yearlyNt: Long,        // G 年利息（NT）
        val investedNt: Double     // 實際買入金額（NT，參考）
    )

    fun compute(amountNt: Double, rate: Double, price: Double, monthlyDivUsd: Double): Result? {
        if (!(amountNt >= 0.0 && rate > 0.0 && price > 0.0 && monthlyDivUsd >= 0.0)) return null
        if (!(amountNt.isFinite() && rate.isFinite() && price.isFinite() && monthlyDivUsd.isFinite())) return null
        val a = BigDecimal.valueOf(amountNt)
        val h = BigDecimal.valueOf(rate)
        val b = BigDecimal.valueOf(price)
        val d = BigDecimal.valueOf(monthlyDivUsd)
        // A ÷ H ÷ B = A ÷ (H × B)，一次除並直接捨去小數
        val shares = a.divide(h.multiply(b), 0, RoundingMode.DOWN)
        val monthlyUsd = shares.multiply(d)
        val monthlyNt = monthlyUsd.multiply(h).setScale(0, RoundingMode.HALF_UP)
        val yearlyNt = monthlyNt.multiply(BigDecimal(12)).setScale(0, RoundingMode.HALF_UP)
        val invested = shares.multiply(b).multiply(h)
        return Result(shares.toLong(), monthlyUsd.toDouble(), monthlyNt.toLong(), yearlyNt.toLong(), invested.toDouble())
    }

    /** 把數字轉成不含科學記號的字串（供輸入欄位使用）。 */
    fun plain(v: Double, scale: Int): String =
        BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
