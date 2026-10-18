package com.nevonai.smartshape.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.model.LengthUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Share-sheet exports: image, measurement summary, OCR text (TXT) and a PDF report. */
object Exporter {

    fun summary(scan: Scan, unit: LengthUnit) = Formats.summary(scan.title, scan.measurements, scan.calibration, unit)

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareImage(context: Context, scan: Scan, unit: LengthUnit) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(scan.imagePath))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, summary(scan, unit))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share scan"))
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    suspend fun shareTxt(context: Context, scan: Scan) {
        val file = withContext(Dispatchers.IO) {
            exportFile(context, scan.title, "txt").apply { writeText(scan.ocr?.text.orEmpty()) }
        }
        shareFile(context, file, "text/plain")
    }

    /**
     * A4 PDF: page 1 is the scan image with a measurement summary, page 2 holds the recognised
     * text as real (selectable, searchable) PDF text.
     */
    suspend fun sharePdf(context: Context, scan: Scan, unit: LengthUnit) {
        val file = withContext(Dispatchers.IO) {
            val image = BitmapIO.load(scan.imagePath, 2000)
            val doc = PdfDocument()
            val pageW = 595
            val pageH = 842
            val margin = 40f

            val title = TextPaint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val body = TextPaint().apply { textSize = 10.5f; isAntiAlias = true }

            doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()).also { page ->
                val c = page.canvas
                c.drawText(scan.title, margin, margin + 14, title)
                var y = margin + 34
                if (image != null) {
                    val maxW = pageW - 2 * margin
                    val maxH = pageH * 0.55f
                    val s = minOf(maxW / image.width, maxH / image.height)
                    val dst = RectF(margin, y, margin + image.width * s, y + image.height * s)
                    c.drawBitmap(image, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    y = dst.bottom + 16
                }
                val text = summary(scan, unit).lines().drop(1).joinToString("\n")
                staticLayout(text, body, (pageW - 2 * margin).toInt()).let {
                    c.save(); c.translate(margin, y); it.draw(c); c.restore()
                }
                doc.finishPage(page)
            }

            val ocrText = scan.ocr?.text.orEmpty()
            if (ocrText.isNotBlank()) {
                val layout = staticLayout(ocrText, body, (pageW - 2 * margin).toInt())
                val usable = pageH - 2 * margin - 30
                var line = 0
                var pageNo = 2
                while (line < layout.lineCount) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo++).create())
                    val c = page.canvas
                    c.drawText("Recognised text", margin, margin + 14, title)
                    val top = layout.getLineTop(line)
                    var last = line
                    while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= usable) last++
                    c.save()
                    c.translate(margin, margin + 30f - top)
                    c.clipRect(0f, top.toFloat(), (pageW - 2 * margin), layout.getLineBottom(last).toFloat())
                    layout.draw(c)
                    c.restore()
                    doc.finishPage(page)
                    line = last + 1
                }
            }

            exportFile(context, scan.title, "pdf").also { f -> f.outputStream().use { doc.writeTo(it) }; doc.close() }
        }
        shareFile(context, file, "application/pdf")
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    private fun exportFile(context: Context, title: String, ext: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "scan" }.take(60)
        return File(dir, "$safe.$ext")
    }

    private fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export"))
    }
}
