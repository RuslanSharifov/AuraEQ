package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/** Official Spotify embed. Does not extract audio or bypass Spotify playback rules. */
@Composable
fun SpotifyEmbedDialog(playlistId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Spotify playlist", style = MaterialTheme.typography.titleMedium)
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(embedUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(375.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://open.spotify.com/playlist/$playlistId")))
                    }) { Text("Spotify-də aç") }
                    TextButton(onClick = onDismiss) { Text("Bağla") }
                }
            }
        }
    }
}
