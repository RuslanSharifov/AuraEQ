package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.data.repository.Playlist
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuraAccent
import com.example.viewmodel.AuraViewModel

@Composable
fun PlaylistsScreen(
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle(emptyList())
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle(emptyList())

    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showingFavorites by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    if (showingFavorites) {
        PlaylistDetailView(
            playlist = Playlist(-1L, stringResource(R.string.favorites), 0L, 0L),
            tracks = favoriteTracks,
            onBack = { showingFavorites = false },
            onPlayTrack = { viewModel.playTrack(it, favoriteTracks) },
            onPlayAll = { viewModel.playAll(favoriteTracks) },
            onShuffleAll = { viewModel.shuffleAll(favoriteTracks) },
            onRemoveTrack = { viewModel.toggleFavorite(it) },
            onMoveTrack = { _, _ -> },
            onDeletePlaylist = {},
            onDuplicatePlaylist = {},
            onRenamePlaylist = {},
            isFavorites = true
        )
    } else if (selectedPlaylist != null) {
        // Detailed Playlist View
        val playlist = selectedPlaylist!!
        val playlistTracks by viewModel.getPlaylistTracks(playlist.id).collectAsStateWithLifecycle(emptyList())

        PlaylistDetailView(
            playlist = playlist,
            tracks = playlistTracks,
            onBack = { selectedPlaylist = null },
            onPlayTrack = { track -> viewModel.playTrack(track, playlistTracks) },
            onPlayAll = { viewModel.playAll(playlistTracks) },
            onShuffleAll = { viewModel.shuffleAll(playlistTracks) },
            onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(playlist.id, track.uriString) },
            onMoveTrack = { fromIdx, toIdx ->
                val list = playlistTracks.map { it.uriString }.toMutableList()
                if (fromIdx in list.indices && toIdx in list.indices) {
                    val item = list.removeAt(fromIdx)
                    list.add(toIdx, item)
                    viewModel.reorderPlaylist(playlist.id, list)
                }
            },
            onDeletePlaylist = {
                viewModel.deletePlaylist(playlist.id)
                selectedPlaylist = null
            },
            onDuplicatePlaylist = {
                viewModel.duplicatePlaylist(playlist.id)
            },
            onRenamePlaylist = {
                playlistToRename = playlist
            }
        )
    } else {
        // List of all playlists
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("playlists_screen")
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.playlists_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Pinned Built-in "Favorites" Playlist Card
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable {
                                showingFavorites = true
                            }
                            .testTag("favorites_playlist_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AuraAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = AuraAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.favorites),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.tracks_count, favoriteTracks.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (favoriteTracks.isNotEmpty()) {
                                IconButton(onClick = { viewModel.playAll(favoriteTracks) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = AuraAccent)
                                }
                                IconButton(onClick = { viewModel.shuffleAll(favoriteTracks) }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = AuraAccent)
                                }
                            }
                        }
                    }
                }

                // Custom User Playlists
                items(playlists, key = { it.id }) { playlist ->
                    var showItemMenu by remember { mutableStateOf(false) }

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedPlaylist = playlist }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = AuraAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box {
                                IconButton(onClick = { showItemMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }

                                DropdownMenu(
                                    expanded = showItemMenu,
                                    onDismissRequest = { showItemMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename_playlist)) },
                                        onClick = {
                                            showItemMenu = false
                                            playlistToRename = playlist
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.duplicate_playlist)) },
                                        onClick = {
                                            showItemMenu = false
                                            viewModel.duplicatePlaylist(playlist.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.delete_playlist),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showItemMenu = false
                                            viewModel.deletePlaylist(playlist.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Create Playlist FAB
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AuraAccent,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 100.dp)
                    .testTag("create_playlist_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_playlist))
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.create_playlist),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.playlist_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameInput.isNotBlank()) {
                                    viewModel.createPlaylist(nameInput.trim())
                                    showCreateDialog = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.create_playlist))
                        }
                    }
                }
            }
        }
    }

    // Rename Playlist Dialog
    playlistToRename?.let { target ->
        var renameInput by remember { mutableStateOf(target.name) }
        Dialog(onDismissRequest = { playlistToRename = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.rename_playlist),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { playlistToRename = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (renameInput.isNotBlank()) {
                                    viewModel.renamePlaylist(target.id, renameInput.trim())
                                    playlistToRename = null
                                }
                            }
                        ) {
                            Text(stringResource(R.string.rename_playlist))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onDeletePlaylist: () -> Unit,
    onDuplicatePlaylist: () -> Unit,
    onRenamePlaylist: () -> Unit,
    isFavorites: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlist_detail_screen")
    ) {
        // Detail Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.tracks_count, tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isFavorites) Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_playlist)) },
                        onClick = { showMenu = false; onRenamePlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_playlist)) },
                        onClick = { showMenu = false; onDuplicatePlaylist() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete_playlist),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { showMenu = false; onDeletePlaylist() }
                    )
                }
            }
        }

        // Action Buttons Row (Play All, Shuffle)
        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraAccent)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.play_all))
                }

                Button(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.shuffle_all), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Track items
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.uriString }) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.albumArtUri != null) {
                                AsyncImage(
                                    model = track.albumArtUri,
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = AuraAccent, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.displayArtist} • ${track.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Reorder controls
                        if (!isFavorites && index > 0) {
                            IconButton(
                                onClick = { onMoveTrack(index, index - 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(18.dp))
                            }
                        }
                        if (!isFavorites && index < tracks.size - 1) {
                            IconButton(
                                onClick = { onMoveTrack(index, index + 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(
                            onClick = { onRemoveTrack(track) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
