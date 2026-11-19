package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.TrackDao
import com.example.data.local.entity.TrackEntity
import com.example.data.model.Track
import com.example.data.preferences.AuraPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class FolderInfo(
    val uriString: String,
    val name: String,
    val trackCount: Int = 0
)

class MusicLibraryRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val preferencesRepository: AuraPreferencesRepository
) {
    private val TAG = "MusicLibraryRepo"

    private val supportedExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "wav", "opus"
    )

    val allTracksFlow: Flow<List<Track>> = trackDao.getAllTracksFlow().map { list ->
        list.map { it.toTrack() }
    }

    val favoriteTracksFlow: Flow<List<Track>> = trackDao.getFavoriteTracksFlow().map { list ->
        list.map { it.toTrack() }
    }

    val recentTracksFlow: Flow<List<Track>> = trackDao.getRecentTracksFlow(25).map { list ->
        list.map { it.toTrack() }
    }

    val recentlyAddedTracksFlow: Flow<List<Track>> = trackDao.getRecentlyAddedTracksFlow(25).map { list ->
        list.map { it.toTrack() }
    }

    val trackCountFlow: Flow<Int> = trackDao.getTrackCountFlow()

    suspend fun getTracksByFolder(folderUri: String): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getTracksByFolder(folderUri).map { it.toTrack() }
    }

    suspend fun toggleFavorite(trackUri: String, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        trackDao.setFavorite(trackUri, !currentFavorite)
    }

    suspend fun recordTrackPlayed(trackUri: String) = withContext(Dispatchers.IO) {
        trackDao.recordPlayback(trackUri, System.currentTimeMillis())
    }

    /**
     * Add folder from SAF ACTION_OPEN_DOCUMENT_TREE.
     * Permission must already be persisted by the caller with takePersistableUriPermission.
     */
    suspend fun addFolder(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val uriStr = treeUri.toString()
        val current = preferencesRepository.folderUrisFlow.first()
        if (!current.contains(uriStr)) {
            val updated = current + uriStr
            preferencesRepository.saveFolderUris(updated)
        }
        scanFolder(treeUri)
        true
    }

    /** Import individually selected local audio files when Android refuses a folder tree. */
    suspend fun importAudioFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val imported = mutableListOf<TrackEntity>()
        for (uri in uris.distinct()) {
            try {
                val file = DocumentFile.fromSingleUri(context, uri) ?: continue
                val name = file.name.orEmpty()
                val extension = name.substringAfterLast('.', "").lowercase()
                val isAudio = context.contentResolver.getType(uri)?.startsWith("audio/") == true
                if (!file.isFile || (extension !in supportedExtensions && !isAudio)) continue
                extractTrackMetadata(file, "manual_audio", "Seçilmiş musiqilər")?.let(imported::add)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot import selected audio file: $uri", e)
            }
        }
        if (imported.isNotEmpty()) trackDao.insertOrUpdateTracks(imported)
        imported.size
    }

    suspend fun removeFolder(folderUriStr: String) = withContext(Dispatchers.IO) {
        val current = preferencesRepository.folderUrisFlow.first()
        val updated = current - folderUriStr
        preferencesRepository.saveFolderUris(updated)
        // Remove tracks belonging to this folder from the database (does not delete user files)
        trackDao.deleteTracksByFolder(folderUriStr)
    }

    suspend fun rescanAllFolders() = withContext(Dispatchers.IO) {
        val folderUris = preferencesRepository.folderUrisFlow.first()
        for (uriStr in folderUris) {
            try {
                scanFolder(Uri.parse(uriStr))
            } catch (e: Exception) {
                Log.w(TAG, "Failed scanning folder: $uriStr", e)
            }
        }
    }

    suspend fun scanFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
            val folderName = root.name ?: "Music Folder"
            val discovered = mutableListOf<TrackEntity>()

            fun scanDir(dir: DocumentFile) {
                val files = dir.listFiles()
                for (file in files) {
                    if (file.isDirectory) {
                        scanDir(file)
                    } else if (file.isFile) {
                        val name = file.name ?: ""
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in supportedExtensions) {
                            try {
                                val track = extractTrackMetadata(file, treeUri.toString(), folderName)
                                if (track != null) {
                                    discovered.add(track)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error extracting metadata for ${file.uri}", e)
                            }
                        }
                    }
                }
            }

            scanDir(root)
            if (discovered.isNotEmpty()) {
                trackDao.insertOrUpdateTracks(discovered)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Storage permission revoked for tree: $treeUri", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder: $treeUri", e)
        }
    }

    private fun extractTrackMetadata(
        file: DocumentFile,
        folderUri: String,
        folderName: String
    ): TrackEntity? {
        val fileUri = file.uri
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, fileUri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: file.name?.substringBeforeLast('.') ?: "Unknown Title"
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                ?: "Unknown Album"
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val dateAdded = file.lastModified()
            val sizeBytes = file.length()

            var albumArtUri: String? = null
            val picBytes = mmr.embeddedPicture
            if (picBytes != null && picBytes.isNotEmpty()) {
                albumArtUri = saveArtworkCache(fileUri.toString(), picBytes)
            }

            TrackEntity(
                uriString = fileUri.toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                folderUri = folderUri,
                folderName = folderName,
                dateAdded = dateAdded,
                sizeBytes = sizeBytes,
                albumArtUri = albumArtUri
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read media tags for ${file.uri}, falling back to file name", e)
            val name = file.name ?: "Unknown Track"
            TrackEntity(
                uriString = fileUri.toString(),
                title = name.substringBeforeLast('.'),
                artist = "Unknown Artist",
                album = "Unknown Album",
                durationMs = 0L,
                folderUri = folderUri,
                folderName = folderName,
                dateAdded = file.lastModified(),
                sizeBytes = file.length(),
                albumArtUri = null
            )
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    private fun saveArtworkCache(uriString: String, bytes: ByteArray): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(uriString.toByteArray()).joinToString("") { "%02x".format(it) }
            val cacheDir = File(context.cacheDir, "album_art")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val artFile = File(cacheDir, "$hash.jpg")
            if (!artFile.exists()) {
                FileOutputStream(artFile).use { it.write(bytes) }
            }
            Uri.fromFile(artFile).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed caching artwork", e)
            null
        }
    }

    fun clearArtworkCache() {
        try {
            val cacheDir = File(context.cacheDir, "album_art")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing artwork cache", e)
        }
    }

    /**
     * Delete file from device storage via SAF DocumentFile or ContentResolver.
     * Then removes it from Room database and references.
     */
    suspend fun deleteTrackFromDevice(trackUriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(trackUriString)
        var fileDeleted = false
        try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            if (docFile != null && docFile.exists()) {
                fileDeleted = docFile.delete()
            } else {
                val deletedRows = context.contentResolver.delete(uri, null, null)
                fileDeleted = deletedRows > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file from storage: $trackUriString", e)
        }

        // Even if SAF delete throws or returns false (e.g. file already gone),
        // purge from DB so it doesn't leave ghost entries.
        trackDao.deleteTrackByUri(trackUriString)
        fileDeleted
    }
}
