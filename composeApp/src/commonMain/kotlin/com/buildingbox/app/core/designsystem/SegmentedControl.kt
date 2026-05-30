package com.buildingbox.app.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style segmented control with an animated sliding thumb. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surfaceInset).padding(4.dp),
    ) {
        val segWidth = maxWidth / options.size
        val offset by animateDpAsState(segWidth * index)

        androidx.compose.foundation.layout.Box(
            Modifier.offset(x = offset).width(segWidth).height(36.dp).clip(RoundedCornerShape(50)).background(c.surface),
        )
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            options.forEach { (value, label) ->
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (value == selected) MaterialTheme.colorScheme.onSurface else c.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
