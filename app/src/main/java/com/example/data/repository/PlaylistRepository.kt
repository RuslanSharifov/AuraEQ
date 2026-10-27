package com.example.data.repository

import com.example.data.local.dao.PlaylistDao
import com.example.data.local.entity.PlaylistEntity
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverUri: String? = null
)

class PlaylistRepository(
    private val playlistDao: PlaylistDao
) {
    val allPlaylistsFlow: Flow<List<Playlist>> = playlistDao.getAllPlaylistsFlow().map { list ->
        list.map { Playlist(it.id, it.name, it.createdAt, it.updatedAt, it.coverUri) }
    }

    suspend fun getPlaylistById(id: Long): Playlist? = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistById(id)?.let {
            Playlist(it.id, it.name, it.createdAt, it.updatedAt, it.coverUri)
        }
    }

    fun getPlaylistTracksFlow(playlistId: Long): Flow<List<Track>> {
        return playlistDao.getTracksForPlaylistFlow(playlistId).map { list ->
            list.map { it.toTrack() }
        }
    }

    suspend fun getPlaylistTracks(playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        playlistDao.getTracksForPlaylist(playlistId).map { it.toTrack() }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val entity = PlaylistEntity(
            name = name.trim().ifEmpty { "New Playlist" },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(id) ?: return@withContext
        playlistDao.updatePlaylist(
            existing.copy(
                name = newName.trim().ifEmpty { existing.name },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(id)
    }

    suspend fun duplicatePlaylist(id: Long, newName: String? = null): Long = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(id) ?: return@withContext -1L
        val tracks = playlistDao.getTracksForPlaylist(id)
        val copyName = newName ?: "${existing.name} (Copy)"
        val newEntity = PlaylistEntity(
            name = copyName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            coverUri = existing.coverUri
        )
        val newId = playlistDao.insertPlaylist(newEntity)
        if (newId > 0 && tracks.isNotEmpty()) {
            playlistDao.addTracksToPlaylist(newId, tracks.map { it.uriString })
        }
        newId
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackUris: List<String>) = withContext(Dispatchers.IO) {
        playlistDao.addTracksToPlaylist(playlistId, trackUris)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackUri: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackUri)
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedTrackUris: List<String>) = withContext(Dispatchers.IO) {
        playlistDao.reorderPlaylist(playlistId, orderedTrackUris)
    }

    suspend fun setPlaylistCover(playlistId: Long, coverUri: String?) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(existing.copy(coverUri = coverUri, updatedAt = System.currentTimeMillis()))
    }
}
