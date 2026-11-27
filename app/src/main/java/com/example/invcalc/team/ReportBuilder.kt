package com.example.invcalc.team

import com.example.invcalc.stock.DateUtil

/** 產出可下載的 Markdown／HTML 報告，以及餵給 AI 的事實摘要。 */
object ReportBuilder {
    const val DISCLAIMER = "本報告的數字與評分皆由程式依固定規則計算，僅供參考，不構成投資建議；投資有風險，請自行判斷並承擔風險。"

    fun ranked(list: List<StockAnalysis>): List<StockAnalysis> = list.sortedByDescending { it.lead.score }

    private fun scoreText(a: AgentReport) = if (a.score != null) "${f2(a.score)}" else "—"

    private fun settingsLines(s: TeamSettings) = listOf(
        "持有期間" to s.horizon.label,
        "風險承受度" to s.risk.label,
        "無風險利率" to "${f2(s.rfPct)}%"
    )

    // ------------------------------------------------------------------ 給 AI 的事實摘要
    fun stockFacts(a: StockAnalysis, s: TeamSettings): String {
        val sb = StringBuilder()
        sb.appendLine("標的：${a.displayName}（${a.symbol}），最新收盤 ${f2(a.price)}（${DateUtil.format(a.lastDay)}）")
        sb.appendLine("使用者設定：持有期間 ${s.horizon.label}、風險承受度 ${s.risk.label}")
        sb.appendLine()
        sb.appendLine("【投資組長】綜合分數 ${f2(a.lead.score)}/100、評級 ${a.lead.rating.label}、部位上限 ${f2(a.lead.positionCapPct)}%、" +
            "支撐 ${f2(a.lead.support)}、壓力 ${f2(a.lead.resistance)}、停損 ${f2(a.lead.stopLoss)}" +
            (a.lead.rewardRisk?.let { "、風險報酬比 ${f2(it)}" } ?: ""))
        a.lead.findings.forEach { sb.appendLine("- $it") }
        for (ag in a.agents()) {
            sb.appendLine()
            sb.appendLine("【${ag.role.label}】分數 ${scoreText(ag)}/100（${ag.verdict}）")
            ag.metrics.forEach { sb.appendLine("- ${it.name}：${it.value}") }
            ag.findings.forEach { sb.appendLine("- 發現：$it") }
        }
        return sb.toString()
    }

    fun rankingFacts(list: List<StockAnalysis>, s: TeamSettings): String {
        val sb = StringBuilder()
        sb.appendLine("使用者設定：持有期間 ${s.horizon.label}、風險承受度 ${s.risk.label}")
        sb.appendLine("排名（由高到低）：")
        ranked(list).forEachIndexed { i, a ->
            sb.appendLine(
                "${i + 1}. ${a.displayName}（${a.symbol}）：綜合 ${f2(a.lead.score)}、評級 ${a.lead.rating.label}、" +
                    "技術 ${scoreText(a.tech)}、財報 ${scoreText(a.fund)}、市場 ${scoreText(a.market)}、風險 ${scoreText(a.risk)}、" +
                    "部位上限 ${f2(a.lead.positionCapPct)}%"
            )
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ Markdown
    fun markdown(list: List<StockAnalysis>, s: TeamSettings, overallAi: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.appendLine("# AI 金融分析團隊投資報告")
        sb.appendLine()
        sb.appendLine("- 產生日期：$dateText")
        settingsLines(s).forEach { sb.appendLine("- ${it.first}：${it.second}") }
        sb.appendLine()
        sb.appendLine("## 排名比較")
        sb.appendLine()
        sb.appendLine("| 排名 | 標的 | 評級 | 綜合 | 技術 | 財報 | 市場 | 風險 | 部位上限 |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        ranked(list).forEachIndexed { i, a ->
            sb.appendLine(
                "| ${i + 1} | ${a.displayName}（${a.symbol}） | ${a.lead.rating.label} | ${f2(a.lead.score)} | " +
                    "${scoreText(a.tech)} | ${scoreText(a.fund)} | ${scoreText(a.market)} | ${scoreText(a.risk)} | ${f2(a.lead.positionCapPct)}% |"
            )
        }
        if (!overallAi.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("### 🧠 組長總結（AI 解讀）")
            sb.appendLine()
            sb.appendLine(overallAi.trim())
        }
        ranked(list).forEachIndexed { i, a ->
            sb.appendLine()
            sb.appendLine("## ${i + 1}. ${a.displayName}（${a.symbol}）")
            sb.appendLine()
            sb.appendLine("- 最新收盤：${f2(a.price)}（${DateUtil.format(a.lastDay)}）")
            sb.appendLine("- **組長評級：${a.lead.rating.label}**（綜合分數 ${f2(a.lead.score)}/100）")
            sb.appendLine("- 部位上限：${f2(a.lead.positionCapPct)}%")
            sb.appendLine("- 參考價位：支撐 ${f2(a.lead.support)}／壓力 ${f2(a.lead.resistance)}／停損 ${f2(a.lead.stopLoss)}" +
                (a.lead.rewardRisk?.let { "（風險報酬比 ${f2(it)}）" } ?: ""))
            sb.appendLine()
            sb.appendLine("### 🧠 投資組長")
            a.lead.findings.forEach { sb.appendLine("- $it") }
            for (ag in a.agents()) {
                sb.appendLine()
                sb.appendLine("### ${ag.role.emoji} ${ag.role.label}　${scoreText(ag)}/100（${ag.verdict}）")
                if (ag.metrics.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("| 指標 | 數值 |")
                    sb.appendLine("|---|---|")
                    ag.metrics.forEach { sb.appendLine("| ${it.name} | ${it.value} |") }
                }
                sb.appendLine()
                ag.findings.forEach { sb.appendLine("- $it") }
            }
            if (!a.aiText.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine("### AI 白話解讀")
                sb.appendLine()
                sb.appendLine(a.aiText!!.trim())
            }
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine(DISCLAIMER)
        return sb.toString()
    }

    // ------------------------------------------------------------------ HTML
    fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun inline(s: String): String =
        esc(s).replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")

    /** 極簡 Markdown → HTML（標題、清單、粗體、段落），供 AI 文字使用。 */
    fun mdToHtml(md: String): String {
        val sb = StringBuilder()
        var inList = false
        fun closeList() { if (inList) { sb.append("</ul>"); inList = false } }
        for (raw in md.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> closeList()
                line.startsWith("### ") -> { closeList(); sb.append("<h4>${inline(line.drop(4))}</h4>") }
                line.startsWith("## ") -> { closeList(); sb.append("<h3>${inline(line.drop(3))}</h3>") }
                line.startsWith("# ") -> { closeList(); sb.append("<h3>${inline(line.drop(2))}</h3>") }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (!inList) { sb.append("<ul>"); inList = true }
                    sb.append("<li>${inline(line.drop(2))}</li>")
                }
                else -> { closeList(); sb.append("<p>${inline(line)}</p>") }
            }
        }
        closeList()
        return sb.toString()
    }

    fun html(list: List<StockAnalysis>, s: TeamSettings, overallAi: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.append(
            "<!doctype html><html lang=\"zh-Hant\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<title>AI 金融分析團隊投資報告</title><style>" +
                "body{font-family:-apple-system,'Noto Sans TC',sans-serif;max-width:860px;margin:24px auto;padding:0 16px;color:#222;line-height:1.6}" +
                "h1{font-size:24px}h2{border-bottom:2px solid #1B7F5C;padding-bottom:4px;margin-top:32px}" +
                "table{border-collapse:collapse;width:100%;margin:8px 0}th,td{border:1px solid #ccc;padding:6px 8px;text-align:left;font-size:14px}" +
                "th{background:#f0f4f2}.card{background:#f7f9f8;border-radius:8px;padding:8px 14px;margin:12px 0}" +
                ".buy{color:#c62828;font-weight:bold}.sell{color:#2e7d32;font-weight:bold}.muted{color:#666;font-size:13px}" +
                "</style></head><body>"
        )
        sb.append("<h1>AI 金融分析團隊投資報告</h1>")
        sb.append("<p class=\"muted\">產生日期：${esc(dateText)}｜" +
            settingsLines(s).joinToString("｜") { "${esc(it.first)}：${esc(it.second)}" } + "</p>")
        sb.append("<h2>排名比較</h2><table><tr><th>排名</th><th>標的</th><th>評級</th><th>綜合</th><th>技術</th><th>財報</th><th>市場</th><th>風險</th><th>部位上限</th></tr>")
        ranked(list).forEachIndexed { i, a ->
            sb.append(
                "<tr><td>${i + 1}</td><td>${esc(a.displayName)}（${esc(a.symbol)}）</td><td>${esc(a.lead.rating.label)}</td>" +
                    "<td>${f2(a.lead.score)}</td><td>${scoreText(a.tech)}</td><td>${scoreText(a.fund)}</td>" +
                    "<td>${scoreText(a.market)}</td><td>${scoreText(a.risk)}</td><td>${f2(a.lead.positionCapPct)}%</td></tr>"
            )
        }
        sb.append("</table>")
        if (!overallAi.isNullOrBlank()) sb.append("<div class=\"card\"><h3>🧠 組長總結（AI 解讀）</h3>${mdToHtml(overallAi)}</div>")
        ranked(list).forEachIndexed { i, a ->
            sb.append("<h2>${i + 1}. ${esc(a.displayName)}（${esc(a.symbol)}）</h2>")
            sb.append("<p>最新收盤：${f2(a.price)}（${DateUtil.format(a.lastDay)}）<br>" +
                "<strong>組長評級：${esc(a.lead.rating.label)}</strong>（綜合分數 ${f2(a.lead.score)}/100）<br>" +
                "部位上限：${f2(a.lead.positionCapPct)}%<br>" +
                "參考價位：支撐 ${f2(a.lead.support)}／壓力 ${f2(a.lead.resistance)}／停損 ${f2(a.lead.stopLoss)}" +
                (a.lead.rewardRisk?.let { "（風險報酬比 ${f2(it)}）" } ?: "") + "</p>")
            sb.append("<div class=\"card\"><h3>🧠 投資組長</h3><ul>" + a.lead.findings.joinToString("") { "<li>${esc(it)}</li>" } + "</ul></div>")
            for (ag in a.agents()) {
                sb.append("<div class=\"card\"><h3>${ag.role.emoji} ${esc(ag.role.label)}　${scoreText(ag)}/100（${esc(ag.verdict)}）</h3>")
                if (ag.metrics.isNotEmpty()) {
                    sb.append("<table><tr><th>指標</th><th>數值</th></tr>")
                    ag.metrics.forEach { sb.append("<tr><td>${esc(it.name)}</td><td>${esc(it.value)}</td></tr>") }
                    sb.append("</table>")
                }
                sb.append("<ul>" + ag.findings.joinToString("") { "<li>${esc(it)}</li>" } + "</ul></div>")
            }
            if (!a.aiText.isNullOrBlank()) sb.append("<div class=\"card\"><h3>AI 白話解讀</h3>${mdToHtml(a.aiText!!)}</div>")
        }
        sb.append("<hr><p class=\"muted\">${esc(DISCLAIMER)}</p></body></html>")
        return sb.toString()
    }

    fun todayText(epochMillis: Long): String = DateUtil.format(Math.floorDiv(epochMillis, 86_400_000L))
}
