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
        val service = ensureServiceRunning()
        if (service != null) {
            service.playTrack(track, queue)
        } else {
            // Service starting up; attempt again after brief delay or retry
            val intent = Intent(context, AuraMediaSessionService::class.java)
            ContextCompat.startForegroundService(context, intent)
            AuraMediaSessionService.instance?.playTrack(track, queue)
        }
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        val service = ensureServiceRunning()
        service?.playAll(tracks, startIndex)
    }

    fun shuffleAll(tracks: List<Track>) {
        val service = ensureServiceRunning()
        service?.shuffleAll(tracks)
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
