package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.AuraApplication
import com.example.MainActivity
import com.example.R
import com.example.audio.SpatialAudioProcessor
import com.example.audio.SpatialSettings
import com.example.audio.Stereo3DAudioProcessor
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AuraMediaSessionService : MediaSessionService() {
    private val app get() = application as AuraApplication
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val spatialProcessor = SpatialAudioProcessor()
    private val surround3dProcessor = Stereo3DAudioProcessor()
    private var spatialSettings = SpatialSettings()
    private var surroundStrength = 0
    private var effectsEnabled = false
    private var queue: List<Track> = emptyList()
    private var attachedSessionId = C.AUDIO_SESSION_ID_UNSET
    private var progressJob: Job? = null
    private var artworkJob: Job? = null
    private var artworkTrackUri: String? = null
    private var bufferingStartedAt: Long? = null

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    internal data class PlaybackRequest(
        val tracks: List<Track>,
        val index: Int,
        val shuffle: Boolean
    )

    companion object {
        private const val TAG = "AuraPlayback"
        private const val CHANNEL_ID = "aura_music_playback"

        var instance: AuraMediaSessionService? = null
            private set

        internal var pendingPlayback: PlaybackRequest? = null

        private val _playbackState = MutableStateFlow(PlaybackState())
        val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel)
                .build()
        )
        player = createPlayer()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
            .also { addSession(it) }
        instance = this
        observePreferences()
        Log.i(TAG, "Playback service and media notification registered")

        val request = pendingPlayback
        pendingPlayback = null
        if (request != null) playAll(request.tracks, request.index, request.shuffle)
    }

    private fun createPlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(spatialProcessor, surround3dProcessor))
                .build()
        }
        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        // Native initialization can stall startup, so attach only after READY.
                        // Session IDs may also change mid-playback when switching output routes.
                        if (player.playbackState == Player.STATE_READY || attachedSessionId > 0) {
                            syncAudioSession(this@apply.audioSessionId)
                        }
                        publishState()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        progressJob?.cancel()
                        if (isPlaying) startProgressTracker()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val now = SystemClock.elapsedRealtime()
                        val bufferedFor = bufferingStartedAt?.let { now - it }
                        bufferingStartedAt = now.takeIf { playbackState == Player.STATE_BUFFERING }
                        val message = "State=$playbackState position=${currentPosition}ms buffer=${totalBufferedDuration}ms"
                        if (bufferedFor != null && bufferedFor >= 1000) {
                            Log.w(TAG, "$message after ${bufferedFor}ms buffering")
                        } else {
                            Log.d(TAG, message)
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        Log.d(TAG, "Play requested=$playWhenReady reason=$reason")
                    }

                    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                        Log.d(TAG, "Playback suppression reason=$playbackSuppressionReason")
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val track = currentTrack()
                        Log.d(TAG, "Track transition index=$currentMediaItemIndex reason=$reason")
                        if (track != null) recordPlayback(track)
                        resolveArtwork(track)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val cause = error.cause?.javaClass?.simpleName ?: "unknown"
                        Log.e(TAG, "Playback failed: ${error.errorCodeName}, cause=$cause, index=$currentMediaItemIndex")
                    }
                })
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioUnderrun(
                        eventTime: AnalyticsListener.EventTime,
                        bufferSize: Int,
                        bufferSizeMs: Long,
                        elapsedSinceLastFeedMs: Long
                    ) {
                        Log.w(TAG, "Audio underrun: buffer=$bufferSize bytes/${bufferSizeMs}ms, " +
                            "time since last feed=${elapsedSinceLastFeedMs}ms")
                    }
                })
            }
    }

    private fun observePreferences() {
        val prefs = app.preferencesRepository
        serviceScope.launch {
            prefs.pauseOnDisconnectFlow.collect { player.setHandleAudioBecomingNoisy(it) }
        }
        serviceScope.launch {
            prefs.isEnabledFlow.collect { setEffectsEnabled(it) }
        }
        serviceScope.launch {
            prefs.spatialSettingsFlow.collect { updateSpatialSettings(it) }
        }
        serviceScope.launch {
            prefs.virtualizerFlow.collect { updateSurroundStrength(it) }
        }
    }

    private fun syncAudioSession(newId: Int) {
        if (attachedSessionId == newId) return
        if (attachedSessionId > 0) {
            app.audioEffectManager.detachSession(attachedSessionId)
            app.onAudioSessionClosed(attachedSessionId)
        }
        attachedSessionId = newId
        if (newId > 0) {
            app.audioEffectManager.attachSession(newId, packageName)
            app.onAudioSessionOpened(newId, packageName)
            Log.d(TAG, "Audio output session changed to $newId")
        }
    }

    private fun startProgressTracker() {
        progressJob = serviceScope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    private fun currentTrack(): Track? {
        val id = player.currentMediaItem?.mediaId
        return queue.getOrNull(player.currentMediaItemIndex)?.takeIf { it.uriString == id }
    }

    private fun publishState() {
        val track = currentTrack()
        _playbackState.value = PlaybackState(
            currentTrack = track,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
            playbackState = player.playbackState,
            currentPositionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0L,
            queue = queue,
            currentQueueIndex = if (track == null) -1 else player.currentMediaItemIndex,
            repeatMode = player.repeatMode,
            isShuffleEnabled = player.shuffleModeEnabled,
            audioSessionId = player.audioSessionId
        )
    }

    private fun recordPlayback(track: Track) {
        serviceScope.launch {
            try {
                app.preferencesRepository.saveLastPlayed(track.uriString, 0L)
                app.musicLibraryRepository.recordTrackPlayed(track.uriString)
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Could not save playback history", e)
            } catch (e: android.database.SQLException) {
                Log.w(TAG, "Could not update playback history", e)
            }
        }
    }

    private fun resolveArtwork(track: Track?) {
        if (artworkTrackUri == track?.uriString) return
        artworkJob?.cancel()
        artworkTrackUri = track?.uriString
        if (track == null) return

        artworkJob = serviceScope.launch {
            val artwork = try {
                app.musicLibraryRepository.resolveArtwork(track)
            } catch (e: android.database.SQLException) {
                Log.w(TAG, "Could not update the library artwork", e)
                return@launch
            }
            if (currentTrack()?.uriString != track.uriString) return@launch
            queue = queue.map { if (it.uriString == track.uriString) it.copy(albumArtUri = artwork) else it }
            val index = player.currentMediaItemIndex
            val item = player.getMediaItemAt(index)
            if (item.mediaMetadata.artworkUri?.toString() != artwork) {
                val metadata = item.mediaMetadata.buildUpon()
                    .setArtworkUri(artwork?.let(Uri::parse))
                    .build()
                // Only metadata changes, keeping the prepared source and current position.
                player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
                Log.d(TAG, "Updated playback cover at index=$index, available=${artwork != null}")
            }
            publishState()
        }
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uriString)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(displayArtist)
                .setAlbumTitle(displayAlbum)
                .setArtworkUri(albumArtUri?.let(Uri::parse))
                .build()
        )
        .build()

    fun playAll(tracks: List<Track>, startIndex: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(tracks.indices)
        queue = tracks.toList()
        artworkJob?.cancel()
        artworkTrackUri = null
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        resolveArtwork(currentTrack())
        player.playWhenReady = true
        player.prepare()
        publishState()
    }

    fun playNext(track: Track) {
        val insertIndex = if (queue.isEmpty()) 0 else player.currentMediaItemIndex + 1
        queue = queue.toMutableList().apply { add(insertIndex, track) }
        player.addMediaItem(insertIndex, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        queue = queue + track
        player.addMediaItem(track.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        queue = queue.toMutableList().apply { removeAt(index) }
        player.removeMediaItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        queue = queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        player.moveMediaItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        queue = emptyList()
        player.pause()
        player.clearMediaItems()
    }

    fun togglePlayPause() {
        if (queue.isEmpty()) return
        val state = player.playbackState
        if (player.playWhenReady && (state == Player.STATE_BUFFERING || state == Player.STATE_READY)) {
            player.pause()
        } else {
            if (state == Player.STATE_IDLE) player.prepare()
            if (state == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun skipToNext() = player.seekToNext()
    fun skipToPrevious() = player.seekToPrevious()

    fun toggleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun setEffectsEnabled(enabled: Boolean) {
        if (effectsEnabled == enabled) return
        effectsEnabled = enabled
        Log.d(TAG, "Local audio effects enabled=$enabled")
        updateSpatialSettings(spatialSettings)
        updateSurroundStrength(surroundStrength)
    }

    fun updateSpatialSettings(settings: SpatialSettings) {
        spatialSettings = settings
        spatialProcessor.update(settings, effectsEnabled)
    }

    fun updateSurroundStrength(strength: Int) {
        surroundStrength = strength
        surround3dProcessor.update(strength, effectsEnabled)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        syncAudioSession(C.AUDIO_SESSION_ID_UNSET)
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        player.release()
        _playbackState.value = PlaybackState()
        Log.i(TAG, "Playback service stopped")
        super.onDestroy()
    }
}
