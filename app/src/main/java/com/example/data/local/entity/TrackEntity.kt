package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Track

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["folderUri"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastPlayed"])
    ]
)
data class TrackEntity(
    @PrimaryKey val uriString: String,
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
    fun toTrack(): Track = Track(
        uriString = uriString,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        folderUri = folderUri,
        folderName = folderName,
        dateAdded = dateAdded,
        sizeBytes = sizeBytes,
        albumArtUri = albumArtUri,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayed = lastPlayed
    )

    companion object {
        fun fromTrack(track: Track): TrackEntity = TrackEntity(
            uriString = track.uriString,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            folderUri = track.folderUri,
            folderName = track.folderName,
            dateAdded = track.dateAdded,
            sizeBytes = track.sizeBytes,
            albumArtUri = track.albumArtUri,
            isFavorite = track.isFavorite,
            playCount = track.playCount,
            lastPlayed = track.lastPlayed
        )
    }
}
