package com.example.invcalc.stock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
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
import com.example.invcalc.stock.TwNames
import com.example.invcalc.stock.SymbolUtil
import com.example.invcalc.stock.YahooApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var showDelete by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragList by remember { mutableStateOf<List<Stock>?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeight by remember { mutableStateOf(0f) }

    fun update(list: List<Stock>) {
        stocks = list
        store.save(list)
    }

    // 已存的台股若還是英文名稱，背景補上中文簡稱
    LaunchedEffect(Unit) {
        val targets = stocks.filter { SymbolUtil.isTaiwanSymbol(it.symbol) && !SymbolUtil.hasCjk(it.name) }
        if (targets.isEmpty()) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) {
            targets.mapNotNull { st -> TwNames.lookup(st.symbol)?.let { st.symbol to it.name } }.toMap()
        }
        if (found.isNotEmpty()) update(stocks.map { st -> found[st.symbol]?.let { st.copy(name = it) } ?: st })
    }

    val current = stocks.firstOrNull { it.symbol == selected }
    if (current != null) {
        BackHandler { selected = null }
        StockDetail(current, onBack = { selected = null })
        return
    }

    ScreenColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Text("新增股票") }
            OutlinedButton(
                onClick = { checked = emptySet(); showDelete = true },
                enabled = stocks.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("刪除") }
        }
        if (stocks.isEmpty()) {
            Text(
                "尚未新增股票。台股輸入數字即可（例如 2330，上櫃請輸入 6488.TWO），美股輸入代號（例如 AAPL）。",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("點一下查看分析，長按後上下拖曳可調整順序。", style = MaterialTheme.typography.bodySmall)
        }
        val spacingPx = with(LocalDensity.current) { 12.dp.toPx() }
        (dragList ?: stocks).forEach { stock ->
            key(stock.symbol) {
                val isDragging = draggingKey == stock.symbol
                Card(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .onSizeChanged { itemHeight = it.height.toFloat() }
                        .pointerInput(stock.symbol) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = stock.symbol
                                    dragOffset = 0f
                                    dragList = stocks
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val list = dragList
                                    if (list != null) {
                                        val idx = list.indexOfFirst { it.symbol == stock.symbol }
                                        val step = itemHeight + spacingPx
                                        if (dragOffset > step / 2 && idx in 0 until list.size - 1) {
                                            val m = list.toMutableList()
                                            m.add(idx + 1, m.removeAt(idx))
                                            dragList = m
                                            dragOffset -= step
                                        } else if (dragOffset < -step / 2 && idx > 0) {
                                            val m = list.toMutableList()
                                            m.add(idx - 1, m.removeAt(idx))
                                            dragList = m
                                            dragOffset += step
                                        }
                                    }
                                },
                                onDragEnd = {
                                    dragList?.let { update(it) }
                                    dragList = null
                                    draggingKey = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragList = null
                                    draggingKey = null
                                    dragOffset = 0f
                                }
                            )
                        }
                        .clickable { selected = stock.symbol }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        Text("≡", style = MaterialTheme.typography.titleLarge)
                    }
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
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("選擇要刪除的股票") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (checked.size == stocks.size) emptySet() else stocks.map { it.symbol }.toSet()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked.size == stocks.size && stocks.isNotEmpty(),
                            onCheckedChange = null
                        )
                        Text("全選", Modifier.padding(start = 8.dp))
                    }
                    stocks.forEach { st ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = if (st.symbol in checked) checked - st.symbol else checked + st.symbol
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = st.symbol in checked, onCheckedChange = null)
                            Text(
                                "${st.name.ifBlank { st.symbol }}（${st.symbol}）",
                                Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = checked.isNotEmpty(),
                    onClick = {
                        update(stocks.filter { it.symbol !in checked })
                        checked = emptySet()
                        showDelete = false
                    }
                ) { Text("刪除（${checked.size}）") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

private sealed interface NameState {
    object Idle : NameState
    object Searching : NameState
    class Found(val symbol: String, val name: String) : NameState
    object NotFound : NameState
}

/** 依代號找名稱：台股用證交所／櫃買中心的中文簡稱，其餘（或查無時）用 Yahoo 名稱。需在背景執行緒呼叫。 */
private fun findName(input: String): TwNames.Match? {
    TwNames.lookup(input)?.let { return it }
    val raw = input.trim().uppercase(Locale.US)
    val candidates = if (SymbolUtil.isTaiwanCode(raw)) listOf("$raw.TW", "$raw.TWO") else listOf(SymbolUtil.normalize(raw))
    for (c in candidates) {
        try {
            return TwNames.Match(c, YahooApi.fetchNameBlocking(c))
        } catch (e: Exception) {
            // 試下一個
        }
    }
    return null
}

@Composable
private fun AddStockDialog(existing: List<String>, onDismiss: () -> Unit, onAdded: (Stock) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf<NameState>(NameState.Idle) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 輸入代號後自動帶入名稱（停止輸入 0.5 秒後查詢）
    LaunchedEffect(symbol) {
        if (symbol.isBlank()) {
            lookup = NameState.Idle
            return@LaunchedEffect
        }
        lookup = NameState.Searching
        delay(500)
        val found = try {
            withContext(Dispatchers.IO) { findName(symbol) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (found != null) {
            lookup = NameState.Found(found.symbol, found.name)
            if (!nameTouched) name = found.name
        } else {
            lookup = NameState.NotFound
            if (!nameTouched) name = ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("新增股票") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it; error = null },
                    label = { Text("股票代號（如 2330、6488、AAPL）") },
                    singleLine = true,
                    enabled = !loading
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameTouched = true },
                    label = { Text("名稱（自動帶入，可修改）") },
                    singleLine = true,
                    enabled = !loading
                )
                when (val l = lookup) {
                    is NameState.Searching -> Text("查詢名稱中…", style = MaterialTheme.typography.bodySmall)
                    is NameState.Found -> Text("✔ ${l.symbol}：${l.name}", style = MaterialTheme.typography.bodySmall)
                    is NameState.NotFound -> Text(
                        "查不到名稱（仍可按新增，會連線驗證代號）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    is NameState.Idle -> Unit
                }
                if (loading) Text("新增中…", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && symbol.isNotBlank(),
                onClick = {
                    val found = lookup as? NameState.Found
                    val raw = symbol.trim().uppercase(Locale.US)
                    val candidates = when {
                        found != null -> listOf(found.symbol)
                        SymbolUtil.isTaiwanCode(raw) -> listOf("$raw.TW", "$raw.TWO")
                        else -> listOf(SymbolUtil.normalize(raw))
                    }
                    if (candidates.any { it in existing }) {
                        error = "已在清單中"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        var parsed: YahooApi.Parsed? = null
                        var usedSymbol = ""
                        var lastError: String? = null
                        for (c in candidates) {
                            try {
                                parsed = withContext(Dispatchers.IO) { YahooApi.fetchBlocking(c) }
                                usedSymbol = c
                                break
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                lastError = e.message
                            }
                        }
                        if (parsed == null) {
                            error = lastError ?: "查詢失敗，請檢查代號與網路"
                            loading = false
                            return@launch
                        }
                        StockCache.put(usedSymbol, parsed.bars)
                        onAdded(Stock(usedSymbol, name.trim().ifBlank { found?.name ?: parsed.name }))
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
                SortinoSection(s.bars)
                TreynorSection(stock, s.bars)
                CapeSection(stock, s.bars)
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
private val cProphet = Color(0xFF0072B2) // Prophet 官方圖的藍色

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

// ---------------------------------------------------------------- 2. 布林通道 + 成交量

@Composable
private fun BollingerSection(bars: List<Bar>) {
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    var maText by rememberSaveable { mutableStateOf("20") }
    var kText by rememberSaveable { mutableStateOf("2") }
    val period = Period.values()[periodIdx]
    val data = remember(bars, periodIdx) { Resample.toPeriod(bars, period) }
    val window = maText.toIntOrNull()
    val k = kText.num()
    val paramsOk = window != null && k != null && window in 2..250 && k > 0.0 && k <= 10.0
    val res = remember(data, window, k) {
        if (window != null && k != null && window in 2..250 && k > 0.0 && k <= 10.0)
            Indicators.bollinger(data, window, k) else null
    }
    val vol = remember(data) { Indicators.volumeFull(data, 20, 2.0) }

    SectionCard("2. 布林通道與成交量") {
        PeriodChips(periodIdx) { periodIdx = it }
        NumField("均線根數（預設 20，範圍 2–250）", maText) { maText = it.filter(Char::isDigit) }
        NumField("標準差倍數（預設 2，範圍 0–10）", kText) { kText = it }
        OutlinedButton(onClick = { maText = "20"; kText = "2" }) { Text("還原預設值（20、2）") }
        if (!paramsOk) {
            Text("參數無效：均線根數需為 2–250 的整數，標準差倍數需大於 0 且不超過 10。", color = MaterialTheme.colorScheme.error)
        } else if (res == null) {
            Text("資料不足：${period.label}至少需要 $window 根 K 棒。")
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
                "${window} 根均線 ± ${kText} 倍標準差\n" +
                    "最新${period.label}：上軌 ${f2(res.upper[i])}、中軌 ${f2(res.mid[i])}、下軌 ${f2(res.lower[i])}\n" +
                    "$position（%B = ${f2(pctB)}%）",
                style = MaterialTheme.typography.bodyMedium
            )
            val labels = res.dates.map { DateUtil.format(it) }
            // 兩張圖共用同一個檢視狀態：日期完全對齊，縮放與平移同步
            val view = rememberChartViewState(labels.size, 150)
            ZoomableLineChart(
                labels = labels,
                series = listOf(
                    ChartSeries("收盤價", cBlue, res.close),
                    ChartSeries("中軌", cOrange, res.mid, 1.2f),
                    ChartSeries("上軌", cRed, res.upper, 1.2f),
                    ChartSeries("下軌", cGreen, res.lower, 1.2f)
                ),
                band = res.upper to res.lower,
                bandName = "通道",
                bandColor = cGray,
                state = view,
                showHint = false
            )
            // 成交量：與上圖相同的日期範圍（前 window-1 根沒有布林通道，故從 offset 起）
            val off = data.size - labels.size
            Text("成交量（日期與上圖對齊）", style = MaterialTheme.typography.labelLarge)
            ZoomableLineChart(
                labels = labels,
                series = listOf(
                    ChartSeries("成交量", cBlue, vol.volume.subList(off, data.size), 1.2f),
                    ChartSeries("均量（20 根）", cOrange, vol.average.subList(off, data.size), 1.6f),
                    ChartSeries("2 倍門檻", cRed, vol.threshold.subList(off, data.size), 1.2f)
                ),
                chartHeight = 150.dp,
                state = view
            )
            Text(
                "成交量線高過紅色門檻線（均量的 2 倍）的日期即為量能突高。兩張圖同步縮放與拖曳。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------- 3. 交易量突高

@Composable
private fun VolumeSection(bars: List<Bar>) {
    val res = remember(bars) { Indicators.volumeSpike(bars, 20, 2.0) }
    val series = remember(bars) { Indicators.volumeSeries(bars, 20, 2.0) }
    SectionCard("3. 交易量突高判斷") {
        if (res == null || series == null) {
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
                    "倍數：${f2(res.ratio)} 倍（判斷標準：當日量 ≥ 月均量的 2 倍）\n" +
                    "近一年（約 250 個交易日）量能突高天數：${series.spikeFlags.takeLast(250).count { it }} 天",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("成交量曲線圖請見上方「2. 布林通道與成交量」下方。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- 4. 索提諾比率

@Composable
private fun SortinoSection(bars: List<Bar>) {
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    var rf by rememberSaveable { mutableStateOf("1.5") }
    val period = Period.values()[periodIdx]
    val data = remember(bars, periodIdx) { Resample.toPeriod(bars, period) }
    val res = remember(data, periodIdx, rf) { Indicators.sortino(data, period, rf.num() ?: 0.0) }

    SectionCard("4. 索提諾比率（Sortino Ratio）") {
        Text(
            "與夏普值類似，但只把「低於無風險利率的下跌波動」視為風險，不懲罰向上的波動。",
            style = MaterialTheme.typography.bodySmall
        )
        PeriodChips(periodIdx) { periodIdx = it }
        NumField("無風險利率（年化 %）", rf) { rf = it }
        if (res == null) {
            Text("資料不足：${period.label}至少需要 ${period.sharpeWindow + 1} 根 K 棒。")
        } else {
            val latest = res.rolling.lastOrNull { it.isFinite() } ?: Double.NaN
            Text(
                "最新滾動索提諾比率（近 ${res.window} 根${period.label}，年化）：${f2(latest)}\n" +
                    "整段期間索提諾比率：${f2(res.overall)}\n" +
                    "數值越高代表每承擔一單位下檔風險所得的超額報酬越高；期間內完全沒有下跌時無法計算（圖上斷線）。",
                style = MaterialTheme.typography.bodyMedium
            )
            ZoomableLineChart(
                labels = res.dates.map { DateUtil.format(it) },
                series = listOf(ChartSeries("滾動索提諾比率", cGreen, res.rolling)),
                initialVisible = 150
            )
        }
    }
}

// ---------------------------------------------------------------- 5. 崔納比率

@Composable
private fun TreynorSection(stock: Stock, bars: List<Bar>) {
    val taiwan = SymbolUtil.isTaiwanSymbol(stock.symbol)
    val indexSymbol = if (taiwan) "^TWII" else "^GSPC"
    val indexName = if (taiwan) "台灣加權指數" else "S&P 500 指數"
    var idx by remember(indexSymbol) { mutableStateOf<LoadState>(LoadState.Loading) }
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    var rf by rememberSaveable { mutableStateOf("1.5") }
    val period = Period.values()[periodIdx]

    LaunchedEffect(indexSymbol) {
        idx = try {
            val b = StockCache.get(indexSymbol)
                ?: withContext(Dispatchers.IO) { YahooApi.fetchBlocking(indexSymbol).bars }
            StockCache.put(indexSymbol, b)
            LoadState.Ready(b)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: "載入失敗")
        }
    }
    val idxBars = (idx as? LoadState.Ready)?.bars
    val res = remember(bars, idxBars, periodIdx, rf) {
        idxBars?.let { Indicators.treynor(bars, it, period, rf.num() ?: 0.0) }
    }

    SectionCard("5. 崔納比率（Treynor Ratio）") {
        Text(
            "每承擔一單位「市場風險（Beta）」所得到的年化超額報酬，市場基準為$indexName。",
            style = MaterialTheme.typography.bodySmall
        )
        PeriodChips(periodIdx) { periodIdx = it }
        NumField("無風險利率（年化 %）", rf) { rf = it }
        when (val s = idx) {
            is LoadState.Loading -> Text("載入大盤資料中…")
            is LoadState.Failed -> Text("大盤資料載入失敗：${s.message}", color = MaterialTheme.colorScheme.error)
            is LoadState.Ready -> if (res == null) {
                Text("資料不足：${period.label}與大盤重疊的資料至少需要 ${period.sharpeWindow + 1} 根 K 棒。")
            } else {
                val latest = res.treynorPct.lastOrNull { it.isFinite() } ?: Double.NaN
                val latestBeta = res.beta.lastOrNull { it.isFinite() } ?: Double.NaN
                Text(
                    "最新滾動崔納比率（近 ${res.window} 根${period.label}）：${f2(latest)}%（Beta ${f2(latestBeta)}）\n" +
                        "整段期間：崔納比率 ${f2(res.overallTreynorPct)}%、Beta ${f2(res.overallBeta)}\n" +
                        "Beta > 1 代表波動大於大盤；崔納比率越高越好。Beta 接近 0 時比率無意義（圖上斷線）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                val labels = res.dates.map { DateUtil.format(it) }
                ZoomableLineChart(
                    labels = labels,
                    series = listOf(ChartSeries("滾動崔納比率（%）", cBlue, res.treynorPct)),
                    initialVisible = 150
                )
                ZoomableLineChart(
                    labels = labels,
                    series = listOf(ChartSeries("滾動 Beta", cOrange, res.beta)),
                    initialVisible = 150
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 6. 席勒本益比

@Composable
private fun CapeSection(stock: Stock, bars: List<Bar>) {
    var eps by remember(stock.symbol) { mutableStateOf<List<Indicators.EpsPoint>?>(StockCache.getEps(stock.symbol)) }
    LaunchedEffect(stock.symbol) {
        if (eps == null) {
            val list = try {
                withContext(Dispatchers.IO) { YahooApi.fetchEpsBlocking(stock.symbol) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            StockCache.putEps(stock.symbol, list)
            eps = list
        }
    }
    val monthly = remember(bars) { Resample.toPeriod(bars, Period.MONTH) }
    val e = eps
    val res = remember(monthly, e) { e?.let { Indicators.cape(monthly, it) } }

    SectionCard("6. 席勒本益比（Shiller P/E，CAPE）") {
        Text(
            "股價 ÷ 過去最多 10 年的平均年度 EPS，用來平滑景氣循環。本程式未做通膨調整；" +
                "Yahoo 免費資料通常只有近 4–5 年的年度 EPS，因此多半不足 10 年，數值僅供參考。",
            style = MaterialTheme.typography.bodySmall
        )
        when {
            e == null -> Text("載入財報 EPS 中…")
            e.isEmpty() -> Text("Yahoo 未提供此股票的歷年 EPS，無法計算席勒本益比。")
            res == null -> Text("EPS 平均為負或資料不足，無法計算。")
            else -> {
                val ratio = res.latest / res.median
                val verdict = when {
                    ratio > 1.2 -> "高於自身歷史中位數（相對偏貴）"
                    ratio < 0.8 -> "低於自身歷史中位數（相對偏便宜）"
                    else -> "接近自身歷史中位數"
                }
                Text(
                    "最新席勒本益比：${f2(res.latest)} 倍\n" +
                        "採用 ${res.yearsUsed} 年平均 EPS：${f2(res.avgEps)}\n" +
                        "此區間歷史中位數：${f2(res.median)} 倍\n$verdict",
                    style = MaterialTheme.typography.bodyMedium
                )
                ZoomableLineChart(
                    labels = res.dates.map { DateUtil.format(it) },
                    series = listOf(
                        ChartSeries("席勒本益比", cBlue, res.cape),
                        ChartSeries("歷史中位數", cGray, List(res.cape.size) { res.median }, 1.2f)
                    ),
                    initialVisible = 120
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 7. Prophet

private sealed interface ProphetState {
    object Running : ProphetState
    class Failed(val message: String) : ProphetState
    class Done(val result: ProphetResult, val trainDays: Int) : ProphetState
}

/** 訓練資料取最近約 5 年（1260 個交易日）的日線收盤價。 */
private const val PROPHET_TRAIN_BARS = 1260

@Composable
private fun ProphetSection(bars: List<Bar>) {
    var state by remember(bars) { mutableStateOf<ProphetState>(ProphetState.Running) }

    LaunchedEffect(bars) {
        state = ProphetState.Running
        state = try {
            val recent = bars.takeLast(PROPHET_TRAIN_BARS)
            // 與 Prophet 的 "auto" 相同：資料跨度 ≥ 2 年才啟用年季節性，週季節性一律啟用；其餘為內建預設值
            val spanDays = recent.last().epochDay - recent.first().epochDay
            val params = ProphetParams(yearlySeasonality = spanDays >= 730, weeklySeasonality = true)
            val res = withContext(Dispatchers.Default) {
                Prophet.fit(recent.map { it.epochDay }, recent.map { it.close }, params)
            }
            ProphetState.Done(res, recent.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProphetState.Failed(e.message ?: "預測失敗")
        }
    }

    SectionCard("7. Prophet 預測") {
        Text(
            "使用 Facebook Prophet 內建預設參數（changepoint_prior_scale 0.05、seasonality_prior_scale 10、" +
                "25 個轉折點、轉折點範圍 80%、信賴區間 80%），以近約 5 年日線收盤價訓練，預測未來 90 天內的交易日（不含週六日）。",
            style = MaterialTheme.typography.bodySmall
        )
        when (val s = state) {
            is ProphetState.Running -> Text("計算中…")
            is ProphetState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is ProphetState.Done -> ProphetChart(s.result, s.trainDays)
        }
    }
}

@Composable
private fun ProphetChart(r: ProphetResult, trainDays: Int) {
    val nh = r.histDays.size
    val nf = r.futureDays.size
    val labels = remember(r) { (r.histDays + r.futureDays).map { DateUtil.format(it) } }
    val nan = Double.NaN
    val actual = remember(r) { r.histActual + List(nf) { nan } }
    val fit = remember(r) { r.histFit + r.yhat }
    val bandLow = remember(r) { r.histLower + r.lower }
    val bandUp = remember(r) { r.histUpper + r.upper }
    val end = nf - 1
    Text(
        "訓練資料 $trainDays 個交易日；預測至 ${DateUtil.format(r.futureDays[end])}（$nf 個交易日）：" +
            "${f2(r.yhat[end])}（區間 ${f2(r.lower[end])} ～ ${f2(r.upper[end])}）",
        style = MaterialTheme.typography.bodyMedium
    )
    ZoomableLineChart(
        labels = labels,
        series = listOf(
            ChartSeries("實際收盤", MaterialTheme.colorScheme.onSurface, actual, dots = true),
            ChartSeries("預測 yhat", cProphet, fit, 1.6f)
        ),
        band = bandUp to bandLow,
        bandName = "信賴區間",
        bandColor = cProphet,
        chartHeight = 300.dp,
        initialVisible = nh + nf // 與 Prophet 官方圖相同：預設顯示整段歷史與預測
    )
}
