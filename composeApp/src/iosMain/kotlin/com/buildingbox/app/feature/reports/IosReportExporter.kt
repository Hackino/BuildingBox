package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.ReportExporter

class IosReportExporter : ReportExporter {
    override fun sharePdf(report: ReportData) {
        // iOS PDF/share via UIActivityViewController — not wired in this build.
        println("[reports] PDF share not implemented on iOS")
    }

    override fun downloadPdf(report: ReportData) {
        println("[reports] PDF download not implemented on iOS")
    }
}
