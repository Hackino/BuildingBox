package com.buildingbox.app.feature.reports.domain

/**
 * Builds the report PDF and saves it to the device.
 *
 * Flow: the UI calls [pdfBytes] to render the PDF, shows an in-app preview, then calls
 * [savePdf] when the user confirms — which writes the file using the platform's native
 * mechanism (folder picker on desktop, Downloads + notification on Android).
 */
interface ReportExporter {
    /** Share a single month's PDF via the platform share/open mechanism. */
    fun sharePdf(report: ReportData)

    /** Render one or more months into PDF bytes — each month starts on its own page. */
    fun pdfBytes(reports: List<ReportData>): ByteArray

    /**
     * Persist already-built [bytes] to the device.
     *  - Desktop: opens a native Save dialog (pre-filled with [suggestedName]).
     *  - Android: writes to the public Downloads folder and posts a notification
     *    that opens the PDF when tapped.
     */
    fun savePdf(bytes: ByteArray, suggestedName: String)
}

/** Auto filename for a set of reports: single month, or a from→to range. */
fun reportsFileName(reports: List<ReportData>): String = when {
    reports.isEmpty() -> "BuildingBox.pdf"
    reports.size == 1 -> "BuildingBox_${reports.first().month}.pdf"
    else -> "BuildingBox_${reports.first().month}_to_${reports.last().month}.pdf"
}
