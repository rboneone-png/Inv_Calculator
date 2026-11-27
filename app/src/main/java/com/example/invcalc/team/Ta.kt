package com.example.invcalc.team

import com.example.invcalc.stock.Bar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** 技術指標（皆回傳與輸入等長的陣列，尚無法計算的位置為 NaN）。 */
object Ta {
    private const val NAN = Double.NaN

    fun sma(v: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(v.size) { NAN }
        var sum = 0.0
        for (i in v.indices) {
            sum += v[i]
            if (i >= n) sum -= v[i - n]
            if (i >= n - 1) out[i] = sum / n
        }
        return out
    }

    /** EMA：以前 n 筆平均為起點；輸入開頭可含 NaN。 */
    fun ema(v: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(v.size) { NAN }
        var s = 0
        while (s < v.size && !v[s].isFinite()) s++
        if (v.size - s < n) return out
        var sum = 0.0
        for (i in s until s + n) sum += v[i]
        var prev = sum / n
        out[s + n - 1] = prev
        val k = 2.0 / (n + 1)
        for (i in s + n until v.size) {
            prev = v[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** Wilder RSI。 */
    fun rsi(c: DoubleArray, n: Int = 14): DoubleArray {
        val out = DoubleArray(c.size) { NAN }
        if (c.size <= n) return out
        fun rs(ag: Double, al: Double) = when {
            al == 0.0 && ag == 0.0 -> 50.0
            al == 0.0 -> 100.0
            else -> 100.0 - 100.0 / (1.0 + ag / al)
        }
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val d = c[i] - c[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var ag = gain / n
        var al = loss / n
        out[n] = rs(ag, al)
        for (i in n + 1 until c.size) {
            val d = c[i] - c[i - 1]
            ag = (ag * (n - 1) + max(d, 0.0)) / n
            al = (al * (n - 1) + max(-d, 0.0)) / n
            out[i] = rs(ag, al)
        }
        return out
    }

    /** MACD：回傳 (DIF, 訊號線, 柱狀體)。 */
    fun macd(c: DoubleArray, fast: Int = 12, slow: Int = 26, sig: Int = 9): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val ef = ema(c, fast)
        val es = ema(c, slow)
        val m = DoubleArray(c.size) { if (ef[it].isFinite() && es[it].isFinite()) ef[it] - es[it] else NAN }
        val s = ema(m, sig)
        val h = DoubleArray(c.size) { if (m[it].isFinite() && s[it].isFinite()) m[it] - s[it] else NAN }
        return Triple(m, s, h)
    }

    /** Wilder ATR。 */
    fun atr(high: DoubleArray, low: DoubleArray, close: DoubleArray, n: Int = 14): DoubleArray {
        val out = DoubleArray(close.size) { NAN }
        if (close.size <= n) return out
        val tr = DoubleArray(close.size)
        for (i in 1 until close.size) {
            tr[i] = maxOf(high[i] - low[i], abs(high[i] - close[i - 1]), abs(low[i] - close[i - 1]))
        }
        var a = 0.0
        for (i in 1..n) a += tr[i]
        a /= n
        out[n] = a
        for (i in n + 1 until close.size) {
            a = (a * (n - 1) + tr[i]) / n
            out[i] = a
        }
        return out
    }

    /** 布林通道：回傳 (中軌, 上軌, 下軌)，母體標準差。 */
    fun bollinger(c: DoubleArray, n: Int = 20, k: Double = 2.0): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val mid = sma(c, n)
        val up = DoubleArray(c.size) { NAN }
        val low = DoubleArray(c.size) { NAN }
        for (i in n - 1 until c.size) {
            var sq = 0.0
            for (j in i - n + 1..i) sq += (c[j] - mid[i]) * (c[j] - mid[i])
            val sd = sqrt(sq / n)
            up[i] = mid[i] + k * sd
            low[i] = mid[i] - k * sd
        }
        return Triple(mid, up, low)
    }

    /** 回撤：價格相對歷史高點的跌幅（≤ 0）。 */
    fun drawdown(v: DoubleArray): DoubleArray {
        var peak = Double.NEGATIVE_INFINITY
        return DoubleArray(v.size) {
            if (v[it] > peak) peak = v[it]
            v[it] / peak - 1.0
        }
    }

    fun returns(v: DoubleArray): DoubleArray = DoubleArray(max(v.size - 1, 0)) { v[it + 1] / v[it] - 1.0 }

    fun mean(v: DoubleArray): Double = if (v.isEmpty()) NAN else v.sum() / v.size

    /** 樣本標準差。 */
    fun std(v: DoubleArray): Double {
        if (v.size < 2) return NAN
        val m = mean(v)
        var sq = 0.0
        for (x in v) sq += (x - m) * (x - m)
        return sqrt(sq / (v.size - 1))
    }

    /** 線性內插百分位（p：0–1）。 */
    fun percentile(v: DoubleArray, p: Double): Double {
        if (v.isEmpty()) return NAN
        val s = v.sortedArray()
        val pos = p * (s.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }
}

/** 一檔股票完整的技術指標序列（與日線等長）。 */
class TechSeries(
    val close: DoubleArray,
    val ma5: DoubleArray,
    val ma20: DoubleArray,
    val ma60: DoubleArray,
    val ma120: DoubleArray,
    val bbMid: DoubleArray,
    val bbUp: DoubleArray,
    val bbLow: DoubleArray,
    val rsi: DoubleArray,
    val macd: DoubleArray,
    val signal: DoubleArray,
    val hist: DoubleArray,
    val atr: DoubleArray
)

fun techSeries(bars: List<Bar>): TechSeries {
    val close = DoubleArray(bars.size) { bars[it].close }
    val high = DoubleArray(bars.size) { bars[it].high }
    val low = DoubleArray(bars.size) { bars[it].low }
    val (bm, bu, bl) = Ta.bollinger(close, 20, 2.0)
    val (m, s, h) = Ta.macd(close)
    return TechSeries(
        close, Ta.sma(close, 5), Ta.sma(close, 20), Ta.sma(close, 60), Ta.sma(close, 120),
        bm, bu, bl, Ta.rsi(close, 14), m, s, h, Ta.atr(high, low, close, 14)
    )
}
