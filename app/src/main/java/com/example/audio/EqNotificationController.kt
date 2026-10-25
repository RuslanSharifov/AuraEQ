package com.example.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.AuraApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * User-visible ongoing notification while EQ is enabled. This is NOT a fake
 * foreground media-playback service; Android 14+ may allow the user to dismiss it.
 */
class EqNotificationController(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var hasShownActiveNotification = false
    companion object {
        const val CHANNEL_ID = "aura_eq_active"
        const val NOTIFICATION_ID = 1008
        const val ACTION_TOGGLE = "com.example.action.TOGGLE_EQ_FROM_NOTIFICATION"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AuraEQ aktiv ekvalayzer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ekvalayzeri bildiriş panelindən idarə et"
                    setShowBadge(false)
                }
            )
        }
    }

    fun clear() {
        hasShownActiveNotification = false
        manager.cancel(NOTIFICATION_ID)
    }

    fun update(enabled: Boolean) {
        // No notification on a fresh disabled startup. Once the user enables it,
        // leave a dismissible "Activate" notification after disabling.
        if (!enabled && !hasShownActiveNotification) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            clear()
            return
        }
        if (enabled) hasShownActiveNotification = true

        val contentIntent = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getBroadcast(
            context, 11, Intent(context, EqToggleReceiver::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(if (enabled) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(if (enabled) "AuraEQ · EQ aktivdir" else "AuraEQ · EQ deaktivdir")
            .setContentText(if (enabled) "Bildirişdən söndürə bilərsən" else "Aktiv et və ya bildirişi sil")
            .setContentIntent(contentIntent)
            .setOngoing(enabled)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, if (enabled) "Deaktiv et" else "Aktiv et", toggleIntent).build())
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ permission was revoked while building the notification.
        }
    }
}

class EqToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != EqNotificationController.ACTION_TOGGLE) return
        val pending = goAsync()
        val application = context.applicationContext as AuraApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val enabled = application.preferencesRepository.isEnabledFlow.first()
                application.preferencesRepository.setEnabled(!enabled)
            } finally {
                pending.finish()
            }
        }
    }
}
