package com.example.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
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
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.AuraApplication
import com.example.MainActivity
import com.example.audio.SpatialAudioProcessor
import com.example.audio.SpatialSettings
import com.example.audio.Stereo3DAudioProcessor
import com.example.data.local.AuraMusicDatabase
import com.example.data.model.Track
import com.example.util.AuraLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AuraMediaSessionService : MediaSessionService() {

    private val area = "Playback"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var notifications: PlaybackNotificationProvider? = null

    val spatialProcessor = SpatialAudioProcessor()
    val surround3dProcessor = Stereo3DAudioProcessor()

    private var progressJob: Job? = null
    private var becomingNoisyReceiver: BroadcastReceiver? = null
    private var lastAudioSessionId = 0

    companion object {
        var instance: AuraMediaSessionService? = null
            private set

        // A playback tap may start this service asynchronously. Keep the requested
        // queue until onCreate has finished constructing the media session.
        @Volatile internal var pendingPlayback: Pair<List<Track>, Int>? = null

        private val _playbackState = MutableStateFlow(PlaybackState())
        val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private val _playbackPosition = MutableStateFlow(PlaybackPosition())
        val playbackPosition: StateFlow<PlaybackPosition> = _playbackPosition.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AuraLog.i(area, "Service created")
        notifications = PlaybackNotificationProvider(this).also { setMediaNotificationProvider(it) }
        initPlayer()
        registerBecomingNoisyReceiver()
        pendingPlayback?.let { (tracks, index) ->
            pendingPlayback = null
            playAll(tracks, index)
        }
    }

    private fun initPlayer() {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(spatialProcessor, surround3dProcessor))
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                AuraLog.d(area, "isPlaying=$isPlaying pos=${player.currentPosition}ms")
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    progressJob?.cancel()
                    progressJob = null
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val dur = if (player.duration > 0) player.duration else 0L
                _playbackState.value = _playbackState.value.copy(
                    playbackState = state,
                    durationMs = dur,
                    isBuffering = state == Player.STATE_BUFFERING
                )
                _playbackPosition.value = _playbackPosition.value.copy(durationMs = dur)
                AuraLog.d(area, "state=${stateName(state)} duration=${dur}ms")
                // Fallback: normally onAudioSessionIdChanged already handled this.
                syncAudioSession(player.audioSessionId)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                _playbackState.value = _playbackState.value.copy(audioSessionId = audioSessionId)
                syncAudioSession(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                AuraLog.e(area, "Playback failed code=${error.errorCodeName}", error)
                _playbackState.value = _playbackState.value.copy(isPlaying = false, isBuffering = false)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AuraLog.i(area, "Track transition index=${player.currentMediaItemIndex} reason=$reason")
                _playbackPosition.value = PlaybackPosition()
                updateCurrentTrackFromPlayer()
                notifications?.refresh()
            }
        })

        exoPlayer = player

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(AuraSessionCallback())
            .build()

        val app = applicationContext as? AuraApplication
        if (app != null) {
            serviceScope.launch {
                app.preferencesRepository.spatialSettingsFlow.collect { settings ->
                    spatialProcessor.update(settings, settings.is8dEnabled)
                }
            }
            serviceScope.launch {
                app.preferencesRepository.virtualizerFlow.collect { virt ->
                    surround3dProcessor.update(virt, virt > 0)
                }
            }
        }
    }

    /** Attaches effects only when the player's audio session actually changes. */
    private fun syncAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == lastAudioSessionId) return
        lastAudioSessionId = sessionId
        AuraLog.i(area, "Audio session changed to $sessionId")
        val app = applicationContext as? AuraApplication ?: return
        try {
            app.audioEffectManager.attachSession(sessionId, packageName)
            app.onAudioSessionOpened(sessionId, packageName)
        } catch (e: Exception) {
            AuraLog.w(area, "Failed attaching effect session $sessionId", e)
        }
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    /** Position updates twice a second; the notification is refreshed once a second. */
    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            var tick = 0
            while (isActive) {
                val player = exoPlayer ?: break
                val pos = player.currentPosition
                val dur = if (player.duration > 0) player.duration else _playbackPosition.value.durationMs
                _playbackPosition.value = PlaybackPosition(positionMs = pos, durationMs = dur)
                tick++
                if (tick % 2 == 0) notifications?.refresh()
                delay(500)
            }
        }
    }

    private fun updateCurrentTrackFromPlayer() {
        val player = exoPlayer ?: return
        val currentIdx = player.currentMediaItemIndex
        val queue = _playbackState.value.queue
        if (currentIdx in queue.indices) {
            val track = queue[currentIdx]
            _playbackState.value = _playbackState.value.copy(
                currentTrack = track,
                currentQueueIndex = currentIdx
            )
            val app = applicationContext as? AuraApplication
            if (app != null) {
                serviceScope.launch {
                    app.preferencesRepository.saveLastPlayed(track.uriString, 0L)
                    AuraMusicDatabase.getInstance(applicationContext).trackDao().recordPlayback(track.uriString)
                }
            }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track>) {
        val targetIdx = newQueue.indexOfFirst { it.uriString == track.uriString }.coerceAtLeast(0)
        setQueueInternal(newQueue, targetIdx, playWhenReady = true)
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.size - 1)
        setQueueInternal(tracks, idx, playWhenReady = true)
    }

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        setQueueInternal(shuffled, 0, playWhenReady = true)
        _playbackState.value = _playbackState.value.copy(isShuffleEnabled = true)
    }

    /** Single place that builds a MediaItem, so every path carries the same artwork. */
    private fun buildMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayArtist)
            .setAlbumTitle(track.displayAlbum)
            .apply {
                track.albumArtUri?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.uriString)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun setQueueInternal(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        val player = exoPlayer ?: return
        AuraLog.i(area, "Set queue size=${tracks.size} start=$startIndex")
        player.setMediaItems(tracks.map { buildMediaItem(it) }, startIndex, 0L)
        player.prepare()
        if (playWhenReady) player.play()

        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentQueueIndex = startIndex,
            currentTrack = tracks.getOrNull(startIndex)
        )
    }

    fun playNext(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val currentIdx = _playbackState.value.currentQueueIndex
        val insertIdx = (currentIdx + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIdx, track)
        exoPlayer?.addMediaItem(insertIdx, buildMediaItem(track))
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
    }

    fun addToQueue(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        currentQueue.add(track)
        exoPlayer?.addMediaItem(buildMediaItem(track))
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        exoPlayer?.removeMediaItem(index)

        val currentIdx = _playbackState.value.currentQueueIndex
        val updatedIdx = if (index < currentIdx) currentIdx - 1 else currentIdx
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentQueueIndex = updatedIdx,
            currentTrack = currentQueue.getOrNull(updatedIdx)
        )
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        exoPlayer?.moveMediaItem(fromIndex, toIndex)

        val currentIdx = _playbackState.value.currentQueueIndex
        val newIdx = when {
            currentIdx == fromIndex -> toIndex
            fromIndex < currentIdx && toIndex >= currentIdx -> currentIdx - 1
            fromIndex > currentIdx && toIndex <= currentIdx -> currentIdx + 1
            else -> currentIdx
        }

        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentQueueIndex = newIdx,
            currentTrack = currentQueue.getOrNull(newIdx)
        )
    }

    fun clearQueue() {
        exoPlayer?.clearMediaItems()
        _playbackPosition.value = PlaybackPosition()
        _playbackState.value = _playbackState.value.copy(
            queue = emptyList(),
            currentQueueIndex = -1,
            currentTrack = null,
            isPlaying = false,
            durationMs = 0L
        )
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playbackPosition.value = _playbackPosition.value.copy(positionMs = positionMs)
    }

    /** Toggles favorite for the playing track in the DB, queue and notification. */
    fun toggleCurrentFavorite() {
        val current = _playbackState.value.currentTrack ?: return
        val next = !current.isFavorite
        val updated = current.copy(isFavorite = next)
        _playbackState.value = _playbackState.value.copy(
            currentTrack = updated,
            queue = _playbackState.value.queue.map { if (it.uriString == current.uriString) updated else it }
        )
        serviceScope.launch(Dispatchers.IO) {
            try {
                AuraMusicDatabase.getInstance(applicationContext)
                    .trackDao()
                    .setFavorite(current.uriString, next)
                AuraLog.i(area, "Favorite=$next for ${current.title}")
            } catch (e: Exception) {
                AuraLog.e(area, "Favorite update failed", e)
            }
        }
        notifications?.refresh()
    }

    fun skipToNext() {
        val player = exoPlayer ?: return
        val state = _playbackState.value
        if (state.queue.isEmpty()) return
        if (state.isShuffleEnabled && state.queue.size > 1) {
            val randomIdx = state.queue.indices.filter { it != state.currentQueueIndex }.randomOrNull() ?: 0
            player.seekToDefaultPosition(randomIdx)
            player.play()
        } else if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else if (state.repeatMode == Player.REPEAT_MODE_ALL) {
            player.seekToDefaultPosition(0)
            player.play()
        }
    }

    fun skipToPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else {
            player.seekTo(0)
        }
    }

    fun toggleRepeatMode() {
        val player = exoPlayer ?: return
        val next = when (_playbackState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        _playbackState.value = _playbackState.value.copy(repeatMode = next)
        AuraLog.i(area, "Repeat mode=$next")
        notifications?.refresh()
    }

    fun toggleShuffle() {
        val player = exoPlayer ?: return
        val next = !_playbackState.value.isShuffleEnabled
        player.shuffleModeEnabled = next
        _playbackState.value = _playbackState.value.copy(isShuffleEnabled = next)
    }

    fun updateSpatialSettings(settings: SpatialSettings) {
        spatialProcessor.update(settings, settings.is8dEnabled)
    }

    private fun registerBecomingNoisyReceiver() {
        becomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    val app = applicationContext as? AuraApplication
                    serviceScope.launch {
                        val pause = app?.preferencesRepository?.pauseOnDisconnectFlow?.first() ?: true
                        AuraLog.i(area, "Audio becoming noisy, pauseOnDisconnect=$pause")
                        if (pause) exoPlayer?.pause()
                    }
                }
            }
        }
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        AuraLog.i(area, "Service destroyed")
        instance = null
        progressJob?.cancel()
        notifications?.cancel()
        serviceScope.cancel()
        becomingNoisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class AuraSessionCallback : MediaSession.Callback {
        // Default media session callback handles transport controls.
    }
}
