package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.AuraAccent
import kotlin.math.roundToInt

/**
 * Controls the phone's actual MUSIC stream level, not an EQ gain/preamp.
 * Uses AudioManager and works even when the equalizer's master switch is off.
 */
@Composable
fun SystemVolumeControl(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val minVolume = remember(audioManager) {
        runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
    }
    val maxVolume = remember(audioManager) {
        runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15).coerceAtLeast(minVolume + 1)
    }
    var volume by remember(audioManager) {
        mutableIntStateOf(
            runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                .getOrDefault(minVolume).coerceIn(minVolume, maxVolume)
        )
    }
    val isFixed = remember(audioManager) {
        runCatching { audioManager.isVolumeFixed }.getOrDefault(false)
    }

    fun writeVolume(requested: Int) {
        if (isFixed) return
        val target = requested.coerceIn(minVolume, maxVolume)
        // Show changes immediately. Do not summon the system volume panel on each slider frame.
        volume = target
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            .onFailure {
                volume = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(volume).coerceIn(minVolume, maxVolume)
            }
    }

    // Hardware volume-key changes made while the app was backgrounded should be reflected.
    DisposableEffect(audioManager, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                volume = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(volume).coerceIn(minVolume, maxVolume)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.system_volume),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${((volume - minVolume) * 100f / (maxVolume - minVolume)).roundToInt()}%",
                    color = AuraAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { writeVolume(volume - 1) }, enabled = !isFixed) {
                    Icon(Icons.Filled.VolumeDown, contentDescription = stringResource(R.string.volume_down), tint = AuraAccent)
                }
                Slider(
                    modifier = Modifier.weight(1f).testTag("system_volume_slider"),
                    value = volume.toFloat().coerceIn(minVolume.toFloat(), maxVolume.toFloat()),
                    valueRange = minVolume.toFloat()..maxVolume.toFloat(),
                    onValueChange = { writeVolume(it.roundToInt()) },
                    enabled = !isFixed,
                    colors = SliderDefaults.colors(
                        thumbColor = AuraAccent,
                        activeTrackColor = AuraAccent
                    )
                )
                IconButton(onClick = { writeVolume(volume + 1) }, enabled = !isFixed) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = stringResource(R.string.volume_up), tint = AuraAccent)
                }
            }
        }
    }
}
