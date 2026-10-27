package com.example.data.model

import android.net.Uri

data class Track(
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderUri: String,
    val folderName: String,
    val dateAdded: Long,
    val sizeBytes: Long,
    val albumArtUri: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0
) {
    val uri: Uri get() = Uri.parse(uriString)

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val displayArtist: String
        get() = if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else artist

    val displayAlbum: String
        get() = if (album.isBlank() || album.equals("<unknown>", ignoreCase = true)) "Unknown Album" else album
}
