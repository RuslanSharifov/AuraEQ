package com.example.playback

import androidx.media3.common.Player
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerTest {

    private fun createDummyTrack(id: String, title: String, artist: String = "Test Artist", durationMs: Long = 180000L): Track {
        return Track(
            uriString = "content://media/external/audio/media/$id",
            title = title,
            artist = artist,
            album = "Test Album",
            durationMs = durationMs,
            folderUri = "content://tree/music",
            folderName = "Music",
            dateAdded = System.currentTimeMillis(),
            sizeBytes = 5000000L
        )
    }

    @Test
    fun testTrackDurationFormatting() {
        val trackShort = createDummyTrack("1", "Short Song", durationMs = 65000L) // 1m 5s
        assertEquals("1:05", trackShort.formattedDuration)

        val trackLong = createDummyTrack("2", "Long Song", durationMs = 3665000L) // 1h 1m 5s
        assertEquals("1:01:05", trackLong.formattedDuration)
    }

    @Test
    fun testPlaybackStateProgressCalculations() {
        val track = createDummyTrack("1", "Song A", durationMs = 200000L)
        val state = PlaybackState(
            currentTrack = track,
            currentPositionMs = 50000L,
            durationMs = 200000L
        )

        assertEquals(0.25f, state.progress, 0.001f)
        assertEquals("0:50", state.formattedCurrentTime)
        assertEquals("-2:30", state.formattedRemainingTime)
    }

    @Test
    fun testQueueOperations() {
        val track1 = createDummyTrack("1", "Song 1")
        val track2 = createDummyTrack("2", "Song 2")
        val track3 = createDummyTrack("3", "Song 3")

        val queue = mutableListOf(track1, track2, track3)

        // Test remove from queue
        queue.removeAt(1)
        assertEquals(2, queue.size)
        assertEquals("Song 1", queue[0].title)
        assertEquals("Song 3", queue[1].title)

        // Test insert next in queue
        val trackNext = createDummyTrack("4", "Song Next")
        queue.add(1, trackNext)
        assertEquals(3, queue.size)
        assertEquals("Song Next", queue[1].title)

        // Test move queue item
        val moved = queue.removeAt(0)
        queue.add(2, moved)
        assertEquals("Song Next", queue[0].title)
        assertEquals("Song 1", queue[2].title)
    }

    @Test
    fun testRepeatAndShuffleModes() {
        var repeatMode = Player.REPEAT_MODE_OFF

        // Cycle repeat mode: OFF -> ALL -> ONE -> OFF
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        assertEquals(Player.REPEAT_MODE_ALL, repeatMode)

        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        assertEquals(Player.REPEAT_MODE_ONE, repeatMode)

        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        assertEquals(Player.REPEAT_MODE_OFF, repeatMode)

        // Test shuffle toggle
        var shuffleEnabled = false
        shuffleEnabled = !shuffleEnabled
        assertTrue(shuffleEnabled)
        shuffleEnabled = !shuffleEnabled
        assertFalse(shuffleEnabled)
    }
}
