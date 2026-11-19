package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import com.example.data.model.AppProfile
import com.example.data.model.AudioDeviceType
import com.example.data.model.AudioEngineCapabilities
import com.example.data.model.EqualizerBand
import com.example.data.model.EqualizerPreset
import com.example.util.AuraLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Android audio effects (Equalizer, BassBoost, Virtualizer) attached to
 * other apps' audio sessions.
 *
 * Performance rule: every setter is idempotent. A parameter is written to the
 * native effect only when its value actually changed, and `enabled` is toggled
 * only on change. Re-toggling a live effect on every playback callback is what
 * caused audible gaps and stutters, so the caches in [SessionEffects] guard it.
 */
class AudioEffectManager(private val context: Context) {

    private val area = "Effects"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _capabilities = MutableStateFlow(AudioEngineCapabilities())
    val capabilities: StateFlow<AudioEngineCapabilities> = _capabilities.asStateFlow()

    private val _hardwareBands = MutableStateFlow<List<EqualizerBand>>(defaultBands())
    val hardwareBands: StateFlow<List<EqualizerBand>> = _hardwareBands.asStateFlow()

    private val _currentDeviceType = MutableStateFlow(AudioDeviceType.SPEAKER)
    val currentDeviceType: StateFlow<AudioDeviceType> = _currentDeviceType.asStateFlow()

    private val _currentDeviceName = MutableStateFlow("Phone Speaker")
    val currentDeviceName: StateFlow<String> = _currentDeviceName.asStateFlow()

    private val _activeSessions = MutableStateFlow<Set<Int>>(emptySet())
    val activeSessions: StateFlow<Set<Int>> = _activeSessions.asStateFlow()

    private val _controlledPackages = MutableStateFlow<Set<String>>(emptySet())
    val controlledPackages: StateFlow<Set<String>> = _controlledPackages.asStateFlow()

    /** Per-session native effects plus the last values written to them. */
    private class SessionEffects(val sessionId: Int) {
        var equalizer: Equalizer? = null
        var bassBoost: BassBoost? = null
        var virtualizer: Virtualizer? = null

        var appliedBands: IntArray? = null
        var appliedEqEnabled: Boolean? = null
        var appliedBass: Int? = null
        var appliedBassEnabled: Boolean? = null
        var appliedVirtStrength: Int? = null
        var appliedVirtMode: Int? = null
        var appliedVirtEnabled: Boolean? = null

        fun hasAnyEffect() = equalizer != null || bassBoost != null || virtualizer != null

        /** Forget cached values so the next apply writes everything again. */
        fun invalidate() {
            appliedBands = null
            appliedEqEnabled = null
            appliedBass = null
            appliedBassEnabled = null
            appliedVirtStrength = null
            appliedVirtMode = null
            appliedVirtEnabled = null
        }

        fun release() {
            try {
                equalizer?.enabled = false
                equalizer?.release()
            } catch (e: Exception) {
                AuraLog.w("Effects", "Error releasing equalizer on session $sessionId", e)
            }
            try {
                bassBoost?.enabled = false
                bassBoost?.release()
            } catch (e: Exception) {
                AuraLog.w("Effects", "Error releasing bassBoost on session $sessionId", e)
            }
            try {
                virtualizer?.enabled = false
                virtualizer?.release()
            } catch (e: Exception) {
                AuraLog.w("Effects", "Error releasing virtualizer on session $sessionId", e)
            }
            equalizer = null
            bassBoost = null
            virtualizer = null
            invalidate()
        }
    }

    private val activeEffectSessions = mutableMapOf<Int, SessionEffects>()
    private val knownSessionPackages = mutableMapOf<Int, String?>()

    private var appPolicies: Map<String, AppProfile> = emptyMap()
    private var availablePresets: Map<String, EqualizerPreset> = emptyMap()

    private var isEnabled = false
    private var bandGains = mutableListOf<Short>()
    private var bassBoostStrength: Short = 0
    private var trebleValue: Int = 0
    private var virtualizerStrength: Short = 0

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            AuraLog.i(area, "Audio device added, re-applying effects")
            detectAudioRoute()
            refreshActiveSessions()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            AuraLog.i(area, "Audio device removed, re-applying effects")
            detectAudioRoute()
            refreshActiveSessions()
        }
    }

    init {
        try {
            detectAudioRoute()
        } catch (e: RuntimeException) {
            AuraLog.w(area, "Audio device route detection unavailable", e)
        }
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: RuntimeException) {
            AuraLog.w(area, "Audio device change callback unavailable", e)
        }
    }

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

    /** Reapply parameters after an external player changes its audio route. */
    @Synchronized
    fun refreshActiveSessions() {
        if (!isEnabled) {
            publishControlStatus()
            return
        }
        knownSessionPackages.toMap().forEach { (sessionId, packageName) ->
            if (!AppAudioPolicy.shouldProcess(packageName, appPolicies)) {
                activeEffectSessions.remove(sessionId)?.release()
                return@forEach
            }
            val existing = activeEffectSessions[sessionId]
            if (existing != null && existing.hasAnyEffect()) {
                existing.invalidate()
                applyCurrentParametersToSession(existing)
            } else {
                attachSession(sessionId, packageName)
            }
        }
        _activeSessions.value = activeEffectSessions.keys.toSet()
        publishControlStatus()
    }

    @Synchronized
    fun configureAppPolicies(
        profiles: Map<String, AppProfile>,
        presets: List<EqualizerPreset>
    ) {
        appPolicies = profiles.toMap()
        availablePresets = presets.associateBy { it.id }
        knownSessionPackages.toMap().forEach { (sessionId, packageName) ->
            if (!AppAudioPolicy.shouldProcess(packageName, appPolicies)) {
                activeEffectSessions.remove(sessionId)?.release()
            } else if (isEnabled) {
                val existing = activeEffectSessions[sessionId]
                if (existing != null && existing.hasAnyEffect()) {
                    existing.invalidate()
                    applyCurrentParametersToSession(existing)
                } else {
                    attachSession(sessionId, packageName)
                }
            }
        }
        _activeSessions.value = activeEffectSessions.keys.toSet()
        publishControlStatus()
    }

    private fun presetForSession(sessionId: Int): EqualizerPreset? =
        AppAudioPolicy.presetFor(knownSessionPackages[sessionId], appPolicies, availablePresets)

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

            val bands = (0 until numBands).map { i ->
                EqualizerBand(
                    index = i.toShort(),
                    centerFreqHz = testEq.getCenterFreq(i.toShort()) / 1000,
                    minMilliBel = minGain,
                    maxMilliBel = maxGain,
                    currentGainMilliBel = 0.toShort()
                )
            }
            _hardwareBands.value = bands.mapIndexed { i, band ->
                band.copy(currentGainMilliBel = bandGains.getOrNull(i) ?: 0)
            }

            var bbSupported = false
            try {
                val bb = BassBoost(0, sampleSessionId)
                bbSupported = bb.strengthSupported
                bb.release()
            } catch (e: Exception) {
                AuraLog.d(area, "BassBoost not supported: ${e.message}")
            }

            var virtSupported = false
            try {
                val virt = Virtualizer(0, sampleSessionId)
                virtSupported = virt.strengthSupported
                virt.release()
            } catch (e: Exception) {
                AuraLog.d(area, "Virtualizer not supported: ${e.message}")
            }

            _capabilities.value = AudioEngineCapabilities(
                isHardwareEqualizerSupported = numBands > 0,
                numberOfBands = numBands,
                minGainMilliBel = minGain,
                maxGainMilliBel = maxGain,
                isBassBoostSupported = bbSupported,
                isVirtualizerSupported = virtSupported
            )
            AuraLog.i(area, "Probe session=$sampleSessionId bands=$numBands bass=$bbSupported virt=$virtSupported")
        } catch (e: Exception) {
            AuraLog.w(area, "Session probe on $sampleSessionId fell back to standard 5-band", e)
            _hardwareBands.value = defaultBands().mapIndexed { i, band ->
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
     * Attaches effects to a valid audio session. If the session already has an
     * Equalizer, this only updates the owner and returns: tuning is pushed by the
     * setters, so re-creating or re-toggling live effects here would cause gaps.
     */
    @Synchronized
    fun attachSession(sessionId: Int, packageName: String? = null) {
        if (sessionId <= 0) return
        val owner = packageName ?: knownSessionPackages[sessionId]
        knownSessionPackages[sessionId] = owner
        if (!isEnabled || !AppAudioPolicy.shouldProcess(owner, appPolicies)) {
            if (activeEffectSessions.remove(sessionId)?.also { it.release() } != null) {
                AuraLog.i(area, "Session $sessionId released (disabled or excluded, pkg=$owner)")
            }
            _activeSessions.value = activeEffectSessions.keys.toSet()
            publishControlStatus()
            return
        }

        val existing = activeEffectSessions[sessionId]
        if (existing?.equalizer != null) {
            publishControlStatus()
            return
        }

        AuraLog.i(area, "Attaching effects to session=$sessionId pkg=$owner")
        val session = existing ?: SessionEffects(sessionId).also { activeEffectSessions[sessionId] = it }

        try {
            if (session.equalizer == null) {
                val eq = Equalizer(1000, sessionId)
                session.equalizer = eq
                eq.setControlStatusListener { effect, hasControl ->
                    synchronized(this@AudioEffectManager) {
                        val current = activeEffectSessions[sessionId] ?: return@synchronized
                        if (current.equalizer === effect) {
                            AuraLog.i(area, "Control status session=$sessionId hasControl=$hasControl")
                            if (hasControl && isEnabled) {
                                current.invalidate()
                                applyCurrentParametersToSession(current)
                            }
                            publishControlStatus()
                        }
                    }
                }
                eq.setEnableStatusListener { effect, _ ->
                    synchronized(this@AudioEffectManager) {
                        if (activeEffectSessions[sessionId]?.equalizer === effect) publishControlStatus()
                    }
                }
                if (!_capabilities.value.isHardwareEqualizerSupported ||
                    _hardwareBands.value.size != eq.numberOfBands.toInt()
                ) {
                    probeHardwareCapabilities(sessionId, eq)
                }
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Failed to create Equalizer for session $sessionId", e)
        }

        try {
            if (session.bassBoost == null) {
                session.bassBoost = BassBoost(1000, sessionId)
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Failed to create BassBoost for session $sessionId", e)
        }

        try {
            if (session.virtualizer == null) {
                session.virtualizer = Virtualizer(1000, sessionId)
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Failed to create Virtualizer for session $sessionId", e)
        }

        if (session.hasAnyEffect()) {
            applyCurrentParametersToSession(session)
        } else {
            AuraLog.w(area, "No effect could be created for session $sessionId")
            activeEffectSessions.remove(sessionId)
        }
        _activeSessions.value = activeEffectSessions.keys.toSet()
        publishControlStatus()
    }

    @Synchronized
    fun detachSession(sessionId: Int) {
        knownSessionPackages.remove(sessionId)
        AuraLog.i(area, "Detaching effects from session=$sessionId")
        activeEffectSessions.remove(sessionId)?.release()
        _activeSessions.value = activeEffectSessions.keys.toSet()
        publishControlStatus()
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        if (enabled == isEnabled) {
            publishControlStatus()
            return
        }
        AuraLog.i(area, "Master EQ ${if (enabled) "ON" else "OFF"}")
        if (!enabled) {
            isEnabled = false
            activeEffectSessions.values.forEach { it.release() }
            activeEffectSessions.clear()
            _activeSessions.value = emptySet()
            publishControlStatus()
            return
        }
        isEnabled = true
        refreshActiveSessions()
    }

    @Synchronized
    fun setBandsGains(gains: List<Int>) {
        bandGains = gains.map { it.toShort() }.toMutableList()
        applyGainsWithTreble()
    }

    @Synchronized
    fun setBandGain(bandIndex: Short, gainMilliBel: Short) {
        while (bandGains.size <= bandIndex) bandGains.add(0)
        bandGains[bandIndex.toInt()] = gainMilliBel
        applyGainsWithTreble()
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000).toShort()
        activeEffectSessions.values.forEach { session ->
            if (presetForSession(session.sessionId) == null) {
                applyBassToSession(session, bassBoostStrength.toInt())
            }
        }
    }

    @Synchronized
    fun setTreble(value: Int) {
        trebleValue = value.coerceIn(-1000, 1000)
        applyGainsWithTreble()
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000).toShort()
        activeEffectSessions.values.forEach { session ->
            val current = (presetForSession(session.sessionId)?.virtualizer
                ?: virtualizerStrength.toInt()).coerceIn(0, 1000)
            applyVirtualizationToSession(session, current)
        }
    }

    /**
     * Uses the OEM binaural mode on headphones. Our own Media3 session is skipped:
     * it already has the deterministic PCM widening, so stacking the OEM effect
     * on it would double the processing.
     */
    private fun applyVirtualizationToSession(session: SessionEffects, strength: Int) {
        val virt = session.virtualizer ?: return
        val ownSession = knownSessionPackages[session.sessionId] == context.packageName
        val want = isEnabled && !ownSession && strength > 0
        try {
            if (!want) {
                if (session.appliedVirtEnabled != false) {
                    virt.enabled = false
                    session.appliedVirtEnabled = false
                }
                return
            }
            if (session.appliedVirtStrength != strength) {
                if (virt.strengthSupported) virt.setStrength(strength.toShort())
                session.appliedVirtStrength = strength
            }
            val isHeadphone = _currentDeviceType.value in setOf(
                AudioDeviceType.WIRED, AudioDeviceType.BLUETOOTH, AudioDeviceType.USB
            )
            val requestedMode = if (isHeadphone) Virtualizer.VIRTUALIZATION_MODE_BINAURAL
            else Virtualizer.VIRTUALIZATION_MODE_AUTO
            if (Build.VERSION.SDK_INT >= 29 && session.appliedVirtMode != requestedMode) {
                runCatching { virt.forceVirtualizationMode(requestedMode) }
                    .onFailure { AuraLog.d(area, "Virtualization mode $requestedMode refused: ${it.message}") }
                session.appliedVirtMode = requestedMode
            }
            if (session.appliedVirtEnabled != true) {
                virt.enabled = true
                session.appliedVirtEnabled = true
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Unable to update 3D effect on session ${session.sessionId}", e)
        }
    }

    private fun applyBassToSession(session: SessionEffects, strength: Int) {
        val bb = session.bassBoost ?: return
        try {
            if (session.appliedBass != strength) {
                if (bb.strengthSupported) bb.setStrength(strength.toShort())
                session.appliedBass = strength
            }
            val want = isEnabled && strength > 0
            if (session.appliedBassEnabled != want) {
                bb.enabled = want
                session.appliedBassEnabled = want
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Unable to change bass on session ${session.sessionId}", e)
        }
    }

    private fun applyGainsWithTreble() {
        val totalBands = _hardwareBands.value.size.coerceAtLeast(bandGains.size)
        val updated = (0 until totalBands).mapNotNull { i ->
            _hardwareBands.value.getOrNull(i)?.let { existing ->
                val baseGain = bandGains.getOrNull(i)?.toInt() ?: 0
                existing.copy(
                    currentGainMilliBel = baseGain.coerceIn(
                        existing.minMilliBel.toInt(), existing.maxMilliBel.toInt()
                    ).toShort()
                )
            }
        }
        if (updated.isNotEmpty()) _hardwareBands.value = updated
        activeEffectSessions.values.forEach(::applyCurrentParametersToSession)
    }

    private fun applyCurrentParametersToSession(session: SessionEffects) {
        try {
            val appPreset = presetForSession(session.sessionId)
            val currentGains = appPreset?.gains ?: bandGains.map { it.toInt() }
            val currentTreble = appPreset?.treble ?: trebleValue
            val currentBass = (appPreset?.bassBoost ?: bassBoostStrength.toInt()).coerceIn(0, 1000)
            val currentVirtualizer = (appPreset?.virtualizer ?: virtualizerStrength.toInt()).coerceIn(0, 1000)

            session.equalizer?.let { eq -> applyEqualizer(session, eq, currentGains, currentTreble) }
            applyBassToSession(session, currentBass)
            applyVirtualizationToSession(session, currentVirtualizer)
        } catch (e: Exception) {
            AuraLog.w(area, "Error applying parameters to session ${session.sessionId}", e)
        }
    }

    /** Writes only the bands whose target changed since the last apply. */
    private fun applyEqualizer(session: SessionEffects, eq: Equalizer, gains: List<Int>, treble: Int) {
        val numBands = eq.numberOfBands.toInt()
        val minGain = _capabilities.value.minGainMilliBel.toInt()
        val maxGain = _capabilities.value.maxGainMilliBel.toInt()
        val target = IntArray(numBands) { i ->
            EqBandTuning.effectiveGain(
                gains.getOrNull(i) ?: 0, treble, i, numBands, minGain, maxGain
            ).toInt()
        }
        val previous = session.appliedBands?.takeIf { it.size == numBands }
        for (i in 0 until numBands) {
            if (previous?.get(i) != target[i]) {
                eq.setBandLevel(i.toShort(), target[i].toShort())
            }
        }
        session.appliedBands = target
        if (session.appliedEqEnabled != isEnabled) {
            eq.enabled = isEnabled
            session.appliedEqEnabled = isEnabled
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

        if (_currentDeviceType.value != detectedType) {
            AuraLog.i(area, "Output route: $detectedType ($detectedName)")
        }
        _currentDeviceType.value = detectedType
        _currentDeviceName.value = detectedName
    }

    @Synchronized
    fun release() {
        isEnabled = false
        knownSessionPackages.clear()
        bandGains.clear()
        bassBoostStrength = 0
        trebleValue = 0
        virtualizerStrength = 0
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {
        }
        activeEffectSessions.values.forEach { it.release() }
        activeEffectSessions.clear()
        _activeSessions.value = emptySet()
        _controlledPackages.value = emptySet()
        AuraLog.i(area, "Effects released")
    }

    private fun defaultBands() = listOf(
        EqualizerBand(0, 60, -1500, 1500, 0),
        EqualizerBand(1, 230, -1500, 1500, 0),
        EqualizerBand(2, 910, -1500, 1500, 0),
        EqualizerBand(3, 3600, -1500, 1500, 0),
        EqualizerBand(4, 14000, -1500, 1500, 0)
    )
}
