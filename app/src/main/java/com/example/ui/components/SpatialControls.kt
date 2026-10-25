package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SpatialSettings
import com.example.ui.theme.AuraAccent

@Composable
fun SpatialControls(
    settings: SpatialSettings,
    enabled: Boolean,
    onChange: (SpatialSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 26.dp, elevation = 4.dp) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.spatial_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.spatial_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(
                    "off" to stringResource(R.string.spatial_off),
                    "static" to stringResource(R.string.spatial_static),
                    "dynamic" to stringResource(R.string.spatial_dynamic)
                ).forEach { (mode, label) ->
                    Button(
                        modifier = Modifier.weight(1f).testTag("spatial_mode_$mode"),
                        onClick = { onChange(settings.copy(mode = mode)) },
                        enabled = enabled,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.mode == mode) AuraAccent
                                else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (settings.mode == mode) androidx.compose.ui.graphics.Color.White
                                else MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }

            EarSlider(stringResource(R.string.spatial_left_volume), settings.leftPercent, 0, 100, "%",
                enabled, "spatial_left_volume") { onChange(settings.copy(leftPercent = it)) }
            EarSlider(stringResource(R.string.spatial_right_volume), settings.rightPercent, 0, 100, "%",
                enabled, "spatial_right_volume") { onChange(settings.copy(rightPercent = it)) }
            EarSlider(stringResource(R.string.spatial_left_tone), settings.leftTone, -100, 100, "",
                enabled, "spatial_left_tone") { onChange(settings.copy(leftTone = it)) }
            EarSlider(stringResource(R.string.spatial_right_tone), settings.rightTone, -100, 100, "",
                enabled, "spatial_right_tone") { onChange(settings.copy(rightTone = it)) }

            if (settings.mode != "off") {
                EarSlider(
                    stringResource(R.string.spatial_depth), settings.depth, 0, 100, "%",
                    enabled, "spatial_depth"
                ) { onChange(settings.copy(depth = it)) }
            }
            if (settings.mode == "static") {
                EarSlider(
                    stringResource(R.string.spatial_pan), settings.staticPan, -100, 100, "",
                    enabled, "spatial_pan"
                ) { onChange(settings.copy(staticPan = it)) }
            }
            if (settings.mode == "dynamic") {
                EarSlider(
                    stringResource(R.string.spatial_speed),
                    settings.periodSeconds.toInt(), 2, 30,
                    "s", enabled, "spatial_speed"
                ) { onChange(settings.copy(periodSeconds = it.toFloat())) }
                Text(
                    stringResource(R.string.spatial_speed_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EarSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    enabled: Boolean,
    tag: String,
    onChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value$unit", color = AuraAccent, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        value = value.coerceIn(min, max).toFloat(),
        onValueChange = { onChange(it.toInt().coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat(),
        enabled = enabled,
        colors = SliderDefaults.colors(thumbColor = AuraAccent, activeTrackColor = AuraAccent)
    )
}
