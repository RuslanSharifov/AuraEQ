package com.example.playback

import androidx.media3.common.Player
import com.example.data.model.Track

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val repeatMode: Int = Player.REPEAT_MODE_OFF, // OFF (0), ONE (1), ALL (2)
    val isShuffleEnabled: Boolean = false,
    val audioSessionId: Int = 0
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedCurrentTime: String
        get() {
            val totalSec = (currentPositionMs / 1000).coerceAtLeast(0)
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format("%d:%02d", m, s)
        }

    val formattedRemainingTime: String
        get() {
            val remSec = ((durationMs - currentPositionMs) / 1000).coerceAtLeast(0)
            val m = remSec / 60
            val s = remSec % 60
            return String.format("-%d:%02d", m, s)
        }
}
