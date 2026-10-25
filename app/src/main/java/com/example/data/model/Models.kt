package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val minMilliBel: Short,
    val maxMilliBel: Short,
    val currentGainMilliBel: Short
) {
    val centerFreqFormatted: String
        get() = if (centerFreqHz >= 1000) {
            val kHz = centerFreqHz / 1000.0
            if (kHz == kHz.toInt().toDouble()) "${kHz.toInt()}k" else String.format("%.1fk", kHz)
        } else {
            "${centerFreqHz}Hz"
        }

    val gainDb: Float
        get() = currentGainMilliBel / 100.0f
}

data class EqualizerPreset(
    val id: String,
    val name: String,
    val gains: List<Int>, // in millibels, e.g. [-300, 200, 500, 100, -200]
    val bassBoost: Int = 0, // 0..1000
    val treble: Int = 0, // -1000..1000 (adjusts high frequencies)
    val virtualizer: Int = 0, // 0..1000
    val isCustom: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        val arr = JSONArray()
        gains.forEach { arr.put(it) }
        obj.put("gains", arr)
        obj.put("bassBoost", bassBoost)
        obj.put("treble", treble)
        obj.put("virtualizer", virtualizer)
        obj.put("isCustom", isCustom)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): EqualizerPreset {
            val id = obj.optString("id", "")
            val name = obj.optString("name", "Custom")
            val gainsArr = obj.optJSONArray("gains") ?: JSONArray()
            val gains = mutableListOf<Int>()
            for (i in 0 until gainsArr.length()) {
                gains.add(gainsArr.optInt(i, 0))
            }
            val bassBoost = obj.optInt("bassBoost", 0)
            val treble = obj.optInt("treble", 0)
            val virtualizer = obj.optInt("virtualizer", 0)
            val isCustom = obj.optBoolean("isCustom", true)
            return EqualizerPreset(id, name, gains, bassBoost, treble, virtualizer, isCustom)
        }

        // Factory Presets with normalized curves suitable for 5 bands (or scaled to N bands)
        fun getFactoryPresets(): List<EqualizerPreset> {
            return listOf(
                EqualizerPreset("flat", "Normal / Flat", listOf(0, 0, 0, 0, 0), bassBoost = 0, treble = 0, virtualizer = 0),
                EqualizerPreset("deep_bass", "Deep Bass", listOf(600, 400, 0, -100, -200), bassBoost = 650, treble = -100, virtualizer = 150),
                EqualizerPreset("bass_boost", "Bass Boost", listOf(800, 500, 100, 0, 100), bassBoost = 800, treble = 0, virtualizer = 200),
                EqualizerPreset("vocal_clarity", "Vocal Clarity", listOf(-200, 100, 500, 600, 300), bassBoost = 0, treble = 400, virtualizer = 100),
                EqualizerPreset("rock", "Rock", listOf(500, 300, -100, 200, 500), bassBoost = 400, treble = 300, virtualizer = 250),
                EqualizerPreset("pop", "Pop", listOf(-100, 200, 500, 300, -100), bassBoost = 200, treble = 100, virtualizer = 150),
                EqualizerPreset("electronic", "Electronic", listOf(600, 400, 0, 300, 600), bassBoost = 700, treble = 500, virtualizer = 400),
                EqualizerPreset("jazz", "Jazz", listOf(300, 200, -200, 200, 400), bassBoost = 200, treble = 200, virtualizer = 300),
                EqualizerPreset("classical", "Classical", listOf(400, 300, -100, 300, 400), bassBoost = 150, treble = 200, virtualizer = 350),
                EqualizerPreset("podcast", "Podcast", listOf(-300, 200, 600, 400, -200), bassBoost = 0, treble = 200, virtualizer = 0),
                EqualizerPreset("gaming", "Gaming", listOf(500, 100, -100, 400, 700), bassBoost = 500, treble = 600, virtualizer = 600),
                EqualizerPreset("movie", "Movie", listOf(600, 200, -100, 300, 500), bassBoost = 600, treble = 300, virtualizer = 700)
            )
        }
    }
}

enum class AudioDeviceType {
    SPEAKER,
    WIRED,
    BLUETOOTH,
    USB,
    OTHER
}

data class AppProfile(
    val packageName: String,
    val appName: String,
    val presetId: String,
    val isEnabled: Boolean = true,
    val isSessionActive: Boolean = false,
    val lastSessionId: Int = 0
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("packageName", packageName)
        obj.put("appName", appName)
        obj.put("presetId", presetId)
        obj.put("isEnabled", isEnabled)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AppProfile {
            return AppProfile(
                packageName = obj.optString("packageName", ""),
                appName = obj.optString("appName", ""),
                presetId = obj.optString("presetId", "flat"),
                isEnabled = obj.optBoolean("isEnabled", true)
            )
        }
    }
}

data class AudioEngineCapabilities(
    // Unknown until an actual, positive-ID audio session has been tested.
    val isHardwareEqualizerSupported: Boolean = false,
    val numberOfBands: Int = 0,
    val minGainMilliBel: Short = -1500,
    val maxGainMilliBel: Short = 1500,
    val isBassBoostSupported: Boolean = false,
    val isVirtualizerSupported: Boolean = false
)
