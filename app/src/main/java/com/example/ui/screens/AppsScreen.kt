package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppProfile
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraAmberWarning
import com.example.ui.theme.AuraGreenSuccess
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel

@Composable
fun AppsScreen(
    state: AuraUiState,
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForPreset by remember { mutableStateOf<AppProfile?>(null) }

    val filteredApps = remember(state.installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            state.installedApps
        } else {
            state.installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.apps_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.apps_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.app_eq_platform_notice),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_apps_hint), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraAccent,
                unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000),
                focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x0A000000),
                unfocusedContainerColor = if (isDark) Color(0x14FFFFFF) else Color(0x06000000)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Apps List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val allPresets = state.factoryPresets + state.customPresets
                val assignedPresetName = if (app.useCustomPreset)
                    allPresets.find { it.id == app.presetId }?.name ?: stringResource(R.string.app_global_eq)
                    else stringResource(R.string.app_global_eq)
                val isSessionActive = state.activeAppSessions.contains(app.packageName)
                val isEffectConnected = app.isEnabled && state.isEnabled &&
                    state.connectedAppPackages.contains(app.packageName)

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    elevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // App Icon and Names
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // App Avatar badge
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isEffectConnected) AuraGreenSuccess.copy(alpha = 0.2f)
                                            else AuraAccent.copy(alpha = 0.15f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isEffectConnected) AuraGreenSuccess else AuraAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = app.appName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Enabled = follow global/per-app preset; disabled = bypass ALL
                            // native AuraEQ effects for this particular app.
                            Switch(
                                modifier = Modifier.testTag("app_eq_enabled_${app.packageName}"),
                                checked = app.isEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.toggleAppProfile(app.packageName, checked)
                                    if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AuraAccent
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                if (app.isEnabled) R.string.app_eq_applies else R.string.app_eq_excluded
                            ),
                            fontSize = 11.sp,
                            color = if (app.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else AuraAmberWarning,
                            modifier = Modifier.testTag("app_eq_policy_${app.packageName}")
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Preset Selector Pill & Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Assigned preset pill
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isDark) Color(0x338274F5) else Color(0x228274F5)
                                    )
                                    .clickable {
                                        selectedAppForPreset = app
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = AuraAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = assignedPresetName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AuraAccent
                                    )
                                }
                            }

                            // Real Hardware / Session Status
                            if (isEffectConnected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AuraGreenSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.app_profile_active),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AuraGreenSuccess
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSessionActive) stringResource(R.string.spotify_session_no_control)
                                                else stringResource(R.string.app_profile_listening),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Spotify can signal a session without granting EQ control.
                        // We must not show green "active" just because a broadcast arrived.
                        if (app.packageName == "com.spotify.music" && app.isEnabled && state.isEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isEffectConnected) stringResource(R.string.spotify_connected_detail)
                                    else if (isSessionActive) stringResource(R.string.spotify_no_control_detail)
                                    else stringResource(R.string.spotify_no_session_detail),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSessionActive && !isEffectConnected) {
                                TextButton(onClick = viewModel::retryAudioSessions) {
                                    Text(stringResource(R.string.spotify_retry_session))
                                }
                            }
                        }

                        // Honest Platform Notice when not active session
                        if (!isSessionActive && app.isEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.app_profile_saved_unsupported),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Select Preset Modal Dialog for App
    selectedAppForPreset?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedAppForPreset = null },
            title = {
                Text(
                    text = stringResource(R.string.app_select_preset),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                val allPresets = state.factoryPresets + state.customPresets
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.app_global_eq),
                        fontSize = 14.sp,
                        color = if (!app.useCustomPreset) AuraAccent
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_preset_global")
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.useGlobalAppPreset(app.packageName)
                                selectedAppForPreset = null
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    allPresets.forEach { preset ->
                        val isSelected = app.useCustomPreset && app.presetId == preset.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) AuraAccent.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .clickable {
                                    viewModel.setAppPreset(app.packageName, preset.id)
                                    selectedAppForPreset = null
                                    if (state.hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = preset.name,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AuraAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAppForPreset = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
