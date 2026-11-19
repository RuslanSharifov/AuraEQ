package com.example.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.AuraLog

/** Handles taps on the custom playback notification. */
class PlaybackNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val service = AuraMediaSessionService.instance
        AuraLog.d("Notify", "Action=$action serviceRunning=${service != null}")
        when (action) {
            ACTION_PREVIOUS -> service?.skipToPrevious()
            ACTION_PLAY_PAUSE -> service?.togglePlayPause()
            ACTION_NEXT -> service?.skipToNext()
            ACTION_REPEAT -> service?.toggleRepeatMode()
            ACTION_FAVORITE -> service?.toggleCurrentFavorite()
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "com.example.action.NOTIF_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.action.NOTIF_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NOTIF_NEXT"
        const val ACTION_REPEAT = "com.example.action.NOTIF_REPEAT"
        const val ACTION_FAVORITE = "com.example.action.NOTIF_FAVORITE"
    }
}
