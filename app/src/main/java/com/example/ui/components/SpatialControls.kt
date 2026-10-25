package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlsEnabled = enabled && settings.is8dEnabled
    // Panel open/closed state survives rotation; the header switch stays
    // reachable even when the panel is collapsed.
    var expanded by rememberSaveable { mutableStateOf(true) }
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 26.dp, elevation = 4.dp) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .testTag("spatial_panel_header")
                ) {
                    Text(
                        stringResource(R.string.spatial_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(if (settings.is8dEnabled) R.string.spatial_enabled else R.string.spatial_disabled),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Switch(
                    modifier = Modifier.testTag("spatial_effect_toggle"),
                    checked = settings.is8dEnabled,
                    enabled = enabled,
                    onCheckedChange = { checked ->
                        // Turning 8D on with "Adi" (off) selected would produce no audible
                        // change, so start from the moving mode instead.
                        val mode = if (checked && settings.mode == "off") "dynamic" else settings.mode
                        onChange(settings.copy(is8dEnabled = checked, mode = mode))
                    }
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.spatial_collapse else R.string.spatial_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(24.dp)
                        .rotate(if (expanded) 180f else 0f)
                        .clickable { expanded = !expanded }
                        .testTag("spatial_panel_toggle")
                )
            }

            if (expanded) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.spatial_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        modifier = Modifier.testTag("spatial_reset"),
                        onClick = onReset
                    ) { Text(stringResource(R.string.spatial_reset), color = MaterialTheme.colorScheme.primary) }
                }
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
                            enabled = controlsEnabled,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.mode == mode) AuraAccent
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (settings.mode == mode) androidx.compose.ui.graphics.Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.83f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
                            )
                        ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }

                EarSlider(stringResource(R.string.spatial_left_volume), settings.leftPercent, 0, 100, "%",
                    controlsEnabled, "spatial_left_volume") { onChange(settings.copy(leftPercent = it)) }
                EarSlider(stringResource(R.string.spatial_right_volume), settings.rightPercent, 0, 100, "%",
                    controlsEnabled, "spatial_right_volume") { onChange(settings.copy(rightPercent = it)) }
                EarSlider(stringResource(R.string.spatial_left_tone), settings.leftTone, -100, 100, "",
                    controlsEnabled, "spatial_left_tone") { onChange(settings.copy(leftTone = it)) }
                EarSlider(stringResource(R.string.spatial_right_tone), settings.rightTone, -100, 100, "",
                    controlsEnabled, "spatial_right_tone") { onChange(settings.copy(rightTone = it)) }

                if (settings.mode != "off") {
                    EarSlider(
                        stringResource(R.string.spatial_depth), settings.depth, 0, 100, "%",
                        controlsEnabled, "spatial_depth"
                    ) { onChange(settings.copy(depth = it)) }
                }
                if (settings.mode == "static") {
                    EarSlider(
                        stringResource(R.string.spatial_pan), settings.staticPan, -100, 100, "",
                        controlsEnabled, "spatial_pan"
                    ) { onChange(settings.copy(staticPan = it)) }
                }
                if (settings.mode == "dynamic") {
                    EarSlider(
                        stringResource(R.string.spatial_speed),
                        settings.periodSeconds.toInt(), 2, 30,
                        "s", controlsEnabled, "spatial_speed"
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
