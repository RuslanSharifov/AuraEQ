package com.example.playback

/**
 * Playback position, kept apart from [PlaybackState] so that the 500 ms progress
 * tick does not re-render every screen that reads the main state.
 */
data class PlaybackPosition(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedCurrent: String
        get() = formatTime(positionMs)

    val formattedRemaining: String
        get() = "-" + formatTime((durationMs - positionMs).coerceAtLeast(0L))

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        return String.format("%d:%02d", totalSec / 60, totalSec % 60)
    }
}
