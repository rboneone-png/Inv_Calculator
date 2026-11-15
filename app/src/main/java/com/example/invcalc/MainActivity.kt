package com.example.invcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

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

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "投資計算機",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> CompoundScreen()
            1 -> DcaScreen()
            2 -> ReturnScreen()
            else -> LoanScreen()
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

@Composable
fun LoanScreen() {
    var amount by rememberSaveable { mutableStateOf("1000000") }
    var rate by rememberSaveable { mutableStateOf("2.5") }
    var years by rememberSaveable { mutableStateOf("20") }
    ScreenColumn {
        NumField("貸款金額", amount) { amount = it }
        NumField("年利率 (%)", rate) { rate = it }
        NumField("貸款年數", years) { years = it }
        val a = amount.num(); val r = rate.num(); val y = years.num()
        ResultCard(
            if (a == null || r == null || y == null || a <= 0 || r < 0 || (y * 12).toInt() <= 0) null
            else Calculations.loan(a, r, y).let {
                listOf(
                    "每月還款（本息平均攤還）" to money(it.monthlyPayment),
                    "還款總額" to money(it.totalPayment),
                    "利息總額" to money(it.totalInterest)
                )
            }
        )
    }
}
