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

    // ------------------------------------------------------------ 索提諾比率

    class SortinoResult(
        val dates: List<Long>,
        val rolling: List<Double>,
        val overall: Double,
        val window: Int
    )

    /** 滾動索提諾比率（年化）：只以低於目標報酬（無風險利率）的下檔偏差作為風險。 */
    fun sortino(bars: List<Bar>, period: Period, rfPct: Double): SortinoResult? {
        if (bars.size < 3) return null
        val rets = DoubleArray(bars.size - 1) { bars[it + 1].adjClose / bars[it].adjClose - 1.0 }
        val w = period.sharpeWindow
        if (rets.size < w) return null
        val target = rfPct / 100.0 / period.periodsPerYear
        val ann = sqrt(period.periodsPerYear.toDouble())

        fun sortinoOf(from: Int, toExclusive: Int): Double {
            val cnt = toExclusive - from
            if (cnt < 2) return Double.NaN
            var sum = 0.0
            var down = 0.0
            for (i in from until toExclusive) {
                val e = rets[i] - target
                sum += e
                if (e < 0) down += e * e
            }
            val dd = sqrt(down / cnt)
            return if (dd < 1e-12) Double.NaN else (sum / cnt) / dd * ann
        }

        val dates = ArrayList<Long>()
        val rolling = ArrayList<Double>()
        for (e in w - 1 until rets.size) {
            dates.add(bars[e + 1].epochDay)
            rolling.add(sortinoOf(e - w + 1, e + 1))
        }
        return SortinoResult(dates, rolling, sortinoOf(0, rets.size), w)
    }

    // ------------------------------------------------------------ 崔納比率

    /** 以日期取交集（兩邊皆需依日期遞增排序）。 */
    fun align(a: List<Bar>, b: List<Bar>): Pair<List<Bar>, List<Bar>> {
        val outA = ArrayList<Bar>()
        val outB = ArrayList<Bar>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i].epochDay == b[j].epochDay -> { outA.add(a[i]); outB.add(b[j]); i++; j++ }
                a[i].epochDay < b[j].epochDay -> i++
                else -> j++
            }
        }
        return outA to outB
    }

    class TreynorResult(
        val dates: List<Long>,
        val treynorPct: List<Double>, // 滾動崔納比率（%）
        val beta: List<Double>,       // 滾動 Beta
        val overallTreynorPct: Double,
        val overallBeta: Double,
        val window: Int
    )

    /**
     * 滾動崔納比率 =（年化平均報酬 − 無風險利率）÷ Beta；Beta 為相對大盤指數的迴歸係數。
     * |Beta| < 0.05 時比率無意義，以 NaN 表示。
     */
    fun treynor(stock: List<Bar>, index: List<Bar>, period: Period, rfPct: Double): TreynorResult? {
        val (s0, m0) = align(stock, index)
        if (s0.size < 3) return null
        val s = Resample.toPeriod(s0, period)
        val m = Resample.toPeriod(m0, period)
        val n = minOf(s.size, m.size)
        if (n < 3) return null
        val rs = DoubleArray(n - 1) { s[it + 1].adjClose / s[it].adjClose - 1.0 }
        val rm = DoubleArray(n - 1) { m[it + 1].adjClose / m[it].adjClose - 1.0 }
        val w = period.sharpeWindow
        if (rs.size < w) return null
        val rfPer = rfPct / 100.0 / period.periodsPerYear
        val ppy = period.periodsPerYear

        // 回傳 (beta, 崔納比率%)
        fun calc(from: Int, toExclusive: Int): Pair<Double, Double> {
            val cnt = toExclusive - from
            if (cnt < 2) return Double.NaN to Double.NaN
            var ms = 0.0
            var mm = 0.0
            for (i in from until toExclusive) { ms += rs[i]; mm += rm[i] }
            ms /= cnt
            mm /= cnt
            var cov = 0.0
            var varM = 0.0
            for (i in from until toExclusive) {
                cov += (rs[i] - ms) * (rm[i] - mm)
                varM += (rm[i] - mm) * (rm[i] - mm)
            }
            if (varM < 1e-14) return Double.NaN to Double.NaN
            val beta = cov / varM
            val tr = if (abs(beta) < 0.05) Double.NaN else (ms - rfPer) * ppy / beta * 100.0
            return beta to tr
        }

        val dates = ArrayList<Long>()
        val trs = ArrayList<Double>()
        val betas = ArrayList<Double>()
        for (e in w - 1 until rs.size) {
            val (b, t) = calc(e - w + 1, e + 1)
            dates.add(s[e + 1].epochDay)
            betas.add(b)
            trs.add(t)
        }
        val (ob, ot) = calc(0, rs.size)
        return TreynorResult(dates, trs, betas, ot, ob, w)
    }

    // ------------------------------------------------------------ 席勒本益比（CAPE）

    class EpsPoint(val asOfDay: Long, val eps: Double)

    class CapeResult(
        val dates: List<Long>,
        val cape: List<Double>,
        val latest: Double,
        val median: Double,
        val yearsUsed: Int,
        val avgEps: Double
    )

    /**
     * 席勒本益比 = 股價 ÷ 過去最多 maxYears 年（年度）EPS 平均。
     * 每個日期只使用「財報截止日 + lagDays（公布延遲）」之前已公布的 EPS；未做通膨調整。
     */
    fun cape(bars: List<Bar>, eps: List<EpsPoint>, lagDays: Int = 90, maxYears: Int = 10): CapeResult? {
        val sorted = eps.sortedBy { it.asOfDay }
        if (sorted.isEmpty() || bars.isEmpty()) return null
        val dates = ArrayList<Long>()
        val values = ArrayList<Double>()
        var lastYears = 0
        var lastAvg = Double.NaN
        for (b in bars) {
            val avail = sorted.filter { it.asOfDay + lagDays <= b.epochDay }
            if (avail.isEmpty()) continue
            val use = avail.takeLast(maxYears)
            val avg = use.sumOf { it.eps } / use.size
            if (avg <= 0) continue
            dates.add(b.epochDay)
            values.add(b.close / avg)
            lastYears = use.size
            lastAvg = avg
        }
        if (values.isEmpty()) return null
        val sortedVals = values.sorted()
        val median = if (sortedVals.size % 2 == 1) sortedVals[sortedVals.size / 2]
        else (sortedVals[sortedVals.size / 2 - 1] + sortedVals[sortedVals.size / 2]) / 2
        return CapeResult(dates, values, values.last(), median, lastYears, lastAvg)
    }

    // ------------------------------------------------------------ 成交量曲線

    class VolumeSeries(
        val dates: List<Long>,
        val volume: List<Double>,
        val average: List<Double>,
        val threshold: List<Double>,
        val spikeFlags: List<Boolean>
    )

    /** 每日成交量、前 window 日均量與 multiple 倍門檻。 */
    fun volumeSeries(daily: List<Bar>, window: Int = 20, multiple: Double = 2.0): VolumeSeries? {
        if (daily.size < window + 1) return null
        val dates = ArrayList<Long>()
        val vol = ArrayList<Double>()
        val avgs = ArrayList<Double>()
        val thr = ArrayList<Double>()
        val flags = ArrayList<Boolean>()
        var sum = 0.0
        for (i in 0 until window) sum += daily[i].volume
        for (i in window until daily.size) {
            val avg = sum / window
            dates.add(daily[i].epochDay)
            vol.add(daily[i].volume)
            avgs.add(avg)
            thr.add(avg * multiple)
            flags.add(avg > 1e-9 && daily[i].volume >= avg * multiple)
            sum += daily[i].volume - daily[i - window].volume
        }
        return VolumeSeries(dates, vol, avgs, thr, flags)
    }
}
