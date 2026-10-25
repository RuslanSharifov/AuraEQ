package com.example.audio

import com.example.data.model.AppProfile
import com.example.data.model.EqualizerPreset

/**
 * Global audio tuning is the default for every eligible positive session.
 * Users may exclude a specific app or explicitly override its preset.
 * This policy cannot create or discover audio sessions hidden by Android.
 */
internal object AppAudioPolicy {
    fun shouldProcess(packageName: String?, profiles: Map<String, AppProfile>): Boolean {
        return packageName == null || profiles[packageName]?.isEnabled != false
    }

    fun presetFor(
        packageName: String?,
        profiles: Map<String, AppProfile>,
        presets: Map<String, EqualizerPreset>
    ): EqualizerPreset? {
        if (packageName == null) return null
        val app = profiles[packageName] ?: return null
        if (!app.isEnabled || !app.useCustomPreset) return null
        return presets[app.presetId]
    }
}
