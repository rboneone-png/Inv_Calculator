package com.example.invcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private val tabs = listOf("複利", "定期定額", "報酬率", "貸款")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "投資計算機",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(title) }
                )
            }
        }
        // 左右滑動切換分頁
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> CompoundScreen()
                1 -> DcaScreen()
                2 -> ReturnScreen()
                else -> LoanScreen()
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultCard(lines: List<Pair<String, String>>?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lines == null) {
                Text("請輸入有效數字", style = MaterialTheme.typography.bodyMedium)
            } else {
                lines.forEach { (k, v) ->
                    Text(k, style = MaterialTheme.typography.labelMedium)
                    Text(v, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
private fun pct(v: Double) = String.format(Locale.US, "%.2f%%", v)
private fun String.num(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
fun CompoundScreen() {
    var principal by rememberSaveable { mutableStateOf("100000") }
    var rate by rememberSaveable { mutableStateOf("5") }
    var years by rememberSaveable { mutableStateOf("10") }
    var perYear by rememberSaveable { mutableStateOf("12") }
    ScreenColumn {
        NumField("本金", principal) { principal = it }
        NumField("年利率 (%)", rate) { rate = it }
        NumField("年數", years) { years = it }
        NumField("每年複利次數（12=月複利）", perYear) { perYear = it }
        val p = principal.num(); val r = rate.num(); val y = years.num()
        val n = perYear.toIntOrNull()
        ResultCard(
            if (p == null || r == null || y == null || n == null || n <= 0 || y < 0) null
            else Calculations.compound(p, r, y, n).let {
                listOf("期末金額" to money(it.futureValue), "利息收益" to money(it.totalInterest))
            }
        )
    }
}

@Composable
fun DcaScreen() {
    var initial by rememberSaveable { mutableStateOf("0") }
    var monthly by rememberSaveable { mutableStateOf("10000") }
    var rate by rememberSaveable { mutableStateOf("7") }
    var years by rememberSaveable { mutableStateOf("20") }
    ScreenColumn {
        NumField("初始本金", initial) { initial = it }
        NumField("每月投入", monthly) { monthly = it }
        NumField("預期年化報酬率 (%)", rate) { rate = it }
        NumField("投資年數", years) { years = it }
        val a = initial.num(); val m = monthly.num(); val r = rate.num(); val y = years.num()
        ResultCard(
            if (a == null || m == null || r == null || y == null || y < 0) null
            else Calculations.dca(a, m, r, y).let {
                listOf(
                    "期末資產" to money(it.futureValue),
                    "總投入本金" to money(it.totalInvested),
                    "投資獲利" to money(it.gain)
                )
            }
        )
    }
}

@Composable
fun ReturnScreen() {
    var initial by rememberSaveable { mutableStateOf("100000") }
    var final by rememberSaveable { mutableStateOf("150000") }
    var years by rememberSaveable { mutableStateOf("3") }
    ScreenColumn {
        NumField("初始金額", initial) { initial = it }
        NumField("期末金額", final) { final = it }
        NumField("持有年數", years) { years = it }
        val a = initial.num(); val b = final.num(); val y = years.num()
        ResultCard(
            if (a == null || b == null || y == null || a <= 0 || b < 0 || y <= 0) null
            else Calculations.returnRate(a, b, y).let {
                listOf(
                    "損益金額" to money(it.profit),
                    "總報酬率" to pct(it.totalReturnPct),
                    "年化報酬率 (CAGR)" to pct(it.cagrPct)
                )
            }
        )
    }
}

private val loanTypes = listOf("房貸", "信貸", "車貸", "其他")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen() {
    var type by rememberSaveable { mutableStateOf(loanTypes[0]) }
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("0") }
    var day by rememberSaveable { mutableIntStateOf(5) }
    var dayExpanded by remember { mutableStateOf(false) }

    val a = amount.num()
    val r = rate.num()
    val n = months.toIntOrNull()
    val k = paid.toIntOrNull()
    val result =
        if (a != null && r != null && n != null && k != null &&
            a > 0 && r >= 0 && n > 0 && k in 0..n
        ) Calculations.loanByMonths(a, r, n) else null

    ScreenColumn {
        Text("貸款型式", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            loanTypes.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名稱／備註（選填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        NumField("貸款金額（元）", amount) { amount = it }
        NumField("利率（年利率 %）", rate) { rate = it }
        OutlinedTextField(
            value = months,
            onValueChange = { months = it.filter(Char::isDigit) },
            label = { Text("貸款期數（每月一期）") },
            supportingText = { Text("例如 20 年 = 240 期") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = paid,
            onValueChange = { paid = it.filter(Char::isDigit) },
            label = { Text("已繳期數") },
            supportingText = { Text("建立時已繳的期數；之後每個繳款日自動再繳一期") },
            isError = n != null && k != null && k > n,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = result?.let { String.format(Locale.US, "%,d", it.monthlyPayment.roundToLong()) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("每月繳款（自動計算，元）") },
            supportingText = {
                Text("本息平均攤還：貸款金額、利率、期數算出每月固定金額，四捨五入到整數")
            },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenuBox(
            expanded = dayExpanded,
            onExpandedChange = { dayExpanded = it },
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            OutlinedTextField(
                value = "$day 日",
                onValueChange = {},
                readOnly = true,
                label = { Text("繳款日（每月幾號）") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                (1..31).forEach { d ->
                    DropdownMenuItem(
                        text = { Text("$d 日") },
                        onClick = { day = d; dayExpanded = false }
                    )
                }
            }
        }
        if (result != null && a != null && r != null && n != null && k != null) {
            ResultCard(
                listOf(
                    "還款總額" to money(result.totalPayment),
                    "利息總額" to money(result.totalInterest),
                    "剩餘期數" to "${n - k} 期",
                    "剩餘本金" to money(Calculations.remainingBalance(a, r, n, k).coerceAtLeast(0.0))
                )
            )
        }
    }
}
