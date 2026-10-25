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
    private val _hardwareBands = MutableStateFlow<List<EqualizerBand>>(
        listOf(
            EqualizerBand(0, 60, -1500, 1500, 0),
            EqualizerBand(1, 230, -1500, 1500, 0),
            EqualizerBand(2, 910, -1500, 1500, 0),
            EqualizerBand(3, 3600, -1500, 1500, 0),
            EqualizerBand(4, 14000, -1500, 1500, 0)
        )
    )
    val hardwareBands: StateFlow<List<EqualizerBand>> = _hardwareBands.asStateFlow()

    // Current output device route
    private val _currentDeviceType = MutableStateFlow(AudioDeviceType.SPEAKER)
    val currentDeviceType: StateFlow<AudioDeviceType> = _currentDeviceType.asStateFlow()

    private val _currentDeviceName = MutableStateFlow("Phone Speaker")
    val currentDeviceName: StateFlow<String> = _currentDeviceName.asStateFlow()

    // Active session count
    private val _activeSessions = MutableStateFlow<Set<Int>>(emptySet())
    val activeSessions: StateFlow<Set<Int>> = _activeSessions.asStateFlow()

    // Only report a package as attached if this process currently owns a live EQ
    // for a session that the player actually published. No invented global EQ.
    private val _controlledPackages = MutableStateFlow<Set<String>>(emptySet())
    val controlledPackages: StateFlow<Set<String>> = _controlledPackages.asStateFlow()

    @Synchronized
    private fun publishControlStatus() {
        _controlledPackages.value = if (!isEnabled) emptySet() else {
            activeEffectSessions.values.mapNotNull { effect ->
                if (runCatching {
                        effect.equalizer?.hasControl() == true && effect.equalizer?.enabled == true
                    }.getOrDefault(false)
                ) knownSessionPackages[effect.sessionId] else null
            }.toSet()
        }
    }

    /** Reapply parameters after an external player changes its audio processing route. */
    @Synchronized
    fun refreshActiveSessions() {
        if (!isEnabled) {
            publishControlStatus()
            return
        }
        knownSessionPackages.toMap().forEach { (sessionId, packageName) ->
            if (activeEffectSessions[sessionId]?.equalizer == null) {
                attachSession(sessionId, packageName)
            } else {
                activeEffectSessions[sessionId]?.let { session ->
                    if (runCatching { session.equalizer?.hasControl() == true }.getOrDefault(false)) {
                        applyCurrentParametersToSession(session)
                    }
                }
            }
        }
        publishControlStatus()
    }

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
    // Remember observed sessions while EQ is disabled so enabling can reattach to a
    // *valid* session (never deprecated global session 0).
    private val knownSessionPackages = mutableMapOf<Int, String?>()

    // Current applied audio parameters
    private var isEnabled = false // Remain neutral until user explicitly enables EQ
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
        // Route discovery and callbacks vary across OEM audio implementations.
        // A failure here must not crash the entire application during startup.
        try {
            detectAudioRoute()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Audio device route detection unavailable", e)
        }
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Audio device change callback unavailable", e)
        }
        // Do not probe deprecated global session 0; await a valid playback session.
    }

    fun probeHardwareCapabilities(sampleSessionId: Int, existingEqualizer: Equalizer? = null) {
        if (sampleSessionId <= 0) return
        var temporaryProbe: Equalizer? = null
        try {
            val testEq = existingEqualizer ?: Equalizer(0, sampleSessionId).also {
                temporaryProbe = it
            }
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
            // Preserve USER band levels. Probing a session must not zero existing
            // sliders (and treble is not a saved/user band level).
            _hardwareBands.value = bands.mapIndexed { i, band ->
                band.copy(currentGainMilliBel = bandGains.getOrNull(i) ?: 0)
            }

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

        } catch (e: Exception) {
            Log.w(TAG, "Session probe on session $sampleSessionId fallback to standard 5-band", e)
            // Fallback default 5 bands: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz
            val fallbackBands = listOf(
                EqualizerBand(0, 60, -1500, 1500, 0),
                EqualizerBand(1, 230, -1500, 1500, 0),
                EqualizerBand(2, 910, -1500, 1500, 0),
                EqualizerBand(3, 3600, -1500, 1500, 0),
                EqualizerBand(4, 14000, -1500, 1500, 0)
            )
            _hardwareBands.value = fallbackBands.mapIndexed { i, band ->
                band.copy(currentGainMilliBel = bandGains.getOrNull(i) ?: 0)
            }
            _capabilities.value = AudioEngineCapabilities(
                isHardwareEqualizerSupported = false,
                numberOfBands = 5,
                minGainMilliBel = -1500,
                maxGainMilliBel = 1500,
                isBassBoostSupported = false,
                isVirtualizerSupported = false
            )
        } finally {
            runCatching { temporaryProbe?.release() }
        }
    }

    /**
     * Attaches audio effects to a valid audio session ID.
     */
    @Synchronized
    fun attachSession(sessionId: Int, packageName: String? = null) {
        if (sessionId <= 0) return
        knownSessionPackages[sessionId] = packageName
        if (!isEnabled) return
        Log.i(TAG, "Attaching audio effects to session: $sessionId (pkg=$packageName)")

        val sessionEffects = activeEffectSessions.getOrPut(sessionId) {
            SessionEffects(sessionId)
        }

        try {
            if (sessionEffects.equalizer == null) {
                val eq = Equalizer(1000, sessionId)
                sessionEffects.equalizer = eq
                eq.setControlStatusListener { effect, hasControl ->
                    synchronized(this@AudioEffectManager) {
                        if (activeEffectSessions[sessionId]?.equalizer === effect) {
                            if (hasControl && isEnabled) {
                                applyCurrentParametersToSession(activeEffectSessions[sessionId]!!)
                            }
                            publishControlStatus()
                        }
                    }
                }
                eq.setEnableStatusListener { effect, _ ->
                    synchronized(this@AudioEffectManager) {
                        if (activeEffectSessions[sessionId]?.equalizer === effect) {
                            publishControlStatus()
                        }
                    }
                }
                // Read hardware bands from the effect we actually own.
                // Creating a second Equalizer just for probing can steal control.
                if (!_capabilities.value.isHardwareEqualizerSupported ||
                    _hardwareBands.value.size != eq.numberOfBands.toInt()
                ) {
                    probeHardwareCapabilities(sessionId, eq)
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
        publishControlStatus()
    }

    /**
     * Detaches and releases audio effects for a closed session.
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        knownSessionPackages.remove(sessionId)
        Log.i(TAG, "Detaching audio effects from session: $sessionId")
        val session = activeEffectSessions.remove(sessionId)
        session?.release()
        _activeSessions.value = activeEffectSessions.keys.toSet()
        publishControlStatus()
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        if (!enabled) {
            this.isEnabled = false
            // Android effects belong to our sessions. Disabling only the master flag
            // can leave native tone coloration on some OEM audio stacks: release them.
            activeEffectSessions.values.forEach { it.release() }
            activeEffectSessions.clear()
            _activeSessions.value = emptySet()
            publishControlStatus()
            return
        }
        this.isEnabled = true
        refreshActiveSessions()
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

    /**
     * Keep EQ sliders as independent USER gains. Treble changes the hardware
     * output on the final two bands but is NEVER stored in a band slider.
     * Previously, editing 60/230/910Hz copied the boosted 3.6k/14k values
     * back into base gains and reapplied Treble, causing positive feedback.
     */
    private fun applyGainsWithTreble() {
        val totalBands = _hardwareBands.value.size.coerceAtLeast(bandGains.size)
        val minGain = _capabilities.value.minGainMilliBel.toInt()
        val maxGain = _capabilities.value.maxGainMilliBel.toInt()
        val updatedBands = mutableListOf<EqualizerBand>()
        for (i in 0 until totalBands) {
            val baseGain = bandGains.getOrNull(i)?.toInt() ?: 0
            val effective = EqBandTuning.effectiveGain(
                baseGain, trebleValue, i, totalBands, minGain, maxGain
            )
            activeEffectSessions.values.forEach { session ->
                try {
                    session.equalizer?.let { eq ->
                        if (i < eq.numberOfBands.toInt()) eq.setBandLevel(i.toShort(), effective)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting band $i to $effective", e)
                }
            }
            _hardwareBands.value.getOrNull(i)?.let { existing ->
                updatedBands.add(
                    existing.copy(currentGainMilliBel = baseGain.coerceIn(
                        existing.minMilliBel.toInt(), existing.maxMilliBel.toInt()
                    ).toShort())
                )
            }
        }
        if (updatedBands.isNotEmpty()) _hardwareBands.value = updatedBands
    }

    private fun applyCurrentParametersToSession(session: SessionEffects) {
        try {
            session.equalizer?.let { eq ->
                eq.enabled = isEnabled
                val numBands = eq.numberOfBands.toInt()
                val minGain = _capabilities.value.minGainMilliBel
                val maxGain = _capabilities.value.maxGainMilliBel
                for (i in 0 until numBands) {
                    val effective = EqBandTuning.effectiveGain(
                        bandGains.getOrNull(i)?.toInt() ?: 0,
                        trebleValue, i, numBands, minGain.toInt(), maxGain.toInt()
                    )
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

    @Synchronized
    fun release() {
        this.isEnabled = false
        knownSessionPackages.clear()
        bandGains.clear()
        bassBoostStrength = 0
        trebleValue = 0
        virtualizerStrength = 0
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}
        activeEffectSessions.values.forEach { it.release() }
        activeEffectSessions.clear()
        _activeSessions.value = emptySet()
        _controlledPackages.value = emptySet()
    }
}
