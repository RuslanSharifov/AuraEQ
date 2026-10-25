package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraSoftCyan
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun GlassDialControl(
    title: String,
    value: Int, // e.g. 0..1000, or -1000..1000
    minValue: Int = 0,
    maxValue: Int = 1000,
    unit: String = "%",
    isEnabled: Boolean,
    hapticEnabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    val range = (maxValue - minValue).toFloat()
    val fraction = ((value - minValue) / range).coerceIn(0f, 1f)
    val updatedFraction by rememberUpdatedState(fraction)
    val updatedValue by rememberUpdatedState(value)
    val updatedOnValueChange by rememberUpdatedState(onValueChange)
    val updatedHaptics by rememberUpdatedState(hapticEnabled)

    val animatedFraction by animateFloatAsState(
        targetValue = if (isEnabled) fraction else 0f,
        animationSpec = spring(),
        label = "dial_fraction"
    )

    val accent = if (isEnabled) AuraAccent else (if (isDark) Color(0xFF4A4E69) else Color(0xFFBAC2D6))
    val cyan = if (isEnabled) AuraSoftCyan else (if (isDark) Color(0xFF2C3147) else Color(0xFFE2E7F4))

    // Display string: percentage or value
    val displayValue = if (unit == "%") {
        val pct = (fraction * 100).roundToInt()
        "$pct%"
    } else {
        "$value"
    }

    GlassCard(
        modifier = modifier.testTag("dial_${title.lowercase().replace(" ", "_")}"),
        cornerRadius = 22.dp,
        elevation = 3.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dial Arc Gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .pointerInput(isEnabled, minValue, maxValue) {
                        if (!isEnabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            // Accumulate each delta across this single gesture, instead of
                            // repeatedly adding it to a stale composition-time fraction.
                            var dragFraction = updatedFraction
                            var lastSent = updatedValue
                            drag(down.id) { change ->
                                val delta = -change.positionChange().y / 200f
                                change.consume()
                                dragFraction = (dragFraction + delta).coerceIn(0f, 1f)
                                val next = (minValue + dragFraction * range).roundToInt()
                                if (next != lastSent) {
                                    lastSent = next
                                    updatedOnValueChange(next)
                                    if (updatedHaptics && next % 100 == 0) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    val startAngle = 135f
                    val sweepAngle = 270f

                    // Background Track Arc
                    drawArc(
                        color = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Arc
                    if (animatedFraction > 0.01f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(AuraAccent, AuraSoftCyan, AuraAccent),
                                center = Offset(size.width / 2f, size.height / 2f)
                            ),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * animatedFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Value Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isEnabled) displayValue else "0%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step Buttons (- / +) for accessibility and touch precision
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                FilledIconButton(
                    onClick = {
                        val step = (range * 0.1f).roundToInt().coerceAtLeast(1)
                        val newVal = (value - step).coerceIn(minValue, maxValue)
                        onValueChange(newVal)
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    enabled = isEnabled && value > minValue,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isDark) Color(0x33FFFFFF) else Color(0x14000000)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease $title",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledIconButton(
                    onClick = {
                        val step = (range * 0.1f).roundToInt().coerceAtLeast(1)
                        val newVal = (value + step).coerceIn(minValue, maxValue)
                        onValueChange(newVal)
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    enabled = isEnabled && value < maxValue,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isDark) Color(0x33FFFFFF) else Color(0x14000000)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase $title",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
