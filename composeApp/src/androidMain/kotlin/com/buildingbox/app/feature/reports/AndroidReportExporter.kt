package com.buildingbox.app.feature.reports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd
import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// A4 @ 72dpi, with the report's palette.
private const val PAGE_W = 595
private const val PAGE_H = 842
private const val M = 24f                // page margin
private const val PAD = 18f              // card inner padding
private const val CARD_L = M
private const val CARD_R = PAGE_W - M
private const val TEXT_L = M + PAD
private const val TEXT_R = PAGE_W - M - PAD
private const val LINE_H = 22f
private const val BG = 0xFFF4F6F8.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val SUBTEAL = 0xFFB9EADF.toInt()
private const val HEAD = 0xFF8A919C.toInt()
private const val BODY = 0xFF1A1E24.toInt()
private const val SECONDARY = 0xFF5B636E.toInt()
private const val FLOW_IN = 0xFF12A37C.toInt()
private const val FLOW_OUT = 0xFFD9533D.toInt()
private const val WARN = 0xFFB8842A.toInt()
private const val BORDER = 0xFFE2E5EA.toInt()
private const val CHIP_BG = 0xFFF7E3DE.toInt()

private data class Ln(val label: String, val value: String, val color: Int = BODY, val bold: Boolean = false)

class AndroidReportExporter(private val context: Context) : ReportExporter {

    override fun sharePdf(report: ReportData) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName(report))
        FileOutputStream(file).use { it.write(buildPdf(report)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${report.buildingName} — ${formatMonth(report.month)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share statement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun downloadPdf(report: ReportData) {
        val bytes = buildPdf(report)
        val name = fileName(report)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                toast("Saved to Downloads · $name")
            } else toast("Could not save the file")
        } else {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
            file.writeBytes(bytes)
            toast("Saved: ${file.absolutePath}")
        }
    }

    private fun fileName(r: ReportData) = "BuildingBox_${r.month}.pdf"
    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    private fun dual(d: DualAmount) = buildList {
        if (d.usdCents != 0L) add(formatUsd(d.usdCents)); if (d.lbp != 0L) add(formatLbp(d.lbp)); if (isEmpty()) add("$0")
    }.joinToString(" + ")

    private fun buildPdf(r: ReportData): ByteArray {
        val doc = PdfDocument()
        val sans = Typeface.SANS_SERIF
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 10f; color = HEAD; letterSpacing = 0.06f }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 12.5f; color = BODY }
        val bodyStrong = Paint(body).apply { typeface = bold }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 12.5f; textAlign = Paint.Align.RIGHT }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 20f; color = WHITE }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 11.5f; color = SUBTEAL }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = BORDER }
        val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHIP_BG }
        val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 10.5f; color = FLOW_OUT }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas.apply { drawColor(BG) }
        var y = M
        var pageNo = 1

        fun ensure(space: Float) {
            if (y + space > PAGE_H - M) {
                doc.finishPage(page)
                pageNo += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas.apply { drawColor(BG) }
                y = M
            }
        }

        // Gradient header card.
        run {
            val h = 70f
            val rect = RectF(CARD_L, y, CARD_R, y + h)
            val grad = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF0E7C68.toInt(), 0xFF0A4F45.toInt(), Shader.TileMode.CLAMP)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
            canvas.drawRoundRect(rect, 18f, 18f, p)
            canvas.drawText(r.buildingName, TEXT_L, y + 32f, titlePaint)
            canvas.drawText("${r.address} · ${formatMonth(r.month)}", TEXT_L, y + 52f, subPaint)
            y += h + 12f
        }

        fun sectionCard(title: String, lines: List<Ln>, chips: List<String> = emptyList()) {
            val chipRows = if (chips.isEmpty()) 0 else 1 + chips.size / 3 // rough wrap estimate
            val contentH = 26f + lines.size * LINE_H + (if (chips.isEmpty()) 0f else 8f + chipRows * 24f) + PAD * 2
            ensure(contentH + 12f)
            val top = y
            val rect = RectF(CARD_L, top, CARD_R, top + contentH)
            canvas.drawRoundRect(rect, 16f, 16f, cardPaint)
            canvas.drawRoundRect(rect, 16f, 16f, borderPaint)
            var yy = top + PAD + 12f
            canvas.drawText(title.uppercase(), TEXT_L, yy, head)
            yy += 22f
            lines.forEach { ln ->
                canvas.drawText(ln.label, TEXT_L, yy, if (ln.bold) bodyStrong else body)
                valuePaint.color = ln.color
                canvas.drawText(ln.value, TEXT_R, yy, valuePaint)
                yy += LINE_H
            }
            if (chips.isNotEmpty()) {
                yy += 8f
                var cx = TEXT_L
                chips.forEach { label ->
                    val w = chipText.measureText(label) + 18f
                    if (cx + w > TEXT_R) { cx = TEXT_L; yy += 24f }
                    val cr = RectF(cx, yy - 13f, cx + w, yy + 7f)
                    canvas.drawRoundRect(cr, 20f, 20f, chipBg)
                    canvas.drawText(label, cx + 9f, yy, chipText)
                    cx += w + 6f
                }
            }
            y = top + contentH + 12f
        }

        sectionCard(
            "Collection",
            listOf(
                Ln("Collected", dual(r.collected), FLOW_IN),
                Ln("Expected", dual(r.expected)),
                Ln("Outstanding", dual(r.outstanding), FLOW_OUT),
                Ln("Units paid", "${r.paidCount}/${r.total}"),
            ),
        )

        sectionCard(
            "Expenses this month",
            buildList {
                if (r.expenseItems.isEmpty()) add(Ln("No expenses", "—", SECONDARY))
                r.expenseItems.forEach { e ->
                    val reason = e.label.ifBlank { e.category.label }
                    add(Ln("$reason  (${e.category.label} · ${formatDayLong(e.date)})", dual(e.amount), FLOW_OUT))
                }
                add(Ln("Total spent", dual(r.totalSpent), FLOW_OUT, bold = true))
            },
        )

        sectionCard(
            "Box balance",
            listOf(
                Ln("Opening (last month)", dual(r.opening)),
                Ln("Total collected", "+ ${dual(r.collected)}", FLOW_IN),
                Ln("Total spent", "− ${dual(r.totalSpent)}", FLOW_OUT),
                Ln("This month (${if (r.isGain) "gain" else "loss"})", dual(r.net), if (r.isGain) FLOW_IN else FLOW_OUT),
                Ln("Closing · available", dual(r.closing), bold = true),
            ),
        )

        sectionCard(
            "Paid this month · ${r.paidList.size}",
            if (r.paidList.isEmpty()) listOf(Ln("No payments yet", "—", SECONDARY))
            else r.paidList.map {
                val on = it.date?.let { d -> " · ${formatDayLong(d)}" } ?: ""
                Ln("${it.name} · ${it.owner}${if (it.partial) "  (partial)" else ""}$on", dual(it.amount), if (it.partial) WARN else FLOW_IN)
            },
        )

        if (r.unpaid.isNotEmpty()) {
            sectionCard("Still due", emptyList(), chips = r.unpaid.map { "${it.first} · ${it.second}" })
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
