package com.buildingbox.app.feature.reports

import androidx.compose.ui.graphics.ImageBitmap

/** Rasterize each page of a PDF (given as bytes) into an [ImageBitmap] for in-app preview. */
expect fun renderPdfPages(bytes: ByteArray): List<ImageBitmap>
