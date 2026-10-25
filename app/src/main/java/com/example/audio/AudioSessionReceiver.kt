package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import com.example.AuraApplication

/**
 * Receives the public Android audio effect session protocol.
 * Spotify must publish a valid session ID; never guess or use deprecated session 0.
 */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION &&
            action != AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        ) return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) {
            Log.d("AuraAudioSession", "Ignoring audio effect request without valid session: $action")
            return
        }
        (context.applicationContext as? AuraApplication)?.handleAudioSessionIntent(intent)
    }
}
