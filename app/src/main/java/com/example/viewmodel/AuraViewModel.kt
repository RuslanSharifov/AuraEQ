package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AuraApplication
import com.example.audio.TestAudioPlayerManager
import com.example.audio.TestPlayerState
import com.example.data.model.AppProfile
import com.example.data.model.AudioDeviceType
import com.example.data.model.AudioEngineCapabilities
import com.example.data.model.EqualizerBand
import com.example.data.model.EqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class AuraUiState(
    val isEnabled: Boolean = true,
    val activePresetId: String = "flat",
    val activePresetName: String = "Normal / Flat",
    val factoryPresets: List<EqualizerPreset> = EqualizerPreset.getFactoryPresets(),
    val customPresets: List<EqualizerPreset> = emptyList(),
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoost: Int = 0, // 0..1000
    val treble: Int = 0, // -1000..1000
    val virtualizer: Int = 0, // 0..1000
    val capabilities: AudioEngineCapabilities = AudioEngineCapabilities(),
    val currentDeviceType: AudioDeviceType = AudioDeviceType.SPEAKER,
    val currentDeviceName: String = "Phone Speaker",
    val activeSessionCount: Int = 0,
    val testPlayerState: TestPlayerState = TestPlayerState(),
    val installedApps: List<AppProfile> = emptyList(),
    val deviceProfiles: Map<AudioDeviceType, String> = emptyMap(),
    val themeMode: String = "light",
    val hapticEnabled: Boolean = true,
    val animationEnabled: Boolean = true,
    val language: String = "az",
    val deviceAutoSwitch: Boolean = true,
    val activeAppSessions: Set<String> = emptySet()
)

class AuraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AuraApplication
    private val audioEffectManager = app.audioEffectManager
    private val preferences = app.preferencesRepository

    private val testAudioPlayer = TestAudioPlayerManager(application) { sessionId ->
        audioEffectManager.attachSession(sessionId, application.packageName)
    }

    private val _uiState = MutableStateFlow(AuraUiState())
    val uiState: StateFlow<AuraUiState> = _uiState.asStateFlow()

    private val activeAppPackages = mutableSetOf<String>()

    init {
        // Collect preferences and engine flows
        viewModelScope.launch {
            preferences.isEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(isEnabled = enabled)
                audioEffectManager.setMasterEnabled(enabled)
            }
        }

        viewModelScope.launch {
            preferences.currentPresetIdFlow.collect { presetId ->
                val allPresets = _uiState.value.factoryPresets + _uiState.value.customPresets
                val name = allPresets.find { it.id == presetId }?.name ?: "Custom"
                _uiState.value = _uiState.value.copy(
                    activePresetId = presetId,
                    activePresetName = name
                )
            }
        }

        viewModelScope.launch {
            preferences.bassBoostFlow.collect { bb ->
                _uiState.value = _uiState.value.copy(bassBoost = bb)
                audioEffectManager.setBassBoost(bb)
            }
        }

        viewModelScope.launch {
            preferences.trebleFlow.collect { treble ->
                _uiState.value = _uiState.value.copy(treble = treble)
                audioEffectManager.setTreble(treble)
            }
        }

        viewModelScope.launch {
            preferences.virtualizerFlow.collect { virt ->
                _uiState.value = _uiState.value.copy(virtualizer = virt)
                audioEffectManager.setVirtualizer(virt)
            }
        }

        viewModelScope.launch {
            preferences.customPresetsFlow.collect { customs ->
                _uiState.value = _uiState.value.copy(customPresets = customs)
            }
        }

        viewModelScope.launch {
            preferences.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }

        viewModelScope.launch {
            preferences.hapticEnabledFlow.collect { haptic ->
                _uiState.value = _uiState.value.copy(hapticEnabled = haptic)
            }
        }

        viewModelScope.launch {
            preferences.animationEnabledFlow.collect { anim ->
                _uiState.value = _uiState.value.copy(animationEnabled = anim)
            }
        }

        viewModelScope.launch {
            preferences.languageFlow.collect { lang ->
                _uiState.value = _uiState.value.copy(language = lang)
            }
        }

        viewModelScope.launch {
            preferences.deviceAutoSwitchFlow.collect { autoSwitch ->
                _uiState.value = _uiState.value.copy(deviceAutoSwitch = autoSwitch)
            }
        }

        viewModelScope.launch {
            preferences.deviceProfilesFlow.collect { devProfiles ->
                _uiState.value = _uiState.value.copy(deviceProfiles = devProfiles)
            }
        }

        viewModelScope.launch {
            audioEffectManager.hardwareBands.collect { bands ->
                _uiState.value = _uiState.value.copy(bands = bands)
            }
        }

        viewModelScope.launch {
            audioEffectManager.capabilities.collect { caps ->
                _uiState.value = _uiState.value.copy(capabilities = caps)
            }
        }

        viewModelScope.launch {
            audioEffectManager.currentDeviceType.collect { devType ->
                _uiState.value = _uiState.value.copy(currentDeviceType = devType)
                if (_uiState.value.deviceAutoSwitch) {
                    val assignedPreset = _uiState.value.deviceProfiles[devType]
                    if (assignedPreset != null) {
                        applyPresetById(assignedPreset)
                    }
                }
            }
        }

        viewModelScope.launch {
            audioEffectManager.currentDeviceName.collect { name ->
                _uiState.value = _uiState.value.copy(currentDeviceName = name)
            }
        }

        viewModelScope.launch {
            audioEffectManager.activeSessions.collect { sessions ->
                _uiState.value = _uiState.value.copy(activeSessionCount = sessions.size)
            }
        }

        viewModelScope.launch {
            testAudioPlayer.state.collect { playerState ->
                _uiState.value = _uiState.value.copy(testPlayerState = playerState)
            }
        }

        // Listen for session open/close broadcasts
        viewModelScope.launch {
            app.sessionEvents.collect { event ->
                if (event.isOpen && event.packageName != null) {
                    activeAppPackages.add(event.packageName)
                    // Check if this app has an assigned preset profile!
                    val appProfile = _uiState.value.installedApps.find { it.packageName == event.packageName }
                    if (appProfile != null && appProfile.isEnabled) {
                        applyPresetById(appProfile.presetId)
                    }
                } else if (!event.isOpen && event.packageName != null) {
                    activeAppPackages.remove(event.packageName)
                }
                _uiState.value = _uiState.value.copy(activeAppSessions = activeAppPackages.toSet())
                loadInstalledMediaApps()
            }
        }

        // Load installed media apps
        loadInstalledMediaApps()
    }

    fun toggleMasterPower() {
        val newState = !_uiState.value.isEnabled
        viewModelScope.launch {
            preferences.setEnabled(newState)
        }
    }

    fun selectPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            preferences.setCurrentPresetId(preset.id)
            preferences.setCurrentGains(preset.gains)
            preferences.setBassBoost(preset.bassBoost)
            preferences.setTreble(preset.treble)
            preferences.setVirtualizer(preset.virtualizer)

            audioEffectManager.setBandsGains(preset.gains)
            audioEffectManager.setBassBoost(preset.bassBoost)
            audioEffectManager.setTreble(preset.treble)
            audioEffectManager.setVirtualizer(preset.virtualizer)
        }
    }

    private fun applyPresetById(presetId: String) {
        val allPresets = _uiState.value.factoryPresets + _uiState.value.customPresets
        val target = allPresets.find { it.id == presetId } ?: return
        selectPreset(target)
    }

    fun updateBandGain(bandIndex: Short, gainMilliBel: Short) {
        audioEffectManager.setBandGain(bandIndex, gainMilliBel)
        val currentGains = _uiState.value.bands.map { it.currentGainMilliBel.toInt() }.toMutableList()
        if (bandIndex < currentGains.size) {
            currentGains[bandIndex.toInt()] = gainMilliBel.toInt()
        }
        viewModelScope.launch {
            preferences.setCurrentPresetId("custom_tuning")
            preferences.setCurrentGains(currentGains)
        }
    }

    fun updateBassBoost(value: Int) {
        audioEffectManager.setBassBoost(value)
        viewModelScope.launch {
            preferences.setBassBoost(value)
        }
    }

    fun updateTreble(value: Int) {
        audioEffectManager.setTreble(value)
        viewModelScope.launch {
            preferences.setTreble(value)
        }
    }

    fun updateVirtualizer(value: Int) {
        audioEffectManager.setVirtualizer(value)
        viewModelScope.launch {
            preferences.setVirtualizer(value)
        }
    }

    fun resetToNeutral() {
        val flat = EqualizerPreset.getFactoryPresets().first()
        selectPreset(flat)
    }

    fun saveCurrentAsCustomPreset(name: String) {
        val gains = _uiState.value.bands.map { it.currentGainMilliBel.toInt() }
        val newPreset = EqualizerPreset(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = name,
            gains = gains,
            bassBoost = _uiState.value.bassBoost,
            treble = _uiState.value.treble,
            virtualizer = _uiState.value.virtualizer,
            isCustom = true
        )
        viewModelScope.launch {
            preferences.saveCustomPreset(newPreset, _uiState.value.customPresets)
            selectPreset(newPreset)
        }
    }

    fun duplicatePreset(preset: EqualizerPreset) {
        val duplicate = preset.copy(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = "${preset.name} (Copy)",
            isCustom = true
        )
        viewModelScope.launch {
            preferences.saveCustomPreset(duplicate, _uiState.value.customPresets)
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            preferences.deleteCustomPreset(presetId, _uiState.value.customPresets)
            if (_uiState.value.activePresetId == presetId) {
                resetToNeutral()
            }
        }
    }

    fun toggleAppProfile(packageName: String, isEnabled: Boolean) {
        val existing = _uiState.value.installedApps.find { it.packageName == packageName } ?: return
        val updated = existing.copy(isEnabled = isEnabled)
        viewModelScope.launch {
            val map = preferences.appProfilesFlow.first().toMutableMap()
            map[packageName] = updated
            preferences.saveAppProfile(updated, map)
            loadInstalledMediaApps()
        }
    }

    fun setAppPreset(packageName: String, presetId: String) {
        val existing = _uiState.value.installedApps.find { it.packageName == packageName } ?: return
        val updated = existing.copy(presetId = presetId)
        viewModelScope.launch {
            val map = preferences.appProfilesFlow.first().toMutableMap()
            map[packageName] = updated
            preferences.saveAppProfile(updated, map)
            loadInstalledMediaApps()
        }
    }

    fun setDeviceProfilePreset(type: AudioDeviceType, presetId: String) {
        viewModelScope.launch {
            val currentMap = _uiState.value.deviceProfiles
            preferences.saveDeviceProfile(type, presetId, currentMap)
        }
    }

    fun toggleTestPlayerPlayPause() {
        testAudioPlayer.togglePlayPause()
    }

    fun seekTestPlayer(positionMs: Long) {
        testAudioPlayer.seekTo(positionMs)
    }

    fun loadCustomAudioUri(uri: Uri, name: String) {
        testAudioPlayer.playCustomUri(uri, name)
    }

    fun loadReferenceTrack() {
        testAudioPlayer.loadReferenceTrack()
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHapticEnabled(enabled)
        }
    }

    fun setAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAnimationEnabled(enabled)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferences.setLanguage(lang)
        }
    }

    fun setDeviceAutoSwitch(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDeviceAutoSwitch(enabled)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            preferences.resetAll()
            resetToNeutral()
        }
    }

    suspend fun exportProfilesJson(): String {
        return preferences.exportJson()
    }

    suspend fun importProfilesJson(json: String): Boolean {
        return preferences.importJson(json)
    }

    private fun loadInstalledMediaApps() {
        // Access the mutable session set on the main thread before switching dispatchers.
        val activePackagesSnapshot = activeAppPackages.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = getApplication<Application>().packageManager
                val savedMap = preferences.appProfilesFlow.first()

            val discovered = mutableListOf<AppProfile>()

            // Query music and media intents
            val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            val musicApps = pm.queryIntentActivities(musicIntent, 0)

            for (resolveInfo in musicApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg != getApplication<Application>().packageName) {
                    val label = resolveInfo.loadLabel(pm).toString()
                    val saved = savedMap[pkg]
                    val isSessionActive = activePackagesSnapshot.contains(pkg)
                    discovered.add(
                        AppProfile(
                            packageName = pkg,
                            appName = label,
                            presetId = saved?.presetId ?: "flat",
                            isEnabled = saved?.isEnabled ?: true,
                            isSessionActive = isSessionActive
                        )
                    )
                }
            }

            // Check well-known media apps (Spotify, YouTube, VLC, etc.)
            val knownMediaPkgs = listOf(
                "com.spotify.music" to "Spotify",
                "com.google.android.apps.youtube.music" to "YouTube Music",
                "com.google.android.youtube" to "YouTube",
                "org.videolan.vlc" to "VLC",
                "com.soundcloud.android" to "SoundCloud",
                "deezer.android.app" to "Deezer",
                "com.apple.android.music" to "Apple Music",
                "com.amazon.mp3" to "Amazon Music",
                "com.maxmpz.audioplayer" to "Poweramp"
            )

            for ((pkg, defaultLabel) in knownMediaPkgs) {
                if (discovered.none { it.packageName == pkg }) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val saved = savedMap[pkg]
                        val isSessionActive = activeAppPackages.contains(pkg)
                        discovered.add(
                            AppProfile(
                                packageName = pkg,
                                appName = label.ifBlank { defaultLabel },
                                presetId = saved?.presetId ?: "flat",
                                isEnabled = saved?.isEnabled ?: true,
                                isSessionActive = isSessionActive
                            )
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Include saved profiles even if not currently queried
                        if (savedMap.containsKey(pkg)) {
                            val saved = savedMap[pkg]!!
                            discovered.add(saved)
                        }
                    }
                }
            }

            // Empty states should be truthful; never display apps that are not installed.
                _uiState.value = _uiState.value.copy(installedApps = discovered.distinctBy { it.packageName })
            } catch (e: Exception) {
                // OEM PackageManager and DataStore may fail. Never terminate the app on first launch.
                Log.e("AuraViewModel", "Unable to load installed media apps", e)
                _uiState.value = _uiState.value.copy(installedApps = emptyList())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        testAudioPlayer.release()
    }
}
