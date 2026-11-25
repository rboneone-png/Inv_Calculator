package com.example.invcalc.stock

import kotlin.math.abs
import kotlin.math.sqrt

object Indicators {

    class SharpeResult(
        val dates: List<Long>,
        val rolling: List<Double>,
        val overall: Double,
        val window: Int
    )

    /**
     * 滾動夏普值（年化）。報酬率使用還原收盤價；
     * rfPct 為年化無風險利率（%）。資料不足時回傳 null。
     */
    fun sharpe(bars: List<Bar>, period: Period, rfPct: Double): SharpeResult? {
        if (bars.size < 3) return null
        val rets = DoubleArray(bars.size - 1) { bars[it + 1].adjClose / bars[it].adjClose - 1.0 }
        val w = period.sharpeWindow
        if (rets.size < w) return null
        val rfPer = rfPct / 100.0 / period.periodsPerYear
        val ann = sqrt(period.periodsPerYear.toDouble())

        fun sharpeOf(from: Int, toExclusive: Int): Double {
            val cnt = toExclusive - from
            if (cnt < 2) return Double.NaN
            var sum = 0.0
            for (i in from until toExclusive) sum += rets[i] - rfPer
            val mean = sum / cnt
            var sq = 0.0
            for (i in from until toExclusive) {
                val e = rets[i] - rfPer - mean
                sq += e * e
            }
            val sd = sqrt(sq / (cnt - 1))
            return if (sd < 1e-12) Double.NaN else mean / sd * ann
        }

        val dates = ArrayList<Long>()
        val rolling = ArrayList<Double>()
        for (e in w - 1 until rets.size) {
            dates.add(bars[e + 1].epochDay)
            rolling.add(sharpeOf(e - w + 1, e + 1))
        }
        return SharpeResult(dates, rolling, sharpeOf(0, rets.size), w)
    }

    class BollingerResult(
        val dates: List<Long>,
        val close: List<Double>,
        val mid: List<Double>,
        val upper: List<Double>,
        val lower: List<Double>
    )

    /** 布林通道：移動平均 ± k 倍母體標準差。 */
    fun bollinger(bars: List<Bar>, window: Int = 20, k: Double = 2.0): BollingerResult? {
        if (bars.size < window) return null
        val dates = ArrayList<Long>()
        val close = ArrayList<Double>()
        val mid = ArrayList<Double>()
        val upper = ArrayList<Double>()
        val lower = ArrayList<Double>()
        for (i in window - 1 until bars.size) {
            var sum = 0.0
            for (j in i - window + 1..i) sum += bars[j].close
            val mean = sum / window
            var sq = 0.0
            for (j in i - window + 1..i) {
                val e = bars[j].close - mean
                sq += e * e
            }
            val sd = sqrt(sq / window)
            dates.add(bars[i].epochDay)
            close.add(bars[i].close)
            mid.add(mean)
            upper.add(mean + k * sd)
            lower.add(mean - k * sd)
        }
        return BollingerResult(dates, close, mid, upper, lower)
    }

    class VolumeSpike(
        val date: Long,
        val volume: Double,
        val average: Double,
        val ratio: Double,
        val isSpike: Boolean
    )

    /** 當日成交量 ≥ 前 window 個交易日（約一個月）平均量的 multiple 倍即為突高。 */
    fun volumeSpike(daily: List<Bar>, window: Int = 20, multiple: Double = 2.0): VolumeSpike? {
        if (daily.size < window + 1) return null
        val last = daily.last()
        var sum = 0.0
        for (i in daily.size - 1 - window until daily.size - 1) sum += daily[i].volume
        val avg = sum / window
        if (abs(avg) < 1e-9) return null
        val ratio = last.volume / avg
        return VolumeSpike(last.epochDay, last.volume, avg, ratio, ratio >= multiple)
    }
}
