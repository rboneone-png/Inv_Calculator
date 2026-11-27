package com.example.invcalc.team

enum class PdfStyle { H1, H2, H3, BODY, BULLET }

class PdfLine(val text: String, val style: PdfStyle)

/** 把 Markdown 報告轉成可直接排版到 PDF 的文字行（移除表情符號與 Markdown 符號）。 */
object PdfText {
    /** 移除 PDF 字型通常無法顯示的表情符號與符號。 */
    fun stripEmoji(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val drop = cp >= 0x1F000 || cp == 0xFE0F || cp == 0x200D || cp in 0x2600..0x27BF
            if (!drop) sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    fun fromMarkdown(md: String): List<PdfLine> {
        val out = ArrayList<PdfLine>()
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line == "---") continue
            if (line.startsWith("|")) {
                val cells = line.trim('|').split("|").map { it.trim() }
                if (cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }) continue // 表格分隔列
                out.add(PdfLine(clean(cells.joinToString("  ｜  ")), PdfStyle.BODY))
                continue
            }
            when {
                line.startsWith("### ") -> out.add(PdfLine(clean(line.drop(4)), PdfStyle.H3))
                line.startsWith("## ") -> out.add(PdfLine(clean(line.drop(3)), PdfStyle.H2))
                line.startsWith("# ") -> out.add(PdfLine(clean(line.drop(2)), PdfStyle.H1))
                line.startsWith("- ") || line.startsWith("* ") -> out.add(PdfLine("• " + clean(line.drop(2)), PdfStyle.BULLET))
                else -> out.add(PdfLine(clean(line), PdfStyle.BODY))
            }
        }
        return out.filter { it.text.isNotBlank() }
    }

    private fun clean(s: String): String = stripEmoji(s.replace("**", "")).trim()
}
