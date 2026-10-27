package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistItemEntity
import com.example.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_items pi ON t.uriString = pi.trackUriString
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position ASC
    """)
    fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_items pi ON t.uriString = pi.trackUriString
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position ASC
    """)
    suspend fun getTracksForPlaylist(playlistId: Long): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackUriString = :trackUri")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackUri: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun getPlaylistItemCountFlow(playlistId: Long): Flow<Int>

    @Transaction
    suspend fun addTracksToPlaylist(playlistId: Long, trackUris: List<String>) {
        val currentMax = getMaxPosition(playlistId) ?: -1
        var nextPos = currentMax + 1
        val items = trackUris.map { uri ->
            PlaylistItemEntity(
                playlistId = playlistId,
                trackUriString = uri,
                position = nextPos++
            )
        }
        insertPlaylistItems(items)
        val playlist = getPlaylistById(playlistId)
        if (playlist != null) {
            updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, orderedTrackUris: List<String>) {
        clearPlaylistItems(playlistId)
        val items = orderedTrackUris.mapIndexed { index, uri ->
            PlaylistItemEntity(
                playlistId = playlistId,
                trackUriString = uri,
                position = index
            )
        }
        insertPlaylistItems(items)
        val playlist = getPlaylistById(playlistId)
        if (playlist != null) {
            updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
