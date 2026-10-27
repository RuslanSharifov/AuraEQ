package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.PlaybackState
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuraAccent
import com.example.viewmodel.AuraUiState
import com.example.viewmodel.AuraViewModel

/**
 * A focused landing dashboard. Browse tracks, playlists and EQ in their own screens,
 * rather than rendering every library section on Home.
 */
@Composable
fun HomeScreen(
    state: AuraUiState,
    playbackState: PlaybackState,
    viewModel: AuraViewModel,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .padding(bottom = 125.dp).testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Musiqin sənin ritmində", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Dinlə, səsi tənzimlə və kitabxananı idarə et.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenNowPlaying)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                        .background(AuraAccent.copy(alpha = .15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = AuraAccent,
                        modifier = Modifier.size(30.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("İNDİ SƏSLƏNİR", style = MaterialTheme.typography.labelSmall,
                        color = AuraAccent, fontWeight = FontWeight.Bold)
                    Text(playbackState.currentTrack?.title ?: "Musiqini seç",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(playbackState.currentTrack?.artist ?: "Kitabxanadan mahnı aç",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = "Pleyeri aç", tint = AuraAccent)
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToEqualizer)
                .testTag("home_eq_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Equalizer, contentDescription = null,
                    tint = AuraAccent, modifier = Modifier.size(32.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ekvalayzer", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(state.activePresetName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (state.isEnabled) "Aktiv" else "Söndürülüb",
                    color = if (state.isEnabled) AuraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
            }
        }

        Text("Bölmələr", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardDestination(
                title = "Kitabxana",
                subtitle = "Mahnılar və qovluqlar",
                icon = Icons.Default.FolderOpen,
                onClick = onNavigateToLibrary,
                modifier = Modifier.weight(1f)
            )
            DashboardDestination(
                title = "Playlistlər",
                subtitle = "Siyahılar və Spotify",
                icon = Icons.Default.QueueMusic,
                onClick = onNavigateToPlaylists,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.selectedFolderUris.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Musiqilərini əlavə et", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Telefonundakı MP3 və digər musiqi fayllarını kitabxanaya əlavə et.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = onNavigateToLibrary,
                        modifier = Modifier.testTag("home_add_folder_button")
                    ) { Text("Kitabxanaya keç") }
                }
            }
        }
    }
}

@Composable
private fun DashboardDestination(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(AuraAccent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AuraAccent)
            }
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2)
        }
    }
}
