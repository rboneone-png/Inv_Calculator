package com.example.invcalc.team

import com.example.invcalc.stock.Bar
import com.example.invcalc.stock.DateUtil
import com.example.invcalc.stock.Stock

/** 把資料餵給五位分析師，產出一檔股票的完整分析與圖表資料。 */
object TeamPipeline {
    const val CHART_BARS = 500

    fun analyze(
        stock: Stock,
        bars: List<Bar>,
        indexBars: List<Bar>?,
        indexName: String,
        years: List<FundYear>,
        news: List<NewsItem>,
        settings: TeamSettings
    ): StockBundle {
        require(bars.size >= 2) { "價格資料不足" }
        val ts = techSeries(bars)
        val price = bars.last().close
        val tech = Agents.technical(bars, ts)
        val fund = Agents.fundamental(years, price)
        val (market, _) = Agents.market(bars, indexBars, indexName, News.sentiment(news))
        val (risk, riskStats) = Agents.risk(bars, settings.rfPct)
        val agents = mapOf(Role.TECH to tech, Role.FUND to fund, Role.MARKET to market, Role.RISK to risk)
        val lead = Agents.lead(price, bars, ts, agents, riskStats, settings)
        val analysis = StockAnalysis(
            stock.symbol, stock.name, price, bars.last().epochDay,
            tech, fund, market, risk, lead
        )
        return StockBundle(analysis, buildCharts(bars, ts, indexBars, indexName, years))
    }

    fun buildCharts(
        bars: List<Bar>,
        ts: TechSeries,
        indexBars: List<Bar>?,
        indexName: String,
        years: List<FundYear>
    ): ChartData {
        val start = maxOf(0, bars.size - CHART_BARS)
        val part = bars.subList(start, bars.size)
        fun sl(a: DoubleArray): List<Double> = a.slice(start until a.size)

        val adjBase = part.first().adjClose
        val stockRel = part.map { it.adjClose / adjBase * 100.0 }

        var indexRel: List<Double>? = null
        if (indexBars != null && indexBars.isNotEmpty()) {
            val map = indexBars.associate { it.epochDay to it.adjClose }
            var cur: Double? = indexBars.lastOrNull { it.epochDay <= part.first().epochDay }?.adjClose
            val vals = part.map { b ->
                map[b.epochDay]?.let { cur = it }
                cur
            }
            val base = vals.firstOrNull { it != null }
            if (base != null) indexRel = vals.map { if (it != null) it / base * 100.0 else Double.NaN }
        }

        val adjAll = DoubleArray(bars.size) { bars[it].adjClose }
        val dd = sl(Ta.drawdown(adjAll)).map { it * 100.0 }

        val rev = years.filter { it.revenue != null }
        return ChartData(
            labels = part.map { DateUtil.format(it.epochDay) },
            candles = part.map { Candle(it.open, it.high, it.low, it.close) },
            ma20 = sl(ts.ma20),
            ma60 = sl(ts.ma60),
            bbUp = sl(ts.bbUp),
            bbLow = sl(ts.bbLow),
            macd = sl(ts.macd),
            signal = sl(ts.signal),
            hist = sl(ts.hist),
            rsi = sl(ts.rsi),
            stockRel = stockRel,
            indexRel = indexRel,
            indexName = indexName,
            drawdown = dd,
            revLabels = rev.map { DateUtil.civil(it.asOfDay).first.toString() },
            revValues = rev.map { it.revenue!! / 1e8 }
        )
    }
}
