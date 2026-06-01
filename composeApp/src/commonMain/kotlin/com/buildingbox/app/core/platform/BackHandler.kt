package com.buildingbox.app.core.platform

import androidx.compose.runtime.Composable

/**
 * Intercept the platform "back" gesture while [enabled].
 *  - Android: wraps androidx.activity.compose.BackHandler (system back / predictive back).
 *  - Desktop: no-op — desktop has no system back button; Esc is handled separately in
 *    MainShell via onPreviewKeyEvent.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
