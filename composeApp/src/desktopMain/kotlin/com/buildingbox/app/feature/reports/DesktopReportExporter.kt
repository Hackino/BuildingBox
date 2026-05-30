package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter
import com.buildingbox.app.feature.reports.domain.reportToText
import java.awt.Desktop
import java.io.File

/** Desktop has no share sheet — write the statement to a file and open it / save to Downloads. */
class DesktopReportExporter : ReportExporter {
    override fun sharePdf(report: ReportData) {
        val file = File(System.getProperty("java.io.tmpdir"), "BuildingBox_${report.month}.txt")
        file.writeText(reportToText(report))
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
    }

    override fun downloadPdf(report: ReportData) {
        val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))
        File(downloads, "BuildingBox_${report.month}.txt").writeText(reportToText(report))
    }
}
