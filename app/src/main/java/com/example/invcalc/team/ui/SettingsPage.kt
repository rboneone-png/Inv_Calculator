package com.example.invcalc.team.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.example.invcalc.AppTheme
import com.example.invcalc.ScreenColumn
import com.example.invcalc.team.ClaudeClient

/** 版本說明（新版本寫在最上面）。 */
object VersionNotes {
    class Entry(val version: String, val items: List<String>)

    val entries = listOf(
        Entry(
            "1.2.10",
            listOf("股票分析 Prophet 預測：開放參數讓使用者輸入（預測天數、趨勢彈性、季節性強度、轉折點數量與範圍、信賴區間、年／週季節性），可一鍵恢復預設")
        ),
        Entry(
            "1.2.9",
            listOf("上方功能選項改為兩排顯示，不用左右捲動即可看到全部分頁")
        ),
        Entry(
            "1.2.8",
            listOf(
                "股票分析：長按股票後上下拖曳即可調整順序（移除 ▲▼ 按鈕與長按刪除）",
                "股票分析：新增「刪除」按鍵，跳出清單可多選股票後一次刪除（含全選）"
            )
        ),
        Entry(
            "1.2.7",
            listOf("QQQI 分頁：各群組都可展開／收合。「試算結果」與「近 6 次配息明細」預設收合，「輸入與即時資料」「貸款估算」「QQQI 分析結果」預設展開")
        ),
        Entry(
            "1.2.6",
            listOf(
                "QQQI 分頁：在貸款估算下方新增「QQQI 分析結果」群組，彙整 F 月股息收入、L 每月繳款、M 每月平衡預估-稅前、Q 每月平衡預估-稅後、N 月預扣稅股息",
                "貸款估算只保留 I、J、K、L；M、Q 改在「QQQI 分析結果」顯示"
            )
        ),
        Entry(
            "1.2.5",
            listOf(
                "QQQI 分頁：M 改稱「每月平衡預估-稅前（NT）」（M = F − L）",
                "新增 Q「每月平衡預估-稅後（NT）」（Q = F − N − L）"
            )
        ),
        Entry(
            "1.2.4",
            listOf("QQQI 分頁：更正 P 年預扣總額的公式為 P = N × 0.95 × 12（小數點四捨五入）")
        ),
        Entry(
            "1.2.3",
            listOf(
                "QQQI 分頁：新增 O 美國預扣稅率（預設 30%）、N 月預扣稅股息 = F × O、P 年預扣總額（P 公式於 1.2.4 更正）",
                "貸款估算的預設值：J 利率 4%、K 期數 84 個月"
            )
        ),
        Entry(
            "1.2.2",
            listOf("QQQI 分頁：「年利息（NT）」名稱改為「年股息（NT）」，計算方式不變（G = F × 12，四捨五入）")
        ),
        Entry(
            "1.2.1",
            listOf(
                "QQQI 分頁新增貸款估算：貸款金額（= 投入金額）、利率、期數 → 每月繳款（本息平均攤還，四捨五入），並算出每月平衡預估（= 每月股息收入 − 每月繳款）"
            )
        ),
        Entry(
            "1.2.0",
            listOf(
                "新增「QQQI」分頁：輸入投入金額（NT），自動換算股數、月股息收入（USD／NT）與年股息（NT）",
                "QQQI 股價、近 6 個月平均配息與美元兌台幣匯率由網路取得（匯率取不到時預設 32），也可手動修改"
            )
        ),
        Entry(
            "1.1.1",
            listOf(
                "分析完成後，點選某一檔股票改為開啟另一層畫面（原本是就地展開），按「← 返回排名」或系統返回鍵回到排名",
                "清單、單檔畫面、設定之間來回切換，會保留捲動位置與各區塊的展開狀態"
            )
        ),
        Entry(
            "1.1.0",
            listOf(
                "AI 分析團隊：底部固定「下載／分析／設定」三個項目",
                "下載：新增 PDF（排在最上面），另有 Markdown、HTML，並可用 Email 寄送",
                "設定：Claude API、Email 設定、畫面選擇（6 種風格）、版本說明",
                "選擇股票取消 5 檔上限",
                "所有區塊都可展開／收合，預設為收合",
                "修正年度營收圖表手勢無法縮放的問題",
                "圖表 X 軸改為資料點置中，柱狀圖與 K 線兩端不再被裁切"
            )
        ),
        Entry(
            "1.0.x",
            listOf(
                "AI 分析團隊：技術、財報、市場趨勢、風險四位分析師與投資組長，排名比較表、K 線／MACD／RSI／相對大盤／回撤／營收圖，Markdown／HTML 報告，選用 Claude 白話解讀",
                "股票分析：夏普值、布林通道（均線與標準差可設定）與成交量、交易量突高判斷、索提諾、崔納、席勒本益比、Prophet 預測；新增股票自動帶入名稱（台股為中文）",
                "複利、定期定額、報酬率、貸款、蛛網（網格）計算；分頁可左右滑動切換"
            )
        )
    )
}

@Composable
fun SettingsPage(prefs: TeamPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn {
        TextButton(onClick = onBack) { Text("← 返回分析") }
        Text("設定", style = MaterialTheme.typography.titleLarge)

        // ---------------------------------------------------------------- Claude API
        var apiKey by remember { mutableStateOf(prefs.apiKey) }
        var model by remember { mutableStateOf(prefs.model.ifBlank { ClaudeClient.DEFAULT_MODEL }) }
        Collapsible("Claude API（選用）", subtitle = if (apiKey.isBlank()) "未設定（使用程式內建的文字解讀）" else "已設定", key = "set:claude") {
            Text(
                "不填也能完整運作。填入後，分析師與組長會用白話文解讀並寫出更完整的報告。" +
                    "只會送出程式算好的數據摘要，不含任何個人資料；AI 只負責解讀，不負責算數。Key 只存在這支手機的 App 私有儲存空間。",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; prefs.apiKey = it },
                label = { Text("Claude API Key（sk-ant-…）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; prefs.model = it },
                label = { Text("模型（預設 ${ClaudeClient.DEFAULT_MODEL}）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---------------------------------------------------------------- Email
        var emailTo by remember { mutableStateOf(prefs.emailTo) }
        var subject by remember { mutableStateOf(prefs.emailSubject) }
        var format by remember { mutableIntStateOf(prefs.emailFormat) }
        var auto by remember { mutableStateOf(prefs.emailAuto) }
        var summary by remember { mutableStateOf(prefs.emailSummary) }
        val recipients = TeamPrefs.parseRecipients(emailTo)
        Collapsible(
            "Email 設定",
            subtitle = if (recipients.isEmpty()) "尚未設定收件人" else "收件人 ${recipients.size} 位・附件 ${ReportFormat.fromIndex(format).label}",
            key = "set:email"
        ) {
            Text(
                "在「下載」選「以 Email 寄送」時，會開啟你手機的寄信 App（如 Gmail），並已填好收件人、主題、內文與報告附件，確認後按送出即可。",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = emailTo,
                onValueChange = { emailTo = it; prefs.emailTo = it },
                label = { Text("收件人 Email（多個以逗號分隔）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it; prefs.emailSubject = it },
                label = { Text("主題（{日期} 會替換成當天日期）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("附件格式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportFormat.values().forEachIndexed { i, f ->
                    FilterChip(selected = format == i, onClick = { format = i; prefs.emailFormat = i }, label = { Text(f.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = summary, onCheckedChange = { summary = it; prefs.emailSummary = it })
                Text("  內文附上排名摘要", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = auto, onCheckedChange = { auto = it; prefs.emailAuto = it })
                Text("  分析完成後自動開啟寄信畫面", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ---------------------------------------------------------------- 畫面選擇
        Collapsible("畫面選擇", subtitle = "目前：${AppTheme.options[AppTheme.index].name}", key = "set:theme") {
            Text("選擇整個 App 的配色風格，立即套用並記住。", style = MaterialTheme.typography.bodySmall)
            AppTheme.options.forEachIndexed { i, opt ->
                val selected = AppTheme.index == i
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { AppTheme.index = i; prefs.theme = i }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 色票：背景、主色、強調色
                    Row {
                        listOf(opt.scheme.background, opt.scheme.primary, opt.scheme.primaryContainer).forEach {
                            Spacer(
                                Modifier
                                    .size(22.dp)
                                    .background(it, RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(opt.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(opt.note, style = MaterialTheme.typography.bodySmall)
                    }
                    if (selected) Text("✔", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---------------------------------------------------------------- 版本說明
        val info = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
        }
        val versionName = info?.versionName ?: "—"
        val build = info?.let { PackageInfoCompat.getLongVersionCode(it).toString() } ?: "—"
        Collapsible("程式版本說明", subtitle = "版本 $versionName（build $build）", key = "set:version") {
            Text("投資計算機　版本 $versionName（build $build）", style = MaterialTheme.typography.titleSmall)
            VersionNotes.entries.forEach { e ->
                Text("版本 ${e.version}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                e.items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Text(
                "本程式的數字與評分皆由程式依固定規則計算，僅供參考，不構成投資建議。股價與財報資料來自 Yahoo Finance 非官方介面，" +
                    "台股中文名稱來自證交所與櫃買中心公開資料。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
