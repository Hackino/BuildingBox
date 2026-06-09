package com.buildingbox.app.feature.reports

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

actual fun renderPdfPages(bytes: ByteArray): List<ImageBitmap> {
    // PdfRenderer needs a seekable file descriptor, so spool to a temp file.
    val tmp = File.createTempFile("bb-preview", ".pdf")
    try {
        tmp.writeBytes(bytes)
        ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        // ~2x page points for a sharp preview.
                        val scale = 2
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.asImageBitmap()
                    }
                }
            }
        }
    } finally {
        tmp.delete()
    }
}
