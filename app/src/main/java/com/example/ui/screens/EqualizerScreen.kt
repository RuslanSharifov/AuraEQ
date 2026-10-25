package com.example.ui.screens

import android.net.Uri
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EqualizerPreset
import com.example.AuraApplication
import com.example.ui.components.SpatialControls
import com.example.ui.components.EqualizerCurveCanvas
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDialControl
import com.example.ui.components.SystemVolumeControl
import com.example.ui.components.VerticalBandSlider
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraGreenSuccess
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel

@Composable
fun EqualizerScreen(
    state: AuraUiState,
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
        if (granted && state.isEnabled) {
            (context.applicationContext as? AuraApplication)?.eqNotification?.update(true)
        }
    }

    // System Document Picker for audio files
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Selected Track"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
            viewModel.loadCustomAudioUri(uri, fileName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Preset Horizontal Chips Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            val allPresets = state.factoryPresets + state.customPresets
            allPresets.forEach { preset ->
                val isSelected = state.activePresetId == preset.id
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("preset_chip_${preset.id}")
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) AuraAccent else (if (isDark) Color(0x2EFFFFFF) else Color(0x1A000000))
                        )
                        .clickable {
                            viewModel.selectPreset(preset)
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = preset.name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Independent control for the phone's real media stream volume.
        // It stays enabled even when the EQ engine is switched off.
        SystemVolumeControl(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(14.dp))

        // Equalizer Card (Visual Curve + Vertical Band Sliders)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            elevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with Hardware info & Reset / Save
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AuraAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.device_hardware_bands, state.bands.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row {
                        // Reset button
                        IconButton(
                            onClick = {
                                viewModel.resetToNeutral()
                                if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.reset_neutral),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Save current profile button
                        IconButton(
                            onClick = {
                                newPresetName = ""
                                showSaveDialog = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(R.string.save_profile),
                                tint = AuraAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Interactive Frequency Response Curve
                EqualizerCurveCanvas(
                    bands = state.bands,
                    isEnabled = state.isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Vertical Band Sliders
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    state.bands.forEach { band ->
                        VerticalBandSlider(
                            band = band,
                            isEnabled = state.isEnabled,
                            hapticEnabled = state.hapticEnabled,
                            onGainChanged = { gain ->
                                viewModel.updateBandGain(band.index, gain)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dials Section: Bass Boost, Treble, and 3D Virtualizer
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Bass Boost Dial
            GlassDialControl(
                title = stringResource(R.string.bass_boost),
                value = state.bassBoost,
                minValue = 0,
                maxValue = 1000,
                unit = "%",
                isEnabled = state.isEnabled,
                hapticEnabled = state.hapticEnabled,
                onValueChange = { viewModel.updateBassBoost(it) },
                modifier = Modifier.weight(1f)
            )

            // Treble Dial
            GlassDialControl(
                title = stringResource(R.string.treble),
                value = state.treble,
                minValue = -1000,
                maxValue = 1000,
                unit = "%",
                isEnabled = state.isEnabled,
                hapticEnabled = state.hapticEnabled,
                onValueChange = { viewModel.updateTreble(it) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3D Virtualizer Surround Control
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            elevation = 3.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x338274F5) else Color(0x228274F5))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SurroundSound,
                            contentDescription = null,
                            tint = AuraAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.virtualizer),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(state.virtualizer / 10f).toInt()}%",
                            fontSize = 12.sp,
                            color = AuraAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = state.virtualizer.toFloat(),
                    onValueChange = { viewModel.updateVirtualizer(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = AuraAccent,
                        activeTrackColor = AuraAccent,
                        inactiveTrackColor = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
                    ),
                    modifier = Modifier
                        .width(150.dp)
                        .testTag("virtualizer_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real per-ear PCM control for the built-in player.
        SpatialControls(
            settings = state.spatialSettings,
            enabled = state.isEnabled,
            onChange = viewModel::setSpatialSettings,
            onReset = viewModel::resetSpatialSettings
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isEnabled && !notificationGranted && Build.VERSION.SDK_INT >= 33) {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.notification_permission_hint))
                    Button(onClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.notification_permission_allow))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Integrated Test Audio Player Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevation = 5.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AuraAccent.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = AuraAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.test_player_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.testPlayerState.currentTitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Session ID Badge
                    if (state.testPlayerState.audioSessionId > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AuraGreenSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.test_player_session_id, state.testPlayerState.audioSessionId),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuraGreenSuccess
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                val duration = state.testPlayerState.durationMs.coerceAtLeast(1)
                val current = state.testPlayerState.currentPositionMs.coerceIn(0, duration)
                val progressFraction = current.toFloat() / duration

                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        val targetMs = (fraction * duration).toLong()
                        viewModel.seekTestPlayer(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AuraAccent,
                        activeTrackColor = AuraAccent,
                        inactiveTrackColor = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Controls: Play/Pause, Reference Signal, Pick File
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play/Pause Button
                    Button(
                        onClick = {
                            viewModel.toggleTestPlayerPlayPause()
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraAccent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("test_player_toggle")
                    ) {
                        Icon(
                            imageVector = if (state.testPlayerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.testPlayerState.isPlaying) stringResource(R.string.test_player_pause) else stringResource(R.string.test_player_play),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.testPlayerState.isPlaying) stringResource(R.string.test_player_pause) else stringResource(R.string.test_player_play),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Reference Tone Button
                    TextButton(
                        onClick = {
                            viewModel.loadReferenceTrack()
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.test_player_sample),
                            fontSize = 12.sp,
                            color = AuraAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Pick File Button
                    Button(
                        onClick = {
                            audioPickerLauncher.launch("audio/*")
                            if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.test_player_pick_file),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(144.dp))
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_create_preset),
                    fontWeight = FontWeight.Bold
                )
            },
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
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraAccent)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
