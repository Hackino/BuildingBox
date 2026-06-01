package com.buildingbox.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A dim, input-blocking scrim with a centered spinner. Place LAST inside a Box that
 * fills the area you want to cover so it draws on top. Shown only while [visible].
 *
 *   Box(Modifier.fillMaxSize()) {
 *       ScreenContent()
 *       LoadingOverlay(visible = uiState.loading)
 *   }
 *
 * While visible it swallows all pointer input (prevents double-taps / interaction
 * with the work-in-progress UI underneath).
 */
@Composable
fun LoadingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) { /* consume every gesture while loading */ },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(44.dp), color = c.accent, strokeWidth = 4.dp)
    }
}
