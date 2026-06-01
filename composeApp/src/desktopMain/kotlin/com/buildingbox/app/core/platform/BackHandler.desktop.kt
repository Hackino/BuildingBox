package com.buildingbox.app.core.platform

import androidx.compose.runtime.Composable

// Desktop has no system back button; Esc is handled in MainShell via onPreviewKeyEvent.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op
}
