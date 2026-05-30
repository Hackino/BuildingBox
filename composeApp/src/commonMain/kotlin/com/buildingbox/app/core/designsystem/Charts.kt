package com.buildingbox.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimal area-less line sparkline. */
@Composable
fun Sparkline(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val min = data.min()
        val max = data.max()
        val range = (max - min).takeIf { it != 0f } ?: 1f
        val stepX = size.width / (data.size - 1)
        val pts = data.mapIndexed { i, v -> Offset(i * stepX, size.height - ((v - min) / range) * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawCircle(color, radius = 3.5f, center = pts.last())
    }
}

/** Circular progress donut with centered content. */
@Composable
fun ProgressRing(
    progress: Float,
    track: Color,
    color: Color,
    size: Dp = 108.dp,
    stroke: Dp = 11.dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
            drawArc(color, -90f, progress.coerceIn(0f, 1f) * 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}
