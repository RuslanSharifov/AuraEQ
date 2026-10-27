package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuraAccent
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: AuraUiState,
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle(0)

    var showResetDialog by remember { mutableStateOf(false) }
    var showKoFiDialog by remember { mutableStateOf(false) }
    var customKoFiInput by remember { mutableStateOf(state.kofiUrl) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.addFolder(treeUri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 120.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ==========================================
        // 1. AUDIO & DSP SECTION
        // ==========================================
        SettingsSectionHeader(title = stringResource(R.string.settings_group_audio), icon = Icons.Default.Equalizer)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Master EQ Switch
                SettingToggleRow(
                    title = "Ekvalayzer Master Gücü",
                    subtitle = if (state.isEnabled) stringResource(R.string.status_power_on) else stringResource(R.string.status_power_off),
                    checked = state.isEnabled,
                    onCheckedChange = { viewModel.toggleMasterPower() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // External App EQ Switch
                SettingToggleRow(
                    title = stringResource(R.string.setting_external_eq_title),
                    subtitle = stringResource(R.string.setting_external_eq_desc),
                    checked = state.externalAppEq,
                    onCheckedChange = { viewModel.setExternalAppEq(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Auto Headphone Profile Switch
                SettingToggleRow(
                    title = "Qulaqlıq profilini avtomatik seç",
                    subtitle = "Qulaqlıq və ya Bluetooth qoşulduqda uyğun profili yüklə",
                    checked = state.deviceAutoSwitch,
                    onCheckedChange = { viewModel.toggleDeviceAutoSwitch() }
                )
            }
        }

        // ==========================================
        // 2. PLAYBACK & QUEUE SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_playback), icon = Icons.Default.PlayArrow)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Resume Playback
                SettingToggleRow(
                    title = stringResource(R.string.setting_resume_playback_title),
                    subtitle = stringResource(R.string.setting_resume_playback_desc),
                    checked = state.resumePlayback,
                    onCheckedChange = { viewModel.setResumePlayback(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Pause on Disconnect
                SettingToggleRow(
                    title = stringResource(R.string.setting_pause_disconnect_title),
                    subtitle = stringResource(R.string.setting_pause_disconnect_desc),
                    checked = state.pauseOnDisconnect,
                    onCheckedChange = { viewModel.setPauseOnDisconnect(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Remember Queue
                SettingToggleRow(
                    title = stringResource(R.string.setting_remember_queue_title),
                    subtitle = stringResource(R.string.setting_remember_queue_desc),
                    checked = state.rememberQueue,
                    onCheckedChange = { viewModel.setRememberQueue(it) }
                )
            }
        }

        // ==========================================
        // 3. LIBRARY & STORAGE SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_library), icon = Icons.Default.Folder)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Library Statistics Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kitabxana Statistikası",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${stringResource(R.string.stats_tracks, trackCount)} • ${stringResource(R.string.stats_folders, state.selectedFolderUris.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AuraAccent
                        )
                    }

                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraAccent)
                    ) {
                        Text(stringResource(R.string.add_folder), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rescan Library Button
                SettingActionRow(
                    title = stringResource(R.string.setting_rescan_library),
                    subtitle = "Bütün seçilmiş qovluqlarda yeni musiqiləri axtar",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        viewModel.rescanLibrary()
                        Toast.makeText(context, "Kitabxana skan edilir…", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Clear Artwork Cache Button
                SettingActionRow(
                    title = stringResource(R.string.setting_clear_artwork),
                    subtitle = "Diskdə saxlanılan albom örtüyü şəkillərini təmizlə",
                    icon = Icons.Default.CleaningServices,
                    onClick = {
                        viewModel.clearArtworkCache()
                        Toast.makeText(context, "Örtük keşi təmizləndi", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ==========================================
        // 4. APPEARANCE & THEME SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_appearance), icon = Icons.Default.Brightness4)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Görünüş Mövzusu",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("light" to "Açıq", "dark" to "Tünd", "system" to "Sistem").forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = AuraAccent)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Glass Intensity Slider
                Text(
                    text = "${stringResource(R.string.setting_glass_intensity)}: ${state.glassIntensity}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = state.glassIntensity.toFloat(),
                    onValueChange = { viewModel.setGlassIntensity(it.toInt()) },
                    valueRange = 20f..100f,
                    colors = SliderDefaults.colors(thumbColor = AuraAccent, activeTrackColor = AuraAccent),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Haptic Feedback
                SettingToggleRow(
                    title = "Toxunma Vibrasiyası (Haptic)",
                    subtitle = "Düymələr və sürgülər üçün toxunma əks-əlaqəsi",
                    checked = state.hapticEnabled,
                    onCheckedChange = { viewModel.toggleHaptic() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Animations
                SettingToggleRow(
                    title = "Hamar Keçid Animasiyaları",
                    subtitle = "Dizayn elementləri arasında vizual effektlər",
                    checked = state.animationEnabled,
                    onCheckedChange = { viewModel.toggleAnimation() }
                )
            }
        }

        // ==========================================
        // 5. LANGUAGE SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_language), icon = Icons.Default.Language)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                listOf("az" to "Azərbaycan dili", "en" to "English").forEach { (langCode, langName) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setLanguage(langCode) }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = state.language == langCode,
                            onClick = { viewModel.setLanguage(langCode) },
                            colors = RadioButtonDefaults.colors(selectedColor = AuraAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = langName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. DATA & PRESETS SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_data), icon = Icons.Default.Upload)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingActionRow(
                    title = "Parametrləri İxrac Et",
                    subtitle = "Profilləri və tənzimləmələri JSON faylı kimi saxla",
                    icon = Icons.Default.Download,
                    onClick = {
                        scope.launch {
                            val json = viewModel.exportSettingsJson()
                            Toast.makeText(context, "Parametrlər köçürüldü (${json.length} bayt)", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingActionRow(
                    title = "Bütün Tənzimləmələri Sıfırla",
                    subtitle = "Standart zavod parametrlərinə qayıt",
                    icon = Icons.Default.RestartAlt,
                    onClick = { showResetDialog = true }
                )
            }
        }

        // ==========================================
        // 7. ABOUT SECTION
        // ==========================================
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionHeader(title = stringResource(R.string.settings_group_about), icon = Icons.Default.Info)

        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AuraAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AuraEQ — Music Player & Equalizer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Versiya 1.0.0 (Offline-First)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingActionRow(
                    title = stringResource(R.string.setting_github),
                    subtitle = "Açıq mənbəli layihə (Apache-2.0)",
                    icon = Icons.Default.OpenInNew,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RuslanSharifov/AuraEQ"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingActionRow(
                    title = stringResource(R.string.setting_kofi_support),
                    subtitle = state.kofiUrl,
                    icon = Icons.Default.Favorite,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.kofiUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            showKoFiDialog = true
                        }
                    }
                )
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Tənzimləmələri sıfırlamaq istəyirsiniz?") },
            text = { Text("Bütün fərdi tənzimləmələr ilkin standart vəziyyətə qaytarılacaq.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetToNeutral()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sıfırla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit Ko-fi Link Dialog
    if (showKoFiDialog) {
        AlertDialog(
            onDismissRequest = { showKoFiDialog = false },
            title = { Text(stringResource(R.string.setting_kofi_support)) },
            text = {
                OutlinedTextField(
                    value = customKoFiInput,
                    onValueChange = { customKoFiInput = it },
                    label = { Text("Ko-fi URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setKofiUrl(customKoFiInput.trim())
                        showKoFiDialog = false
                    }
                ) {
                    Text("Saxla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKoFiDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AuraAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AuraAccent)
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AuraAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
