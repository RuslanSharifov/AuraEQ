package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE uriString = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentTracksFlow(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAddedTracksFlow(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderUri = :folderUri ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByFolderFlow(folderUri: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderUri = :folderUri")
    suspend fun getTracksByFolder(folderUri: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE uriString = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE uriString = :uri")
    suspend fun recordPlayback(uri: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracks WHERE uriString = :uri")
    suspend fun deleteTrackByUri(uri: String)

    @Query("DELETE FROM tracks WHERE folderUri = :folderUri")
    suspend fun deleteTracksByFolder(folderUri: String)

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int
}
