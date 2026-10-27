package com.example.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.audio.SpatialSettings
import com.example.data.model.Track
import kotlinx.coroutines.flow.StateFlow

class PlaybackRepository(private val context: Context) {

    val playbackState: StateFlow<PlaybackState> = AuraMediaSessionService.playbackState

    private fun ensureServiceRunning(): AuraMediaSessionService? {
        val existing = AuraMediaSessionService.instance
        if (existing != null) return existing

        val intent = Intent(context, AuraMediaSessionService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            context.startService(intent)
        }
        return AuraMediaSessionService.instance
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val startIndex = queue.indexOfFirst { it.uriString == track.uriString }.coerceAtLeast(0)
        playAll(queue.ifEmpty { listOf(track) }, startIndex)
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val service = AuraMediaSessionService.instance
        if (service != null) {
            service.playAll(tracks, startIndex)
        } else {
            AuraMediaSessionService.pendingPlayback = tracks to startIndex
            ensureServiceRunning()
        }
    }

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        playAll(shuffled, 0)
        AuraMediaSessionService.instance?.toggleShuffle()
    }

    fun playNext(track: Track) {
        AuraMediaSessionService.instance?.playNext(track)
    }

    fun addToQueue(track: Track) {
        AuraMediaSessionService.instance?.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        AuraMediaSessionService.instance?.removeFromQueue(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        AuraMediaSessionService.instance?.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        AuraMediaSessionService.instance?.clearQueue()
    }

    fun togglePlayPause() {
        val service = ensureServiceRunning()
        service?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        AuraMediaSessionService.instance?.seekTo(positionMs)
    }

    fun skipToNext() {
        AuraMediaSessionService.instance?.skipToNext()
    }

    fun skipToPrevious() {
        AuraMediaSessionService.instance?.skipToPrevious()
    }

    fun toggleRepeatMode() {
        AuraMediaSessionService.instance?.toggleRepeatMode()
    }

    fun toggleShuffle() {
        AuraMediaSessionService.instance?.toggleShuffle()
    }

    fun updateSpatialSettings(settings: SpatialSettings) {
        AuraMediaSessionService.instance?.updateSpatialSettings(settings)
    }
}
