package com.example.invcalc.team

enum class Role(val label: String, val emoji: String) {
    TECH("技術分析師", "📈"),
    FUND("財報分析師", "🧾"),
    MARKET("市場趨勢分析師", "🌐"),
    RISK("風險分析師", "🛡️"),
    LEAD("投資組長", "🧠")
}

/** 持有期間：決定四位分析師的基本權重與參考價位的回看天數。 */
enum class Horizon(
    val label: String,
    val lookback: Int,
    val tech: Double,
    val fund: Double,
    val market: Double,
    val risk: Double
) {
    SHORT("短線（1–4 週）", 20, 0.40, 0.10, 0.30, 0.20),
    SWING("波段（1–3 個月）", 60, 0.30, 0.20, 0.30, 0.20),
    LONG("長期（6 個月以上）", 120, 0.15, 0.40, 0.20, 0.25)
}

/** 風險承受度：調整權重、部位上限倍率與停損的 ATR 倍數。 */
enum class RiskProfile(
    val label: String,
    val posMult: Double,
    val atrK: Double,
    val techMul: Double,
    val fundMul: Double,
    val marketMul: Double,
    val riskMul: Double
) {
    CONSERVATIVE("保守", 0.6, 1.5, 0.9, 1.2, 0.9, 1.5),
    MODERATE("穩健", 1.0, 2.0, 1.0, 1.0, 1.0, 1.0),
    AGGRESSIVE("積極", 1.5, 2.5, 1.2, 0.9, 1.2, 0.6)
}

enum class Rating(val label: String, val basePosPct: Double) {
    STRONG_BUY("強力買進", 15.0),
    BUY("買進", 10.0),
    HOLD("持有", 5.0),
    REDUCE("減碼", 2.0),
    SELL("賣出", 0.0);

    companion object {
        fun fromScore(score: Double): Rating = when {
            score >= 75 -> STRONG_BUY
            score >= 60 -> BUY
            score >= 45 -> HOLD
            score >= 30 -> REDUCE
            else -> SELL
        }
    }
}

class TeamSettings(val horizon: Horizon, val risk: RiskProfile, val rfPct: Double)

class Metric(val name: String, val value: String)

/** 單一分析師的結果。score 為 null 表示資料不足、不納入加權。 */
class AgentReport(
    val role: Role,
    val score: Double?,
    val verdict: String,
    val metrics: List<Metric>,
    val findings: List<String>
)

class LeadResult(
    val score: Double,
    val rating: Rating,
    val positionCapPct: Double,
    val support: Double,
    val resistance: Double,
    val stopLoss: Double,
    val rewardRisk: Double?,
    val weights: Map<Role, Double>,
    val findings: List<String>
)

class StockAnalysis(
    val symbol: String,
    val name: String,
    val price: Double,
    val lastDay: Long,
    val tech: AgentReport,
    val fund: AgentReport,
    val market: AgentReport,
    val risk: AgentReport,
    val lead: LeadResult,
    var aiText: String? = null,
    var aiError: String? = null
) {
    val displayName: String get() = name.ifBlank { symbol }
    fun agents(): List<AgentReport> = listOf(tech, fund, market, risk)
}

class Candle(val open: Double, val high: Double, val low: Double, val close: Double)

/** 圖表資料（皆與 labels 等長，無法計算處為 NaN）。 */
class ChartData(
    val labels: List<String>,
    val candles: List<Candle>,
    val ma20: List<Double>,
    val ma60: List<Double>,
    val bbUp: List<Double>,
    val bbLow: List<Double>,
    val macd: List<Double>,
    val signal: List<Double>,
    val hist: List<Double>,
    val rsi: List<Double>,
    val stockRel: List<Double>,
    val indexRel: List<Double>?,
    val indexName: String,
    val drawdown: List<Double>,
    val revLabels: List<String>,
    val revValues: List<Double>
)

class StockBundle(val analysis: StockAnalysis, val charts: ChartData)
