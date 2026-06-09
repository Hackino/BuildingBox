package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** Desktop: build the PDF in memory, preview it in-app, then save via the native Save dialog. */
class DesktopReportExporter : ReportExporter {

    /** Share = build the PDF and open it in the system viewer. */
    override fun sharePdf(report: ReportData) {
        runCatching {
            val bytes = buildReportPdf(report)
            val file = File(System.getProperty("java.io.tmpdir"), "BuildingBox_${report.month}.pdf")
            file.writeBytes(bytes)
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
        }.onFailure { showError("Could not open the statement", it) }
    }

    override fun pdfBytes(reports: List<ReportData>): ByteArray = buildReportsPdf(reports)

    /**
     * Native Save dialog (reliable on macOS & Windows), pre-filled with [suggestedName],
     * then write the bytes to the exact chosen path and open it. Failures are shown in a
     * dialog instead of crashing.
     */
    override fun savePdf(bytes: ByteArray, suggestedName: String) {
        SwingUtilities.invokeLater {
            val dialog = FileDialog(null as Frame?, "Save statement", FileDialog.SAVE).apply {
                file = suggestedName            // auto-named; user can keep or change it
                directory = defaultDir().absolutePath
                isVisible = true
            }
            val chosenName = dialog.file ?: return@invokeLater // null = cancelled
            val chosenDir = dialog.directory ?: defaultDir().absolutePath
            val outFile = File(chosenDir, chosenName.ensurePdf())
            runCatching {
                outFile.writeBytes(bytes)
                runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(outFile) }
            }.onFailure { showError("Could not save the statement", it) }
        }
    }

    private fun String.ensurePdf(): String = if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"

    private fun defaultDir(): File =
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))

    private fun showError(title: String, e: Throwable) {
        // Full stack trace — needed to diagnose ProGuard/runtime failures where the
        // exception message alone is null. Also written to a log file you can copy from.
        val trace = e.stackTraceToString()
        runCatching {
            File(System.getProperty("java.io.tmpdir"), "buildingbox-error.log").writeText("$title\n\n$trace")
        }
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                null,
                "$title:\n\n${trace.take(1500)}\n\n(Full log: ${System.getProperty("java.io.tmpdir")}buildingbox-error.log)",
                "BuildingBox",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}
