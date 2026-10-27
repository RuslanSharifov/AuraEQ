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
import com.example.audio.SpatialSettings
import com.example.audio.External8dCaptureService
import com.example.data.model.AppProfile
import com.example.data.model.AudioDeviceType
import com.example.data.model.AudioEngineCapabilities
import com.example.data.model.EqualizerBand
import com.example.data.model.EqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class AuraUiState(
    val isEnabled: Boolean = false,
    val activePresetId: String = "flat",
    val activePresetName: String = "Normal / Flat",
    val factoryPresets: List<EqualizerPreset> = EqualizerPreset.getFactoryPresets(),
    val customPresets: List<EqualizerPreset> = emptyList(),
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoost: Int = 0, // 0..1000
    val treble: Int = 0, // -1000..1000
    val virtualizer: Int = 0, // 0..1000
    val spatialSettings: SpatialSettings = SpatialSettings(),
    val capabilities: AudioEngineCapabilities = AudioEngineCapabilities(),
    val currentDeviceType: AudioDeviceType = AudioDeviceType.SPEAKER,
    val currentDeviceName: String = "Phone Speaker",
    val activeSessionCount: Int = 0,
    val installedApps: List<AppProfile> = emptyList(),
    val deviceProfiles: Map<AudioDeviceType, String> = emptyMap(),
    val themeMode: String = "light",
    val hapticEnabled: Boolean = true,
    val animationEnabled: Boolean = true,
    val language: String = "az",
    val deviceAutoSwitch: Boolean = true,
    val activeAppSessions: Set<String> = emptySet(),
    val connectedAppPackages: Set<String> = emptySet()
)

class AuraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AuraApplication
    private val audioEffectManager = app.audioEffectManager
    private val preferences = app.preferencesRepository

    private val _uiState = MutableStateFlow(AuraUiState())
    val uiState: StateFlow<AuraUiState> = _uiState.asStateFlow()

    private val activeAppPackages = mutableSetOf<String>()

    // Audio engine updates happen immediately; durable settings are written only after
    // the user pauses dragging. Avoid launching a DataStore transaction per pixel.
    private var bandSaveJob: Job? = null
    private var bassSaveJob: Job? = null
    private var trebleSaveJob: Job? = null
    private var virtualizerSaveJob: Job? = null
    private var spatialSaveJob: Job? = null
    private val sliderSaveDelayMs = 180L

    private fun cancelPendingSliderSaves() {
        bandSaveJob?.cancel()
        bassSaveJob?.cancel()
        trebleSaveJob?.cancel()
        virtualizerSaveJob?.cancel()
        spatialSaveJob?.cancel()
    }

    init {
        // Collect preferences and engine flows
        viewModelScope.launch {
            preferences.isEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(isEnabled = enabled)
                audioEffectManager.setMasterEnabled(enabled)
                External8dCaptureService.current?.updateSettings(_uiState.value.spatialSettings, enabled)
            }
        }

        viewModelScope.launch {
            preferences.spatialSettingsFlow.collect { spatial ->
                // A user edit still waiting in the save debounce is newer than any
                // stored value; applying the stored one would snap the slider back.
                if (spatialSaveJob?.isActive == true) return@collect
                _uiState.value = _uiState.value.copy(spatialSettings = spatial)
                External8dCaptureService.current?.updateSettings(spatial, _uiState.value.isEnabled)
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
            audioEffectManager.controlledPackages.collect { packages ->
                _uiState.value = _uiState.value.copy(connectedAppPackages = packages)
            }
        }

        // Replayable session snapshot handles broadcasts received before this
        // ViewModel started. Multiple sessions per player are counted correctly.
        viewModelScope.launch {
            app.openSessionPackages.collect { sessions ->
                val packages = sessions.values.filterNotNull().toSet()
                activeAppPackages.clear()
                activeAppPackages.addAll(packages)
                _uiState.value = _uiState.value.copy(activeAppSessions = packages)
                loadInstalledMediaApps()
            }
        }

        }

        // Per-app preset ownership lives inside AudioEffectManager. An incoming
        // Spotify session must NEVER replace global EQ sliders for every app.

        // Load installed media apps
        loadInstalledMediaApps()
    }

    fun retryAudioSessions() {
        audioEffectManager.refreshActiveSessions()
    }

    fun toggleMasterPower() {
        val newState = !_uiState.value.isEnabled
        _uiState.value = _uiState.value.copy(isEnabled = newState)
        audioEffectManager.setMasterEnabled(newState)
        External8dCaptureService.current?.updateSettings(_uiState.value.spatialSettings, newState)
        viewModelScope.launch { preferences.setEnabled(newState) }
    }

    fun selectPreset(preset: EqualizerPreset) {
        cancelPendingSliderSaves()
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
        if (bandIndex < 0 || bandIndex.toInt() >= _uiState.value.bands.size) return
        audioEffectManager.setBandGain(bandIndex, gainMilliBel)
        // Capture each latest change, including a later drag on another frequency.
        val gains = audioEffectManager.hardwareBands.value
            .map { it.currentGainMilliBel.toInt() }
            .toMutableList()
        if (bandIndex.toInt() < gains.size) {
            gains[bandIndex.toInt()] = gainMilliBel.toInt()
        }
        _uiState.value = _uiState.value.copy(activePresetId = "custom_tuning", bands = audioEffectManager.hardwareBands.value)
        bandSaveJob?.cancel()
        bandSaveJob = viewModelScope.launch {
            delay(sliderSaveDelayMs)
            preferences.setCurrentPresetId("custom_tuning")
            preferences.setCurrentGains(gains)
        }
    }

    fun updateBassBoost(value: Int) {
        val newValue = value.coerceIn(0, 1000)
        audioEffectManager.setBassBoost(newValue)
        _uiState.value = _uiState.value.copy(bassBoost = newValue)
        bassSaveJob?.cancel()
        bassSaveJob = viewModelScope.launch {
            delay(sliderSaveDelayMs)
            preferences.setBassBoost(newValue)
        }
    }

    fun updateTreble(value: Int) {
        val newValue = value.coerceIn(-1000, 1000)
        audioEffectManager.setTreble(newValue)
        _uiState.value = _uiState.value.copy(treble = newValue)
        trebleSaveJob?.cancel()
        trebleSaveJob = viewModelScope.launch {
            delay(sliderSaveDelayMs)
            preferences.setTreble(newValue)
        }
    }

    fun updateVirtualizer(value: Int) {
        val newValue = value.coerceIn(0, 1000)
        audioEffectManager.setVirtualizer(newValue)
        _uiState.value = _uiState.value.copy(virtualizer = newValue)
        virtualizerSaveJob?.cancel()
        virtualizerSaveJob = viewModelScope.launch {
            delay(sliderSaveDelayMs)
            preferences.setVirtualizer(newValue)
        }
    }

    fun setSpatialSettings(value: SpatialSettings) {
        val updated = value.sanitized()
        _uiState.value = _uiState.value.copy(spatialSettings = updated)
        External8dCaptureService.current?.updateSettings(updated, _uiState.value.isEnabled)
        spatialSaveJob?.cancel()
        spatialSaveJob = viewModelScope.launch {
            delay(sliderSaveDelayMs)
            preferences.saveSpatialSettings(updated)
        }
    }

    /** Reset just the 8D / left-right headphone controls, without changing EQ bands. */
    fun resetSpatialSettings() {
        spatialSaveJob?.cancel()
        val defaults = SpatialSettings()
        _uiState.value = _uiState.value.copy(spatialSettings = defaults)
        External8dCaptureService.current?.updateSettings(defaults, _uiState.value.isEnabled)
        viewModelScope.launch { preferences.saveSpatialSettings(defaults) }
    }

    fun resetToNeutral() {
        cancelPendingSliderSaves()
        audioEffectManager.setBandsGains(listOf(0, 0, 0, 0, 0))
        audioEffectManager.setBassBoost(0)
        audioEffectManager.setTreble(0)
        audioEffectManager.setVirtualizer(0)
        External8dCaptureService.current?.updateSettings(SpatialSettings(), _uiState.value.isEnabled)
        _uiState.value = _uiState.value.copy(
            activePresetId = "flat",
            activePresetName = "Normal / Flat",
            bassBoost = 0,
            treble = 0,
            virtualizer = 0,
            spatialSettings = SpatialSettings()
        )
        viewModelScope.launch { preferences.resetAudioTuning() }
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
        updateAppPolicy(existing.copy(isEnabled = isEnabled))
    }

    fun setAppPreset(packageName: String, presetId: String) {
        val existing = _uiState.value.installedApps.find { it.packageName == packageName } ?: return
        updateAppPolicy(existing.copy(presetId = presetId, useCustomPreset = true))
    }

    fun useGlobalAppPreset(packageName: String) {
        val existing = _uiState.value.installedApps.find { it.packageName == packageName } ?: return
        updateAppPolicy(existing.copy(useCustomPreset = false))
    }

    private fun updateAppPolicy(profile: AppProfile) {
        _uiState.value = _uiState.value.copy(installedApps = _uiState.value.installedApps.map {
            if (it.packageName == profile.packageName) profile else it
        })
        viewModelScope.launch {
            val map = preferences.appProfilesFlow.first().toMutableMap()
            map[profile.packageName] = profile
            preferences.saveAppProfile(profile, map)
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
    }

    fun seekTestPlayer(positionMs: Long) {
    }

    fun loadCustomAudioUri(uri: Uri, name: String) {
    }

    fun loadReferenceTrack() {
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
                            useCustomPreset = saved?.useCustomPreset ?: false,
                            isSessionActive = isSessionActive
                        )
                    )
                }
            }

            // Offer explicit bypass for all user-launchable apps Android permits
            // us to discover; this does not imply we can access their audio sessions.
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            for (activity in pm.queryIntentActivities(launcherIntent, 0)) {
                val pkg = activity.activityInfo.packageName
                if (pkg == getApplication<Application>().packageName ||
                    discovered.any { it.packageName == pkg }
                ) continue
                val saved = savedMap[pkg]
                discovered.add(
                    AppProfile(
                        packageName = pkg,
                        appName = activity.loadLabel(pm).toString().ifBlank { pkg },
                        presetId = saved?.presetId ?: "flat",
                        isEnabled = saved?.isEnabled ?: true,
                        useCustomPreset = saved?.useCustomPreset ?: false,
                        isSessionActive = activePackagesSnapshot.contains(pkg)
                    )
                )
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
                        val isSessionActive = activePackagesSnapshot.contains(pkg)
                        discovered.add(
                            AppProfile(
                                packageName = pkg,
                                appName = label.ifBlank { defaultLabel },
                                presetId = saved?.presetId ?: "flat",
                                isEnabled = saved?.isEnabled ?: true,
                                useCustomPreset = saved?.useCustomPreset ?: false,
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
        cancelPendingSliderSaves()
        super.onCleared()
    }
}
