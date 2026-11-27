package com.example.invcalc.team.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.invcalc.NumField
import com.example.invcalc.ScreenColumn
import com.example.invcalc.num
import com.example.invcalc.stock.DateUtil
import com.example.invcalc.stock.Stock
import com.example.invcalc.stock.StockCache
import com.example.invcalc.stock.StockStore
import com.example.invcalc.stock.SymbolUtil
import com.example.invcalc.stock.YahooApi
import com.example.invcalc.stock.ui.ChartSeries
import com.example.invcalc.stock.ui.ZoomableLineChart
import com.example.invcalc.stock.ui.rememberChartViewState
import com.example.invcalc.team.AgentReport
import com.example.invcalc.team.ClaudeClient
import com.example.invcalc.team.Fundamentals
import com.example.invcalc.team.Horizon
import com.example.invcalc.team.News
import com.example.invcalc.team.Rating
import com.example.invcalc.team.ReportBuilder
import com.example.invcalc.team.RiskProfile
import com.example.invcalc.team.StockAnalysis
import com.example.invcalc.team.StockBundle
import com.example.invcalc.team.TeamPipeline
import com.example.invcalc.team.TeamSettings
import com.example.invcalc.team.f2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_STOCKS = 5

private val cUp = Color(0xFFC62828)   // 紅（台股慣例：漲／買進）
private val cDown = Color(0xFF2E7D32) // 綠（跌／賣出）
private val cBlue = Color(0xFF1E88E5)
private val cOrange = Color(0xFFFB8C00)
private val cPurple = Color(0xFF8E24AA)
private val cGray = Color(0xFF757575)

private fun ratingColor(r: Rating): Color = when (r) {
    Rating.STRONG_BUY, Rating.BUY -> cUp
    Rating.HOLD -> cGray
    Rating.REDUCE, Rating.SELL -> cDown
}

/** 分析結果與設定放在程序層級，切換分頁（頁面被回收）或分析進行中離開都不會遺失。 */
object TeamSession {
    var selected by mutableStateOf(listOf<String>())
    var horizonIdx by mutableIntStateOf(1)
    var riskIdx by mutableIntStateOf(1)
    var rfText by mutableStateOf("1.5")

    var results by mutableStateOf(listOf<StockBundle>())
    var overallAi by mutableStateOf<String?>(null)
    var errors by mutableStateOf(listOf<String>())
    var status by mutableStateOf("")
    var running by mutableStateOf(false)
    var usedSettings by mutableStateOf<TeamSettings?>(null)
    var aiTick by mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun load(st: Stock, settings: TeamSettings): StockBundle {
        val bars = StockCache.get(st.symbol)
            ?: YahooApi.fetchBlocking(st.symbol).bars.also { StockCache.put(st.symbol, it) }
        val taiwan = SymbolUtil.isTaiwanSymbol(st.symbol)
        val indexSymbol = if (taiwan) "^TWII" else "^GSPC"
        val indexName = if (taiwan) "台灣加權指數" else "S&P 500 指數"
        val idx = try {
            StockCache.get(indexSymbol)
                ?: YahooApi.fetchBlocking(indexSymbol).bars.also { StockCache.put(indexSymbol, it) }
        } catch (e: Exception) {
            null
        }
        val years = Fundamentals.fetchBlocking(st.symbol)
        val news = News.fetchBlocking(st.symbol)
        return TeamPipeline.analyze(st, bars, idx, indexName, years, news, settings)
    }

    fun run(stocks: List<Stock>, settings: TeamSettings, apiKey: String, model: String) {
        if (running) return
        running = true
        results = emptyList()
        overallAi = null
        errors = emptyList()
        usedSettings = settings
        scope.launch {
            try {
                status = "取得 ${stocks.size} 檔股票的價格、財報與新聞資料並分析中…"
                val outcomes = coroutineScope {
                    stocks.map { st ->
                        async(Dispatchers.IO) {
                            try {
                                Result.success(load(st, settings))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure<StockBundle>(e)
                            }
                        }
                    }.awaitAll()
                }
                val ok = ArrayList<StockBundle>()
                val errs = ArrayList<String>()
                stocks.forEachIndexed { i, st ->
                    val r = outcomes[i]
                    r.onSuccess { ok.add(it) }
                    r.onFailure { errs.add("${st.name.ifBlank { st.symbol }}（${st.symbol}）：${it.message ?: "分析失敗"}") }
                }
                results = ok.sortedByDescending { it.analysis.lead.score }
                errors = errs
                status = if (ok.isEmpty()) "沒有成功分析的股票" else "程式計算完成"

                // 選用：Claude 白話解讀（AI 只解讀，數字與評分皆已由程式算好）
                if (apiKey.isNotBlank() && ok.isNotEmpty()) {
                    for ((i, b) in results.withIndex()) {
                        status = "Claude 解讀中 ${i + 1}/${results.size}：${b.analysis.displayName}…"
                        try {
                            val text = withContext(Dispatchers.IO) {
                                ClaudeClient.complete(apiKey, model, ClaudeClient.SYSTEM_STOCK, ReportBuilder.stockFacts(b.analysis, settings))
                            }
                            b.analysis.aiText = text
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            b.analysis.aiError = e.message ?: "AI 解讀失敗"
                        }
                        aiTick++
                    }
                    if (results.size >= 2) {
                        status = "Claude 撰寫組長總結中…"
                        try {
                            overallAi = withContext(Dispatchers.IO) {
                                ClaudeClient.complete(
                                    apiKey, model, ClaudeClient.SYSTEM_OVERALL,
                                    ReportBuilder.rankingFacts(results.map { it.analysis }, settings), 1024
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errors = errors + "組長總結（AI）：${e.message ?: "失敗"}"
                        }
                    }
                    status = "完成（含 Claude 白話解讀）"
                } else if (ok.isNotEmpty()) {
                    status = "完成（未填 API Key，使用程式內建的文字解讀）"
                }
            } finally {
                running = false
                aiTick++
            }
        }
    }
}

@Composable
fun TeamTab() {
    val context = LocalContext.current
    val store = remember { StockStore(context) }
    val prefs = remember { context.getSharedPreferences("ai_team", Context.MODE_PRIVATE) }
    var watch by remember { mutableStateOf(store.load()) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", ClaudeClient.DEFAULT_MODEL) ?: ClaudeClient.DEFAULT_MODEL) }
    var message by remember { mutableStateOf("") }
    val s = TeamSession

    fun writeTo(uri: Uri?, text: String) {
        if (uri == null) return
        message = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            "已儲存報告"
        } catch (e: Exception) {
            "儲存失敗：${e.message}"
        }
    }

    val analyses = s.results.map { it.analysis }
    val dateText = ReportBuilder.todayText(System.currentTimeMillis())
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        s.usedSettings?.let { writeTo(uri, ReportBuilder.markdown(analyses, it, s.overallAi, dateText)) }
    }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        s.usedSettings?.let { writeTo(uri, ReportBuilder.html(analyses, it, s.overallAi, dateText)) }
    }

    ScreenColumn {
        Text("AI 金融分析團隊", style = MaterialTheme.typography.titleLarge)
        Text(
            "📈 技術、🧾 財報、🌐 市場趨勢、🛡️ 風險四位分析師各自分析，🧠 投資組長依你的「持有期間 × 風險承受度」加權整合，" +
                "給出評級、部位上限與參考價位。數字與評分全部由程式計算、可以驗證；Claude（選用）只負責用白話文解讀。",
            style = MaterialTheme.typography.bodySmall
        )

        // ---- 選股 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("選擇股票（最多 $MAX_STOCKS 檔，已選 ${s.selected.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { watch = store.load() }) { Text("重新載入") }
                }
                if (watch.isEmpty()) {
                    Text("自選股清單是空的，請先到「股票分析」分頁新增股票，再回到這裡。", style = MaterialTheme.typography.bodyMedium)
                }
                watch.forEach { st ->
                    val checked = st.symbol in s.selected
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = checked || s.selected.size < MAX_STOCKS) {
                            s.selected = if (checked) s.selected - st.symbol else s.selected + st.symbol
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = checked || s.selected.size < MAX_STOCKS,
                            onCheckedChange = { s.selected = if (it) s.selected + st.symbol else s.selected - st.symbol }
                        )
                        Text("${st.name.ifBlank { st.symbol }}（${st.symbol}）", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- 設定 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("持有期間", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Horizon.values().forEachIndexed { i, h ->
                        FilterChip(selected = s.horizonIdx == i, onClick = { s.horizonIdx = i }, label = { Text(h.label.substringBefore("（")) })
                    }
                }
                Text(Horizon.values()[s.horizonIdx].label, style = MaterialTheme.typography.bodySmall)
                Text("風險承受度", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskProfile.values().forEachIndexed { i, r ->
                        FilterChip(selected = s.riskIdx == i, onClick = { s.riskIdx = i }, label = { Text(r.label) })
                    }
                }
                NumField("無風險利率（年化 %，用於夏普值）", s.rfText) { s.rfText = it }
            }
        }

        // ---- Claude（選用）----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Claude API（選用）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "不填也能完整運作。填入後，分析師與組長會用白話文解讀並寫出更完整的報告（會把程式算好的數據摘要送到 Anthropic API，" +
                        "不包含任何個人資料）。Key 只存在這支手機的 App 私有儲存空間。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; prefs.edit().putString("api_key", it.trim()).apply() },
                    label = { Text("Claude API Key（sk-ant-…）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; prefs.edit().putString("model", it.trim()).apply() },
                    label = { Text("模型（預設 ${ClaudeClient.DEFAULT_MODEL}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            enabled = !s.running && s.selected.isNotEmpty(),
            onClick = {
                watch = store.load()
                val stocks = watch.filter { it.symbol in s.selected }
                message = ""
                s.run(
                    stocks,
                    TeamSettings(Horizon.values()[s.horizonIdx], RiskProfile.values()[s.riskIdx], s.rfText.num() ?: 1.5),
                    apiKey.trim(),
                    model.trim().ifBlank { ClaudeClient.DEFAULT_MODEL }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (s.running) "分析中…" else "開始分析（${s.selected.size} 檔）") }
        if (s.status.isNotBlank()) Text(s.status, style = MaterialTheme.typography.bodyMedium)
        if (s.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.errors.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        // ---- 結果 ----
        if (s.results.isNotEmpty()) {
            @Suppress("UNUSED_VARIABLE") val tick = s.aiTick // 讓 AI 文字更新時重新組合
            RankingTable(analyses)
            s.overallAi?.let { text ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🧠 組長總結（Claude 解讀）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AiText(text)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mdLauncher.launch("AI金融分析報告_$dateText.md") }) { Text("下載 Markdown") }
                OutlinedButton(onClick = { htmlLauncher.launch("AI金融分析報告_$dateText.html") }) { Text("下載 HTML") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            s.results.forEachIndexed { i, b -> StockResultCard(i + 1, b, s.aiTick) }
            Text(ReportBuilder.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------- 排名表

@Composable
private fun RankingTable(list: List<StockAnalysis>) {
    val rows = ReportBuilder.ranked(list)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("排名比較", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                Cell("#", 0.5f, true); Cell("標的", 2.2f, true); Cell("評級", 1.3f, true); Cell("綜合", 1f, true)
                Cell("技", 0.8f, true); Cell("財", 0.8f, true); Cell("市", 0.8f, true); Cell("險", 0.8f, true); Cell("部位", 1f, true)
            }
            rows.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Cell("${i + 1}", 0.5f)
                    Cell(a.displayName, 2.2f)
                    Cell(a.lead.rating.label, 1.3f, color = ratingColor(a.lead.rating))
                    Cell(f2(a.lead.score), 1f)
                    Cell(sc(a.tech), 0.8f); Cell(sc(a.fund), 0.8f); Cell(sc(a.market), 0.8f); Cell(sc(a.risk), 0.8f)
                    Cell("${f2(a.lead.positionCapPct)}%", 1f)
                }
            }
            Text("技＝技術、財＝財報、市＝市場趨勢、險＝風險（分數越高越好；風險分數越高代表風險越低）；部位＝部位上限。", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun sc(a: AgentReport) = if (a.score != null) "${a.score.toInt()}" else "—"

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, header: Boolean = false, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 2
    )
}

// ---------------------------------------------------------------------------------------------- 單檔結果

@Composable
private fun StockResultCard(rank: Int, b: StockBundle, aiTick: Int) {
    val a = b.analysis
    var expanded by remember(a.symbol) { mutableStateOf(rank == 1) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$rank. ${a.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${a.symbol}　收盤 ${f2(a.price)}（${DateUtil.format(a.lastDay)}）", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(a.lead.rating.label, color = ratingColor(a.lead.rating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${f2(a.lead.score)} 分", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "▲" else "▼", modifier = Modifier.padding(start = 8.dp))
            }
            if (expanded) {
                LeadCard(a)
                a.agents().forEach { AgentCard(it) }
                @Suppress("UNUSED_VARIABLE") val tick = aiTick
                if (!a.aiText.isNullOrBlank()) {
                    Text("Claude 白話解讀", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    AiText(a.aiText!!)
                } else if (!a.aiError.isNullOrBlank()) {
                    Text("AI 解讀失敗：${a.aiError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("圖表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                StockCharts(b)
            }
        }
    }
}

@Composable
private fun LeadCard(a: StockAnalysis) {
    val l = a.lead
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🧠 投資組長", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "評級：${l.rating.label}（綜合 ${f2(l.score)}/100）\n部位上限：${f2(l.positionCapPct)}%（占整體投資組合）\n" +
                "支撐 ${f2(l.support)}　壓力 ${f2(l.resistance)}　停損 ${f2(l.stopLoss)}" +
                (l.rewardRisk?.let { "\n風險報酬比 ${f2(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium
        )
        l.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AgentCard(r: AgentReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${r.role.emoji} ${r.role.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (r.score != null) "${f2(r.score)}/100　${r.verdict}" else r.verdict, style = MaterialTheme.typography.bodySmall)
        }
        if (r.score != null) LinearProgressIndicator(progress = { (r.score / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        r.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        if (r.metrics.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            r.metrics.forEach {
                Row(Modifier.fillMaxWidth()) {
                    Text(it.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.value, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.7f))
                }
            }
        }
    }
}

/** 顯示 Claude 回傳的簡單 Markdown（標題、清單、粗體標記去除）。 */
@Composable
private fun AiText(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trim().replace("**", "")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Text("• " + line.drop(2), style = MaterialTheme.typography.bodyMedium)
                else -> Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- 圖表

@Composable
private fun StockCharts(b: StockBundle) {
    val c = b.charts
    val n = c.labels.size
    // K 線、MACD、RSI、相對大盤、回撤共用同一個檢視狀態：日期對齊、同步縮放平移
    val view = rememberChartViewState(n, 120)

    Text("K 線與均線、布林通道", style = MaterialTheme.typography.labelLarge)
    ZoomableLineChart(
        labels = c.labels,
        series = listOf(
            ChartSeries("MA20", cOrange, c.ma20, 1.2f),
            ChartSeries("MA60", cPurple, c.ma60, 1.2f)
        ),
        band = c.bbUp to c.bbLow,
        bandName = "布林通道",
        bandColor = cGray,
        chartHeight = 280.dp,
        state = view,
        showHint = false,
        candles = c.candles
    )
    Text("MACD", style = MaterialTheme.typography.labelLarge)
    ZoomableLineChart(
        labels = c.labels,
        series = listOf(
            ChartSeries("柱狀體", cUp, c.hist, bars = true, negColor = cDown),
            ChartSeries("DIF", cBlue, c.macd, 1.2f),
            ChartSeries("訊號線", cOrange, c.signal, 1.2f)
        ),
        chartHeight = 150.dp,
        state = view,
        showHint = false
    )
    Text("RSI(14)（70 以上超買、30 以下超賣）", style = MaterialTheme.typography.labelLarge)
    ZoomableLineChart(
        labels = c.labels,
        series = listOf(
            ChartSeries("RSI", cPurple, c.rsi),
            ChartSeries("70", cUp, List(n) { 70.0 }, 1f),
            ChartSeries("30", cDown, List(n) { 30.0 }, 1f)
        ),
        chartHeight = 130.dp,
        state = view,
        showHint = false
    )
    Text("相對大盤（期初 = 100）", style = MaterialTheme.typography.labelLarge)
    val rel = buildList {
        add(ChartSeries("本股", cBlue, c.stockRel))
        c.indexRel?.let { add(ChartSeries(c.indexName, cGray, it)) }
    }
    ZoomableLineChart(labels = c.labels, series = rel, chartHeight = 180.dp, state = view, showHint = false)
    Text("回撤（%，相對歷史高點）", style = MaterialTheme.typography.labelLarge)
    ZoomableLineChart(
        labels = c.labels,
        series = listOf(ChartSeries("回撤 %", cDown, c.drawdown)),
        chartHeight = 150.dp,
        state = view
    )
    Text("年度營收（億）", style = MaterialTheme.typography.labelLarge)
    if (c.revLabels.size >= 2) {
        ZoomableLineChart(
            labels = c.revLabels,
            series = listOf(ChartSeries("營收（億）", cBlue, c.revValues, bars = true)),
            chartHeight = 170.dp,
            initialVisible = c.revLabels.size
        )
    } else {
        Text("Yahoo 未提供足夠的年度營收資料（ETF 或資料缺漏時常見），無法繪製。", style = MaterialTheme.typography.bodySmall)
    }
}
