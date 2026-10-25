package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EqualizerBand
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraSoftCyan

@Composable
fun EqualizerCurveCanvas(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth().height(140.dp)
) {
    val textMeasurer = rememberTextMeasurer()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val accentColor = if (isEnabled) AuraAccent else (if (isDark) Color(0xFF4A4E69) else Color(0xFFBAC2D6))
    val cyanColor = if (isEnabled) AuraSoftCyan else (if (isDark) Color(0xFF2C3147) else Color(0xFFE2E7F4))
    val gridColor = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)
    val textColor = if (isDark) Color(0x77FFFFFF) else Color(0x7720263B)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val paddingHorizontal = 40.dp.toPx()
        val paddingVertical = 20.dp.toPx()
        val usableHeight = h - paddingVertical * 2
        val centerY = paddingVertical + usableHeight / 2f

        // Draw horizontal reference lines: +12dB, 0dB, -12dB
        val gridYLevels = listOf(
            centerY - usableHeight * 0.4f to "+12",
            centerY to "0",
            centerY + usableHeight * 0.4f to "-12"
        )

        for ((y, label) in gridYLevels) {
            drawLine(
                color = gridColor,
                start = Offset(paddingHorizontal, y),
                end = Offset(w - paddingHorizontal, y),
                strokeWidth = 1.dp.toPx()
            )
            val measuredText = textMeasurer.measure(
                text = "$label dB",
                style = TextStyle(fontSize = 9.sp, color = textColor)
            )
            drawText(
                textLayoutResult = measuredText,
                topLeft = Offset(8.dp.toPx(), y - measuredText.size.height / 2f)
            )
        }

        if (bands.isEmpty()) return@Canvas

        val numPoints = bands.size
        val stepX = (w - paddingHorizontal * 2) / (numPoints - 1).coerceAtLeast(1)

        val points = bands.mapIndexed { index, band ->
            val x = paddingHorizontal + index * stepX
            val minMilli = band.minMilliBel.toFloat().coerceAtMost(-100f)
            val maxMilli = band.maxMilliBel.toFloat().coerceAtLeast(100f)
            val currentMilli = if (isEnabled) band.currentGainMilliBel.toFloat() else 0f
            val normalized = (currentMilli / maxMilli).coerceIn(-1f, 1f)
            val y = centerY - (normalized * (usableHeight * 0.45f))
            Offset(x, y)
        }

        // Build smooth Cubic Bezier Path
        val path = Path()
        val fillPath = Path()

        path.moveTo(points.first().x, points.first().y)
        fillPath.moveTo(points.first().x, centerY + usableHeight / 2f)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val controlPointX = (p0.x + p1.x) / 2f
            path.cubicTo(
                controlPointX, p0.y,
                controlPointX, p1.y,
                p1.x, p1.y
            )
            fillPath.cubicTo(
                controlPointX, p0.y,
                controlPointX, p1.y,
                p1.x, p1.y
            )
        }

        fillPath.lineTo(points.last().x, centerY + usableHeight / 2f)
        fillPath.close()

        // Draw glowing gradient fill under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isEnabled) 0.35f else 0.1f),
                    cyanColor.copy(alpha = if (isEnabled) 0.15f else 0.05f),
                    Color.Transparent
                ),
                startY = paddingVertical,
                endY = centerY + usableHeight / 2f
            )
        )

        // Draw main curve stroke
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(accentColor, cyanColor, accentColor),
                startX = paddingHorizontal,
                endX = w - paddingHorizontal
            ),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Draw node points
        points.forEach { pt ->
            // Outer subtle glow ring
            drawCircle(
                color = accentColor.copy(alpha = if (isEnabled) 0.3f else 0.1f),
                radius = 7.dp.toPx(),
                center = pt
            )
            // Solid center dot
            drawCircle(
                color = if (isEnabled) Color.White else (if (isDark) Color(0xFF6B728E) else Color(0xFFA5B0C8)),
                radius = 3.5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = accentColor,
                radius = 3.5.dp.toPx(),
                center = pt,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
