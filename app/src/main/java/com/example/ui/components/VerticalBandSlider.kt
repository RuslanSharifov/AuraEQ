package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EqualizerBand
import com.example.ui.theme.AuraAccent
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VerticalBandSlider(
    band: EqualizerBand,
    isEnabled: Boolean,
    hapticEnabled: Boolean,
    onGainChanged: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val latestOnGainChanged by rememberUpdatedState(onGainChanged)
    val latestValue by rememberUpdatedState(band.currentGainMilliBel)
    val latestHaptics by rememberUpdatedState(hapticEnabled)
    val minGain = band.minMilliBel.toInt()
    val maxGain = band.maxMilliBel.toInt().coerceAtLeast(minGain + 1)
    val fraction = ((band.currentGainMilliBel.toFloat() - minGain) / (maxGain - minGain))
        .coerceIn(0f, 1f)
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Column(
        modifier = modifier.testTag("band_slider_${band.index}").padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val db = band.currentGainMilliBel / 100f
        Text(
            text = if (isEnabled) "%.1f".format(db).let { if (db > 0) "+$it" else it } else "0.0",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(46.dp)
                .height(170.dp)
                // Capture the DOWN event immediately. This slider owns vertical movement,
                // rather than the screen's enclosing verticalScroll or horizontalScroll.
                // Do NOT key on 'band' or 'gain': doing so cancels an active drag each frame.
                .pointerInput(isEnabled, minGain, maxGain) {
                    if (!isEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var lastSent = latestValue.toInt()
                        val trackHeight = size.height.toFloat().coerceAtLeast(1f)
                        fun updateAt(y: Float) {
                            val ratio = (1f - y.coerceIn(0f, trackHeight) / trackHeight)
                                .coerceIn(0f, 1f)
                            val raw = (minGain + ratio * (maxGain - minGain)).roundToInt()
                            val value = if (abs(raw) < 60 && minGain <= 0 && maxGain >= 0) 0 else raw
                            if (lastSent != value) {
                                val crossedZero = (lastSent < 0 && value >= 0) ||
                                    (lastSent > 0 && value <= 0)
                                lastSent = value
                                latestOnGainChanged(value.toShort())
                                if (latestHaptics && crossedZero) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        updateAt(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            updateAt(change.position.y)
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            val trackHeight = 170.dp
            val thumbHeight = 26.dp
            Canvas(
                modifier = Modifier
                    .width(20.dp)
                    .height(trackHeight)
            ) {
                val cx = size.width / 2f
                val top = thumbHeight.toPx() / 2f
                val bottom = size.height - top
                val y = bottom - fraction * (bottom - top)
                val neutralY = bottom - ((0f - minGain) / (maxGain - minGain))
                    .coerceIn(0f, 1f) * (bottom - top)
                drawLine(
                    color = if (isDark) Color(0x55FFFFFF) else Color(0x440E1630),
                    start = Offset(cx, top),
                    end = Offset(cx, bottom),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (isEnabled) {
                    drawLine(
                        color = AuraAccent,
                        start = Offset(cx, neutralY),
                        end = Offset(cx, y),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.3f),
                    start = Offset(0f, neutralY),
                    end = Offset(size.width, neutralY),
                    strokeWidth = 2.dp.toPx()
                )
            }
            val thumbOffset = ((170f - 26f) * (1f - fraction)).roundToInt()
            // Layout in dp, unlike pointer coordinates in pixels.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (thumbOffset).dp)
                    .size(26.dp)
                    .shadow(4.dp, CircleShape, spotColor = AuraAccent)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) Brush.radialGradient(listOf(Color.White, AuraAccent))
                        else Brush.radialGradient(listOf(Color(0xFFE2E6F0), Color(0xFFBAC3D6)))
                    )
            ) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (isEnabled) AuraAccent else Color(0xFF8B96AF))
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = band.centerFreqFormatted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
