package com.example.invcalc.team.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.invcalc.team.PdfLine
import com.example.invcalc.team.PdfStyle
import com.example.invcalc.team.PdfText
import com.example.invcalc.team.ReportBuilder
import com.example.invcalc.team.StockAnalysis
import com.example.invcalc.team.TeamSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

enum class ReportFormat(val ext: String, val mime: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    HTML("html", "text/html", "HTML"),
    MARKDOWN("md", "text/markdown", "Markdown");

    companion object {
        fun fromIndex(i: Int): ReportFormat = values().getOrElse(i) { PDF }
    }
}

object ReportExport {
    fun fileName(format: ReportFormat, dateText: String) = "AI金融分析報告_$dateText.${format.ext}"

    fun bytes(
        format: ReportFormat,
        analyses: List<StockAnalysis>,
        settings: TeamSettings,
        overallAi: String?,
        dateText: String
    ): ByteArray = when (format) {
        ReportFormat.PDF -> {
            val out = ByteArrayOutputStream()
            PdfWriter.write(PdfText.fromMarkdown(ReportBuilder.markdown(analyses, settings, overallAi, dateText)), out)
            out.toByteArray()
        }
        ReportFormat.HTML -> ReportBuilder.html(analyses, settings, overallAi, dateText).toByteArray(Charsets.UTF_8)
        ReportFormat.MARKDOWN -> ReportBuilder.markdown(analyses, settings, overallAi, dateText).toByteArray(Charsets.UTF_8)
    }

    fun writeToUri(context: Context, uri: Uri, data: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            ?: throw IllegalStateException("無法寫入選擇的位置")
    }

    fun cacheFile(context: Context, name: String, data: ByteArray): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 只保留最新的暫存檔
        return File(dir, name).also { it.writeBytes(data) }
    }
}

/** 以系統內建的 PdfDocument 排版 A4 文字報告（含中文自動換行與頁碼）。 */
object PdfWriter {
    private const val W = 595
    private const val H = 842
    private const val M = 40

    private fun sizeOf(s: PdfStyle) = when (s) {
        PdfStyle.H1 -> 20f
        PdfStyle.H2 -> 15f
        PdfStyle.H3 -> 12.5f
        else -> 10.5f
    }

    private fun gapBefore(s: PdfStyle) = when (s) {
        PdfStyle.H1 -> 4f
        PdfStyle.H2 -> 14f
        PdfStyle.H3 -> 9f
        PdfStyle.BULLET -> 2f
        PdfStyle.BODY -> 3f
    }

    fun write(lines: List<PdfLine>, out: OutputStream) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var y = M.toFloat()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 9f; textAlign = Paint.Align.CENTER }

        fun finishPage() {
            page.canvas.drawText("第 $pageNo 頁", W / 2f, H - 18f, footer)
            doc.finishPage(page)
        }

        for (line in lines) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = sizeOf(line.style)
                isFakeBoldText = line.style == PdfStyle.H1 || line.style == PdfStyle.H2 || line.style == PdfStyle.H3
            }
            val layout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, W - 2 * M)
                .setLineSpacing(0f, 1.2f)
                .build()
            val gap = if (y == M.toFloat()) 0f else gapBefore(line.style)
            if (y + gap + layout.height > H - M && y > M) {
                finishPage()
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                y = M.toFloat()
            } else {
                y += gap
            }
            val canvas = page.canvas
            canvas.save()
            canvas.translate(M.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }
        finishPage()
        doc.writeTo(out)
        doc.close()
    }
}

object EmailSender {
    /** 開啟系統的寄信選擇器（Gmail 等），收件人、主題、內文與附件都已填好。 */
    fun send(context: Context, to: List<String>, subject: String, body: String, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "選擇寄信的 App").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
