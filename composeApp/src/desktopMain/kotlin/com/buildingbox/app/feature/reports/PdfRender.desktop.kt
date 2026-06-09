package com.buildingbox.app.feature.reports

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun renderPdfPages(bytes: ByteArray): List<ImageBitmap> {
    PDDocument.load(bytes).use { doc ->
        val renderer = PDFRenderer(doc)
        return (0 until doc.numberOfPages).map { i ->
            // 144 DPI = crisp preview without huge memory use.
            val awt = renderer.renderImageWithDPI(i, 144f)
            val baos = ByteArrayOutputStream()
            ImageIO.write(awt, "png", baos)
            Image.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
        }
    }
}
