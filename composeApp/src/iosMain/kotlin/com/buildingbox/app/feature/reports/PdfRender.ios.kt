package com.buildingbox.app.feature.reports

import androidx.compose.ui.graphics.ImageBitmap

// PDF preview not wired on iOS in this build.
actual fun renderPdfPages(bytes: ByteArray): List<ImageBitmap> = emptyList()
