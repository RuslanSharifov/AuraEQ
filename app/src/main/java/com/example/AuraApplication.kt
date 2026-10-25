package com.example

import android.app.Application
import com.example.audio.AudioEffectManager
import com.example.audio.EqNotificationController
import com.example.data.preferences.AuraPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val _sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val sessionEvents = _sessionEvents.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = AuraPreferencesRepository(this)
        audioEffectManager = AudioEffectManager(this)
        eqNotification = EqNotificationController(this)

        // A fresh process must not silently reapply leftover audio coloration.
        // Saved user presets are retained; live effects start neutral and disabled.
        applicationScope.launch {
            preferencesRepository.resetAudioTuning(disable = true)
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
        eqNotification.update(false)
        applicationScope.launch {
            preferencesRepository.resetAudioTuning(disable = true)
        }
    }

    fun onAudioSessionOpened(sessionId: Int, packageName: String?) {
        sessionPackageNames[sessionId] = packageName
        applicationScope.launch {
            _sessionEvents.emit(SessionEvent(sessionId, packageName, isOpen = true))
        }
    }

    fun onAudioSessionClosed(sessionId: Int) {
        val packageName = sessionPackageNames.remove(sessionId)
        applicationScope.launch {
            _sessionEvents.emit(SessionEvent(sessionId, packageName, isOpen = false))
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        audioEffectManager.release()
        eqNotification.update(false)
    }
}
