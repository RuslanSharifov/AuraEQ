package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import com.example.AuraApplication

class AudioSessionReceiver : BroadcastReceiver() {

    private val TAG = "AudioSessionReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)

        Log.i(TAG, "AudioSession broadcast received: action=$action, session=$sessionId, pkg=$packageName")

        if (sessionId < 0) return

        val app = context.applicationContext as? AuraApplication ?: return
        val audioEffectManager = app.audioEffectManager

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                audioEffectManager.attachSession(sessionId, packageName)
                app.onAudioSessionOpened(sessionId, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                audioEffectManager.detachSession(sessionId)
                app.onAudioSessionClosed(sessionId)
            }
        }
    }
}
