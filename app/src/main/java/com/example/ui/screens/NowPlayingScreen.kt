package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Track
import com.example.playback.PlaybackState
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.QueueDialog
import com.example.ui.theme.AuraAccent
import com.example.ui.theme.AuraSoftCyan
import com.example.viewmodel.AuraViewModel
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: com.example.viewmodel.AuraUiState,
    playbackState: PlaybackState,
    viewModel: AuraViewModel,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpen8D: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val track = playbackState.currentTrack ?: return
    var showMenu by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var userSeekingProgress by remember { mutableFloatStateOf(-1f) }

    val activeProgress = if (userSeekingProgress >= 0f) userSeekingProgress else playbackState.progress

    // Background gradient
    val isDark = state.themeMode == "dark" || (state.themeMode == "system" && androidx.compose.foundation.isSystemInDarkTheme())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(
                        Color(0xFF242C42),
                        Color(0xFF141926),
                        Color(0xFF0F121C)
                    ) else listOf(
                        Color(0xFFE9ECF8),
                        Color(0xFFDFE4F2),
                        Color(0xFFD6DCED)
                    )
                )
            )
            .statusBarsPadding()
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("now_playing_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.now_playing).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AuraAccent
                    )
                    Text(
                        text = track.folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("now_playing_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_to_playlist)) },
                            onClick = {
                                showMenu = false
                                showAddToPlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_equalizer)) },
                            onClick = {
                                showMenu = false
                                onOpenEqualizer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_8d)) },
                            onClick = {
                                showMenu = false
                                onOpen8D()
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
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Album Artwork with Glassmorphism Border & Glow
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = AuraAccent.copy(alpha = 0.3f),
                        spotColor = AuraAccent.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AuraAccent.copy(alpha = 0.6f),
                                Color.Transparent,
                                AuraSoftCyan.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AuraAccent,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track title & Artist & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.displayArtist} • ${track.displayAlbum}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(track) },
                    modifier = Modifier.testTag("now_playing_favorite")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio-reactive visualizer bars (proportional to playback progress and EQ profile bands)
            NowPlayingVisualizer(
                isPlaying = playbackState.isPlaying,
                progress = activeProgress,
                bands = state.bands,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Slider
            Slider(
                value = activeProgress,
                onValueChange = { userSeekingProgress = it },
                onValueChangeFinished = {
                    val targetMs = (userSeekingProgress * playbackState.durationMs).toLong()
                    viewModel.seekTo(targetMs)
                    userSeekingProgress = -1f
                },
                colors = SliderDefaults.colors(
                    thumbColor = AuraAccent,
                    activeTrackColor = AuraAccent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = playbackState.formattedCurrentTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = playbackState.formattedRemainingTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("control_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("control_previous")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Main Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AuraAccent,
                                    AuraAccent.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("control_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("control_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRepeatMode() },
                    modifier = Modifier.testTag("control_repeat")
                ) {
                    val repeatIcon = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val isRepeatActive = playbackState.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom action shortcuts: Equalizer, 8D Audio, Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Equalizer button
                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier.testTag("shortcut_equalizer")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = stringResource(R.string.open_equalizer),
                            tint = if (state.isEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "EQ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (state.isEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 8D Audio button
                IconButton(
                    onClick = onOpen8D,
                    modifier = Modifier.testTag("shortcut_8d")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SpatialAudio,
                            contentDescription = stringResource(R.string.open_8d),
                            tint = if (state.spatialSettings.is8dEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "8D",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (state.spatialSettings.is8dEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Queue button
                IconButton(
                    onClick = { showQueueDialog = true },
                    modifier = Modifier.testTag("shortcut_queue")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = stringResource(R.string.queue_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "${playbackState.queue.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Queue Dialog
    if (showQueueDialog) {
        QueueDialog(
            queue = playbackState.queue,
            currentIndex = playbackState.currentQueueIndex,
            onTrackSelect = { idx ->
                val q = playbackState.queue
                if (idx in q.indices) {
                    viewModel.playTrack(q[idx], q)
                }
            },
            onRemoveFromQueue = { idx -> viewModel.removeFromQueue(idx) },
            onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) },
            onClearQueue = { viewModel.clearQueue() },
            onDismiss = { showQueueDialog = false }
        )
    }

    // Delete File Confirmation Dialog
    if (showDeleteDialog) {
        DeleteConfirmDialog(
            track = track,
            onConfirmDelete = {
                showDeleteDialog = false
                viewModel.deleteTrackFromDevice(track) {
                    // Handled
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Add To Playlist Dialog
    if (showAddToPlaylistDialog) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle(emptyList())
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onAddToPlaylist = { pId ->
                viewModel.addTracksToPlaylist(pId, listOf(track.uriString))
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name) { newId ->
                    viewModel.addTracksToPlaylist(newId, listOf(track.uriString))
                }
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }
}

@Composable
private fun NowPlayingVisualizer(
    isPlaying: Boolean,
    progress: Float,
    bands: List<com.example.data.model.EqualizerBand>,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val accentColor = AuraAccent

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val maxHeight = size.height
        val barWidth = (totalWidth / barCount) * 0.65f
        val spacing = (totalWidth / barCount) * 0.35f

        for (i in 0 until barCount) {
            val bandIdx = (i * bands.size / barCount).coerceIn(0, (bands.size - 1).coerceAtLeast(0))
            val bandGain = bands.getOrNull(bandIdx)?.gainDb ?: 0f
            val baseHeight = if (isPlaying) {
                val phase = progress * 100f + i * 0.5f
                val wave = ((sin(phase.toDouble()) + 1.0) / 2.0).toFloat()
                val boost = (bandGain + 15f) / 30f
                (wave * 0.7f + boost * 0.3f).coerceIn(0.15f, 1.0f) * maxHeight
            } else {
                maxHeight * 0.15f
            }

            val x = i * (barWidth + spacing)
            val y = maxHeight - baseHeight

            drawRoundRect(
                color = accentColor.copy(alpha = if (isPlaying) 0.85f else 0.35f),
                topLeft = Offset(x, y),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
