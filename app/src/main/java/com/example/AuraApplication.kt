package com.example

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.AudioSessionReceiver
import com.example.audio.AudioEffectManager
import com.example.audio.EqNotificationController
import com.example.data.preferences.AuraPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class SessionEvent(val sessionId: Int, val packageName: String?, val isOpen: Boolean)

class AuraApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var audioEffectManager: AudioEffectManager
        private set

    lateinit var preferencesRepository: AuraPreferencesRepository
        private set

    lateinit var eqNotification: EqNotificationController
        private set

    private val sessionPackageNames = mutableMapOf<Int, String?>()
    private val _openSessionPackages = MutableStateFlow<Map<Int, String?>>(emptyMap())
    val openSessionPackages = _openSessionPackages.asStateFlow()
    private val liveAudioSessionReceiver = AudioSessionReceiver()
    private var liveReceiverRegistered = false
    private val _sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val sessionEvents = _sessionEvents.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = AuraPreferencesRepository(this)
        audioEffectManager = AudioEffectManager(this)
        eqNotification = EqNotificationController(this)

        // Android 8+ can suppress manifest-declared implicit broadcasts from Spotify.
        // A context-registered EXPORTED receiver handles them while the app process lives.
        // Keep the manifest receiver for explicitly addressed broadcasts.
        try {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            ContextCompat.registerReceiver(
                this,
                liveAudioSessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            liveReceiverRegistered = true
        } catch (e: RuntimeException) {
            Log.w("AuraApplication", "Dynamic audio-session listener unavailable", e)
        }

        // Do not reset EQ on every process restart: Android may reclaim a background
        // process while Spotify is playing. Reset only on explicit user exit/reset.
        applicationScope.launch {
            preferencesRepository.isEnabledFlow.collect { enabled ->
                audioEffectManager.setMasterEnabled(enabled)
                eqNotification.update(enabled)
            }
        }
        applicationScope.launch {
            preferencesRepository.currentGainsFlow.collect { gains ->
                audioEffectManager.setBandsGains(gains)
            }
        }
        applicationScope.launch {
            preferencesRepository.bassBoostFlow.collect { bb ->
                audioEffectManager.setBassBoost(bb)
            }
        }
        applicationScope.launch {
            preferencesRepository.trebleFlow.collect { treble ->
                audioEffectManager.setTreble(treble)
            }
        }
        applicationScope.launch {
            preferencesRepository.virtualizerFlow.collect { virt ->
                audioEffectManager.setVirtualizer(virt)
            }
        }
    }

    /** User closes the last Activity: release all native effects and clear live tuning. */
    fun exitAndReset() {
        audioEffectManager.release()
        eqNotification.clear()
        applicationScope.launch {
            preferencesRepository.resetAudioTuning(disable = true)
        }
    }

    /** Single entrypoint for both manifest and live Spotify session broadcasts. */
    @Synchronized
    fun handleAudioSessionIntent(intent: Intent?) {
        val message = intent ?: return
        val sessionId = message.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) return
        when (message.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val packageName = message.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                // Repeated broadcasts may arrive through both registered receivers.
                audioEffectManager.attachSession(sessionId, packageName)
                onAudioSessionOpened(sessionId, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                audioEffectManager.detachSession(sessionId)
                onAudioSessionClosed(sessionId)
            }
        }
    }

    @Synchronized
    fun onAudioSessionOpened(sessionId: Int, packageName: String?) {
        val previous = sessionPackageNames[sessionId]
        if (sessionPackageNames.containsKey(sessionId) && previous == packageName) return
        sessionPackageNames[sessionId] = packageName
        _openSessionPackages.value = sessionPackageNames.toMap()
        applicationScope.launch {
            _sessionEvents.emit(SessionEvent(sessionId, packageName, isOpen = true))
        }
    }

    @Synchronized
    fun onAudioSessionClosed(sessionId: Int) {
        if (!sessionPackageNames.containsKey(sessionId)) return
        val packageName = sessionPackageNames.remove(sessionId)
        _openSessionPackages.value = sessionPackageNames.toMap()
        applicationScope.launch {
            _sessionEvents.emit(SessionEvent(sessionId, packageName, isOpen = false))
        }
    }

    override fun onTerminate() {
        if (liveReceiverRegistered) {
            runCatching { unregisterReceiver(liveAudioSessionReceiver) }
            liveReceiverRegistered = false
        }
        super.onTerminate()
        audioEffectManager.release()
        eqNotification.clear()
    }
}
