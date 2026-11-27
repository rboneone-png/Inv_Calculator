package com.example.invcalc.team

object Recipients {
    /** 以逗號、分號、空白或換行分隔；只保留看起來像 Email 的項目，並去除重複。 */
    fun parse(text: String): List<String> =
        text.split(',', ';', ' ', '\n', '\t', '，', '；')
            .map { it.trim() }
            .filter { it.length >= 3 && it.count { c -> c == '@' } == 1 && !it.startsWith("@") && !it.endsWith("@") }
            .distinct()

    /** 主題範本：{日期} 替換成日期。 */
    fun subject(template: String, dateText: String, default: String): String =
        template.ifBlank { default }.replace("{日期}", dateText)
}
