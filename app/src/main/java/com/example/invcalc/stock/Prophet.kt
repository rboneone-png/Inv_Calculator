package com.example.invcalc.stock

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** 參數與預設值皆取自 Facebook Prophet 的建議預設值。 */
data class ProphetParams(
    val periods: Int = 90,                       // 預測期間（日曆天）；只取其中的交易日（週一至週五）
    val changepointPriorScale: Double = 0.05,    // changepoint_prior_scale
    val seasonalityPriorScale: Double = 10.0,    // seasonality_prior_scale
    val nChangepoints: Int = 25,                 // n_changepoints
    val changepointRange: Double = 0.8,          // changepoint_range
    val intervalWidth: Double = 0.8,             // interval_width
    val yearlySeasonality: Boolean = true,       // yearly_seasonality（傅立葉階數 10）
    val weeklySeasonality: Boolean = true,       // weekly_seasonality（傅立葉階數 3）
    val uncertaintySamples: Int = 1000           // uncertainty_samples
)

class ProphetResult(
    val histDays: List<Long>,
    val histActual: List<Double>,
    val histFit: List<Double>,
    val histLower: List<Double>,
    val histUpper: List<Double>,
    val futureDays: List<Long>,
    val yhat: List<Double>,
    val lower: List<Double>,
    val upper: List<Double>
)

/**
 * Prophet 的 Kotlin 實作（Android 上無法執行 Python／Stan 版本）。
 * 模型同 Prophet：分段線性趨勢 + 傅立葉季節性（加法模式），
 * 以 MAP 估計（changepoint 採 Laplace 先驗、季節性採 Normal 先驗，用 IRLS 求解）；
 * 信賴區間以 Prophet 相同的方式模擬未來趨勢變化與觀測雜訊。
 */
object Prophet {
    private const val YEARLY_PERIOD = 365.25
    private const val YEARLY_ORDER = 10
    private const val WEEKLY_PERIOD = 7.0
    private const val WEEKLY_ORDER = 3

    fun fit(days: List<Long>, values: List<Double>, params: ProphetParams, seed: Long = 42L): ProphetResult {
        val n = days.size
        require(n == values.size && n >= 30) { "資料不足（至少需要 30 筆日線資料）" }
        require(params.periods >= 1) { "預測天數需 ≥ 1" }

        val yMax = values.maxOf { abs(it) }.let { if (it > 0) it else 1.0 }
        val ys = DoubleArray(n) { values[it] / yMax }
        val d0 = days.first().toDouble()
        val span = max((days.last() - days.first()).toDouble(), 1.0)
        val t = DoubleArray(n) { (days[it] - d0) / span }

        // ---- changepoints（與 Prophet 相同：前 changepoint_range 比例內等距）----
        val histSize = floor(n * params.changepointRange).toInt()
        var nCp = params.nChangepoints
        if (nCp + 1 > histSize) nCp = histSize - 1
        if (nCp < 0) nCp = 0
        val cps = DoubleArray(nCp) { j ->
            t[Math.round((j + 1).toDouble() * (histSize - 1) / nCp).toInt()]
        }

        val seasons = ArrayList<Pair<Double, Int>>()
        if (params.yearlySeasonality) seasons.add(YEARLY_PERIOD to YEARLY_ORDER)
        if (params.weeklySeasonality) seasons.add(WEEKLY_PERIOD to WEEKLY_ORDER)
        val seasCols = seasons.sumOf { 2 * it.second }
        val cols = 2 + nCp + seasCols

        // 設計矩陣一列：[1, t, max(0,t-s_j)..., 傅立葉項...]
        fun row(day: Double, tt: Double): DoubleArray {
            val r = DoubleArray(cols)
            r[0] = 1.0
            r[1] = tt
            for (j in 0 until nCp) r[2 + j] = max(0.0, tt - cps[j])
            var c = 2 + nCp
            for ((period, order) in seasons) {
                for (k in 1..order) {
                    val a = 2.0 * PI * k * day / period
                    r[c++] = sin(a)
                    r[c++] = cos(a)
                }
            }
            return r
        }

        val x = Array(n) { row(days[it].toDouble(), t[it]) }
        val xtx = Array(cols) { DoubleArray(cols) }
        val xty = DoubleArray(cols)
        for (i in 0 until n) {
            val xi = x[i]
            for (a in 0 until cols) {
                val v = xi[a]
                if (v == 0.0) continue
                xty[a] += v * ys[i]
                for (b in 0 until cols) xtx[a][b] += v * xi[b]
            }
        }

        // ---- MAP：以 IRLS 近似 Laplace 先驗 ----
        val tau = params.changepointPriorScale
        val seasVar = params.seasonalityPriorScale.pow(2)
        val delta = DoubleArray(nCp) { 0.05 }
        var sigma2 = 0.01
        var theta = DoubleArray(cols)
        for (iter in 0 until 30) {
            val a = Array(cols) { xtx[it].copyOf() }
            a[0][0] += sigma2 / 25.0 // 截距、斜率：Normal(0, 5)
            a[1][1] += sigma2 / 25.0
            for (j in 0 until nCp) a[2 + j][2 + j] += sigma2 / (tau * max(abs(delta[j]), 1e-4))
            for (c in 2 + nCp until cols) a[c][c] += sigma2 / seasVar
            theta = solve(a, xty.copyOf())
            for (j in 0 until nCp) delta[j] = theta[2 + j]
            var rss = 0.0
            for (i in 0 until n) {
                val e = ys[i] - dot(x[i], theta)
                rss += e * e
            }
            sigma2 = max(rss / n, 1e-8)
        }
        val sigma = sqrt(sigma2)
        val fitScaled = DoubleArray(n) { dot(x[it], theta) }
        val histFit = List(n) { fitScaled[it] * yMax }

        // ---- 未來日期：預測期間內的交易日（週一至週五）----
        // 股價資料沒有週末，週季節性對週六日沒有任何訓練資料，若預測週末會嚴重失真（鋸齒狀）。
        val lastDay = days.last()
        val futureDays = (1..params.periods).map { lastDay + it }.filter { DateUtil.dayOfWeek(it) < 5 }
        require(futureDays.isNotEmpty()) { "預測期間內沒有交易日" }
        val h = futureDays.size
        val tf = DoubleArray(h) { (futureDays[it] - d0) / span }
        val point = DoubleArray(h) { dot(row(futureDays[it].toDouble(), tf[it]), theta) }

        // ---- 信賴區間：模擬未來趨勢變化 + 觀測雜訊 ----
        val samples = max(params.uncertaintySamples, 50)
        val rnd = Random(seed)
        val meanAbsDelta = if (nCp > 0) delta.sumOf { abs(it) } / nCp else 0.0
        val lam = meanAbsDelta + 1e-8
        val pNew = if (nCp > 0) nCp.toDouble() / n else 0.0
        val sim = Array(h) { DoubleArray(samples) }
        for (s in 0 until samples) {
            var shift = 0.0
            var slope = 0.0
            var prevT = t[n - 1]
            for (i in 0 until h) {
                shift += slope * (tf[i] - prevT)
                prevT = tf[i]
                if (rnd.nextDouble() < pNew) {
                    val u = rnd.nextDouble() - 0.5
                    slope += -lam * sign(u) * ln(max(1.0 - 2.0 * abs(u), 1e-12))
                }
                sim[i][s] = point[i] + shift + sigma * rnd.nextGaussian()
            }
        }
        val lo = ((1 - params.intervalWidth) / 2 * (samples - 1)).roundToInt().coerceIn(0, samples - 1)
        val hi = ((1 + params.intervalWidth) / 2 * (samples - 1)).roundToInt().coerceIn(0, samples - 1)
        val lower = ArrayList<Double>(h)
        val upper = ArrayList<Double>(h)
        for (i in 0 until h) {
            sim[i].sort()
            lower.add(sim[i][lo] * yMax)
            upper.add(sim[i][hi] * yMax)
        }

        // 歷史區間：趨勢無不確定性，只含觀測雜訊（Prophet 對樣本內資料的作法）
        val noise = DoubleArray(samples)
        val rnd2 = Random(seed + 1)
        for (i in 0 until samples) noise[i] = rnd2.nextGaussian()
        noise.sort()
        val zLo = noise[lo]
        val zHi = noise[hi]
        val histLower = List(n) { (fitScaled[it] + sigma * zLo) * yMax }
        val histUpper = List(n) { (fitScaled[it] + sigma * zHi) * yMax }

        return ProphetResult(
            histDays = days,
            histActual = values,
            histFit = histFit,
            histLower = histLower,
            histUpper = histUpper,
            futureDays = futureDays,
            yhat = List(h) { point[it] * yMax },
            lower = lower,
            upper = upper
        )
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    /** 高斯消去（部分選主元）。 */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-12) continue
            if (piv != col) {
                val tmp = a[piv]; a[piv] = a[col]; a[col] = tmp
                val tb = b[piv]; b[piv] = b[col]; b[col] = tb
            }
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                if (f != 0.0) {
                    for (c in col until n) a[r][c] -= f * a[col][c]
                    b[r] -= f * b[col]
                }
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var s = b[i]
            for (c in i + 1 until n) s -= a[i][c] * x[c]
            x[i] = if (abs(a[i][i]) < 1e-12) 0.0 else s / a[i][i]
        }
        return x
    }
}
