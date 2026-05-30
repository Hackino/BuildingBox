package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** Desktop has no share sheet — render the same PDF as Android, then open it or let the user save it. */
class DesktopReportExporter : ReportExporter {

    /** Share = build the PDF and open it in the system viewer (best desktop analogue of a share sheet). */
    override fun sharePdf(report: ReportData) {
        val bytes = buildReportPdf(report)
        val file = File(System.getProperty("java.io.tmpdir"), fileName(report))
        file.writeBytes(bytes)
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
    }

    /** Download = native folder picker; filename is auto. Writes the PDF into the chosen directory. */
    override fun downloadPdf(report: ReportData) {
        val bytes = buildReportPdf(report)
        val name = fileName(report)
        // The Swing chooser must run on the AWT event thread.
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose a folder to save the statement"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                currentDirectory = defaultDir()
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val dir = chooser.selectedFile ?: return@invokeLater
                runCatching { File(dir, name).writeBytes(bytes) }
                    .onSuccess { runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(File(dir, name)) } }
            }
        }
    }

    private fun fileName(r: ReportData) = "BuildingBox_${r.month}.pdf"

    private fun defaultDir(): File =
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
}
