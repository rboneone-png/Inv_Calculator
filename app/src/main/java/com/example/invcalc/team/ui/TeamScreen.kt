package com.example.invcalc.team.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val gate = Semaphore(3) // 不限檔數，但同時最多連線 3 檔，避免被資料來源限流

    fun run(stocks: List<Stock>, settings: TeamSettings, apiKey: String, model: String, onDone: suspend () -> Unit = {}) {
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
                                Result.success(gate.withPermit { load(st, settings) })
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
                if (ok.isNotEmpty()) {
                    try {
                        onDone()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors = errors + "完成後動作失敗：${e.message}"
                    }
                }
            } finally {
                running = false
                aiTick++
            }
        }
    }
}


// ---------------------------------------------------------------------------------------------- Email／下載

/** 產生報告並開啟寄信畫面（需在協程中呼叫）。回傳給使用者看的訊息。 */
private suspend fun emailReport(context: Context, prefs: TeamPrefs): String {
    val settings = TeamSession.usedSettings ?: return "尚無分析結果，請先按「分析」"
    val analyses = TeamSession.results.map { it.analysis }
    if (analyses.isEmpty()) return "尚無分析結果，請先按「分析」"
    val format = ReportFormat.fromIndex(prefs.emailFormat)
    val date = ReportBuilder.todayText(System.currentTimeMillis())
    val overall = TeamSession.overallAi
    val file = withContext(Dispatchers.IO) {
        val data = ReportExport.bytes(format, analyses, settings, overall, date)
        ReportExport.cacheFile(context, ReportExport.fileName(format, date), data)
    }
    val subject = com.example.invcalc.team.Recipients.subject(prefs.emailSubject, date, TeamPrefs.DEFAULT_SUBJECT)
    val body = buildString {
        appendLine("AI 金融分析團隊投資報告（$date）")
        if (prefs.emailSummary) {
            appendLine()
            append(ReportBuilder.rankingFacts(analyses, settings))
        }
        appendLine()
        append(ReportBuilder.DISCLAIMER)
    }
    val to = TeamPrefs.parseRecipients(prefs.emailTo)
    EmailSender.send(context, to, subject, body, file)
    return if (to.isEmpty()) "已開啟寄信畫面（尚未在設定填收件人，請在信件中輸入）" else "已開啟寄信畫面"
}

@Composable
private fun DownloadDialog(hasResults: Boolean, onPick: (ReportFormat?) -> Unit, onEmail: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下載報告") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!hasResults) {
                    Text("尚無分析結果，請先按下方的「分析」。")
                } else {
                    TextButton(onClick = { onPick(ReportFormat.PDF) }, modifier = Modifier.fillMaxWidth()) { Text("📄  PDF（建議）", modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { onPick(ReportFormat.MARKDOWN) }, modifier = Modifier.fillMaxWidth()) { Text("📝  Markdown", modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = { onPick(ReportFormat.HTML) }, modifier = Modifier.fillMaxWidth()) { Text("🌐  HTML", modifier = Modifier.fillMaxWidth()) }
                    TextButton(onClick = onEmail, modifier = Modifier.fillMaxWidth()) { Text("✉️  以 Email 寄送", modifier = Modifier.fillMaxWidth()) }
                    Text("PDF 為文字版報告（含排名、各分析師指標與發現）；圖表請在畫面上查看。", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// ---------------------------------------------------------------------------------------------- 主畫面

@Composable
fun TeamTab() {
    val context = LocalContext.current
    val prefs = remember { TeamPrefs(context) }
    val store = remember { StockStore(context) }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) } // 0 = 分析、1 = 設定
    var showDownload by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val s = TeamSession

    fun startAnalysis() {
        page = 0
        val stocks = store.load().filter { it.symbol in s.selected }
        if (stocks.isEmpty()) {
            message = "請先在「選擇股票」勾選至少一檔股票，再按「分析」"
            return
        }
        if (s.running) {
            message = "分析進行中，請稍候…"
            return
        }
        message = ""
        s.run(
            stocks,
            TeamSettings(Horizon.values()[s.horizonIdx], RiskProfile.values()[s.riskIdx], s.rfText.num() ?: 1.5),
            prefs.apiKey,
            prefs.model.ifBlank { ClaudeClient.DEFAULT_MODEL },
            onDone = {
                if (prefs.emailAuto) message = emailReport(context.applicationContext, prefs)
            }
        )
    }

    fun save(uri: Uri?, format: ReportFormat) {
        if (uri == null) return
        val settings = s.usedSettings ?: return
        val analyses = s.results.map { it.analysis }
        val overall = s.overallAi
        val date = ReportBuilder.todayText(System.currentTimeMillis())
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    ReportExport.writeToUri(context, uri, ReportExport.bytes(format, analyses, settings, overall, date))
                }
                "已儲存 ${format.label} 報告"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "儲存失敗：${e.message}"
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { save(it, ReportFormat.PDF) }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { save(it, ReportFormat.MARKDOWN) }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { save(it, ReportFormat.HTML) }

    Column(Modifier.fillMaxSize()) {
        if (message.isNotBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            if (page == 0) AnalysisPage(store) else SettingsPage(prefs, onBack = { page = 0 })
        }
        // 固定在最下方：下載／分析／設定
        NavigationBar {
            NavigationBarItem(
                selected = false,
                onClick = { showDownload = true },
                icon = { Text("📥") },
                label = { Text("下載") }
            )
            NavigationBarItem(
                selected = page == 0,
                onClick = { startAnalysis() },
                icon = { Text(if (s.running) "⏳" else "▶️") },
                label = { Text(if (s.running) "分析中" else "分析") }
            )
            NavigationBarItem(
                selected = page == 1,
                onClick = { page = if (page == 1) 0 else 1 },
                icon = { Text("⚙️") },
                label = { Text("設定") }
            )
        }
    }

    if (showDownload) {
        val date = ReportBuilder.todayText(System.currentTimeMillis())
        DownloadDialog(
            hasResults = s.results.isNotEmpty(),
            onPick = { f ->
                showDownload = false
                when (f) {
                    ReportFormat.PDF -> pdfLauncher.launch(ReportExport.fileName(ReportFormat.PDF, date))
                    ReportFormat.MARKDOWN -> mdLauncher.launch(ReportExport.fileName(ReportFormat.MARKDOWN, date))
                    ReportFormat.HTML -> htmlLauncher.launch(ReportExport.fileName(ReportFormat.HTML, date))
                    null -> Unit
                }
            },
            onEmail = {
                showDownload = false
                scope.launch {
                    message = try {
                        emailReport(context, prefs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "寄信失敗：${e.message}"
                    }
                }
            },
            onDismiss = { showDownload = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------- 分析頁

@Composable
private fun AnalysisPage(store: StockStore) {
    val s = TeamSession
    var watch by remember { mutableStateOf(store.load()) }

    ScreenColumn {
        Text("AI 金融分析團隊", style = MaterialTheme.typography.titleLarge)

        Collapsible("團隊介紹", subtitle = "四位分析師＋投資組長，數字由程式計算", key = "team:intro") {
            Text(
                "📈 技術分析師：均線、RSI、MACD、布林通道、量能\n" +
                    "🧾 財報分析師：本益比、ROE、淨利率、營收成長、負債、自由現金流\n" +
                    "🌐 市場趨勢分析師：多期間報酬、相對大盤強弱、Beta、新聞情緒\n" +
                    "🛡️ 風險分析師：波動率、最大跌幅、VaR、夏普值\n" +
                    "🧠 投資組長：依「持有期間 × 風險承受度」加權整合，給出評級、部位上限與參考價位（支撐、壓力、停損）",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "數字與評分全部由程式計算、可以驗證；Claude（選用，於「設定」填 API Key）只負責用白話文解讀，不負責算數。" +
                    "選好股票後按下方的「分析」開始。",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Collapsible("選擇股票", subtitle = "已選 ${s.selected.size} 檔／共 ${watch.size} 檔（不限檔數）", key = "team:select") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { s.selected = watch.map { it.symbol } }) { Text("全選") }
                TextButton(onClick = { s.selected = emptyList() }) { Text("清除") }
                TextButton(onClick = { watch = store.load() }) { Text("重新載入") }
            }
            if (watch.isEmpty()) {
                Text("自選股清單是空的，請先到「股票分析」分頁新增股票，再回到這裡按「重新載入」。", style = MaterialTheme.typography.bodyMedium)
            }
            watch.forEach { st ->
                val checked = st.symbol in s.selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        s.selected = if (checked) s.selected - st.symbol else s.selected + st.symbol
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { s.selected = if (it) s.selected + st.symbol else s.selected - st.symbol }
                    )
                    Text("${st.name.ifBlank { st.symbol }}（${st.symbol}）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Collapsible(
            "分析條件",
            subtitle = "${Horizon.values()[s.horizonIdx].label.substringBefore("（")} × ${RiskProfile.values()[s.riskIdx].label}",
            key = "team:conditions"
        ) {
            Text("持有期間", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Horizon.values().forEachIndexed { i, h ->
                    FilterChip(selected = s.horizonIdx == i, onClick = { s.horizonIdx = i }, label = { Text(h.label.substringBefore("（")) })
                }
            }
            Text(Horizon.values()[s.horizonIdx].label, style = MaterialTheme.typography.bodySmall)
            Text("風險承受度", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskProfile.values().forEachIndexed { i, r ->
                    FilterChip(selected = s.riskIdx == i, onClick = { s.riskIdx = i }, label = { Text(r.label) })
                }
            }
            NumField("無風險利率（年化 %，用於夏普值）", s.rfText) { s.rfText = it }
        }

        if (s.status.isNotBlank()) Text(s.status, style = MaterialTheme.typography.bodyMedium)
        if (s.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.errors.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (s.results.isNotEmpty()) {
            val analyses = s.results.map { it.analysis }
            Collapsible("排名比較", subtitle = "${analyses.size} 檔，依綜合分數由高到低", key = "team:ranking") { RankingTable(analyses) }
            s.overallAi?.let { text ->
                Collapsible("🧠 組長總結（Claude 解讀）", key = "team:overall") { AiText(text) }
            }
            s.results.forEachIndexed { i, b -> StockResultCard(i + 1, b, s.aiTick) }
            Text(ReportBuilder.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        } else if (!s.running) {
            Text("尚無分析結果：選好股票後，按下方的「分析」。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------- 排名表

@Composable
private fun RankingTable(list: List<StockAnalysis>) {
    val rows = ReportBuilder.ranked(list)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

// ---------------------------------------------------------------------------------------------- 單檔結果（每一層都可收合）

@Composable
private fun StockResultCard(rank: Int, b: StockBundle, aiTick: Int) {
    val a = b.analysis
    @Suppress("UNUSED_VARIABLE") val tick = aiTick // AI 文字更新時重新組合
    Collapsible(
        title = "$rank. ${a.displayName}（${a.symbol}）",
        subtitle = "${a.lead.rating.label}　${f2(a.lead.score)} 分　部位上限 ${f2(a.lead.positionCapPct)}%　收盤 ${f2(a.price)}",
        subtitleColor = ratingColor(a.lead.rating),
        key = "team:stock:${a.symbol}"
    ) {
        Text("收盤 ${f2(a.price)}（${DateUtil.format(a.lastDay)}）", style = MaterialTheme.typography.bodySmall)
        LeadSection(a)
        a.agents().forEach { AgentSection(a.symbol, it) }
        AiSection(a)
        ChartsSection(a.symbol, b.charts)
    }
}

@Composable
private fun LeadSection(a: StockAnalysis) {
    val l = a.lead
    Collapsible(
        "🧠 投資組長",
        level = 1,
        subtitle = "${l.rating.label}・部位上限 ${f2(l.positionCapPct)}%・支撐 ${f2(l.support)}／壓力 ${f2(l.resistance)}／停損 ${f2(l.stopLoss)}",
        subtitleColor = ratingColor(l.rating),
        key = "team:lead:${a.symbol}"
    ) {
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
private fun AgentSection(symbol: String, r: AgentReport) {
    Collapsible(
        "${r.role.emoji} ${r.role.label}",
        level = 1,
        subtitle = if (r.score != null) "${f2(r.score)}/100　${r.verdict}" else r.verdict,
        key = "team:agent:$symbol:${r.role.name}"
    ) {
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

@Composable
private fun AiSection(a: StockAnalysis) {
    if (!a.aiText.isNullOrBlank()) {
        Collapsible("Claude 白話解讀", level = 1, key = "team:ai:${a.symbol}") { AiText(a.aiText!!) }
    } else if (!a.aiError.isNullOrBlank()) {
        Text("AI 解讀失敗：${a.aiError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

// ---------------------------------------------------------------------------------------------- 圖表（每張可收合）

@Composable
private fun ChartsSection(symbol: String, c: com.example.invcalc.team.ChartData) {
    val n = c.labels.size
    // K 線、MACD、RSI、相對大盤、回撤共用同一個檢視狀態：日期對齊、同步縮放平移
    val view = rememberChartViewState(n, 120)

    Collapsible("📊 K 線與均線、布林通道", level = 1, key = "team:chart:k:$symbol") {
        ZoomableLineChart(
            labels = c.labels,
            series = listOf(ChartSeries("MA20", cOrange, c.ma20, 1.2f), ChartSeries("MA60", cPurple, c.ma60, 1.2f)),
            band = c.bbUp to c.bbLow,
            bandName = "布林通道",
            bandColor = cGray,
            chartHeight = 280.dp,
            state = view,
            candles = c.candles
        )
    }
    Collapsible("📊 MACD", level = 1, key = "team:chart:macd:$symbol") {
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
    }
    Collapsible("📊 RSI(14)", subtitle = "70 以上超買、30 以下超賣", level = 1, key = "team:chart:rsi:$symbol") {
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
    }
    Collapsible("📊 相對大盤", subtitle = "期初 = 100", level = 1, key = "team:chart:rel:$symbol") {
        val rel = buildList {
            add(ChartSeries("本股", cBlue, c.stockRel))
            c.indexRel?.let { add(ChartSeries(c.indexName, cGray, it)) }
        }
        ZoomableLineChart(labels = c.labels, series = rel, chartHeight = 180.dp, state = view, showHint = false)
    }
    Collapsible("📊 回撤", subtitle = "相對歷史高點的跌幅 %", level = 1, key = "team:chart:dd:$symbol") {
        ZoomableLineChart(
            labels = c.labels,
            series = listOf(ChartSeries("回撤 %", cDown, c.drawdown)),
            chartHeight = 150.dp,
            state = view
        )
    }
    Collapsible("📊 年度營收", subtitle = "單位：億", level = 1, key = "team:chart:rev:$symbol") {
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
}
