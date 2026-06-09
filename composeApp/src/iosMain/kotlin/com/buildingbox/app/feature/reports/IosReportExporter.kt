package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter

class IosReportExporter : ReportExporter {
    override fun sharePdf(report: ReportData) {
        // iOS PDF/share via UIActivityViewController — not wired in this build.
        println("[reports] PDF share not implemented on iOS")
    }

    override fun pdfBytes(reports: List<ReportData>): ByteArray {
        println("[reports] PDF build not implemented on iOS")
        return ByteArray(0)
    }

    override fun savePdf(bytes: ByteArray, suggestedName: String) {
        println("[reports] PDF save not implemented on iOS")
    }
}
