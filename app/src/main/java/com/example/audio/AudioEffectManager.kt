package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.example.data.model.AudioDeviceType
import com.example.data.model.AudioEngineCapabilities
import com.example.data.model.EqualizerBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioEffectManager(private val context: Context) {

    private val TAG = "AuraAudioEffectManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Capabilities
    private val _capabilities = MutableStateFlow(AudioEngineCapabilities())
    val capabilities: StateFlow<AudioEngineCapabilities> = _capabilities.asStateFlow()

    // Real hardware bands exposed to UI
    private val _hardwareBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val hardwareBands: StateFlow<List<EqualizerBand>> = _hardwareBands.asStateFlow()

    // Current output device route
    private val _currentDeviceType = MutableStateFlow(AudioDeviceType.SPEAKER)
    val currentDeviceType: StateFlow<AudioDeviceType> = _currentDeviceType.asStateFlow()

    private val _currentDeviceName = MutableStateFlow("Phone Speaker")
    val currentDeviceName: StateFlow<String> = _currentDeviceName.asStateFlow()

    // Active session count
    private val _activeSessions = MutableStateFlow<Set<Int>>(emptySet())
    val activeSessions: StateFlow<Set<Int>> = _activeSessions.asStateFlow()

    // Internal session effects holder
    private class SessionEffects(
        val sessionId: Int,
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null
    ) {
        fun release() {
            try {
                equalizer?.enabled = false
                equalizer?.release()
            } catch (e: Exception) {
                Log.w("SessionEffects", "Error releasing equalizer", e)
            }
            try {
                bassBoost?.enabled = false
                bassBoost?.release()
            } catch (e: Exception) {
                Log.w("SessionEffects", "Error releasing bassBoost", e)
            }
            try {
                virtualizer?.enabled = false
                virtualizer?.release()
            } catch (e: Exception) {
                Log.w("SessionEffects", "Error releasing virtualizer", e)
            }
        }
    }

    private val activeEffectSessions = mutableMapOf<Int, SessionEffects>()

    // Current applied audio parameters
    private var isEnabled = true
    private var bandGains = mutableListOf<Short>()
    private var bassBoostStrength: Short = 0
    private var trebleValue: Int = 0 // -1000..1000
    private var virtualizerStrength: Short = 0

    // AudioDeviceCallback for route changes
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            detectAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            detectAudioRoute()
        }
    }

    init {
        detectAudioRoute()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        // Do not probe deprecated global session 0; await a valid playback session.
    }

    fun probeHardwareCapabilities(sampleSessionId: Int) {
        if (sampleSessionId <= 0) return
        try {
            // Test with session 0 or sample session
            val testEq = Equalizer(0, sampleSessionId)
            val numBands = testEq.numberOfBands.toInt()
            val range = testEq.bandLevelRange
            val minGain: Short = range?.getOrNull(0) ?: (-1500).toShort()
            val maxGain: Short = range?.getOrNull(1) ?: 1500.toShort()

            val bands = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val centerFreq = testEq.getCenterFreq(i.toShort()) / 1000 // mHz to Hz
                bands.add(
                    EqualizerBand(
                        index = i.toShort(),
                        centerFreqHz = centerFreq,
                        minMilliBel = minGain,
                        maxMilliBel = maxGain,
                        currentGainMilliBel = 0.toShort()
                    )
                )
            }
            _hardwareBands.value = bands

            // Check BassBoost
            var bbSupported = false
            try {
                val bb = BassBoost(0, sampleSessionId)
                bbSupported = bb.strengthSupported
                bb.release()
            } catch (e: Exception) {
                Log.d(TAG, "BassBoost not supported: ${e.message}")
            }

            // Check Virtualizer
            var virtSupported = false
            try {
                val virt = Virtualizer(0, sampleSessionId)
                virtSupported = virt.strengthSupported
                virt.release()
            } catch (e: Exception) {
                Log.d(TAG, "Virtualizer not supported: ${e.message}")
            }

            _capabilities.value = AudioEngineCapabilities(
                isHardwareEqualizerSupported = numBands > 0,
                numberOfBands = numBands,
                minGainMilliBel = minGain,
                maxGainMilliBel = maxGain,
                isBassBoostSupported = bbSupported,
                isVirtualizerSupported = virtSupported
            )

            testEq.release()
        } catch (e: Exception) {
            Log.w(TAG, "Global probe on session $sampleSessionId fallback to standard 5-band", e)
            // Fallback default 5 bands: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz
            val fallbackBands = listOf(
                EqualizerBand(0, 60, -1500, 1500, 0),
                EqualizerBand(1, 230, -1500, 1500, 0),
                EqualizerBand(2, 910, -1500, 1500, 0),
                EqualizerBand(3, 3600, -1500, 1500, 0),
                EqualizerBand(4, 14000, -1500, 1500, 0)
            )
            _hardwareBands.value = fallbackBands
            _capabilities.value = AudioEngineCapabilities(
                isHardwareEqualizerSupported = false,
                numberOfBands = 5,
                minGainMilliBel = -1500,
                maxGainMilliBel = 1500,
                isBassBoostSupported = false,
                isVirtualizerSupported = false
            )
        }
    }

    /**
     * Attaches audio effects to a valid audio session ID.
     */
    @Synchronized
    fun attachSession(sessionId: Int, packageName: String? = null) {
        if (sessionId <= 0) return
        Log.i(TAG, "Attaching audio effects to session: $sessionId (pkg=$packageName)")

        val sessionEffects = activeEffectSessions.getOrPut(sessionId) {
            SessionEffects(sessionId)
        }

        try {
            if (sessionEffects.equalizer == null) {
                val eq = Equalizer(1000, sessionId)
                eq.enabled = isEnabled
                sessionEffects.equalizer = eq
                // Re-verify bands with this active session
                if (_hardwareBands.value.isEmpty() || _hardwareBands.value.size != eq.numberOfBands.toInt()) {
                    probeHardwareCapabilities(sessionId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Equalizer for session $sessionId: ${e.message}")
        }

        try {
            if (sessionEffects.bassBoost == null) {
                val bb = BassBoost(1000, sessionId)
                if (bb.strengthSupported) {
                    bb.setStrength(bassBoostStrength)
                }
                bb.enabled = isEnabled
                sessionEffects.bassBoost = bb
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create BassBoost for session $sessionId: ${e.message}")
        }

        try {
            if (sessionEffects.virtualizer == null) {
                val virt = Virtualizer(1000, sessionId)
                if (virt.strengthSupported) {
                    virt.setStrength(virtualizerStrength)
                }
                virt.enabled = isEnabled
                sessionEffects.virtualizer = virt
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Virtualizer for session $sessionId: ${e.message}")
        }

        if (sessionEffects.equalizer == null &&
            sessionEffects.bassBoost == null &&
            sessionEffects.virtualizer == null
        ) {
            // No audio effect attached. Do not report the session as processing.
            activeEffectSessions.remove(sessionId)
        } else {
            applyCurrentParametersToSession(sessionEffects)
        }
        _activeSessions.value = activeEffectSessions.keys.toSet()
    }

    /**
     * Detaches and releases audio effects for a closed session.
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        Log.i(TAG, "Detaching audio effects from session: $sessionId")
        val session = activeEffectSessions.remove(sessionId)
        session?.release()
        _activeSessions.value = activeEffectSessions.keys.toSet()
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        activeEffectSessions.values.forEach { session ->
            try { session.equalizer?.enabled = enabled } catch (_: Exception) {}
            try { session.bassBoost?.enabled = enabled } catch (_: Exception) {}
            try { session.virtualizer?.enabled = enabled } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun setBandsGains(gains: List<Int>) {
        this.bandGains = gains.map { it.toShort() }.toMutableList()
        applyGainsWithTreble()
    }

    @Synchronized
    fun setBandGain(bandIndex: Short, gainMilliBel: Short) {
        while (bandGains.size <= bandIndex) {
            bandGains.add(0)
        }
        bandGains[bandIndex.toInt()] = gainMilliBel
        applyGainsWithTreble()
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        this.bassBoostStrength = strength.coerceIn(0, 1000).toShort()
        activeEffectSessions.values.forEach { session ->
            try {
                session.bassBoost?.let {
                    if (it.strengthSupported) {
                        it.setStrength(bassBoostStrength)
                    }
                    it.enabled = isEnabled && bassBoostStrength > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error setting bass boost", e)
            }
        }
    }

    @Synchronized
    fun setTreble(value: Int) {
        this.trebleValue = value.coerceIn(-1000, 1000)
        applyGainsWithTreble()
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        this.virtualizerStrength = strength.coerceIn(0, 1000).toShort()
        activeEffectSessions.values.forEach { session ->
            try {
                session.virtualizer?.let {
                    if (it.strengthSupported) {
                        it.setStrength(virtualizerStrength)
                    }
                    it.enabled = isEnabled && virtualizerStrength > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error setting virtualizer", e)
            }
        }
    }

    private fun applyGainsWithTreble() {
        val totalBands = _hardwareBands.value.size.coerceAtLeast(bandGains.size)
        val minGain = _capabilities.value.minGainMilliBel
        val maxGain = _capabilities.value.maxGainMilliBel

        // Calculate treble influence: shifts the highest band(s) proportionally
        // trebleValue is -1000..1000. Scale to ±600 mB max addition
        val trebleDelta = ((trebleValue / 1000.0f) * 600).toInt().toShort()

        val updatedBands = mutableListOf<EqualizerBand>()
        for (i in 0 until totalBands) {
            val baseGain = bandGains.getOrNull(i) ?: 0.toShort()
            val extraTreble = if (i >= (totalBands - 2).coerceAtLeast(1)) {
                // Apply higher weight to very top band, half weight to second to top
                if (i == totalBands - 1) trebleDelta else (trebleDelta / 2).toShort()
            } else {
                0.toShort()
            }
            val effectiveGain = (baseGain + extraTreble).coerceIn(minGain.toInt(), maxGain.toInt()).toShort()

            activeEffectSessions.values.forEach { session ->
                try {
                    session.equalizer?.setBandLevel(i.toShort(), effectiveGain)
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting band $i to $effectiveGain", e)
                }
            }

            val existing = _hardwareBands.value.getOrNull(i)
            if (existing != null) {
                updatedBands.add(existing.copy(currentGainMilliBel = effectiveGain))
            }
        }
        if (updatedBands.isNotEmpty()) {
            _hardwareBands.value = updatedBands
        }
    }

    private fun applyCurrentParametersToSession(session: SessionEffects) {
        try {
            session.equalizer?.let { eq ->
                eq.enabled = isEnabled
                val numBands = eq.numberOfBands.toInt()
                val minGain = _capabilities.value.minGainMilliBel
                val maxGain = _capabilities.value.maxGainMilliBel
                val trebleDelta = ((trebleValue / 1000.0f) * 600).toInt().toShort()

                for (i in 0 until numBands) {
                    val baseGain = bandGains.getOrNull(i) ?: 0.toShort()
                    val extraTreble = if (i >= (numBands - 2).coerceAtLeast(1)) {
                        if (i == numBands - 1) trebleDelta else (trebleDelta / 2).toShort()
                    } else {
                        0.toShort()
                    }
                    val effective = (baseGain + extraTreble).coerceIn(minGain.toInt(), maxGain.toInt()).toShort()
                    eq.setBandLevel(i.toShort(), effective)
                }
            }
            session.bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(bassBoostStrength)
                }
                bb.enabled = isEnabled && bassBoostStrength > 0
            }
            session.virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    virt.setStrength(virtualizerStrength)
                }
                virt.enabled = isEnabled && virtualizerStrength > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying current params to session ${session.sessionId}", e)
        }
    }

    fun detectAudioRoute() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var detectedType = AudioDeviceType.SPEAKER
        var detectedName = "Phone Speaker"

        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_HEARING_AID -> {
                    detectedType = AudioDeviceType.BLUETOOTH
                    detectedName = device.productName.toString().ifBlank { "Bluetooth Audio" }
                    break
                }
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                    detectedType = AudioDeviceType.WIRED
                    detectedName = device.productName.toString().ifBlank { "Wired Headphones" }
                    break
                }
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                    detectedType = AudioDeviceType.USB
                    detectedName = device.productName.toString().ifBlank { "USB Audio" }
                    break
                }
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    detectedType = AudioDeviceType.SPEAKER
                    detectedName = "Phone Speaker"
                }
            }
        }

        _currentDeviceType.value = detectedType
        _currentDeviceName.value = detectedName
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}
        activeEffectSessions.values.forEach { it.release() }
        activeEffectSessions.clear()
        _activeSessions.value = emptySet()
    }
}
