package com.buildingbox.app.feature.reports.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.designsystem.AppButton
import com.buildingbox.app.core.designsystem.LoadingOverlay
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.feature.reports.renderPdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen PDF preview: renders each page as an image, with a Download button that
 * persists the bytes via the platform exporter (folder picker on desktop, Downloads +
 * notification on Android).
 */
@Composable
fun PdfPreviewScreen(
    bytes: ByteArray,
    suggestedName: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    val c = LocalAppColors.current

    // Rasterize off the main thread; null while still rendering.
    val pages by produceState<List<ImageBitmap>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.Default) {
            runCatching { renderPdfPages(bytes) }.getOrElse { emptyList() }
        }
    }

    Column(Modifier.fillMaxSize().background(c.surfaceInset)) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val p = pages
            when {
                p == null -> LoadingOverlay(visible = true)
                p.isEmpty() -> Text(
                    "Couldn't render the preview.",
                    color = c.textTertiary,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(p) { _, img ->
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().background(Color.White),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(c.surface).padding(16.dp)) {
            AppButton(
                "Download",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                leading = { Icon(Icons.Filled.Download, null, modifier = Modifier.height(18.dp)) },
            )
        }
    }
}
