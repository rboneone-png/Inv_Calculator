package com.example.invcalc.team.ui

import android.content.Context

/** 「AI 分析團隊」的設定（存在 App 私有的 SharedPreferences）。 */
class TeamPrefs(context: Context) {
    private val p = context.getSharedPreferences("ai_team", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) { p.edit().putString("api_key", v.trim()).apply() }

    var model: String
        get() = p.getString("model", "") ?: ""
        set(v) { p.edit().putString("model", v.trim()).apply() }

    /** 收件人，多個以逗號、分號或空白分隔。 */
    var emailTo: String
        get() = p.getString("email_to", "") ?: ""
        set(v) { p.edit().putString("email_to", v.trim()).apply() }

    var emailSubject: String
        get() = p.getString("email_subject", DEFAULT_SUBJECT) ?: DEFAULT_SUBJECT
        set(v) { p.edit().putString("email_subject", v).apply() }

    /** 附件格式：0 = PDF、1 = HTML、2 = Markdown。 */
    var emailFormat: Int
        get() = p.getInt("email_format", 0)
        set(v) { p.edit().putInt("email_format", v).apply() }

    /** 分析完成後自動開啟寄信畫面。 */
    var emailAuto: Boolean
        get() = p.getBoolean("email_auto", false)
        set(v) { p.edit().putBoolean("email_auto", v).apply() }

    /** 信件內文附上排名摘要。 */
    var emailSummary: Boolean
        get() = p.getBoolean("email_summary", true)
        set(v) { p.edit().putBoolean("email_summary", v).apply() }

    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }

    companion object {
        const val DEFAULT_SUBJECT = "AI 金融分析團隊投資報告 {日期}"

        fun parseRecipients(text: String): List<String> = com.example.invcalc.team.Recipients.parse(text)
    }
}
