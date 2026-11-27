package com.example.invcalc.team

import com.example.invcalc.stock.Bar
import com.example.invcalc.stock.Indicators
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal fun f2(v: Double) = if (v.isFinite()) String.format(Locale.US, "%.2f", v) else "—"

/** v 為小數（0.123 → 12.3%）。 */
internal fun pctS(v: Double?) = if (v != null && v.isFinite()) String.format(Locale.US, "%.1f%%", v * 100) else "—"

private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

/** 五位分析師。所有數字與評分皆由程式依固定規則計算。 */
object Agents {

    private fun noData(role: Role, reason: String) =
        AgentReport(role, null, "資料不足", emptyList(), listOf(reason))

    private fun verdict(role: Role, score: Double): String = when (role) {
        Role.RISK -> when {
            score >= 70 -> "風險低"
            score >= 55 -> "風險適中偏低"
            score >= 45 -> "風險適中"
            score >= 30 -> "風險偏高"
            else -> "風險高"
        }
        Role.FUND -> when {
            score >= 70 -> "體質優良"
            score >= 55 -> "體質尚可"
            score >= 45 -> "體質普通"
            score >= 30 -> "體質偏弱"
            else -> "體質差"
        }
        else -> when {
            score >= 70 -> "偏多"
            score >= 55 -> "中性偏多"
            score >= 45 -> "中性"
            score >= 30 -> "中性偏空"
            else -> "偏空"
        }
    }

    // ------------------------------------------------------------------ 📈 技術分析師（滿分 100）
    fun technical(bars: List<Bar>, ts: TechSeries): AgentReport {
        val n = bars.size
        if (n < 60) return noData(Role.TECH, "日線資料不足 60 根，無法完整判斷技術面。")
        val i = n - 1
        val c = ts.close[i]
        val ma5 = ts.ma5[i]
        val ma20 = ts.ma20[i]
        val ma60 = ts.ma60[i]
        val ma120 = ts.ma120[i]
        val f = ArrayList<String>()

        // 1. 趨勢（30）：均線位置與排列
        var trend = 0.0
        if (c > ma20) { trend += 8; f.add("收盤價在 20 日均線之上（短期偏多）") } else f.add("收盤價在 20 日均線之下（短期偏弱）")
        if (c > ma60) { trend += 8; f.add("收盤價在 60 日均線之上（中期偏多）") } else f.add("收盤價在 60 日均線之下（中期偏弱）")
        if (ma20 > ma60) { trend += 7; f.add("20 日均線在 60 日均線之上（多頭排列）") } else f.add("20 日均線在 60 日均線之下（空頭排列）")
        val ma60Prev = ts.ma60.getOrNull(i - 20) ?: Double.NaN
        if (ma60Prev.isFinite()) {
            if (ma60 > ma60Prev) { trend += 7; f.add("60 日均線向上") } else f.add("60 日均線向下")
        } else trend += 3.5

        // 2. RSI（20）
        val rsi = ts.rsi[i]
        val rsiPts = when {
            !rsi.isFinite() -> 10.0
            rsi < 30 -> 14.0
            rsi < 45 -> 8.0
            rsi < 55 -> 12.0
            rsi <= 70 -> 20.0
            else -> 10.0
        }
        f.add(
            when {
                !rsi.isFinite() -> "RSI 無法計算"
                rsi < 30 -> "RSI(14) = ${f2(rsi)}，處於超賣區，可能反彈但趨勢仍弱"
                rsi < 45 -> "RSI(14) = ${f2(rsi)}，動能偏弱"
                rsi < 55 -> "RSI(14) = ${f2(rsi)}，動能中性"
                rsi <= 70 -> "RSI(14) = ${f2(rsi)}，動能強勁且未過熱"
                else -> "RSI(14) = ${f2(rsi)}，處於超買區，短線追高風險升高"
            }
        )

        // 3. MACD（20）
        val dif = ts.macd[i]
        val sig = ts.signal[i]
        val hist = ts.hist[i]
        val histPrev = ts.hist[i - 1]
        var macdPts = 0.0
        if (dif.isFinite() && sig.isFinite()) {
            if (dif > sig) { macdPts += 10; f.add("MACD 在訊號線之上（多方）") } else f.add("MACD 在訊號線之下（空方）")
            if (hist.isFinite() && histPrev.isFinite()) {
                if (hist > histPrev) { macdPts += 5; f.add("MACD 柱狀體擴大（動能增強）") } else f.add("MACD 柱狀體縮小（動能減弱）")
            }
            if (dif > 0) { macdPts += 5; f.add("MACD 在零軸之上") } else f.add("MACD 在零軸之下")
        } else macdPts = 10.0

        // 4. 布林通道（15）：%B
        val up = ts.bbUp[i]
        val low = ts.bbLow[i]
        val pb = if (up.isFinite() && low.isFinite() && up > low) (c - low) / (up - low) else Double.NaN
        val bbPts = when {
            !pb.isFinite() -> 7.5
            pb > 0.85 -> 8.0
            pb >= 0.5 -> 15.0
            pb >= 0.2 -> 8.0
            else -> 5.0
        }
        f.add(
            when {
                !pb.isFinite() -> "布林通道無法計算"
                pb > 1.0 -> "收盤價突破布林上軌（%B = ${f2(pb)}），強勢但短線過熱"
                pb > 0.85 -> "收盤價逼近布林上軌（%B = ${f2(pb)}）"
                pb >= 0.5 -> "收盤價位於布林中軌之上（%B = ${f2(pb)}），偏多"
                pb >= 0.2 -> "收盤價位於布林中軌之下（%B = ${f2(pb)}），偏弱"
                pb >= 0.0 -> "收盤價逼近布林下軌（%B = ${f2(pb)}）"
                else -> "收盤價跌破布林下軌（%B = ${f2(pb)}），弱勢"
            }
        )

        // 5. 量能（15）
        var sum = 0.0
        for (k in i - 20 until i) sum += bars[k].volume
        val avgVol = sum / 20
        val volRatio = if (avgVol > 0) bars[i].volume / avgVol else Double.NaN
        val upDay = c > bars[i - 1].close
        val volPts = when {
            !volRatio.isFinite() -> 8.0
            upDay && volRatio >= 2.0 -> 15.0
            upDay && volRatio >= 1.0 -> 11.0
            upDay -> 7.0
            volRatio >= 2.0 -> 2.0
            volRatio >= 1.0 -> 6.0
            else -> 9.0
        }
        f.add(
            when {
                !volRatio.isFinite() -> "無成交量資料"
                upDay && volRatio >= 2.0 -> "上漲且爆量（${f2(volRatio)} 倍月均量），買盤積極"
                upDay && volRatio >= 1.0 -> "上漲且量能放大（${f2(volRatio)} 倍月均量）"
                upDay -> "上漲但量縮（${f2(volRatio)} 倍月均量），追價力道有限"
                volRatio >= 2.0 -> "下跌且爆量（${f2(volRatio)} 倍月均量），賣壓沉重"
                volRatio >= 1.0 -> "下跌且量能放大（${f2(volRatio)} 倍月均量）"
                else -> "下跌但量縮（${f2(volRatio)} 倍月均量），賣壓不大"
            }
        )

        val score = clamp(trend + rsiPts + macdPts + bbPts + volPts, 0.0, 100.0)
        val metrics = listOf(
            Metric("收盤價", f2(c)),
            Metric("MA5 / MA20 / MA60 / MA120", "${f2(ma5)} / ${f2(ma20)} / ${f2(ma60)} / ${f2(ma120)}"),
            Metric("RSI(14)", f2(rsi)),
            Metric("MACD（DIF／訊號／柱）", "${f2(dif)} / ${f2(sig)} / ${f2(hist)}"),
            Metric("布林通道（下／中／上）", "${f2(low)} / ${f2(ts.bbMid[i])} / ${f2(up)}"),
            Metric("%B", f2(pb)),
            Metric("量比（當日／20 日均量）", f2(volRatio)),
            Metric("分項得分", "趨勢 ${f2(trend)}/30、RSI ${f2(rsiPts)}/20、MACD ${f2(macdPts)}/20、布林 ${f2(bbPts)}/15、量能 ${f2(volPts)}/15")
        )
        return AgentReport(Role.TECH, score, verdict(Role.TECH, score), metrics, f)
    }

    // ------------------------------------------------------------------ 🧾 財報分析師
    fun fundamental(years: List<FundYear>, price: Double): AgentReport {
        if (years.isEmpty()) {
            return noData(Role.FUND, "Yahoo 未提供此標的的財報資料（ETF、指數或資料缺漏時常見），此項不納入加權。")
        }
        val last = years.last()
        val f = ArrayList<String>()
        var earned = 0.0
        var possible = 0.0
        fun add(pts: Double, max: Double) { earned += pts; possible += max }

        // 本益比（20）
        val pe = if (last.eps != null && last.eps > 0 && price > 0) price / last.eps else null
        if (last.eps != null) {
            val pts = when {
                last.eps <= 0 -> 0.0
                pe!! < 15 -> 20.0
                pe < 25 -> 14.0
                pe < 40 -> 8.0
                else -> 3.0
            }
            add(pts, 20.0)
            f.add(
                if (last.eps <= 0) "最近年度 EPS 為負（${f2(last.eps)}），公司處於虧損"
                else "本益比 ${f2(pe!!)} 倍（以最近年度 EPS ${f2(last.eps)} 計）" +
                    when { pe < 15 -> "，估值偏低"; pe < 25 -> "，估值合理"; pe < 40 -> "，估值偏高"; else -> "，估值很高" }
            )
        }
        // ROE（20）
        val roe = if (last.netIncome != null && last.equity != null && last.equity > 0) last.netIncome / last.equity else null
        if (roe != null) {
            val pts = when {
                roe >= 0.20 -> 20.0
                roe >= 0.15 -> 16.0
                roe >= 0.10 -> 11.0
                roe >= 0.05 -> 6.0
                else -> 2.0
            }
            add(pts, 20.0)
            f.add("ROE ${pctS(roe)}" + when { roe >= 0.15 -> "，股東權益報酬優異"; roe >= 0.10 -> "，尚可"; else -> "，偏低" })
        }
        // 淨利率（20）
        val margin = if (last.netIncome != null && last.revenue != null && last.revenue > 0) last.netIncome / last.revenue else null
        if (margin != null) {
            val pts = when {
                margin >= 0.20 -> 20.0
                margin >= 0.10 -> 15.0
                margin >= 0.05 -> 10.0
                margin >= 0.0 -> 5.0
                else -> 0.0
            }
            add(pts, 20.0)
            f.add("淨利率 ${pctS(margin)}" + when { margin >= 0.10 -> "，獲利能力佳"; margin >= 0.0 -> "，獲利微薄"; else -> "，處於虧損" })
        }
        // 營收成長（20）
        val withRev = years.filter { it.revenue != null && it.revenue > 0 }
        val growth = if (withRev.size >= 2) withRev[withRev.size - 1].revenue!! / withRev[withRev.size - 2].revenue!! - 1.0 else null
        if (growth != null) {
            val pts = when {
                growth >= 0.20 -> 20.0
                growth >= 0.10 -> 16.0
                growth >= 0.0 -> 10.0
                growth >= -0.10 -> 4.0
                else -> 0.0
            }
            add(pts, 20.0)
            f.add("營收年增率 ${pctS(growth)}" + when { growth >= 0.10 -> "，成長動能強"; growth >= 0.0 -> "，溫和成長"; else -> "，營收衰退" })
        }
        // 負債（10）：總負債／股東權益
        val de = if (last.debt != null && last.equity != null && last.equity > 0) last.debt / last.equity else null
        if (de != null) {
            val pts = when {
                de < 0.5 -> 10.0
                de < 1.0 -> 8.0
                de < 2.0 -> 5.0
                else -> 2.0
            }
            add(pts, 10.0)
            f.add("負債／股東權益 ${f2(de)} 倍" + when { de < 0.5 -> "，財務結構穩健"; de < 1.0 -> "，負債適中"; de < 2.0 -> "，負債偏高"; else -> "，槓桿很高（金融業屬常態）" })
        }
        // 自由現金流（10）
        if (last.fcf != null) {
            val fcfMargin = if (last.revenue != null && last.revenue > 0) last.fcf / last.revenue else null
            val pts = when {
                last.fcf > 0 && fcfMargin != null && fcfMargin >= 0.10 -> 10.0
                last.fcf > 0 -> 7.0
                else -> 0.0
            }
            add(pts, 10.0)
            f.add(
                if (last.fcf > 0) "自由現金流為正（${bil(last.fcf)}）" + (if (fcfMargin != null) "，占營收 ${pctS(fcfMargin)}" else "")
                else "自由現金流為負（${bil(last.fcf)}），需仰賴外部資金"
            )
        }

        val metrics = listOf(
            Metric("財報年度", com.example.invcalc.stock.DateUtil.format(last.asOfDay)),
            Metric("本益比（PE）", if (pe != null) f2(pe) else "—"),
            Metric("ROE", pctS(roe)),
            Metric("淨利率", pctS(margin)),
            Metric("營收年增率", pctS(growth)),
            Metric("負債／股東權益", if (de != null) f2(de) else "—"),
            Metric("自由現金流", if (last.fcf != null) bil(last.fcf) else "—"),
            Metric("營收", if (last.revenue != null) bil(last.revenue) else "—")
        )
        if (possible < 30.0) {
            return AgentReport(Role.FUND, null, "資料不足", metrics, f + "可用的財報指標太少，此項不納入加權。")
        }
        val score = clamp(earned / possible * 100.0, 0.0, 100.0)
        return AgentReport(Role.FUND, score, verdict(Role.FUND, score), metrics, f)
    }

    /** 以「億」為單位顯示金額。 */
    internal fun bil(v: Double): String = String.format(Locale.US, "%,.1f 億", v / 1e8)

    // ------------------------------------------------------------------ 🌐 市場趨勢分析師
    class MarketExtras(val beta: Double?, val returns: Map<String, Double?>, val relative: Map<String, Double?>)

    fun market(
        bars: List<Bar>,
        indexBars: List<Bar>?,
        indexName: String,
        sentiment: Sentiment?
    ): Pair<AgentReport, MarketExtras?> {
        if (bars.size < 64) return noData(Role.MARKET, "日線資料不足 64 根，無法判斷多期間報酬。") to null
        val adj = DoubleArray(bars.size) { bars[it].adjClose }
        fun ret(a: DoubleArray, n: Int): Double? = if (a.size > n) a[a.size - 1] / a[a.size - 1 - n] - 1.0 else null

        val periods = listOf("1 個月" to 21, "3 個月" to 63, "6 個月" to 126, "1 年" to 252)
        val scales = mapOf(21 to 0.15, 63 to 0.25, 126 to 0.35, 252 to 0.50)
        val rets = periods.associate { (name, n) -> name to ret(adj, n) }
        val f = ArrayList<String>()
        val retScores = periods.mapNotNull { (_, n) ->
            val r = ret(adj, n) ?: return@mapNotNull null
            clamp(50.0 + r / scales.getValue(n) * 50.0, 0.0, 100.0)
        }
        periods.forEach { (name, n) ->
            val r = ret(adj, n)
            if (r != null) f.add("近 $name 報酬 ${pctS(r)}")
        }
        val retScore = if (retScores.isEmpty()) null else retScores.average()

        // 相對大盤
        var relScore: Double? = null
        var beta: Double? = null
        val relMap = HashMap<String, Double?>()
        if (indexBars != null) {
            val (s, m) = Indicators.align(bars, indexBars)
            if (s.size > 64) {
                val sa = DoubleArray(s.size) { s[it].adjClose }
                val ma = DoubleArray(m.size) { m[it].adjClose }
                val rel3 = ret(sa, 63)?.let { a -> ret(ma, 63)?.let { b -> a - b } }
                val rel12 = ret(sa, 252)?.let { a -> ret(ma, 252)?.let { b -> a - b } }
                relMap["3 個月"] = rel3
                relMap["1 年"] = rel12
                val parts = ArrayList<Double>()
                if (rel3 != null) {
                    parts.add(clamp(50.0 + rel3 / 0.20 * 50.0, 0.0, 100.0))
                    f.add("近 3 個月${if (rel3 >= 0) "強於" else "弱於"}$indexName ${pctS(abs(rel3))}")
                }
                if (rel12 != null) {
                    parts.add(clamp(50.0 + rel12 / 0.30 * 50.0, 0.0, 100.0))
                    f.add("近 1 年${if (rel12 >= 0) "強於" else "弱於"}$indexName ${pctS(abs(rel12))}")
                }
                if (parts.isNotEmpty()) relScore = parts.average()
                // Beta：最近 252 個共同交易日的日報酬
                val k = min(sa.size, 253)
                val rs = Ta.returns(sa.copyOfRange(sa.size - k, sa.size))
                val rm = Ta.returns(ma.copyOfRange(ma.size - k, ma.size))
                if (rs.size >= 60) {
                    val ms = Ta.mean(rs)
                    val mm = Ta.mean(rm)
                    var cov = 0.0
                    var vr = 0.0
                    for (j in rs.indices) {
                        cov += (rs[j] - ms) * (rm[j] - mm)
                        vr += (rm[j] - mm) * (rm[j] - mm)
                    }
                    if (vr > 1e-14) {
                        beta = cov / vr
                        f.add(
                            "Beta ${f2(beta)}" + when {
                                beta >= 1.2 -> "，波動明顯大於大盤"
                                beta >= 0.8 -> "，與大盤波動相近"
                                else -> "，波動小於大盤"
                            }
                        )
                    }
                }
            } else f.add("與${indexName}重疊的資料不足，無法比較相對強弱與 Beta")
        } else f.add("沒有取得${indexName}資料，無法比較相對強弱與 Beta")

        // 新聞情緒
        var newsScore: Double? = null
        if (sentiment != null) {
            newsScore = clamp(50.0 + sentiment.score * 50.0, 0.0, 100.0)
            f.add(
                "新聞情緒：${News.label(sentiment)}（${sentiment.total} 則標題中 ${sentiment.classified} 則有明確多空字眼，" +
                    "偏多 ${sentiment.positive}、偏空 ${sentiment.negative}）"
            )
        } else f.add("沒有取得新聞標題，新聞情緒不納入")

        // 加權：多期間報酬 40、相對大盤 35、新聞 25；缺項時重新分配
        val parts = listOfNotNull(
            retScore?.let { it to 0.40 },
            relScore?.let { it to 0.35 },
            newsScore?.let { it to 0.25 }
        )
        val metrics = ArrayList<Metric>()
        periods.forEach { (name, _) -> metrics.add(Metric("近 $name 報酬", pctS(rets[name]))) }
        metrics.add(Metric("相對$indexName（3 個月）", pctS(relMap["3 個月"])))
        metrics.add(Metric("相對$indexName（1 年）", pctS(relMap["1 年"])))
        metrics.add(Metric("Beta", if (beta != null) f2(beta) else "—"))
        metrics.add(Metric("新聞情緒", if (sentiment != null) "${f2(sentiment.score)}（${News.label(sentiment)}）" else "—"))
        if (parts.isEmpty()) return AgentReport(Role.MARKET, null, "資料不足", metrics, f) to null
        val wsum = parts.sumOf { it.second }
        val score = clamp(parts.sumOf { it.first * it.second } / wsum, 0.0, 100.0)
        metrics.add(Metric("分項得分", "報酬 ${f2(retScore ?: Double.NaN)}、相對大盤 ${f2(relScore ?: Double.NaN)}、新聞 ${f2(newsScore ?: Double.NaN)}"))
        return AgentReport(Role.MARKET, score, verdict(Role.MARKET, score), metrics, f) to MarketExtras(beta, rets, relMap)
    }

    // ------------------------------------------------------------------ 🛡️ 風險分析師
    class RiskStats(val vol: Double, val mdd1y: Double, val mddAll: Double, val var95: Double, val sharpe: Double)

    fun risk(bars: List<Bar>, rfPct: Double): Pair<AgentReport, RiskStats?> {
        if (bars.size < 60) return noData(Role.RISK, "日線資料不足 60 根，無法評估風險。") to null
        val adj = DoubleArray(bars.size) { bars[it].adjClose }
        val k = min(adj.size, 253)
        val win = adj.copyOfRange(adj.size - k, adj.size)
        val r = Ta.returns(win)
        val vol = Ta.std(r) * sqrt(252.0)
        val mdd1y = Ta.drawdown(win).min()
        val mddAll = Ta.drawdown(adj).min()
        val var95 = -Ta.percentile(r, 0.05)
        val annRet = Ta.mean(r) * 252.0
        val sharpe = if (vol > 1e-12) (annRet - rfPct / 100.0) / vol else Double.NaN

        val volScore = when { vol < 0.15 -> 100.0; vol < 0.25 -> 80.0; vol < 0.35 -> 60.0; vol < 0.50 -> 35.0; else -> 15.0 }
        val mddScore = when { mdd1y > -0.10 -> 100.0; mdd1y > -0.20 -> 80.0; mdd1y > -0.30 -> 60.0; mdd1y > -0.45 -> 35.0; else -> 15.0 }
        val varScore = when { var95 < 0.015 -> 100.0; var95 < 0.025 -> 80.0; var95 < 0.035 -> 60.0; var95 < 0.05 -> 35.0; else -> 15.0 }
        val sharpeScore = when {
            !sharpe.isFinite() -> 40.0
            sharpe >= 1.5 -> 100.0
            sharpe >= 1.0 -> 80.0
            sharpe >= 0.5 -> 60.0
            sharpe >= 0.0 -> 40.0
            else -> 15.0
        }
        val score = (volScore + mddScore + varScore + sharpeScore) / 4.0
        val f = listOf(
            "年化波動率 ${pctS(vol)}" + when { vol < 0.25 -> "，波動不大"; vol < 0.35 -> "，波動適中"; else -> "，波動偏大" },
            "近 1 年最大跌幅 ${pctS(mdd1y)}（上市以來 ${pctS(mddAll)}）",
            "單日 95% VaR ${pctS(var95)}：歷史上 95% 的交易日單日虧損不會超過此幅度",
            "夏普值 ${f2(sharpe)}（無風險利率 ${f2(rfPct)}%）" + when {
                !sharpe.isFinite() -> ""
                sharpe >= 1.0 -> "，風險報酬比佳"
                sharpe >= 0.0 -> "，風險報酬比普通"
                else -> "，報酬不及無風險利率"
            }
        )
        val metrics = listOf(
            Metric("年化波動率（1 年）", pctS(vol)),
            Metric("最大跌幅（1 年）", pctS(mdd1y)),
            Metric("最大跌幅（全期間）", pctS(mddAll)),
            Metric("單日 VaR 95%（歷史法）", pctS(var95)),
            Metric("夏普值（1 年）", f2(sharpe)),
            Metric("分項得分", "波動 ${f2(volScore)}、跌幅 ${f2(mddScore)}、VaR ${f2(varScore)}、夏普 ${f2(sharpeScore)}（各 100 分平均）")
        )
        return AgentReport(Role.RISK, score, verdict(Role.RISK, score), metrics, f) to
            RiskStats(vol, mdd1y, mddAll, var95, sharpe)
    }

    // ------------------------------------------------------------------ 🧠 投資組長
    /** 依「持有期間 × 風險承受度」計算各分析師權重（缺資料者排除後重新正規化）。 */
    fun weights(settings: TeamSettings, agents: Map<Role, AgentReport>): Map<Role, Double> {
        val h = settings.horizon
        val p = settings.risk
        val raw = mapOf(
            Role.TECH to h.tech * p.techMul,
            Role.FUND to h.fund * p.fundMul,
            Role.MARKET to h.market * p.marketMul,
            Role.RISK to h.risk * p.riskMul
        ).filter { agents[it.key]?.score != null }
        val total = raw.values.sum()
        return if (total <= 0) emptyMap() else raw.mapValues { it.value / total }
    }

    fun lead(
        price: Double,
        bars: List<Bar>,
        ts: TechSeries,
        agents: Map<Role, AgentReport>,
        riskStats: RiskStats?,
        settings: TeamSettings
    ): LeadResult {
        val w = weights(settings, agents)
        val f = ArrayList<String>()
        val score = if (w.isEmpty()) 50.0 else w.entries.sumOf { (agents.getValue(it.key).score ?: 50.0) * it.value }
        var rating = Rating.fromScore(score)
        f.add(
            "加權方式（${settings.horizon.label} × ${settings.risk.label}）：" +
                w.entries.joinToString("、") { "${it.key.label.removeSuffix("分析師")} ${f2(it.value * 100)}%" } +
                "。綜合分數 ${f2(score)}。"
        )
        val missing = agents.filter { it.value.score == null }.keys
        if (missing.isNotEmpty()) f.add("資料不足、未納入加權：" + missing.joinToString("、") { it.label })

        val riskScore = agents[Role.RISK]?.score
        if (settings.risk == RiskProfile.CONSERVATIVE && riskScore != null && riskScore < 40 && rating.ordinal < Rating.HOLD.ordinal) {
            rating = Rating.HOLD
            f.add("保守型投資人：風險分數僅 ${f2(riskScore)}，評級上限降為「持有」。")
        }

        // 部位上限 = 評級基準 × 風險承受度倍率 × 波動調整（波動越大越低）
        val volAdj = if (riskStats != null && riskStats.vol > 0) clamp(0.25 / riskStats.vol, 0.4, 1.2) else 1.0
        val cap = clamp(rating.basePosPct * settings.risk.posMult * volAdj, 0.0, 25.0)
        f.add(
            "部位上限 ${f2(cap)}%（評級基準 ${f2(rating.basePosPct)}% × 風險倍率 ${f2(settings.risk.posMult)} × 波動調整 ${f2(volAdj)}），指占整體投資組合的比例。"
        )

        // 參考價位
        val i = bars.size - 1
        val n = min(settings.horizon.lookback, bars.size)
        val recent = bars.subList(bars.size - n, bars.size)
        val recentLow = recent.minOf { it.low }
        val recentHigh = recent.maxOf { it.high }
        val atr = ts.atr[i]
        val below = listOf(recentLow, ts.ma60[i], ts.ma20[i], ts.bbLow[i]).filter { it.isFinite() && it < price }
        val support = below.maxOrNull() ?: recentLow
        val above = listOf(recentHigh, ts.bbUp[i], ts.ma120[i]).filter { it.isFinite() && it > price }
        val resistance = above.minOrNull() ?: if (atr.isFinite()) price + 2 * atr else recentHigh
        var stop = if (atr.isFinite()) max(price - settings.risk.atrK * atr, support - 0.5 * atr) else support * 0.97
        stop = min(stop, price * 0.99)
        val rr = if (resistance > price && price > stop) (resistance - price) / (price - stop) else null
        f.add(
            "支撐 ${f2(support)}（${settings.horizon.lookback} 日低點與均線、布林下軌中最近者）、" +
                "壓力 ${f2(resistance)}、停損 ${f2(stop)}（取「現價 − ${f2(settings.risk.atrK)}×ATR」與「支撐 − 0.5×ATR」中較高者，即較緊的停損）" +
                (if (rr != null) "；風險報酬比 ${f2(rr)}" else "")
        )
        return LeadResult(score, rating, cap, support, resistance, stop, rr, w, f)
    }
}
