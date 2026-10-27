package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.playback.PlaybackState
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuraAccent
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel

enum class LibrarySortBy {
    TITLE, ARTIST, ALBUM, DURATION, DATE_ADDED, SIZE, RECENT
}

@Composable
fun LibraryScreen(
    state: AuraUiState,
    playbackState: PlaybackState,
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle(emptyList())
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle(emptyList())
    val playlists by viewModel.playlists.collectAsStateWithLifecycle(emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(LibrarySortBy.TITLE) }
    var isAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    var trackForActionMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
        }
        if (uris.isNotEmpty()) viewModel.importAudioFiles(uris) { count ->
            Toast.makeText(context, "$count musiqi kitabxanaya əlavə edildi", Toast.LENGTH_LONG).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.addFolder(treeUri)
        }
    }

    val tabs = listOf(
        stringResource(R.string.tab_all_songs),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_folders),
        stringResource(R.string.tab_favorites)
    )

    // Filter and sort tracks
    val filteredTracks = remember(allTracks, searchQuery, sortBy, isAscending, selectedTabIndex) {
        val baseList = if (selectedTabIndex == 4) favoriteTracks else allTracks
        val searched = if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }

        val sorted = when (sortBy) {
            LibrarySortBy.TITLE -> searched.sortedBy { it.title.lowercase() }
            LibrarySortBy.ARTIST -> searched.sortedBy { it.artist.lowercase() }
            LibrarySortBy.ALBUM -> searched.sortedBy { it.album.lowercase() }
            LibrarySortBy.DURATION -> searched.sortedBy { it.durationMs }
            LibrarySortBy.DATE_ADDED -> searched.sortedBy { it.dateAdded }
            LibrarySortBy.SIZE -> searched.sortedBy { it.sizeBytes }
            LibrarySortBy.RECENT -> searched.sortedBy { it.lastPlayed }
        }

        if (isAscending) sorted else sorted.reversed()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Search & Sort Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("library_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = stringResource(R.string.sort_by),
                        tint = AuraAccent
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_title)) },
                        onClick = { sortBy = LibrarySortBy.TITLE; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_artist)) },
                        onClick = { sortBy = LibrarySortBy.ARTIST; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_album)) },
                        onClick = { sortBy = LibrarySortBy.ALBUM; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_duration)) },
                        onClick = { sortBy = LibrarySortBy.DURATION; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_date_added)) },
                        onClick = { sortBy = LibrarySortBy.DATE_ADDED; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_size)) },
                        onClick = { sortBy = LibrarySortBy.SIZE; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_recent)) },
                        onClick = { sortBy = LibrarySortBy.RECENT; showSortMenu = false }
                    )
                }
            }

            // Ascending / Descending Toggle
            IconButton(
                onClick = { isAscending = !isAscending },
                modifier = Modifier.testTag("library_order_toggle")
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isAscending) "Ascending" else "Descending",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = AuraAccent
                    )
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedTabIndex == index) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        // Content Area depending on tab
        when (selectedTabIndex) {
            0, 4 -> {
                // All Songs / Favorites
                TrackListView(
                    tracks = filteredTracks,
                    currentPlayingUri = playbackState.currentTrack?.uriString,
                    isPlaying = playbackState.isPlaying,
                    onTrackClick = { track -> viewModel.playTrack(track, filteredTracks) },
                    onPlayAll = { viewModel.playAll(filteredTracks) },
                    onShuffleAll = { viewModel.shuffleAll(filteredTracks) },
                    onMoreClick = { track -> trackForActionMenu = track },
                    onAddFolder = { audioPicker.launch(arrayOf("audio/*")) }
                )
            }
            1 -> {
                // Artists
                val artists = remember(filteredTracks) {
                    filteredTracks.groupBy { it.displayArtist }
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(artists.keys.toList()) { artistName ->
                        val artistTracks = artists[artistName] ?: emptyList()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playAll(artistTracks) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AuraAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AuraAccent)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.tracks_count, artistTracks.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                // Albums
                val albums = remember(filteredTracks) {
                    filteredTracks.groupBy { it.displayAlbum }
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums.keys.toList()) { albumName ->
                        val albumTracks = albums[albumName] ?: emptyList()
                        val cover = albumTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playAll(albumTracks) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cover != null) {
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Album, contentDescription = null, tint = AuraAccent)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = albumName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${albumTracks.firstOrNull()?.displayArtist ?: ""} • ${albumTracks.size} mahnı",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            3 -> {
                // Folders Management View
                FoldersView(
                    selectedFolderUris = state.selectedFolderUris,
                    allTracks = allTracks,
                    onAddFolder = { audioPicker.launch(arrayOf("audio/*")) },
                    onRemoveFolder = { viewModel.removeFolder(it) },
                    onRescanLibrary = { viewModel.rescanLibrary() },
                    onPlayFolder = { fUri ->
                        val fTracks = allTracks.filter { it.folderUri == fUri }
                        viewModel.playAll(fTracks)
                    },
                    onShuffleFolder = { fUri ->
                        val fTracks = allTracks.filter { it.folderUri == fUri }
                        viewModel.shuffleAll(fTracks)
                    }
                )
            }
        }
    }

    // Action Menu Dialog for selected track
    trackForActionMenu?.let { track ->
        var showTrackMenu by remember { mutableStateOf(true) }
        if (showTrackMenu) {
            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = {
                    showTrackMenu = false
                    trackForActionMenu = null
                }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.play_next)) },
                    onClick = {
                        viewModel.playNext(track)
                        showTrackMenu = false
                        trackForActionMenu = null
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_queue)) },
                    onClick = {
                        viewModel.addToQueue(track)
                        showTrackMenu = false
                        trackForActionMenu = null
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_playlist)) },
                    onClick = {
                        showTrackMenu = false
                        showAddToPlaylistDialog = true
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showTrackMenu = false
                        showDeleteConfirmDialog = true
                    }
                )
            }
        }
    }

    // Add To Playlist Dialog
    if (showAddToPlaylistDialog && trackForActionMenu != null) {
        val targetTrack = trackForActionMenu!!
        AddToPlaylistDialog(
            track = targetTrack,
            playlists = playlists,
            onAddToPlaylist = { playlistId ->
                viewModel.addTracksToPlaylist(playlistId, listOf(targetTrack.uriString))
                showAddToPlaylistDialog = false
                trackForActionMenu = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name) { newId ->
                    viewModel.addTracksToPlaylist(newId, listOf(targetTrack.uriString))
                }
                showAddToPlaylistDialog = false
                trackForActionMenu = null
            },
            onDismiss = {
                showAddToPlaylistDialog = false
                trackForActionMenu = null
            }
        )
    }

    // Delete File Confirmation Dialog
    if (showDeleteConfirmDialog && trackForActionMenu != null) {
        val targetTrack = trackForActionMenu!!
        DeleteConfirmDialog(
            track = targetTrack,
            onConfirmDelete = {
                showDeleteConfirmDialog = false
                viewModel.deleteTrackFromDevice(targetTrack) {
                    trackForActionMenu = null
                }
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                trackForActionMenu = null
            }
        )
    }
}

@Composable
private fun TrackListView(
    tracks: List<Track>,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onMoreClick: (Track) -> Unit,
    onAddFolder: () -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = AuraAccent,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_tracks_added_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraAccent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_folder))
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tracks_count, tracks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row {
                        IconButton(onClick = onPlayAll) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = AuraAccent)
                        }
                        IconButton(onClick = onShuffleAll) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle All", tint = AuraAccent)
                        }
                    }
                }
            }

            items(tracks, key = { it.uriString }) { track ->
                val isCurrent = track.uriString == currentPlayingUri

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCurrent) AuraAccent.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                        .clickable { onTrackClick(track) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (isCurrent) AuraAccent else MaterialTheme.colorScheme.onSurface,
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

                    IconButton(onClick = { onMoreClick(track) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersView(
    selectedFolderUris: Set<String>,
    allTracks: List<Track>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescanLibrary: () -> Unit,
    onPlayFolder: (String) -> Unit,
    onShuffleFolder: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAddFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraAccent),
                    modifier = Modifier.testTag("library_add_folder_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_folder))
                }

                IconButton(onClick = onRescanLibrary) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AuraAccent)
                }
            }
        }

        if (selectedFolderUris.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.add_folder_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(selectedFolderUris.toList()) { uriString ->
                val folderTracks = allTracks.filter { it.folderUri == uriString }
                val folderName = folderTracks.firstOrNull()?.folderName ?: Uri.parse(uriString).lastPathSegment ?: "Folder"

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = AuraAccent, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.tracks_count, folderTracks.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemoveFolder(uriString) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (folderTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row {
                                Button(
                                    onClick = { onPlayFolder(uriString) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraAccent)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.play_all), fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onShuffleFolder(uriString) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.shuffle_all), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
