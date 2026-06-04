package com.buildingbox.app.feature.reports.domain

/** Renders a [ReportData] to a PDF for sharing (share sheet) or downloading (to device storage). */
interface ReportExporter {
    fun sharePdf(report: ReportData)
    fun downloadPdf(report: ReportData)

    /**
     * Download a single PDF that bundles multiple months' reports — each month starts
     * on its own page. [reports] should already be in the desired order.
     */
    fun downloadMultiPdf(reports: List<ReportData>)
}
