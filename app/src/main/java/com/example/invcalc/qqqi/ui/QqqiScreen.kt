package com.example.invcalc.qqqi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.invcalc.NumField
import com.example.invcalc.ScreenColumn
import com.example.invcalc.num
import com.example.invcalc.qqqi.Div
import com.example.invcalc.qqqi.QqqiApi
import com.example.invcalc.qqqi.QqqiCalc
import com.example.invcalc.stock.DateUtil
import com.example.invcalc.team.ui.Collapsible
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 輸入值與最新資料放在程序層級，切換分頁（頁面被回收）後不會遺失。 */
object QqqiSession {
    var amountText by mutableStateOf("1000000")  // A 投入金額（NT）
    var priceText by mutableStateOf("")          // B QQQI 股價（USD）
    var divText by mutableStateOf("")            // D 月股息（平均值，USD）
    var fxText by mutableStateOf("32")           // H 匯率（USD → NT）
    var rateText by mutableStateOf("")           // J 利率（年利率 %）
    var monthsText by mutableStateOf("")         // K 貸款期數（月）

    var status by mutableStateOf("")
    var fxNote by mutableStateOf("")
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var allDividends by mutableStateOf(listOf<Div>())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun refresh() {
        if (loading) return
        loading = true
        status = "取得 QQQI 股價、配息與匯率中…"
        scope.launch {
            try {
                val fxJob = async(Dispatchers.IO) {
                    try {
                        Result.success(QqqiApi.fetchUsdTwd())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure<Double>(e)
                    }
                }
                val etfJob = async(Dispatchers.IO) {
                    try {
                        Result.success(QqqiApi.fetchQqqi())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure<com.example.invcalc.qqqi.ChartInfo>(e)
                    }
                }
                val fx = fxJob.await()
                val etf = etfJob.await()

                // H 匯率：取得失敗就用預設 32
                val rate = fx.getOrNull()
                if (rate != null) {
                    fxText = QqqiCalc.plain(rate, 4)
                    fxNote = "即時匯率（Yahoo Finance）"
                } else {
                    fxText = QqqiCalc.plain(QqqiApi.DEFAULT_FX, 4)
                    fxNote = "匯率取得失敗，已使用預設值 ${QqqiCalc.plain(QqqiApi.DEFAULT_FX, 0)}（可手動修改）"
                }

                val info = etf.getOrNull()
                if (info == null) {
                    status = "QQQI 資料取得失敗：${etf.exceptionOrNull()?.message ?: "未知錯誤"}。請檢查網路後按「重新取得」，或手動輸入股價與月股息。"
                } else {
                    val problems = ArrayList<String>()
                    if (info.price != null) priceText = QqqiCalc.plain(info.price, 4) else problems.add("查無股價")
                    val avg = QqqiApi.averageDividend(info.dividends)
                    if (avg != null) divText = QqqiCalc.plain(avg, 6) else problems.add("查無配息資料")
                    allDividends = info.dividends
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    status = if (problems.isEmpty()) "已更新（$now）" else "已更新（$now），但${problems.joinToString("、")}，請手動輸入"
                }
            } finally {
                loading = false
                loaded = true
            }
        }
    }
}

private fun fmtInt(v: Long) = String.format(Locale.US, "%,d", v)
private fun fmtNt(v: Long) = "NT$ " + fmtInt(v)

/** 整數金額不顯示小數，否則顯示到小數 2 位。 */
private fun fmtAmount(v: Double): String =
    if (v == Math.floor(v)) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

/** 期數只接受整數。 */
@Composable
private fun OutlinedMonths(value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text("K　貸款期數（月）") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun QqqiTab() {
    val s = QqqiSession
    LaunchedEffect(Unit) { if (!s.loaded && !s.loading) s.refresh() }

    val a = s.amountText.num()
    val b = s.priceText.num()
    val d = s.divText.num()
    val h = s.fxText.num()
    val r = if (a != null && b != null && d != null && h != null) QqqiCalc.compute(a, h, b, d) else null

    ScreenColumn {
        Text("QQQI 股息試算", style = MaterialTheme.typography.titleLarge)
        Text(
            "輸入投資金額（NT），自動換算可買股數與每月、每年的股息收入。股價、配息與匯率從網路取得（Yahoo Finance），也可手動修改。",
            style = MaterialTheme.typography.bodySmall
        )

        // ---------------------------------------------------------------- 輸入與即時資料
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("A　投入金額（NT）", s.amountText) { s.amountText = it }
                NumField("B　QQQI 股價（USD，自動取得，可修改）", s.priceText) { s.priceText = it }
                NumField("H　匯率（USD → NT，取不到時預設 32）", s.fxText) { s.fxText = it }
                NumField("D　月股息・平均值（USD，近 6 個月配息平均）", s.divText) { s.divText = it }
                if (s.fxNote.isNotBlank()) Text(s.fxNote, style = MaterialTheme.typography.bodySmall)
                if (s.status.isNotBlank()) Text(s.status, style = MaterialTheme.typography.bodySmall)
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = { s.refresh() }, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.loading) "取得中…" else "重新取得最新資料")
                }
            }
        }

        // ---------------------------------------------------------------- 試算結果
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("試算結果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (r == null) {
                    Text("請輸入有效數字：投入金額需 ≥ 0，股價與匯率需 > 0，月股息需 ≥ 0。", color = MaterialTheme.colorScheme.error)
                } else {
                    ResultRow("C　股數（股）", "${fmtInt(r.shares)} 股", "C = A ÷ H ÷ B，小數點捨去")
                    ResultRow("E　月股息收入（USD）", "US$ " + String.format(Locale.US, "%,.2f", r.monthlyUsd), "E = C × D")
                    ResultRow("F　月股息收入（NT）", fmtNt(r.monthlyNt), "F = E × H，小數點四捨五入")
                    ResultRow("G　年利息（NT）", fmtNt(r.yearlyNt), "G = F × 12，小數點四捨五入", highlight = true)
                    Text(
                        "實際買入約 NT$ ${String.format(Locale.US, "%,.0f", r.investedNt)}" +
                            "（${fmtInt(r.shares)} 股，零頭約 NT$ ${String.format(Locale.US, "%,.0f", (a ?: 0.0) - r.investedNt)} 未使用）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---------------------------------------------------------------- 貸款估算
        val j = s.rateText.num()
        val k = s.monthsText.toIntOrNull()
        val loanOk = a != null && j != null && k != null && k in 1..1200
        val l = if (loanOk) QqqiCalc.loanPayment(a!!, j!!, k!!) else null
        val m = if (l != null && r != null) QqqiCalc.monthlyBalance(r.monthlyNt, l) else null
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("貸款估算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("假設以貸款方式取得投入金額，用每月股息收入來支應每月繳款。", style = MaterialTheme.typography.bodySmall)
                ResultRow(
                    "I　貸款金額（NT）",
                    if (a != null) "NT$ " + fmtAmount(a) else "—",
                    "I = A（同投入金額）"
                )
                NumField("J　利率（年利率 %）", s.rateText) { s.rateText = it }
                OutlinedMonths(s.monthsText) { s.monthsText = it }
                if (!loanOk) {
                    Text("請輸入利率（≥ 0）與貸款期數（1–1200 個月的整數），才會計算每月繳款。", style = MaterialTheme.typography.bodySmall)
                } else if (l != null) {
                    ResultRow("L　每月繳款（NT）", fmtNt(l), "本息平均攤還，小數點四捨五入")
                    if (m != null) {
                        ResultRow(
                            "M　每月平衡預估（NT）",
                            if (m >= 0) "NT$ +" + fmtInt(m) else "-NT$ " + fmtInt(-m),
                            "M = F − L（每月股息收入 − 每月繳款）",
                            highlight = true,
                            valueColor = if (m >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            if (m >= 0) "每月股息收入足以支應繳款，約結餘 NT$ ${fmtInt(m)}。"
                            else "每月股息收入不足以支應繳款，每月約需自行補貼 NT$ ${fmtInt(-m)}。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("上方的股息試算尚無有效結果，無法計算每月平衡。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 配息明細
        val last = QqqiApi.lastDividends(s.allDividends)
        Collapsible(
            "近 ${QqqiApi.AVG_COUNT} 次配息明細",
            subtitle = if (last.isEmpty()) "尚無資料" else "共 ${last.size} 筆，平均 US$ ${QqqiCalc.plain(QqqiApi.averageDividend(last) ?: 0.0, 4)}",
            key = "qqqi:dividends"
        ) {
            if (last.isEmpty()) {
                Text("沒有取得配息資料，請按「重新取得最新資料」，或在上方手動輸入月股息。", style = MaterialTheme.typography.bodyMedium)
            } else {
                last.reversed().forEach {
                    Row(Modifier.fillMaxWidth()) {
                        Text(DateUtil.format(it.epochDay), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("US$ " + String.format(Locale.US, "%.4f", it.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("月股息（D）＝以上各次配息的平均值。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "提醒：QQQI 每月配息金額會變動，過去配息不代表未來；試算未計入股息預扣稅、手續費、匯差與貸款相關費用，僅供參考，不構成投資建議。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    formula: String,
    highlight: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (valueColor != androidx.compose.ui.graphics.Color.Unspecified) valueColor
                else if (highlight) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified
            )
        }
        Text(formula, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
