package com.example.invcalc.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StockLogicTest {

    private fun bar(day: Long, close: Double, vol: Double = 100.0, adj: Double = close) =
        Bar(day, close, adj, vol)

    @Test
    fun dateUtil() {
        assertEquals("1970-01-01", DateUtil.format(0))
        assertEquals("2022-01-08", DateUtil.format(19000))
        assertEquals("2024-02-29", DateUtil.format(19782))
        assertEquals(3, DateUtil.dayOfWeek(0)) // 1970-01-01 是星期四
        assertEquals(0, DateUtil.dayOfWeek(4)) // 1970-01-05 是星期一
        assertEquals(6, DateUtil.dayOfWeek(10)) // 星期日
    }

    @Test
    fun symbolNormalize() {
        assertEquals("2330.TW", SymbolUtil.normalize(" 2330 "))
        assertEquals("AAPL", SymbolUtil.normalize("aapl"))
        assertEquals("6488.TWO", SymbolUtil.normalize("6488.two"))
    }

    @Test
    fun resampleWeekAndMonth() {
        // 1970-01-05(一) 起連續 14 天 → 兩週
        val daily = (4L..17L).map { bar(it, it.toDouble(), 10.0) }
        val weekly = Resample.toPeriod(daily, Period.WEEK)
        assertEquals(2, weekly.size)
        assertEquals(10.0, weekly[0].close, 1e-9) // 第一週最後一天（日 10）
        assertEquals(70.0, weekly[0].volume, 1e-9)
        assertEquals(17.0, weekly[1].close, 1e-9)

        // 跨月：1970-01-30、01-31、02-01
        val m = Resample.toPeriod(listOf(bar(29, 1.0), bar(30, 2.0), bar(31, 3.0)), Period.MONTH)
        assertEquals(2, m.size)
        assertEquals(2.0, m[0].close, 1e-9)
        assertEquals(3.0, m[1].close, 1e-9)
    }

    @Test
    fun bollingerConstantSeries() {
        val bars = (0L until 30L).map { bar(it, 50.0) }
        val r = Indicators.bollinger(bars, 20, 2.0)!!
        assertEquals(11, r.close.size)
        assertEquals(50.0, r.mid.last(), 1e-9)
        assertEquals(50.0, r.upper.last(), 1e-9)
        assertEquals(50.0, r.lower.last(), 1e-9)
        assertNull(Indicators.bollinger(bars.take(10), 20, 2.0))
    }

    @Test
    fun bollingerKnownBand() {
        // 20 個點：10 個 90、10 個 110 → 平均 100、母體標準差 10 → 上下軌 120 / 80
        val closes = List(10) { 90.0 } + List(10) { 110.0 }
        val bars = closes.mapIndexed { i, c -> bar(i.toLong(), c) }
        val r = Indicators.bollinger(bars, 20, 2.0)!!
        assertEquals(100.0, r.mid[0], 1e-9)
        assertEquals(120.0, r.upper[0], 1e-9)
        assertEquals(80.0, r.lower[0], 1e-9)
    }

    @Test
    fun sharpeInsufficientAndConstant() {
        val few = (0L until 10L).map { bar(it, 100.0 + it) }
        assertNull(Indicators.sharpe(few, Period.DAY, 0.0)) // 需 60 筆報酬
        // 每期固定報酬 → 標準差為 0 → NaN
        val flat = (0L until 80L).map { bar(it, 100.0 * Math.pow(1.001, it.toDouble())) }
        val r = Indicators.sharpe(flat, Period.DAY, 0.0)!!
        assertTrue(r.overall.isNaN())
    }

    @Test
    fun sharpeValue() {
        // 報酬交替 +2% / 0% → 平均 1%、樣本標準差 ≈ 1.0152%
        val rets = List(80) { if (it % 2 == 0) 0.02 else 0.0 }
        var p = 100.0
        val bars = ArrayList<Bar>()
        bars.add(bar(0, p))
        rets.forEachIndexed { i, r -> p *= 1 + r; bars.add(bar(i + 1L, p)) }
        val res = Indicators.sharpe(bars, Period.MONTH, 0.0)!! // 年化因子 sqrt(12)、視窗 12
        val sd = Math.sqrt(80 * 0.0001 / 79)
        assertEquals(0.01 / sd * Math.sqrt(12.0), res.overall, 1e-6)
        assertEquals(80 - 12 + 1, res.rolling.size)
    }

    @Test
    fun volumeSpike() {
        val base = (0L until 20L).map { bar(it, 10.0, 100.0) }
        val spike = Indicators.volumeSpike(base + bar(20, 10.0, 250.0), 20, 2.0)!!
        assertTrue(spike.isSpike)
        assertEquals(2.5, spike.ratio, 1e-9)
        val exact = Indicators.volumeSpike(base + bar(20, 10.0, 200.0), 20, 2.0)!!
        assertTrue(exact.isSpike) // ≥ 2 倍含等於
        val normal = Indicators.volumeSpike(base + bar(20, 10.0, 150.0), 20, 2.0)!!
        assertFalse(normal.isSpike)
        assertNull(Indicators.volumeSpike(base, 20, 2.0)) // 不足 21 筆
    }

    private fun weekdays(count: Int, startDay: Long = 19000): List<Long> {
        val out = ArrayList<Long>()
        var d = startDay
        while (out.size < count) {
            if (DateUtil.dayOfWeek(d) < 5) out.add(d)
            d++
        }
        return out
    }

    @Test
    fun prophetLinearTrend() {
        val days = weekdays(400)
        val rnd = Random(1)
        val ys = days.indices.map { 100 + 0.1 * it + rnd.nextGaussian() * 0.5 }
        val r = Prophet.fit(days, ys, ProphetParams(periods = 20))
        assertEquals(20, r.yhat.size)
        // 預期趨勢約 100 + 0.1 * (399 + 20) = 141.9
        assertEquals(141.9, r.yhat.last(), 2.0)
        for (i in r.yhat.indices) {
            assertTrue(r.lower[i] < r.yhat[i] && r.yhat[i] < r.upper[i])
        }
        // 擬合誤差應接近雜訊大小 0.5
        var rss = 0.0
        for (i in ys.indices) rss += Math.pow(ys[i] - r.histFit[i], 2.0)
        assertTrue(Math.sqrt(rss / ys.size) < 0.8)
        // 預測日皆為週一至週五，且接在歷史最後一日之後
        assertTrue(r.futureDays.first() > days.last())
        assertTrue(r.futureDays.all { DateUtil.dayOfWeek(it) < 5 })
    }

    @Test
    fun prophetWiderIntervalIsWider() {
        val days = weekdays(300)
        val rnd = Random(7)
        val ys = days.indices.map { 50 + 0.05 * it + rnd.nextGaussian() }
        val narrow = Prophet.fit(days, ys, ProphetParams(periods = 10, intervalWidth = 0.5))
        val wide = Prophet.fit(days, ys, ProphetParams(periods = 10, intervalWidth = 0.95))
        val wn = narrow.upper.last() - narrow.lower.last()
        val ww = wide.upper.last() - wide.lower.last()
        assertTrue(ww > wn)
    }

    @Test
    fun prophetNeedsEnoughData() {
        val days = weekdays(10)
        var failed = false
        try {
            Prophet.fit(days, days.map { 1.0 }, ProphetParams())
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun yahooParse() {
        val json = """
        {"chart":{"result":[{"meta":{"symbol":"2330.TW","longName":"Taiwan Semiconductor","gmtoffset":28800},
        "timestamp":[1700000000,1700086400,1700172800],
        "indicators":{"quote":[{"close":[100.0,null,102.0],"volume":[1000,2000,null]}],
        "adjclose":[{"adjclose":[99.0,99.5,101.0]}]}}],"error":null}}
        """.trimIndent()
        val p = YahooApi.parse(json)
        assertEquals("Taiwan Semiconductor", p.name)
        assertEquals(2, p.bars.size) // close 為 null 的略過
        assertEquals(100.0, p.bars[0].close, 1e-9)
        assertEquals(99.0, p.bars[0].adjClose, 1e-9)
        assertEquals(1000.0, p.bars[0].volume, 1e-9)
        assertEquals(0.0, p.bars[1].volume, 1e-9) // volume 為 null → 0
        assertNotNull(p.bars[1])
    }

    @Test
    fun yahooParseError() {
        val json = """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}"""
        var msg: String? = null
        try {
            YahooApi.parse(json)
        } catch (e: IllegalArgumentException) {
            msg = e.message
        }
        assertEquals("No data found, symbol may be delisted", msg)
    }

    // ---------------------------------------------------------------- 新增功能

    @Test
    fun dateParse() {
        assertEquals(0L, DateUtil.parse("1970-01-01"))
        assertEquals(19000L, DateUtil.parse("2022-01-08"))
        assertEquals(19782L, DateUtil.parse("2024-02-29"))
        assertNull(DateUtil.parse("not-a-date"))
        // 與 format 互為反運算
        for (d in listOf(-1000L, 0L, 12345L, 20000L)) assertEquals(d, DateUtil.parse(DateUtil.format(d)))
    }

    @Test
    fun symbolTaiwanCodes() {
        assertEquals("00679B.TW", SymbolUtil.normalize("00679b"))
        assertEquals("0050.TW", SymbolUtil.normalize("0050"))
        assertEquals("AAPL", SymbolUtil.normalize("AAPL"))
        assertTrue(SymbolUtil.isTaiwanSymbol("6488.TWO"))
        assertFalse(SymbolUtil.isTaiwanSymbol("AAPL"))
        assertTrue(SymbolUtil.hasCjk("台積電"))
        assertFalse(SymbolUtil.hasCjk("Taiwan Semiconductor"))
    }

    @Test
    fun sortinoValue() {
        // 報酬交替 +2% / -1%：目標 0 → 平均 0.5%、下檔偏差 sqrt(0.5 * 0.01%) 
        var p = 100.0
        val bars = ArrayList<Bar>()
        bars.add(bar(0, p))
        for (i in 0 until 80) {
            p *= if (i % 2 == 0) 1.02 else 0.99
            bars.add(bar(i + 1L, p))
        }
        val res = Indicators.sortino(bars, Period.MONTH, 0.0)!!
        val expected = 0.005 / Math.sqrt(0.5 * 0.0001) * Math.sqrt(12.0)
        assertEquals(expected, res.overall, 1e-6)
        assertEquals(80 - 12 + 1, res.rolling.size)
        // 完全沒有下跌 → 下檔偏差為 0 → NaN
        val up = (0L until 80L).map { bar(it, 100.0 * Math.pow(1.01, it.toDouble())) }
        assertTrue(Indicators.sortino(up, Period.MONTH, 0.0)!!.overall.isNaN())
    }

    @Test
    fun treynorBetaTwo() {
        // 股票報酬恰為大盤的 2 倍 → Beta = 2
        val rnd = Random(3)
        var pm = 1000.0
        var ps = 50.0
        val idx = ArrayList<Bar>()
        val stk = ArrayList<Bar>()
        idx.add(bar(0, pm)); stk.add(bar(0, ps))
        val rsList = ArrayList<Double>()
        for (i in 1..100) {
            val r = (rnd.nextDouble() - 0.5) * 0.02
            pm *= 1 + r
            ps *= 1 + 2 * r
            rsList.add(2 * r)
            idx.add(bar(i.toLong(), pm)); stk.add(bar(i.toLong(), ps))
        }
        val res = Indicators.treynor(stk, idx, Period.DAY, 0.0)!!
        assertEquals(2.0, res.overallBeta, 1e-6)
        val expected = rsList.average() * 252 / 2.0 * 100
        assertEquals(expected, res.overallTreynorPct, 1e-6)
        assertEquals(100 - 60 + 1, res.treynorPct.size)
        assertTrue(res.beta.all { Math.abs(it - 2.0) < 1e-6 })
    }

    @Test
    fun alignByDate() {
        val a = listOf(bar(1, 1.0), bar(2, 2.0), bar(4, 4.0), bar(5, 5.0))
        val b = listOf(bar(2, 20.0), bar(3, 30.0), bar(5, 50.0))
        val (x, y) = Indicators.align(a, b)
        assertEquals(listOf(2L, 5L), x.map { it.epochDay })
        assertEquals(listOf(20.0, 50.0), y.map { it.close })
    }

    @Test
    fun capeUsesOnlyPublishedEps() {
        val d0 = 19000L
        val bars = listOf(bar(d0 + 10, 100.0), bar(d0 + 100, 100.0), bar(d0 + 500, 100.0))
        val eps = listOf(Indicators.EpsPoint(d0, 5.0), Indicators.EpsPoint(d0 + 365, 10.0))
        val r = Indicators.cape(bars, eps, lagDays = 90, maxYears = 10)!!
        assertEquals(2, r.cape.size) // 第一個日期尚無已公布 EPS
        assertEquals(20.0, r.cape[0], 1e-9)       // 100 / 5
        assertEquals(100.0 / 7.5, r.cape[1], 1e-9) // 兩年平均 7.5
        assertEquals(2, r.yearsUsed)
        assertEquals(7.5, r.avgEps, 1e-9)
        assertEquals((20.0 + 100.0 / 7.5) / 2, r.median, 1e-9)
        // EPS 平均為負 → 不計算
        assertNull(Indicators.cape(bars, listOf(Indicators.EpsPoint(d0, -3.0)), 90, 10))
        assertNull(Indicators.cape(bars, emptyList(), 90, 10))
    }

    @Test
    fun volumeSeriesFlagsSpikes() {
        val bars = (0L until 25L).map { bar(it, 10.0, if (it == 22L) 300.0 else 100.0) }
        val v = Indicators.volumeSeries(bars, 20, 2.0)!!
        assertEquals(5, v.dates.size)
        assertEquals(200.0, v.threshold[0], 1e-9)
        assertEquals(listOf(false, false, true, false, false), v.spikeFlags)
        assertEquals(110.0, v.average[3], 1e-9) // 視窗含 300 的那天：(19*100+300)/20
    }

    @Test
    fun twNamesParse() {
        val twse = """[{"Date":"1141125","Code":"2330","Name":"台積電"},{"Date":"1141125","Code":"0050","Name":"元大台灣50"}]"""
        val m1 = TwNames.parseTable(twse)
        assertEquals("台積電", m1["2330"])
        assertEquals("元大台灣50", m1["0050"])

        // 公司基本資料：簡稱優先於全名
        val basic = """[{"出表日期":"1141125","公司代號":"2330","公司名稱":"台灣積體電路製造股份有限公司","公司簡稱":"台積電"}]"""
        assertEquals("台積電", TwNames.parseTable(basic)["2330"])

        val tpex = """[{"Date":"1141125","SecuritiesCompanyCode":"6488","CompanyName":"環球晶"}]"""
        assertEquals("環球晶", TwNames.parseTable(tpex)["6488"])

        assertTrue(TwNames.parseTable("[]").isEmpty())
        assertTrue(TwNames.parseTable("""[{"foo":"bar"}]""").isEmpty())
    }

    @Test
    fun yahooParseEps() {
        val json = """
        {"timeseries":{"result":[
          {"meta":{"symbol":["AAPL"],"type":["annualDilutedEPS"]},"timestamp":[1,2],
           "annualDilutedEPS":[
             {"asOfDate":"2023-09-30","reportedValue":{"raw":6.13,"fmt":"6.13"}},
             {"asOfDate":"2022-09-30","reportedValue":{"raw":6.11,"fmt":"6.11"}}]},
          {"meta":{"symbol":["AAPL"],"type":["annualBasicEPS"]},"timestamp":[1],
           "annualBasicEPS":[{"asOfDate":"2023-09-30","reportedValue":{"raw":6.16}}]}
        ],"error":null}}
        """.trimIndent()
        val eps = YahooApi.parseEps(json)
        assertEquals(2, eps.size)
        assertEquals(DateUtil.parse("2022-09-30"), eps[0].asOfDay) // 依日期排序
        assertEquals(6.11, eps[0].eps, 1e-9)
        assertEquals(6.13, eps[1].eps, 1e-9)

        // 無稀釋 EPS 時退而使用基本 EPS
        val basicOnly = """{"timeseries":{"result":[{"meta":{"type":["annualBasicEPS"]},"annualBasicEPS":[{"asOfDate":"2023-09-30","reportedValue":{"raw":6.16}}]}],"error":null}}"""
        assertEquals(6.16, YahooApi.parseEps(basicOnly)[0].eps, 1e-9)
        assertTrue(YahooApi.parseEps("""{"timeseries":{"result":[],"error":null}}""").isEmpty())
    }

    @Test
    fun volumeFullAlignsWithBars() {
        val bars = (0L until 25L).map { bar(it, 10.0, if (it == 22L) 300.0 else 100.0) }
        val f = Indicators.volumeFull(bars, 20, 2.0)
        assertEquals(25, f.volume.size)
        assertTrue(f.average[19].isNaN() && f.threshold[19].isNaN())
        assertEquals(100.0, f.average[20], 1e-9)
        assertEquals(200.0, f.threshold[20], 1e-9)
        assertEquals(110.0, f.average[23], 1e-9) // 視窗 3..22 含 300
        // 與 volumeSeries 的數值一致
        val s = Indicators.volumeSeries(bars, 20, 2.0)!!
        for (i in s.dates.indices) assertEquals(s.average[i], f.average[i + 20], 1e-9)
    }

    @Test
    fun bollingerCustomWindowAndK() {
        val closes = List(5) { 90.0 } + List(5) { 110.0 }
        val bars = closes.mapIndexed { i, c -> bar(i.toLong(), c) }
        val r = Indicators.bollinger(bars, 10, 1.0)!! // 平均 100、母體標準差 10
        assertEquals(1, r.mid.size)
        assertEquals(110.0, r.upper[0], 1e-9)
        assertEquals(90.0, r.lower[0], 1e-9)
        val r3 = Indicators.bollinger(bars, 10, 3.0)!!
        assertEquals(130.0, r3.upper[0], 1e-9)
        // 視窗與日期對應：從第 window 根起
        val r5 = Indicators.bollinger(bars, 5, 2.0)!!
        assertEquals(6, r5.dates.size)
        assertEquals(4L, r5.dates.first())
    }
}
