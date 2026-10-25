package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EqualizerBand
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraSoftCyan
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
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val minMilli = band.minMilliBel.toFloat()
    val maxMilli = band.maxMilliBel.toFloat()
    val currentMilli = band.currentGainMilliBel.toFloat()

    // Fraction 0.0 (bottom, min) to 1.0 (top, max)
    val fraction = ((currentMilli - minMilli) / (maxMilli - minMilli)).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag("band_slider_${band.index}")
            .padding(horizontal = 2.dp)
    ) {
        // Gain dB value label
        val gainDb = band.currentGainMilliBel / 100.0f
        val formattedDb = if (gainDb > 0) "+%.1f".format(gainDb) else "%.1f".format(gainDb)

        Text(
            text = if (isEnabled) formattedDb else "0.0",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isEnabled) {
                if (gainDb > 0) AuraAccent else MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Vertical Track Box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(44.dp)
                .height(160.dp)
                .onGloballyPositioned { coordinates ->
                    trackHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(isEnabled, band) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (trackHeightPx > 0) {
                                val touchY = offset.y.coerceIn(0f, trackHeightPx)
                                val newFraction = 1f - (touchY / trackHeightPx)
                                val newGain = (minMilli + newFraction * (maxMilli - minMilli)).roundToInt()
                                // Snap near 0 mB (within +/- 50 mB)
                                val finalGain = if (kotlin.math.abs(newGain) < 60) 0 else newGain
                                onGainChanged(finalGain.toShort())
                                if (hapticEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (trackHeightPx > 0) {
                                val deltaFraction = -dragAmount / trackHeightPx
                                val newFrac = (fraction + deltaFraction).coerceIn(0f, 1f)
                                val rawGain = (minMilli + newFrac * (maxMilli - minMilli)).roundToInt()
                                val finalGain = if (kotlin.math.abs(rawGain) < 60) 0 else rawGain
                                if (finalGain.toShort() != band.currentGainMilliBel) {
                                    onGainChanged(finalGain.toShort())
                                    if (hapticEnabled && finalGain == 0) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            // Track Groove Background
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
                    )
            )

            // Center 0dB indicator notch
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(
                        if (isDark) Color(0x66FFFFFF) else Color(0x33000000),
                        shape = RoundedCornerShape(1.dp)
                    )
            )

            // Active Colored Fill Track from center
            val thumbRadius = 14.dp
            val centerYFraction = 0.5f

            // Calculate Thumb Y offset
            val density = androidx.compose.ui.platform.LocalDensity.current
            val thumbYOffset = if (trackHeightPx > 0) {
                // fraction=1.0 -> y=0; fraction=0.0 -> y=trackHeightPx - thumbSize
                val thumbSizePx = with(density) { 28.dp.toPx() }
                val availableRange = trackHeightPx - thumbSizePx
                (availableRange * (1f - fraction)).roundToInt()
            } else {
                0
            }

            // Glow thumb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(0, thumbYOffset) }
                    .size(28.dp)
                    .shadow(
                        elevation = if (isEnabled) 4.dp else 1.dp,
                        shape = CircleShape,
                        spotColor = if (isEnabled) AuraAccent else Color.Transparent
                    )
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) {
                            Brush.radialGradient(
                                colors = listOf(Color.White, AuraAccent)
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFE2E6F0), Color(0xFFBAC3D6))
                            )
                        }
                    )
            ) {
                // Center accent pip
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) AuraAccent else Color(0xFF8B96AF))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Center Frequency label (e.g. 60Hz, 3.6kHz)
        Text(
            text = band.centerFreqFormatted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
