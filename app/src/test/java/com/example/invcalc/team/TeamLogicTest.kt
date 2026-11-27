package com.example.invcalc.team

import com.example.invcalc.stock.Bar
import com.example.invcalc.stock.DateUtil
import com.example.invcalc.stock.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TeamLogicTest {

    // ---------------------------------------------------------------- 測試資料

    /** 幾何隨機漫步：每日報酬 = drift + vol * N(0,1)。 */
    private fun makeBars(n: Int, start: Double, drift: Double, vol: Double, seed: Long, volume: Double = 1000.0): List<Bar> {
        val rnd = Random(seed)
        var p = start
        val out = ArrayList<Bar>()
        for (i in 0 until n) {
            val r = drift + vol * rnd.nextGaussian()
            val prev = p
            p *= (1 + r)
            val hi = maxOf(prev, p) * 1.004
            val lo = minOf(prev, p) * 0.996
            out.add(Bar(19000L + i, p, p, volume, prev, hi, lo))
        }
        return out
    }

    /** 無雜訊的穩定趨勢。 */
    private fun trendBars(n: Int, start: Double, growth: Double): List<Bar> =
        (0 until n).map { i ->
            val c = start * Math.pow(1 + growth, i.toDouble())
            Bar(19000L + i, c, c, 1000.0, c, c * 1.005, c * 0.995)
        }

    private val settings = TeamSettings(Horizon.SWING, RiskProfile.MODERATE, 1.5)

    // ---------------------------------------------------------------- 技術指標

    @Test
    fun smaAndEma() {
        val sma = Ta.sma(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertTrue(sma[0].isNaN() && sma[1].isNaN())
        assertEquals(2.0, sma[2], 1e-12)
        assertEquals(4.0, sma[4], 1e-12)
        // EMA(3)：以前 3 筆平均 2 為起點，k = 0.5
        val ema = Ta.ema(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertEquals(2.0, ema[2], 1e-12)
        assertEquals(3.0, ema[3], 1e-12)
        assertEquals(4.0, ema[4], 1e-12)
    }

    @Test
    fun rsiExtremes() {
        val up = DoubleArray(40) { 100.0 + it }
        val down = DoubleArray(40) { 200.0 - it }
        val flat = DoubleArray(40) { 100.0 }
        assertEquals(100.0, Ta.rsi(up)[39], 1e-9)
        assertEquals(0.0, Ta.rsi(down)[39], 1e-9)
        assertEquals(50.0, Ta.rsi(flat)[39], 1e-9)
        assertTrue(Ta.rsi(up)[13].isNaN()) // 前 14 根無法計算
    }

    @Test
    fun macdOfConstantIsZero() {
        val (m, s, h) = Ta.macd(DoubleArray(80) { 50.0 })
        assertTrue(m[24].isNaN())
        assertEquals(0.0, m[79], 1e-9)
        assertEquals(0.0, s[79], 1e-9)
        assertEquals(0.0, h[79], 1e-9)
        // 上升趨勢：DIF > 0
        val (m2, _, _) = Ta.macd(DoubleArray(80) { 50.0 + it })
        assertTrue(m2[79] > 0)
    }

    @Test
    fun atrAndDrawdownAndPercentile() {
        val c = DoubleArray(30) { 100.0 }
        val atr = Ta.atr(DoubleArray(30) { 101.0 }, DoubleArray(30) { 99.0 }, c, 14)
        assertEquals(2.0, atr[29], 1e-9)
        assertTrue(atr[13].isNaN())
        val dd = Ta.drawdown(doubleArrayOf(100.0, 120.0, 90.0, 130.0))
        assertEquals(0.0, dd[1], 1e-12)
        assertEquals(-0.25, dd[2], 1e-12)
        assertEquals(0.0, dd[3], 1e-12)
        assertEquals(1.2, Ta.percentile(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), 0.05), 1e-12)
    }

    // ---------------------------------------------------------------- 新聞與財報解析

    @Test
    fun sentiment() {
        val items = listOf(
            NewsItem("TSMC shares surge after strong earnings beat", "x", 0),
            NewsItem("Chipmaker plunges on lawsuit and weak outlook", "x", 0),
            NewsItem("台積電法說會 看好 上修 全年成長", "x", 0),
            NewsItem("Company announces new board member", "x", 0)
        )
        val s = News.sentiment(items)!!
        assertEquals(4, s.total)
        assertEquals(3, s.classified)
        assertEquals(2, s.positive)
        assertEquals(1, s.negative)
        assertEquals(1.0 / 3.0, s.score, 1e-9) // (+1 −1 +1) / 3
        assertNull(News.sentiment(emptyList()))
        // 沒有多空字眼 → 分數 0、classified 0；「mission」「bank」不應誤判
        val neutral = News.sentiment(listOf(NewsItem("Mission to the bank opens", "x", 0)))!!
        assertEquals(0, neutral.classified)
        assertEquals(0.0, neutral.score, 1e-12)
    }

    @Test
    fun newsParse() {
        val json = """{"news":[{"title":"A surges","publisher":"P","providerPublishTime":1700000000},{"title":"","publisher":"Q"}],"quotes":[]}"""
        val items = News.parse(json)
        assertEquals(1, items.size)
        assertEquals("A surges", items[0].title)
        assertTrue(News.parse("""{"quotes":[]}""").isEmpty())
    }

    @Test
    fun fundamentalsParse() {
        val json = """
        {"timeseries":{"result":[
         {"meta":{"type":["annualTotalRevenue"]},"annualTotalRevenue":[
           {"asOfDate":"2022-12-31","reportedValue":{"raw":1000.0}},{"asOfDate":"2023-12-31","reportedValue":{"raw":1200.0}}]},
         {"meta":{"type":["annualNetIncome"]},"annualNetIncome":[
           {"asOfDate":"2023-12-31","reportedValue":{"raw":300.0}}]},
         {"meta":{"type":["annualDilutedEPS"]},"annualDilutedEPS":[
           {"asOfDate":"2023-12-31","reportedValue":{"raw":5.5}}]}
        ],"error":null}}
        """.trimIndent()
        val y = Fundamentals.parse(json)
        assertEquals(2, y.size)
        assertEquals(1000.0, y[0].revenue!!, 1e-9)
        assertNull(y[0].netIncome)
        assertEquals(1200.0, y[1].revenue!!, 1e-9)
        assertEquals(300.0, y[1].netIncome!!, 1e-9)
        assertEquals(5.5, y[1].eps!!, 1e-9)
        assertTrue(Fundamentals.parse("""{"timeseries":{"result":[],"error":null}}""").isEmpty())
    }

    // ---------------------------------------------------------------- 分析師

    @Test
    fun technicalUptrendVsDowntrend() {
        val up = trendBars(200, 100.0, 0.002)
        val down = trendBars(200, 100.0, -0.002)
        val su = Agents.technical(up, techSeries(up)).score!!
        val sd = Agents.technical(down, techSeries(down)).score!!
        assertTrue("上升趨勢應高分：$su", su >= 60)
        assertTrue("下降趨勢應低分：$sd", sd <= 40)
        assertTrue(su > sd)
        assertNull(Agents.technical(up.take(30), techSeries(up.take(30))).score)
    }

    @Test
    fun fundamentalScoring() {
        val strong = listOf(
            FundYear(100, 800e8, 150e8, 600e8, 100e8, 80e8, 6.0),
            FundYear(465, 1000e8, 250e8, 1000e8, 150e8, 150e8, 10.0) // 營收 +25%、淨利率 25%、ROE 25%
        )
        val s = Agents.fundamental(strong, 120.0)
        assertTrue("優質公司應高分：${s.score}", s.score!! >= 85)
        val weak = listOf(
            FundYear(100, 1000e8, -50e8, 200e8, 800e8, -30e8, -1.0),
            FundYear(465, 700e8, -120e8, 80e8, 900e8, -60e8, -3.0)
        )
        val w = Agents.fundamental(weak, 20.0)
        assertTrue("虧損公司應低分：${w.score}", w.score!! <= 20)
        // 無資料或可用指標太少 → null（不納入加權）
        assertNull(Agents.fundamental(emptyList(), 100.0).score)
        assertNull(Agents.fundamental(listOf(FundYear(1, null, null, null, null, 5e8, null)), 100.0).score)
    }

    @Test
    fun riskStatsMatchTheory() {
        val bars = makeBars(400, 100.0, 0.0005, 0.01, 5)
        val (rep, st) = Agents.risk(bars, 1.5)
        st!!
        // 日波動 1% → 年化約 15.9%；VaR95 約 1.645%
        assertTrue("年化波動 ${st.vol}", st.vol in 0.13..0.19)
        assertTrue("VaR ${st.var95}", st.var95 in 0.010..0.022)
        assertTrue(st.mdd1y <= 0.0 && st.mddAll <= st.mdd1y + 1e-12)
        assertTrue(rep.score!! in 0.0..100.0)
        // 高波動標的的風險分數應更低
        val wild = makeBars(400, 100.0, 0.0, 0.04, 6)
        assertTrue(Agents.risk(wild, 1.5).first.score!! < rep.score!!)
        assertNull(Agents.risk(bars.take(30), 1.5).first.score)
    }

    @Test
    fun marketBetaAndRelativeStrength() {
        // 個股報酬恰為大盤的 2 倍 → Beta = 2
        val rnd = Random(9)
        var pm = 1000.0
        var ps = 50.0
        val idx = ArrayList<Bar>()
        val stk = ArrayList<Bar>()
        for (i in 0 until 300) {
            val r = 0.0003 + (rnd.nextDouble() - 0.5) * 0.02
            pm *= 1 + r
            ps *= 1 + 2 * r
            idx.add(Bar(19000L + i, pm, pm, 1.0))
            stk.add(Bar(19000L + i, ps, ps, 1.0))
        }
        val (rep, extras) = Agents.market(stk, idx, "測試大盤", null)
        assertEquals(2.0, extras!!.beta!!, 1e-6)
        assertTrue(rep.score!! in 0.0..100.0)
        // 槓桿 2 倍且大盤上漲：近 3 個月應強於大盤
        assertTrue(extras.relative["3 個月"]!! > 0)
        // 沒有大盤與新聞也能算（只用報酬）
        val (rep2, ex2) = Agents.market(stk, null, "測試大盤", null)
        assertNotNull(rep2.score)
        assertNull(ex2!!.beta)
        assertNull(Agents.market(stk.take(30), idx, "x", null).first.score)
    }

    // ---------------------------------------------------------------- 組長

    private fun fake(role: Role, score: Double?) =
        AgentReport(role, score, "", emptyList(), emptyList())

    @Test
    fun leadWeightsAndScore() {
        val agents = mapOf(Role.TECH to fake(Role.TECH, 80.0), Role.FUND to fake(Role.FUND, 60.0),
            Role.MARKET to fake(Role.MARKET, 70.0), Role.RISK to fake(Role.RISK, 50.0))
        val w = Agents.weights(settings, agents)
        assertEquals(1.0, w.values.sum(), 1e-9)
        assertEquals(0.30, w.getValue(Role.TECH), 1e-9)
        // 波段 × 穩健：0.3*80 + 0.2*60 + 0.3*70 + 0.2*50 = 67
        val bars = trendBars(200, 100.0, 0.001)
        val ts = techSeries(bars)
        val lead = Agents.lead(bars.last().close, bars, ts, agents, null, settings)
        assertEquals(67.0, lead.score, 1e-9)
        assertEquals(Rating.BUY, lead.rating)
        assertEquals(10.0, lead.positionCapPct, 1e-9) // 買進基準 10% × 穩健 1.0 × 無波動資料 1.0

        // 缺資料的分析師排除後重新正規化
        val missing = agents + (Role.FUND to fake(Role.FUND, null))
        val w2 = Agents.weights(settings, missing)
        assertFalse(w2.containsKey(Role.FUND))
        assertEquals(1.0, w2.values.sum(), 1e-9)

        // 持有期間不同 → 權重不同：長期更重視財報
        val longW = Agents.weights(TeamSettings(Horizon.LONG, RiskProfile.MODERATE, 1.5), agents)
        assertTrue(longW.getValue(Role.FUND) > w.getValue(Role.FUND))
        // 保守型更重視風險
        val consW = Agents.weights(TeamSettings(Horizon.SWING, RiskProfile.CONSERVATIVE, 1.5), agents)
        assertTrue(consW.getValue(Role.RISK) > w.getValue(Role.RISK))
    }

    @Test
    fun leadConservativeCapAndRiskMultiplier() {
        val bars = trendBars(200, 100.0, 0.001)
        val ts = techSeries(bars)
        val agents = mapOf(Role.TECH to fake(Role.TECH, 90.0), Role.FUND to fake(Role.FUND, 90.0),
            Role.MARKET to fake(Role.MARKET, 90.0), Role.RISK to fake(Role.RISK, 30.0))
        val cons = Agents.lead(bars.last().close, bars, ts, agents, null, TeamSettings(Horizon.SWING, RiskProfile.CONSERVATIVE, 1.5))
        assertTrue(cons.score >= 60) // 本應為「買進」…
        assertEquals(Rating.HOLD, cons.rating) // …但保守型風險分數 < 40，上限為持有
        val mod = Agents.lead(bars.last().close, bars, ts, agents, null, settings)
        assertTrue(mod.rating.ordinal < Rating.HOLD.ordinal) // 穩健型不受此限
        // 同評級下，積極型部位上限高於保守型
        val a = Agents.lead(bars.last().close, bars, ts, mapOf(Role.TECH to fake(Role.TECH, 80.0)), null,
            TeamSettings(Horizon.SWING, RiskProfile.AGGRESSIVE, 1.5))
        val c = Agents.lead(bars.last().close, bars, ts, mapOf(Role.TECH to fake(Role.TECH, 80.0)), null,
            TeamSettings(Horizon.SWING, RiskProfile.CONSERVATIVE, 1.5))
        assertEquals(a.rating, c.rating)
        assertTrue(a.positionCapPct > c.positionCapPct)
    }

    @Test
    fun referencePricesAreConsistent() {
        for (seed in 1L..5L) {
            val bars = makeBars(300, 100.0, 0.0004, 0.015, seed)
            val ts = techSeries(bars)
            val price = bars.last().close
            val agents = mapOf(Role.TECH to fake(Role.TECH, 60.0))
            for (h in Horizon.values()) for (r in RiskProfile.values()) {
                val lead = Agents.lead(price, bars, ts, agents, null, TeamSettings(h, r, 1.5))
                assertTrue("停損應低於現價", lead.stopLoss < price)
                assertTrue("支撐應不高於現價", lead.support <= price)
                assertTrue("壓力應不低於現價", lead.resistance >= price)
                assertTrue(lead.score in 0.0..100.0 && lead.positionCapPct in 0.0..25.0)
            }
        }
    }

    // ---------------------------------------------------------------- 整合與報告

    private fun bundle(symbol: String, name: String, bars: List<Bar>, idx: List<Bar>?): StockBundle =
        TeamPipeline.analyze(
            Stock(symbol, name), bars, idx, "大盤", emptyList(), emptyList(), settings
        )

    @Test
    fun pipelineProducesAlignedCharts() {
        val bars = makeBars(700, 100.0, 0.0006, 0.012, 21)
        val idx = makeBars(700, 1000.0, 0.0003, 0.008, 22)
        val b = bundle("TEST", "測試股", bars, idx)
        val n = minOf(TeamPipeline.CHART_BARS, bars.size)
        val c = b.charts
        for (size in listOf(c.labels.size, c.candles.size, c.ma20.size, c.ma60.size, c.bbUp.size, c.bbLow.size,
            c.macd.size, c.signal.size, c.hist.size, c.rsi.size, c.stockRel.size, c.drawdown.size)) {
            assertEquals(n, size)
        }
        assertEquals(n, c.indexRel!!.size)
        assertEquals(100.0, c.stockRel[0], 1e-9)
        assertEquals(100.0, c.indexRel!![0], 1e-9)
        assertTrue(c.drawdown.all { it <= 0.0 })
        assertEquals(DateUtil.format(bars.last().epochDay), c.labels.last())
        assertNull(b.analysis.fund.score) // 沒有財報資料 → 不納入
        assertEquals(1.0, b.analysis.lead.weights.values.sum(), 1e-9)
        // 沒有大盤也能完整運作
        assertNull(bundle("T2", "", bars, null).charts.indexRel)
    }

    @Test
    fun reportMarkdownAndHtml() {
        val a = bundle("AAA", "強勢股", trendBars(300, 100.0, 0.002), null).analysis
        val b = bundle("BBB", "<b>弱勢&股</b>", trendBars(300, 100.0, -0.002), null).analysis
        assertTrue(a.lead.score > b.lead.score)
        a.aiText = "## 📈 技術分析師\n- 趨勢向上，**留意**過熱。"
        val md = ReportBuilder.markdown(listOf(b, a), settings, "整體來說 AAA 較佳。", "2026-11-27")
        assertTrue(md.startsWith("# AI 金融分析團隊投資報告"))
        assertTrue(md.contains("| 排名 | 標的 | 評級 |"))
        assertTrue(md.indexOf("強勢股") < md.indexOf("<b>弱勢&股</b>")) // 依分數排序（強勢股在前）
        assertTrue(md.contains("AI 白話解讀"))
        assertTrue(md.contains("整體來說 AAA 較佳。"))
        assertTrue(md.contains(ReportBuilder.DISCLAIMER))

        val html = ReportBuilder.html(listOf(b, a), settings, null, "2026-11-27")
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("&lt;b&gt;弱勢&amp;股&lt;/b&gt;"))
        assertFalse(html.contains("<b>弱勢"))
        assertTrue(html.contains("<strong>留意</strong>"))
        assertTrue(html.contains("<li>趨勢向上"))
        assertTrue(html.endsWith("</html>"))
    }

    @Test
    fun mdToHtmlBasics() {
        val html = ReportBuilder.mdToHtml("## 標題\n- A\n- B\n\n段落 **粗** <x>")
        assertEquals("<h3>標題</h3><ul><li>A</li><li>B</li></ul><p>段落 <strong>粗</strong> &lt;x&gt;</p>", html)
    }

    @Test
    fun factsForAiContainOnlyComputedNumbers() {
        val a = bundle("AAA", "強勢股", trendBars(300, 100.0, 0.002), null).analysis
        val facts = ReportBuilder.stockFacts(a, settings)
        assertTrue(facts.contains("評級 ${a.lead.rating.label}"))
        assertTrue(facts.contains("支撐 ${f2(a.lead.support)}"))
        assertTrue(facts.contains("【技術分析師】"))
        assertTrue(facts.contains("資料不足")) // 財報分析師無資料
        assertTrue(ReportBuilder.rankingFacts(listOf(a), settings).contains("1. 強勢股（AAA）"))
    }

    // ---------------------------------------------------------------- Claude API

    @Test
    fun claudeRequestAndResponse() {
        val body = ClaudeClient.buildRequest("claude-sonnet-5-5", "系統提示", "使用者內容", 1234)
        val o = org.json.JSONObject(body)
        assertEquals("claude-sonnet-5-5", o.getString("model"))
        assertEquals(1234, o.getInt("max_tokens"))
        assertEquals("系統提示", o.getString("system"))
        assertEquals("user", o.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("使用者內容", o.getJSONArray("messages").getJSONObject(0).getString("content"))

        val ok = """{"content":[{"type":"text","text":"你好"},{"type":"text","text":"世界"}],"stop_reason":"end_turn"}"""
        assertEquals("你好世界", ClaudeClient.parseResponse(ok))

        val err = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        var msg: String? = null
        try { ClaudeClient.parseResponse(err) } catch (e: IllegalStateException) { msg = e.message }
        assertEquals("invalid x-api-key", msg)

        var empty = false
        try { ClaudeClient.parseResponse("""{"content":[]}""") } catch (e: IllegalStateException) { empty = true }
        assertTrue(empty)
        // 系統提示明確要求 AI 不得改動數字
        assertTrue(ClaudeClient.SYSTEM_STOCK.contains("不得重新計算"))
    }

    // ---------------------------------------------------------------- PDF 文字

    @Test
    fun pdfTextFromMarkdown() {
        val md = """
            # AI 金融分析團隊投資報告
            - 產生日期：2026-11-27
            ## 排名比較
            | 排名 | 標的 | 評級 |
            |---|---|---|
            | 1 | 台積電（2330.TW） | 買進 |
            ### 📈 技術分析師　78.0/100（偏多）
            - 發現：收盤價在 **20 日均線**之上
            ---
            免責聲明
        """.trimIndent()
        val lines = PdfText.fromMarkdown(md)
        assertEquals(PdfStyle.H1, lines[0].style)
        assertEquals("AI 金融分析團隊投資報告", lines[0].text)
        assertEquals("• 產生日期：2026-11-27", lines[1].text)
        assertEquals(PdfStyle.BULLET, lines[1].style)
        assertEquals(PdfStyle.H2, lines[2].style)
        assertEquals("排名  ｜  標的  ｜  評級", lines[3].text) // 表頭列保留
        assertEquals("1  ｜  台積電（2330.TW）  ｜  買進", lines[4].text) // 分隔列 |---| 被略過
        assertEquals("技術分析師　78.0/100（偏多）", lines[5].text) // 表情符號被移除
        assertEquals("• 發現：收盤價在 20 日均線之上", lines[6].text) // 粗體標記被移除
        assertEquals("免責聲明", lines[7].text)
        assertEquals(8, lines.size)
    }

    @Test
    fun stripEmojiKeepsChinese() {
        assertEquals("風險分析師", PdfText.stripEmoji("🛡️風險分析師"))
        assertEquals("投資組長 (1)", PdfText.stripEmoji("🧠投資組長 (1)"))
        assertEquals("台積電", PdfText.stripEmoji("台積電"))
    }

    @Test
    fun fullReportConvertsToPdfLines() {
        val a = bundle("AAA", "強勢股", trendBars(300, 100.0, 0.002), null).analysis
        val md = ReportBuilder.markdown(listOf(a), settings, null, "2026-11-27")
        val lines = PdfText.fromMarkdown(md)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.none { it.text.contains("**") || it.text.contains("|---") })
        assertTrue(lines.any { it.style == PdfStyle.H1 && it.text.startsWith("AI 金融分析團隊") })
        assertTrue(lines.any { it.text.contains("強勢股") })
        assertTrue(lines.last().text.contains("不構成投資建議"))
    }

    @Test
    fun recipientsParsing() {
        assertEquals(listOf("a@b.com", "c@d.org"), Recipients.parse("a@b.com, c@d.org"))
        assertEquals(listOf("a@b.com", "c@d.org"), Recipients.parse("a@b.com；c@d.org\na@b.com")) // 去除重複
        assertTrue(Recipients.parse("沒有信箱, abc, @x, y@").isEmpty())
        assertTrue(Recipients.parse("").isEmpty())
        assertEquals("報告 2026-11-27", Recipients.subject("報告 {日期}", "2026-11-27", "預設"))
        assertEquals("預設 2026-11-27", Recipients.subject("", "2026-11-27", "預設 {日期}"))
    }
}
