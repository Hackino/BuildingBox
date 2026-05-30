package com.buildingbox.app.feature.reports.domain

/** Renders a [ReportData] to a PDF for sharing (share sheet) or downloading (to device storage). */
interface ReportExporter {
    fun sharePdf(report: ReportData)
    fun downloadPdf(report: ReportData)
}
