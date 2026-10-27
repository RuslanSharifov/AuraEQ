package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuraMusicDatabase
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.TrackDao
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuraDatabaseTest {

    private lateinit var db: AuraMusicDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuraMusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackDao = db.trackDao()
        playlistDao = db.playlistDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testTrackInsertAndQuery() = runBlocking {
        val track = TrackEntity(
            uriString = "content://media/1",
            title = "Acoustic Melody",
            artist = "Aura Artist",
            album = "Chill Horizons",
            durationMs = 210000L,
            folderUri = "content://tree/music",
            folderName = "Music",
            dateAdded = 1000L,
            sizeBytes = 4000000L,
            isFavorite = true
        )

        trackDao.insertOrUpdateTrack(track)

        val retrieved = trackDao.getTrackByUri("content://media/1")
        assertNotNull(retrieved)
        assertEquals("Acoustic Melody", retrieved?.title)
        assertEquals("Aura Artist", retrieved?.artist)
        assertTrue(retrieved?.isFavorite == true)

        // Test favorite toggle
        trackDao.setFavorite("content://media/1", false)
        val unfaved = trackDao.getTrackByUri("content://media/1")
        assertEquals(false, unfaved?.isFavorite)

        // Test track count
        assertEquals(1, trackDao.getTrackCount())

        // Test delete
        trackDao.deleteTrackByUri("content://media/1")
        assertNull(trackDao.getTrackByUri("content://media/1"))
        assertEquals(0, trackDao.getTrackCount())
    }

    @Test
    fun testPlaylistCRUDAndAssociations() = runBlocking {
        // 1. Create Playlist
        val playlist = PlaylistEntity(name = "Chill Vibes")
        val playlistId = playlistDao.insertPlaylist(playlist)
        assertTrue(playlistId > 0)

        // 2. Insert tracks
        val t1 = TrackEntity("content://media/10", "Song 1", "Artist 1", "Album 1", 180000L, "folder1", "Folder", 1000L, 1000L)
        val t2 = TrackEntity("content://media/20", "Song 2", "Artist 2", "Album 2", 200000L, "folder1", "Folder", 1000L, 1000L)
        trackDao.insertOrUpdateTracks(listOf(t1, t2))

        // 3. Add tracks to playlist
        playlistDao.addTracksToPlaylist(playlistId, listOf(t1.uriString, t2.uriString))

        val tracksInPlaylist = playlistDao.getTracksForPlaylist(playlistId)
        assertEquals(2, tracksInPlaylist.size)
        assertEquals("Song 1", tracksInPlaylist[0].title)
        assertEquals("Song 2", tracksInPlaylist[1].title)

        // 4. Reorder tracks
        playlistDao.reorderPlaylist(playlistId, listOf(t2.uriString, t1.uriString))
        val reordered = playlistDao.getTracksForPlaylist(playlistId)
        assertEquals("Song 2", reordered[0].title)
        assertEquals("Song 1", reordered[1].title)

        // 5. Remove single track
        playlistDao.removeTrackFromPlaylist(playlistId, t1.uriString)
        val remaining = playlistDao.getTracksForPlaylist(playlistId)
        assertEquals(1, remaining.size)
        assertEquals("Song 2", remaining[0].title)

        // 6. Rename playlist
        val existing = playlistDao.getPlaylistById(playlistId)
        assertNotNull(existing)
        playlistDao.updatePlaylist(existing!!.copy(name = "Super Chill"))
        val renamed = playlistDao.getPlaylistById(playlistId)
        assertEquals("Super Chill", renamed?.name)

        // 7. Delete playlist
        playlistDao.deletePlaylist(playlistId)
        assertNull(playlistDao.getPlaylistById(playlistId))
    }
}
