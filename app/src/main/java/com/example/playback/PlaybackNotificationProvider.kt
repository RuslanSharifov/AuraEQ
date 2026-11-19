package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.example.MainActivity
import com.example.R
import com.example.util.AuraLog
import com.google.common.collect.ImmutableList

/**
 * Media notification drawn with a custom RemoteViews layout: artwork, title and
 * artist, favorite, previous, play/pause, next, repeat, and a progress bar.
 *
 * Android cannot blur what is behind a notification, so the "glass" look is a
 * translucent rounded panel tinted with the dominant colour of the track's
 * embedded cover. Controls send broadcasts to [PlaybackNotificationReceiver].
 */
@OptIn(UnstableApi::class)
class PlaybackNotificationProvider(
    private val service: AuraMediaSessionService
) : MediaNotification.Provider {

    private val area = "Notify"
    private val manager = service.getSystemService(NotificationManager::class.java)

    /** True once Media3 has shown our notification, so pause-state refreshes still apply. */
    private var showing = false

    private var artKey: String? = null
    private var artBitmap: Bitmap? = null
    private var tint: Int = DEFAULT_TINT

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Musiqi pleyeri", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Mahnı idarəetməsi və irəliləyiş"
                    setShowBadge(false)
                    setSound(null, null)
                }
            )
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        showing = true
        return MediaNotification(NOTIFICATION_ID, buildNotification())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

    /** Re-posts the notification with the latest state (progress, play icon, favorite). */
    fun refresh() {
        val state = AuraMediaSessionService.playbackState.value
        if (state.currentTrack == null || !(state.isPlaying || showing)) return
        try {
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            AuraLog.w(area, "Notification permission missing, cannot refresh", e)
        }
    }

    fun cancel() {
        showing = false
        manager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(): Notification {
        val state = AuraMediaSessionService.playbackState.value
        val track = state.currentTrack
        val title = track?.title ?: service.getString(R.string.app_name)
        val subtitle = track?.let { "${it.displayArtist} · ${it.displayAlbum}" }.orEmpty()

        val art = loadArtwork(track?.albumArtUri)
        val views = RemoteViews(service.packageName, R.layout.notification_player)

        views.setTextViewText(R.id.notif_title, title)
        views.setTextViewText(R.id.notif_subtitle, subtitle)
        if (art != null) {
            views.setImageViewBitmap(R.id.notif_art, art)
        } else {
            views.setImageViewResource(R.id.notif_art, R.drawable.ic_notif_music)
        }
        views.setInt(R.id.notif_tint, "setBackgroundColor", tint)

        views.setImageViewResource(
            R.id.notif_fav,
            if (track?.isFavorite == true) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart
        )
        views.setImageViewResource(
            R.id.notif_play,
            if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )
        val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
        views.setImageViewResource(
            R.id.notif_repeat,
            if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_notif_repeat_one else R.drawable.ic_notif_repeat
        )
        views.setInt(R.id.notif_repeat, "setImageAlpha", if (repeatOn) 255 else 110)

        val position = AuraMediaSessionService.playbackPosition.value
        val duration = if (position.durationMs > 0) position.durationMs else state.durationMs
        val max = PROGRESS_MAX
        val progress = if (duration > 0) {
            ((position.positionMs.coerceIn(0L, duration) * max) / duration).toInt()
        } else {
            0
        }
        views.setProgressBar(R.id.notif_progress, max, progress, false)
        views.setTextViewText(R.id.notif_time_start, position.formattedCurrent)
        views.setTextViewText(R.id.notif_time_end, track?.formattedDuration ?: "0:00")

        views.setOnClickPendingIntent(R.id.notif_root, contentIntent())
        views.setOnClickPendingIntent(R.id.notif_fav, actionIntent(PlaybackNotificationReceiver.ACTION_FAVORITE, 31))
        views.setOnClickPendingIntent(R.id.notif_prev, actionIntent(PlaybackNotificationReceiver.ACTION_PREVIOUS, 32))
        views.setOnClickPendingIntent(R.id.notif_play, actionIntent(PlaybackNotificationReceiver.ACTION_PLAY_PAUSE, 33))
        views.setOnClickPendingIntent(R.id.notif_next, actionIntent(PlaybackNotificationReceiver.ACTION_NEXT, 34))
        views.setOnClickPendingIntent(R.id.notif_repeat, actionIntent(PlaybackNotificationReceiver.ACTION_REPEAT, 35))

        return Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_music)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(state.isPlaying)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(Notification.DecoratedMediaCustomViewStyle())
            .build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        service,
        30,
        Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        service,
        requestCode,
        Intent(service, PlaybackNotificationReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Decodes the cover once per track. The notification is rebuilt every second,
     * so re-decoding here would waste CPU and memory.
     */
    private fun loadArtwork(uriString: String?): Bitmap? {
        if (uriString == null) {
            artKey = null
            artBitmap = null
            tint = DEFAULT_TINT
            return null
        }
        if (uriString == artKey) return artBitmap

        artKey = uriString
        val bitmap = try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            if (uri.scheme == "file") {
                BitmapFactory.decodeFile(uri.path, options)
            } else {
                service.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: Exception) {
            AuraLog.w(area, "Artwork decode failed", e)
            null
        }
        artBitmap = bitmap
        tint = bitmap?.let { glassTint(it) } ?: DEFAULT_TINT
        AuraLog.d(area, "Artwork loaded=${bitmap != null} for $uriString")
        return bitmap
    }

    /** Average colour of the cover, translucent, used to tint the glass panel. */
    private fun glassTint(bitmap: Bitmap): Int {
        val one = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        val pixel = one.getPixel(0, 0)
        if (one !== bitmap) one.recycle()
        return Color.argb(0x5A, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
    }

    companion object {
        const val CHANNEL_ID = "aura_playback"
        const val NOTIFICATION_ID = 2001
        private const val PROGRESS_MAX = 1000
        private const val DEFAULT_TINT = 0x331E1E2E
    }
}
