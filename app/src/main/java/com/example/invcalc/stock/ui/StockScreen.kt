package com.example.invcalc.stock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.invcalc.stock.Bar
import com.example.invcalc.stock.DateUtil
import com.example.invcalc.stock.Indicators
import com.example.invcalc.stock.Period
import com.example.invcalc.stock.Prophet
import com.example.invcalc.stock.ProphetParams
import com.example.invcalc.stock.ProphetResult
import com.example.invcalc.stock.Resample
import com.example.invcalc.stock.Stock
import com.example.invcalc.stock.StockCache
import com.example.invcalc.stock.StockStore
import com.example.invcalc.stock.SymbolUtil
import com.example.invcalc.stock.YahooApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------- 股票清單

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockTab() {
    val context = LocalContext.current
    val store = remember { StockStore(context) }
    var stocks by remember { mutableStateOf(store.load()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Stock?>(null) }

    fun update(list: List<Stock>) {
        stocks = list
        store.save(list)
    }

    val current = stocks.firstOrNull { it.symbol == selected }
    if (current != null) {
        BackHandler { selected = null }
        StockDetail(current, onBack = { selected = null })
        return
    }

    ScreenColumn {
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("新增股票") }
        if (stocks.isEmpty()) {
            Text(
                "尚未新增股票。台股輸入數字即可（例如 2330，上櫃請輸入 6488.TWO），美股輸入代號（例如 AAPL）。",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("點一下查看分析，長按可刪除，▲▼ 可調整順序。", style = MaterialTheme.typography.bodySmall)
        }
        stocks.forEachIndexed { index, stock ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { selected = stock.symbol },
                        onLongClick = { deleteTarget = stock }
                    )
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stock.name.ifBlank { stock.symbol },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(stock.symbol, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        enabled = index > 0,
                        onClick = {
                            val m = stocks.toMutableList()
                            m.add(index - 1, m.removeAt(index))
                            update(m)
                        }
                    ) { Text("▲") }
                    TextButton(
                        enabled = index < stocks.size - 1,
                        onClick = {
                            val m = stocks.toMutableList()
                            m.add(index + 1, m.removeAt(index))
                            update(m)
                        }
                    ) { Text("▼") }
                }
            }
        }
    }

    if (showAdd) {
        AddStockDialog(
            existing = stocks.map { it.symbol },
            onDismiss = { showAdd = false },
            onAdded = { update(stocks + it); showAdd = false }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除股票") },
            text = { Text("確定要刪除「${target.name.ifBlank { target.symbol }}（${target.symbol}）」嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    update(stocks.filter { it.symbol != target.symbol })
                    deleteTarget = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AddStockDialog(existing: List<String>, onDismiss: () -> Unit, onAdded: (Stock) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("新增股票") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it; error = null },
                    label = { Text("股票代號（如 2330、AAPL）") },
                    singleLine = true,
                    enabled = !loading
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱（選填，留空自動帶入）") },
                    singleLine = true,
                    enabled = !loading
                )
                if (loading) Text("查詢中…", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && symbol.isNotBlank(),
                onClick = {
                    val sym = SymbolUtil.normalize(symbol)
                    if (sym in existing) {
                        error = "已在清單中"
                        return@TextButton
                    }
                    loading = true
                    scope.launch {
                        try {
                            val parsed = withContext(Dispatchers.IO) { YahooApi.fetchBlocking(sym) }
                            StockCache.put(sym, parsed.bars)
                            onAdded(Stock(sym, name.trim().ifBlank { parsed.name }))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "查詢失敗，請檢查代號與網路"
                            loading = false
                        }
                    }
                }
            ) { Text("新增") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------- 單一股票分析

private sealed interface LoadState {
    object Loading : LoadState
    class Failed(val message: String) : LoadState
    class Ready(val bars: List<Bar>) : LoadState
}

@Composable
private fun StockDetail(stock: Stock, onBack: () -> Unit) {
    var state by remember(stock.symbol) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reload by remember(stock.symbol) { mutableIntStateOf(0) }

    LaunchedEffect(stock.symbol, reload) {
        state = LoadState.Loading
        state = try {
            val cached = if (reload == 0) StockCache.get(stock.symbol) else null
            val bars = cached ?: withContext(Dispatchers.IO) { YahooApi.fetchBlocking(stock.symbol).bars }
            StockCache.put(stock.symbol, bars)
            LoadState.Ready(bars)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: "載入失敗")
        }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Column(Modifier.weight(1f)) {
                Text(stock.name.ifBlank { stock.symbol }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(stock.symbol, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { reload++ }) { Text("重新整理") }
        }
        when (val s = state) {
            is LoadState.Loading -> Text("載入股價資料中…")
            is LoadState.Failed -> {
                Text("載入失敗：${s.message}", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { reload++ }) { Text("重試") }
            }
            is LoadState.Ready -> {
                val last = s.bars.last()
                Text(
                    "最新收盤 ${String.format(Locale.US, "%,.2f", last.close)}（${DateUtil.format(last.epochDay)}），" +
                        "共 ${s.bars.size} 個交易日資料",
                    style = MaterialTheme.typography.bodyMedium
                )
                SharpeSection(s.bars)
                BollingerSection(s.bars)
                VolumeSection(s.bars)
                ProphetSection(s.bars)
                Text("以上分析僅供參考，不構成投資建議。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PeriodChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Period.values().forEachIndexed { i, p ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(p.label) })
        }
    }
}

private val cBlue = Color(0xFF1E88E5)
private val cOrange = Color(0xFFFB8C00)
private val cRed = Color(0xFFE53935)
private val cGreen = Color(0xFF43A047)
private val cGray = Color(0xFF757575)

private fun f2(v: Double) = if (v.isFinite()) String.format(Locale.US, "%.2f", v) else "—"

// ---------------------------------------------------------------- 1. 夏普值

@Composable
private fun SharpeSection(bars: List<Bar>) {
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    var rf by rememberSaveable { mutableStateOf("1.5") }
    val period = Period.values()[periodIdx]
    val data = remember(bars, periodIdx) { Resample.toPeriod(bars, period) }
    val res = remember(data, periodIdx, rf) { Indicators.sharpe(data, period, rf.num() ?: 0.0) }

    SectionCard("1. 夏普值（Sharpe Ratio）") {
        PeriodChips(periodIdx) { periodIdx = it }
        NumField("無風險利率（年化 %）", rf) { rf = it }
        if (res == null) {
            Text("資料不足：${period.label}至少需要 ${period.sharpeWindow + 1} 根 K 棒。")
        } else {
            val latest = res.rolling.lastOrNull { it.isFinite() } ?: Double.NaN
            Text(
                "最新滾動夏普值（近 ${res.window} 根${period.label}，年化）：${f2(latest)}\n" +
                    "整段期間夏普值：${f2(res.overall)}\n" +
                    "一般來說 >1 為佳、>2 為優、<0 表示報酬不及無風險利率。",
                style = MaterialTheme.typography.bodyMedium
            )
            ZoomableLineChart(
                labels = res.dates.map { DateUtil.format(it) },
                series = listOf(ChartSeries("滾動夏普值", cBlue, res.rolling)),
                initialVisible = 150
            )
        }
    }
}

// ---------------------------------------------------------------- 2. 布林通道

@Composable
private fun BollingerSection(bars: List<Bar>) {
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.values()[periodIdx]
    val data = remember(bars, periodIdx) { Resample.toPeriod(bars, period) }
    val res = remember(data) { Indicators.bollinger(data, 20, 2.0) }

    SectionCard("2. 布林通道（20 根均線 ± 2 倍標準差）") {
        PeriodChips(periodIdx) { periodIdx = it }
        if (res == null) {
            Text("資料不足：${period.label}至少需要 20 根 K 棒。")
        } else {
            val i = res.close.lastIndex
            val c = res.close[i]
            val position = when {
                c > res.upper[i] -> "收盤價高於上軌（偏強／可能過熱）"
                c < res.lower[i] -> "收盤價低於下軌（偏弱／可能超跌）"
                else -> "收盤價位於通道內"
            }
            val pctB = (c - res.lower[i]) / (res.upper[i] - res.lower[i]) * 100
            Text(
                "最新${period.label}：上軌 ${f2(res.upper[i])}、中軌 ${f2(res.mid[i])}、下軌 ${f2(res.lower[i])}\n" +
                    "$position（%B = ${f2(pctB)}%）",
                style = MaterialTheme.typography.bodyMedium
            )
            ZoomableLineChart(
                labels = res.dates.map { DateUtil.format(it) },
                series = listOf(
                    ChartSeries("收盤價", cBlue, res.close),
                    ChartSeries("中軌", cOrange, res.mid, 1.2f),
                    ChartSeries("上軌", cRed, res.upper, 1.2f),
                    ChartSeries("下軌", cGreen, res.lower, 1.2f)
                ),
                band = res.upper to res.lower,
                bandName = "通道",
                bandColor = cGray,
                initialVisible = 150
            )
        }
    }
}

// ---------------------------------------------------------------- 3. 交易量突高

@Composable
private fun VolumeSection(bars: List<Bar>) {
    val res = remember(bars) { Indicators.volumeSpike(bars, 20, 2.0) }
    SectionCard("3. 交易量突高判斷") {
        if (res == null) {
            Text("資料不足，無法計算（需至少 21 個交易日且有成交量）。")
        } else {
            Text(
                if (res.isSpike) "⚠ 量能突高" else "量能正常",
                style = MaterialTheme.typography.titleLarge,
                color = if (res.isSpike) cRed else cGreen
            )
            Text(
                "日期：${DateUtil.format(res.date)}\n" +
                    "當日成交量：${String.format(Locale.US, "%,.0f", res.volume)}\n" +
                    "近 20 個交易日平均量（月均量）：${String.format(Locale.US, "%,.0f", res.average)}\n" +
                    "倍數：${f2(res.ratio)} 倍（判斷標準：當日量 ≥ 月均量的 2 倍）",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ---------------------------------------------------------------- 4. Prophet

private sealed interface ProphetState {
    object Idle : ProphetState
    object Running : ProphetState
    class Failed(val message: String) : ProphetState
    class Done(val result: ProphetResult) : ProphetState
}

@Composable
private fun ProphetSection(bars: List<Bar>) {
    val def = remember { ProphetParams() }
    var periods by rememberSaveable { mutableStateOf(def.periods.toString()) }
    var cps by rememberSaveable { mutableStateOf(def.changepointPriorScale.toString()) }
    var sps by rememberSaveable { mutableStateOf(def.seasonalityPriorScale.toString()) }
    var ncp by rememberSaveable { mutableStateOf(def.nChangepoints.toString()) }
    var cpr by rememberSaveable { mutableStateOf(def.changepointRange.toString()) }
    var iw by rememberSaveable { mutableStateOf(def.intervalWidth.toString()) }
    var yearly by rememberSaveable { mutableStateOf(def.yearlySeasonality) }
    var weekly by rememberSaveable { mutableStateOf(def.weeklySeasonality) }
    var state by remember(bars) { mutableStateOf<ProphetState>(ProphetState.Idle) }
    val scope = rememberCoroutineScope()

    fun parse(): ProphetParams? {
        val p = periods.toIntOrNull() ?: return null
        val a = cps.num() ?: return null
        val b = sps.num() ?: return null
        val c = ncp.toIntOrNull() ?: return null
        val d = cpr.num() ?: return null
        val e = iw.num() ?: return null
        if (p !in 1..365 || a <= 0 || b <= 0 || c !in 0..100 || d !in 0.1..1.0 || e !in 0.5..0.99) return null
        return ProphetParams(p, a, b, c, d, e, yearly, weekly)
    }

    SectionCard("4. Prophet 預測") {
        Text(
            "參數預設值為 Facebook Prophet 建議值，可自行調整後重新預測。",
            style = MaterialTheme.typography.bodySmall
        )
        NumField("預測交易日數（1–365，預設 ${def.periods}）", periods) { periods = it }
        NumField("changepoint_prior_scale（趨勢彈性，預設 ${def.changepointPriorScale}）", cps) { cps = it }
        NumField("seasonality_prior_scale（季節性強度，預設 ${def.seasonalityPriorScale}）", sps) { sps = it }
        NumField("n_changepoints（趨勢轉折點數，0–100，預設 ${def.nChangepoints}）", ncp) { ncp = it }
        NumField("changepoint_range（轉折點範圍 0.1–1，預設 ${def.changepointRange}）", cpr) { cpr = it }
        NumField("interval_width（信賴區間 0.5–0.99，預設 ${def.intervalWidth}）", iw) { iw = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = yearly, onCheckedChange = { yearly = it })
            Text("  年季節性", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = weekly, onCheckedChange = { weekly = it }, modifier = Modifier.padding(start = 16.dp))
            Text("  週季節性", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state !is ProphetState.Running,
                onClick = {
                    val params = parse()
                    if (params == null) {
                        state = ProphetState.Failed("參數無效，請檢查數值範圍")
                        return@Button
                    }
                    state = ProphetState.Running
                    scope.launch {
                        state = try {
                            val res = withContext(Dispatchers.Default) {
                                Prophet.fit(bars.map { it.epochDay }, bars.map { it.close }, params)
                            }
                            ProphetState.Done(res)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ProphetState.Failed(e.message ?: "預測失敗")
                        }
                    }
                }
            ) { Text("執行預測") }
            OutlinedButton(onClick = {
                periods = def.periods.toString(); cps = def.changepointPriorScale.toString()
                sps = def.seasonalityPriorScale.toString(); ncp = def.nChangepoints.toString()
                cpr = def.changepointRange.toString(); iw = def.intervalWidth.toString()
                yearly = def.yearlySeasonality; weekly = def.weeklySeasonality
            }) { Text("還原預設值") }
        }
        when (val s = state) {
            is ProphetState.Idle -> Unit
            is ProphetState.Running -> Text("計算中…")
            is ProphetState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is ProphetState.Done -> ProphetChart(s.result)
        }
    }
}

@Composable
private fun ProphetChart(r: ProphetResult) {
    val nh = r.histDays.size
    val nf = r.futureDays.size
    val labels = remember(r) { (r.histDays + r.futureDays).map { DateUtil.format(it) } }
    val actual = remember(r) { r.histActual + List(nf) { Double.NaN } }
    val fit = remember(r) { r.histFit + r.yhat }
    val bandUp = remember(r) { List(nh) { Double.NaN } + r.upper }
    val bandLow = remember(r) { List(nh) { Double.NaN } + r.lower }
    val end = nf - 1
    Text(
        "預測 $nf 個交易日後（${DateUtil.format(r.futureDays[end])}）：" +
            "${f2(r.yhat[end])}（區間 ${f2(r.lower[end])} ～ ${f2(r.upper[end])}）",
        style = MaterialTheme.typography.bodyMedium
    )
    ZoomableLineChart(
        labels = labels,
        series = listOf(
            ChartSeries("實際收盤", cGray, actual, 1.2f),
            ChartSeries("Prophet 擬合／預測", cOrange, fit, 1.8f)
        ),
        band = bandUp to bandLow,
        bandName = "信賴區間",
        bandColor = cOrange,
        initialVisible = 250
    )
}
