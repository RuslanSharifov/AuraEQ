package com.example.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.audio.SpatialSettings
import com.example.data.model.Track
import kotlinx.coroutines.flow.StateFlow

class PlaybackRepository(private val context: Context) {

    val playbackState: StateFlow<PlaybackState> = AuraMediaSessionService.playbackState

    fun playTrack(track: Track, queue: List<Track>) {
        val index = queue.indexOfFirst { it.uriString == track.uriString }
        val shuffle = playbackState.value.isShuffleEnabled
        if (index >= 0) startPlayback(queue, index, shuffle)
        else startPlayback(listOf(track), 0, shuffle)
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        startPlayback(tracks, startIndex, shuffle = false)
    }

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        startPlayback(tracks, tracks.indices.random(), shuffle = true)
    }

    private fun startPlayback(tracks: List<Track>, index: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        val service = AuraMediaSessionService.instance
        if (service != null) {
            service.playAll(tracks, index, shuffle)
            return
        }

        AuraMediaSessionService.pendingPlayback =
            AuraMediaSessionService.PlaybackRequest(tracks.toList(), index, shuffle)
        try {
            context.startService(Intent(context, AuraMediaSessionService::class.java))
        } catch (e: IllegalStateException) {
            AuraMediaSessionService.pendingPlayback = null
            Log.e("AuraPlayback", "Could not start the media session from the current app state", e)
        }
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
        AuraMediaSessionService.instance?.togglePlayPause()
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

    fun setEffectsEnabled(enabled: Boolean) {
        AuraMediaSessionService.instance?.setEffectsEnabled(enabled)
    }

    fun updateSurroundStrength(strength: Int) {
        AuraMediaSessionService.instance?.updateSurroundStrength(strength)
    }

    fun updateSpatialSettings(settings: SpatialSettings) {
        AuraMediaSessionService.instance?.updateSpatialSettings(settings)
    }
}
