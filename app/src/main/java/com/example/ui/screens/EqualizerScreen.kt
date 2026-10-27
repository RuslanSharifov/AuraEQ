package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SpatialSettings
import com.example.data.model.EqualizerPreset
import com.example.ui.components.EqualizerCurveCanvas
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialControl
import com.example.ui.components.SystemVolumeControl
import com.example.ui.components.VerticalBandSlider
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraGreenSuccess
import com.example.ui.theme.AuraRedError
import com.example.ui.theme.AuraSoftCyan
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EqualizerScreen(
    state: AuraUiState,
    viewModel: AuraViewModel,
    onStartExternal8d: () -> Unit = {},
    onStopExternal8d: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ekvalayzer", "8D Audio", "Profillər")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen")
    ) {
        // Sub-tabs at top
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 20.dp,
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = AuraAccent
                    )
                }
            },
            containerColor = Color.Transparent,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedSubTab == index) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> EqualizerMainTab(state = state, viewModel = viewModel)
            1 -> Dedicated8DAudioTab(state = state, viewModel = viewModel, onStartCapture = onStartExternal8d, onStopCapture = onStopExternal8d)
            2 -> PresetsSubTab(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun EqualizerMainTab(
    state: AuraUiState,
    viewModel: AuraViewModel
) {
    val view = LocalView.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 120.dp)
    ) {
        // Frequency Curve Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.active_preset, state.activePresetName),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.device_hardware_bands, state.bands.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                showSaveDialog = true
                            },
                            modifier = Modifier.testTag("save_preset_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Save Preset", tint = AuraAccent)
                        }
                        IconButton(
                            onClick = {
                                if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.resetToNeutral()
                            },
                            modifier = Modifier.testTag("reset_eq_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                EqualizerCurveCanvas(
                    bands = state.bands,
                    isEnabled = state.isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }

        // Quick Preset Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allPresets = state.factoryPresets + state.customPresets
            allPresets.forEach { preset ->
                val isSelected = preset.id == state.activePresetId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) AuraAccent.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AuraAccent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.selectPreset(preset)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) AuraAccent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Equalizer Sliders Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                state.bands.forEach { band ->
                    VerticalBandSlider(
                        band = band,
                        isEnabled = state.isEnabled,
                        hapticEnabled = state.hapticEnabled,
                        onGainChanged = { newGain ->
                            viewModel.updateBandGain(band.index, newGain)
                        }
                    )
                }
            }
        }

        // Bass Boost, Treble, Virtualizer Dials Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlassDialControl(
                    title = stringResource(R.string.bass_boost),
                    value = state.bassBoost,
                    minValue = 0,
                    maxValue = 1000,
                    isEnabled = state.isEnabled,
                    hapticEnabled = state.hapticEnabled,
                    onValueChange = { viewModel.updateBassBoost(it) }
                )

                GlassDialControl(
                    title = stringResource(R.string.treble),
                    value = state.treble,
                    minValue = -1000,
                    maxValue = 1000,
                    isEnabled = state.isEnabled,
                    hapticEnabled = state.hapticEnabled,
                    onValueChange = { viewModel.updateTreble(it) }
                )

                GlassDialControl(
                    title = stringResource(R.string.virtualizer),
                    value = state.virtualizer,
                    minValue = 0,
                    maxValue = 1000,
                    isEnabled = state.isEnabled,
                    hapticEnabled = state.hapticEnabled,
                    onValueChange = { viewModel.updateVirtualizer(it) }
                )
            }
        }

        // System Media Volume Card
        SystemVolumeControl(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.save_profile)) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            viewModel.saveCurrentAsCustomPreset(newPresetName.trim())
                            showSaveDialog = false
                            newPresetName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.save_profile))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun Dedicated8DAudioTab(
    state: AuraUiState,
    viewModel: AuraViewModel,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    val scrollState = rememberScrollState()
    val spatial = state.spatialSettings
    var isClockwise by remember { mutableStateOf(true) }

    // Pulsing and orbiting animation
    val infiniteTransition = rememberInfiniteTransition(label = "8D_orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isClockwise) 360f else -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (spatial.periodSeconds * 1000).toInt().coerceAtLeast(1000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 120.dp)
    ) {
        // Main 8D Switch Card with Orbiting Radar Animation
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.page_8d_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.page_8d_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = spatial.is8dEnabled,
                        onCheckedChange = { checked ->
                            viewModel.setSpatialSettings(
                                spatial.copy(
                                    is8dEnabled = checked,
                                    mode = if (checked) "dynamic" else "off"
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AuraAccent),
                        modifier = Modifier.testTag("dedicated_8d_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Real-time Rotating Stereo Orbit Canvas
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    val headColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width * 0.38f

                        // Orbit guide circle
                        drawCircle(
                            color = AuraAccent.copy(alpha = 0.25f),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )

                        // Center Headphone Node
                        drawCircle(
                            color = headColor,
                            radius = 12.dp.toPx(),
                            center = center
                        )

                        if (spatial.is8dEnabled) {
                            val rad = Math.toRadians(orbitAngle.toDouble())
                            val lx = center.x + (radius * cos(rad)).toFloat()
                            val ly = center.y + (radius * sin(rad)).toFloat()

                            val rx = center.x - (radius * cos(rad)).toFloat()
                            val ry = center.y - (radius * sin(rad)).toFloat()

                            // Left satellite node (Cyan)
                            drawCircle(
                                color = AuraSoftCyan,
                                radius = 9.dp.toPx(),
                                center = Offset(lx, ly)
                            )

                            // Right satellite node (Purple Accent)
                            drawCircle(
                                color = AuraAccent,
                                radius = 9.dp.toPx(),
                                center = Offset(rx, ry)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (spatial.is8dEnabled) stringResource(R.string.spatial_enabled) else stringResource(R.string.spatial_disabled),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (spatial.is8dEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Rotation Speed & Direction Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.rotation_speed),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.rotation_speed_value, spatial.periodSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraAccent
                )

                Slider(
                    value = spatial.periodSeconds,
                    onValueChange = { newVal ->
                        viewModel.setSpatialSettings(spatial.copy(periodSeconds = newVal))
                    },
                    valueRange = 2f..24f,
                    colors = SliderDefaults.colors(thumbColor = AuraAccent, activeTrackColor = AuraAccent),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isClockwise = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isClockwise) AuraAccent else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.direction_clockwise), fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            isClockwise = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isClockwise) AuraAccent else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.direction_counter_clockwise), fontSize = 11.sp)
                    }
                }
            }
        }

        // Spatial Depth & Intensity Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.spatial_intensity),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${spatial.depth}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraAccent
                )

                Slider(
                    value = spatial.depth.toFloat(),
                    onValueChange = { newVal ->
                        viewModel.setSpatialSettings(spatial.copy(depth = newVal.toInt()))
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = AuraAccent, activeTrackColor = AuraAccent),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.stereo_pan),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = spatial.staticPan.toFloat(),
                    onValueChange = { newVal ->
                        viewModel.setSpatialSettings(spatial.copy(staticPan = newVal.toInt()))
                    },
                    valueRange = -100f..100f,
                    colors = SliderDefaults.colors(thumbColor = AuraSoftCyan, activeTrackColor = AuraSoftCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Reset to Defaults Button
        Button(
            onClick = { viewModel.resetSpatialSettings() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.reset_defaults), color = MaterialTheme.colorScheme.onSurface)
        }

        // Quality and Platform Note
        Text(
            text = stringResource(R.string.internal_player_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PresetsSubTab(
    state: AuraUiState,
    viewModel: AuraViewModel
) {
    val view = LocalView.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<EqualizerPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<EqualizerPreset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.presets_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.presets_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Custom Presets
            if (state.customPresets.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.preset_custom_tag),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AuraAccent,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                items(state.customPresets, key = { it.id }) { preset ->
                    PresetItemCard(
                        preset = preset,
                        isSelected = preset.id == state.activePresetId,
                        isCustom = true,
                        onSelect = {
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.selectPreset(preset)
                        },
                        onDuplicate = { viewModel.duplicatePreset(preset) },
                        onDelete = { presetToDelete = preset }
                    )
                }
            }

            // Factory Presets
            item {
                Text(
                    text = stringResource(R.string.preset_system_tag),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            }

            items(state.factoryPresets, key = { it.id }) { preset ->
                PresetItemCard(
                    preset = preset,
                    isSelected = preset.id == state.activePresetId,
                    isCustom = false,
                    onSelect = {
                        if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.selectPreset(preset)
                    },
                    onDuplicate = { viewModel.duplicatePreset(preset) },
                    onDelete = {}
                )
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = AuraAccent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp)
                .testTag("create_preset_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Preset")
        }
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_new_preset)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.saveCurrentAsCustomPreset(nameInput.trim())
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.create_new_preset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    presetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text(stringResource(R.string.delete_preset)) },
            text = { Text(target.name) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomPreset(target)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_preset))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PresetItemCard(
    preset: EqualizerPreset,
    isSelected: Boolean,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AuraAccent.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AuraAccent, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isSelected) AuraAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bas: ${preset.bassBoost / 10}% • 3D: ${preset.virtualizer / 10}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }

            if (isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
