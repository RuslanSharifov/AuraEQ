package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.SpatialSettings
import com.example.data.model.AppProfile
import com.example.data.model.AudioDeviceType
import com.example.data.model.EqualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "aura_eq_prefs")

class AuraPreferencesRepository(private val context: Context) {

    private val KEY_SPATIAL_SETTINGS = stringPreferencesKey("spatial_settings")
    private val KEY_ENABLED = booleanPreferencesKey("is_enabled")
    private val KEY_CURRENT_PRESET_ID = stringPreferencesKey("current_preset_id")
    private val KEY_CURRENT_GAINS = stringPreferencesKey("current_gains")
    private val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
    private val KEY_TREBLE = intPreferencesKey("treble")
    private val KEY_VIRTUALIZER = intPreferencesKey("virtualizer")
    private val KEY_CUSTOM_PRESETS = stringPreferencesKey("custom_presets")
    private val KEY_APP_PROFILES = stringPreferencesKey("app_profiles")
    private val KEY_DEVICE_PROFILES = stringPreferencesKey("device_profiles")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    private val KEY_ANIMATION_ENABLED = booleanPreferencesKey("animation_enabled")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_DEVICE_AUTO_SWITCH = booleanPreferencesKey("device_auto_switch")
    private val KEY_FOLDER_URIS = stringPreferencesKey("folder_uris")
    private val KEY_EXTERNAL_APP_EQ = booleanPreferencesKey("external_app_eq")
    private val KEY_RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
    private val KEY_REMEMBER_QUEUE = booleanPreferencesKey("remember_queue")
    private val KEY_PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
    private val KEY_LAST_PLAYED_URI = stringPreferencesKey("last_played_uri")
    private val KEY_LAST_PLAYED_POSITION = stringPreferencesKey("last_played_position")
    private val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
    private val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
    private val KEY_GLASS_INTENSITY = intPreferencesKey("glass_intensity")
    private val KEY_KOFI_URL = stringPreferencesKey("kofi_url")

    val spatialSettingsFlow: Flow<SpatialSettings> = context.dataStore.data.map { prefs ->
        try {
            val obj = JSONObject(prefs[KEY_SPATIAL_SETTINGS] ?: "{}")
            SpatialSettings(
                is8dEnabled = obj.optBoolean("is8dEnabled", false),
                mode = obj.optString("mode", "off"),
                leftPercent = obj.optInt("leftPercent", 100),
                rightPercent = obj.optInt("rightPercent", 100),
                leftTone = obj.optInt("leftTone", 0),
                rightTone = obj.optInt("rightTone", 0),
                staticPan = obj.optInt("staticPan", 0),
                depth = obj.optInt("depth", 100),
                periodSeconds = obj.optDouble("periodSeconds", 8.0).toFloat()
            ).sanitized()
        } catch (_: Exception) {
            SpatialSettings()
        }
    }.distinctUntilChanged()

    suspend fun saveSpatialSettings(value: SpatialSettings) {
        val state = value.sanitized()
        val obj = JSONObject()
            .put("is8dEnabled", state.is8dEnabled)
            .put("mode", state.mode)
            .put("leftPercent", state.leftPercent)
            .put("rightPercent", state.rightPercent)
            .put("leftTone", state.leftTone)
            .put("rightTone", state.rightTone)
            .put("staticPan", state.staticPan)
            .put("depth", state.depth)
            .put("periodSeconds", state.periodSeconds)
        context.dataStore.edit { it[KEY_SPATIAL_SETTINGS] = obj.toString() }
    }

    /** Reset all *applied* audio parameters atomically; saved user presets remain available. */
    suspend fun resetAudioTuning(disable: Boolean = false) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_PRESET_ID] = "flat"
            prefs[KEY_CURRENT_GAINS] = "0,0,0,0,0"
            prefs[KEY_BASS_BOOST] = 0
            prefs[KEY_TREBLE] = 0
            prefs[KEY_VIRTUALIZER] = 0
            prefs.remove(KEY_SPATIAL_SETTINGS)
            if (disable) prefs[KEY_ENABLED] = false
        }
    }

    val isEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: false
    }.distinctUntilChanged()

    val currentPresetIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_PRESET_ID] ?: "flat"
    }

    val currentGainsFlow: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CURRENT_GAINS] ?: ""
        if (raw.isBlank()) {
            listOf(0, 0, 0, 0, 0)
        } else {
            raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }.distinctUntilChanged()

    val bassBoostFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASS_BOOST] ?: 0
    }.distinctUntilChanged()

    val trebleFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TREBLE] ?: 0
    }.distinctUntilChanged()

    val virtualizerFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIRTUALIZER] ?: 0
    }.distinctUntilChanged()

    val customPresetsFlow: Flow<List<EqualizerPreset>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_PRESETS] ?: ""
        if (raw.isBlank()) {
            emptyList()
        } else {
            try {
                val arr = JSONArray(raw)
                val list = mutableListOf<EqualizerPreset>()
                for (i in 0 until arr.length()) {
                    list.add(EqualizerPreset.fromJson(arr.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val appProfilesFlow: Flow<Map<String, AppProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_APP_PROFILES] ?: ""
        if (raw.isBlank()) {
            emptyMap()
        } else {
            try {
                val arr = JSONArray(raw)
                val map = mutableMapOf<String, AppProfile>()
                for (i in 0 until arr.length()) {
                    val p = AppProfile.fromJson(arr.getJSONObject(i))
                    map[p.packageName] = p
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    val deviceProfilesFlow: Flow<Map<AudioDeviceType, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_DEVICE_PROFILES] ?: ""
        val result = mutableMapOf<AudioDeviceType, String>()
        if (raw.isNotBlank()) {
            try {
                val obj = JSONObject(raw)
                obj.keys().forEach { key ->
                    try {
                        val type = AudioDeviceType.valueOf(key)
                        result[type] = obj.getString(key)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        // Defaults if missing
        if (!result.containsKey(AudioDeviceType.SPEAKER)) result[AudioDeviceType.SPEAKER] = "flat"
        if (!result.containsKey(AudioDeviceType.WIRED)) result[AudioDeviceType.WIRED] = "rock"
        if (!result.containsKey(AudioDeviceType.BLUETOOTH)) result[AudioDeviceType.BLUETOOTH] = "deep_bass"
        result
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "light" // light by default per requirement
    }

    val hapticEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_ENABLED] ?: true
    }

    val animationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANIMATION_ENABLED] ?: true
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "az" // Azerbaijani default per requirement
    }

    val deviceAutoSwitchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_AUTO_SWITCH] ?: true
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setCurrentPresetId(id: String) {
        context.dataStore.edit { it[KEY_CURRENT_PRESET_ID] = id }
    }

    suspend fun setCurrentGains(gains: List<Int>) {
        context.dataStore.edit { it[KEY_CURRENT_GAINS] = gains.joinToString(",") }
    }

    suspend fun setBassBoost(value: Int) {
        context.dataStore.edit { it[KEY_BASS_BOOST] = value.coerceIn(0, 1000) }
    }

    suspend fun setTreble(value: Int) {
        context.dataStore.edit { it[KEY_TREBLE] = value.coerceIn(-1000, 1000) }
    }

    suspend fun setVirtualizer(value: Int) {
        context.dataStore.edit { it[KEY_VIRTUALIZER] = value.coerceIn(0, 1000) }
    }

    suspend fun saveCustomPreset(preset: EqualizerPreset, existing: List<EqualizerPreset>) {
        val updated = existing.filter { it.id != preset.id } + preset
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[KEY_CUSTOM_PRESETS] = arr.toString() }
    }

    suspend fun deleteCustomPreset(presetId: String, existing: List<EqualizerPreset>) {
        val updated = existing.filter { it.id != presetId }
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[KEY_CUSTOM_PRESETS] = arr.toString() }
    }

    suspend fun saveAppProfile(profile: AppProfile, existing: Map<String, AppProfile>) {
        val updated = existing.toMutableMap()
        updated[profile.packageName] = profile
        val arr = JSONArray()
        updated.values.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { it[KEY_APP_PROFILES] = arr.toString() }
    }

    suspend fun saveDeviceProfile(deviceType: AudioDeviceType, presetId: String, existing: Map<AudioDeviceType, String>) {
        val updated = existing.toMutableMap()
        updated[deviceType] = presetId
        val obj = JSONObject()
        updated.forEach { (type, pId) -> obj.put(type.name, pId) }
        context.dataStore.edit { it[KEY_DEVICE_PROFILES] = obj.toString() }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    suspend fun setAnimationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ANIMATION_ENABLED] = enabled }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setDeviceAutoSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEVICE_AUTO_SWITCH] = enabled }
    }

    val folderUrisFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_FOLDER_URIS] ?: "[]"
        try {
            val arr = JSONArray(raw)
            val result = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                result.add(arr.getString(i))
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }.distinctUntilChanged()

    suspend fun saveFolderUris(uris: Set<String>) {
        val arr = JSONArray()
        uris.forEach { arr.put(it) }
        context.dataStore.edit { it[KEY_FOLDER_URIS] = arr.toString() }
    }

    val externalAppEqFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EXTERNAL_APP_EQ] ?: false
    }.distinctUntilChanged()

    suspend fun setExternalAppEq(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EXTERNAL_APP_EQ] = enabled }
    }

    val resumePlaybackFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RESUME_PLAYBACK] ?: true
    }.distinctUntilChanged()

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_PLAYBACK] = enabled }
    }

    val rememberQueueFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMEMBER_QUEUE] ?: true
    }.distinctUntilChanged()

    suspend fun setRememberQueue(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMEMBER_QUEUE] = enabled }
    }

    val pauseOnDisconnectFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAUSE_ON_DISCONNECT] ?: true
    }.distinctUntilChanged()

    suspend fun setPauseOnDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAUSE_ON_DISCONNECT] = enabled }
    }

    val lastPlayedUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PLAYED_URI]
    }.distinctUntilChanged()

    val lastPlayedPositionFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PLAYED_POSITION]?.toLongOrNull() ?: 0L
    }.distinctUntilChanged()

    suspend fun saveLastPlayed(uri: String, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PLAYED_URI] = uri
            prefs[KEY_LAST_PLAYED_POSITION] = positionMs.toString()
        }
    }

    val repeatModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPEAT_MODE] ?: 0
    }.distinctUntilChanged()

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { it[KEY_REPEAT_MODE] = mode }
    }

    val shuffleModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHUFFLE_MODE] ?: false
    }.distinctUntilChanged()

    suspend fun setShuffleMode(shuffle: Boolean) {
        context.dataStore.edit { it[KEY_SHUFFLE_MODE] = shuffle }
    }

    val glassIntensityFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_GLASS_INTENSITY] ?: 80
    }.distinctUntilChanged()

    suspend fun setGlassIntensity(intensity: Int) {
        context.dataStore.edit { it[KEY_GLASS_INTENSITY] = intensity }
    }

    val kofiUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_KOFI_URL] ?: "https://ko-fi.com"
    }.distinctUntilChanged()

    suspend fun setKofiUrl(url: String) {
        context.dataStore.edit { it[KEY_KOFI_URL] = url }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun exportJson(): String {
        val obj = JSONObject()
        // Read a DataStore snapshot. Calling map() without collecting the Flow never runs it.
        val prefs = context.dataStore.data.first()
        prefs.asMap().forEach { (key, value) ->
            obj.put(key.name, value)
        }
        return obj.toString(2)
    }

    suspend fun importJson(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            context.dataStore.edit { prefs ->
                if (obj.has("spatial_settings")) prefs[KEY_SPATIAL_SETTINGS] = obj.getString("spatial_settings")
                if (obj.has("is_enabled")) prefs[KEY_ENABLED] = obj.getBoolean("is_enabled")
                if (obj.has("current_preset_id")) prefs[KEY_CURRENT_PRESET_ID] = obj.getString("current_preset_id")
                if (obj.has("current_gains")) prefs[KEY_CURRENT_GAINS] = obj.getString("current_gains")
                if (obj.has("bass_boost")) prefs[KEY_BASS_BOOST] = obj.getInt("bass_boost")
                if (obj.has("treble")) prefs[KEY_TREBLE] = obj.getInt("treble")
                if (obj.has("virtualizer")) prefs[KEY_VIRTUALIZER] = obj.getInt("virtualizer")
                if (obj.has("custom_presets")) prefs[KEY_CUSTOM_PRESETS] = obj.getString("custom_presets")
                if (obj.has("app_profiles")) prefs[KEY_APP_PROFILES] = obj.getString("app_profiles")
                if (obj.has("device_profiles")) prefs[KEY_DEVICE_PROFILES] = obj.getString("device_profiles")
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
