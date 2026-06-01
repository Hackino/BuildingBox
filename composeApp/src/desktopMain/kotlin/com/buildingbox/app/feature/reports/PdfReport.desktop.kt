package com.buildingbox.app.feature.reports

import com.buildingbox.app.core.datetime.formatDayLong
import com.buildingbox.app.core.datetime.formatMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.core.money.formatLbp
import com.buildingbox.app.core.money.formatUsd
import com.buildingbox.app.feature.reports.domain.ReportData
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.io.ByteArrayOutputStream

// A4 @ 72dpi with the report's palette — mirrors the Android PdfReport.kt layout 1:1.
private const val PAGE_W = 595f
private const val PAGE_H = 842f
private const val M = 24f
private const val PAD = 18f
private const val CARD_L = M
private const val CARD_R = PAGE_W - M
private const val TEXT_L = M + PAD
private const val TEXT_R = PAGE_W - M - PAD
private const val LINE_H = 22f

private val BG = Color(0xF4, 0xF6, 0xF8)
private val WHITE = Color.WHITE
private val SUBTEAL = Color(0xB9, 0xEA, 0xDF)
private val HEAD = Color(0x8A, 0x91, 0x9C)
private val BODY = Color(0x1A, 0x1E, 0x24)
private val SECONDARY = Color(0x5B, 0x63, 0x6E)
private val FLOW_IN = Color(0x12, 0xA3, 0x7C)
private val FLOW_OUT = Color(0xD9, 0x53, 0x3D)
private val WARN = Color(0xB8, 0x84, 0x2A)
private val BORDER = Color(0xE2, 0xE5, 0xEA)
private val CHIP_BG = Color(0xF7, 0xE3, 0xDE)
private val HEADER_TOP = Color(0x0E, 0x7C, 0x68)
private val HEADER_BOTTOM = Color(0x0A, 0x4F, 0x45)

private data class Ln(val label: String, val value: String, val color: Color = BODY, val bold: Boolean = false)

private fun dual(d: DualAmount) = buildList {
    if (d.usdCents != 0L) add(formatUsd(d.usdCents))
    if (d.lbp != 0L) add(formatLbp(d.lbp))
    if (isEmpty()) add("$0")
}.joinToString(" + ")

/** Render [r] to PDF bytes. Shared by share + download on desktop. */
internal fun buildReportPdf(r: ReportData): ByteArray {
    val doc = PDDocument()

    // One PdfBoxGraphics2D per page; flush appends it to a fresh PDPage.
    var page = PDPage(PDRectangle(PAGE_W, PAGE_H))
    var g = newGraphics(doc)
    var y = M

    fun flushPage() {
        g.dispose()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs -> cs.drawForm(g.xFormObject) }
    }

    fun newPage() {
        flushPage()
        page = PDPage(PDRectangle(PAGE_W, PAGE_H))
        g = newGraphics(doc)
        y = M
    }

    fun ensure(space: Float) {
        if (y + space > PAGE_H - M) newPage()
    }

    // Header gradient card.
    run {
        val h = 70f
        g.paint = GradientPaint(CARD_L, y, HEADER_TOP, CARD_R, y + h, HEADER_BOTTOM)
        g.fill(RoundRectangle2D.Float(CARD_L, y, CARD_R - CARD_L, h, 18f, 18f))
        g.color = WHITE
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString(r.buildingName, TEXT_L, y + 32f)
        g.color = SUBTEAL
        g.font = Font("SansSerif", Font.PLAIN, 11)
        g.drawString("${r.address} · ${formatMonth(r.month)}", TEXT_L, y + 52f)
        y += h + 12f
    }

    fun sectionCard(title: String, lines: List<Ln>, chips: List<String> = emptyList()) {
        val chipRows = if (chips.isEmpty()) 0 else 1 + chips.size / 3
        val contentH = 26f + lines.size * LINE_H + (if (chips.isEmpty()) 0f else 8f + chipRows * 24f) + PAD * 2
        ensure(contentH + 12f)
        val top = y
        val rect = RoundRectangle2D.Float(CARD_L, top, CARD_R - CARD_L, contentH, 16f, 16f)
        g.color = WHITE
        g.fill(rect)
        g.color = BORDER
        g.stroke = BasicStroke(1f)
        g.draw(rect)

        var yy = top + PAD + 12f
        g.color = HEAD
        g.font = Font("SansSerif", Font.BOLD, 10)
        g.drawString(title.uppercase(), TEXT_L, yy)
        yy += 22f

        lines.forEach { ln ->
            g.font = Font("SansSerif", if (ln.bold) Font.BOLD else Font.PLAIN, 12)
            g.color = BODY
            g.drawString(ln.label, TEXT_L, yy)
            g.color = ln.color
            g.font = Font("SansSerif", Font.BOLD, 12)
            val vw = g.fontMetrics.stringWidth(ln.value).toFloat()
            g.drawString(ln.value, TEXT_R - vw, yy)
            yy += LINE_H
        }

        if (chips.isNotEmpty()) {
            yy += 8f
            var cx = TEXT_L
            g.font = Font("SansSerif", Font.BOLD, 10)
            chips.forEach { label ->
                val w = g.fontMetrics.stringWidth(label).toFloat() + 18f
                if (cx + w > TEXT_R) { cx = TEXT_L; yy += 24f }
                g.color = CHIP_BG
                g.fill(RoundRectangle2D.Float(cx, yy - 13f, w, 20f, 20f, 20f))
                g.color = FLOW_OUT
                g.drawString(label, cx + 9f, yy)
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
            // Show each expense's reason; category in parentheses for context.
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
        if (r.paidList.isEmpty()) {
            listOf(Ln("No payments yet", "—", SECONDARY))
        } else {
            r.paidList.map {
                val on = it.date?.let { d -> " · ${formatDayLong(d)}" } ?: ""
                Ln(
                    "${it.name} · ${it.owner}${if (it.partial) "  (partial)" else ""}$on",
                    dual(it.amount),
                    if (it.partial) WARN else FLOW_IN,
                )
            }
        },
    )

    if (r.unpaid.isNotEmpty()) {
        sectionCard("Still due", emptyList(), chips = r.unpaid.map { "${it.first} · ${it.second}" })
    }

    flushPage()
    val out = ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    return out.toByteArray()
}

/** A page-sized Graphics2D with the background already painted and antialiasing on. */
private fun newGraphics(doc: PDDocument): PdfBoxGraphics2D {
    val g = PdfBoxGraphics2D(doc, PAGE_W, PAGE_H)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = BG
    g.fillRect(0, 0, PAGE_W.toInt(), PAGE_H.toInt())
    return g
}
