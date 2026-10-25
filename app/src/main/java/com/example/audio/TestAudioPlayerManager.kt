package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TestPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val currentTitle: String = "Harmonic Reference Track",
    val audioSessionId: Int = 0,
    val isReady: Boolean = false
)

@OptIn(UnstableApi::class)
class TestAudioPlayerManager(
    private val context: Context,
    private val onSessionReady: (Int) -> Unit
) {
    private val TAG = "TestAudioPlayer"
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(TestPlayerState())
    val state: StateFlow<TestPlayerState> = _state.asStateFlow()

    // Do not initialize ExoPlayer or synthesize a test track when the app first opens.
    // Some OEM audio stacks fail at eager session creation, even when the user never plays audio.
    private fun ensurePlayer(): ExoPlayer? {
        if (exoPlayer == null) initPlayer()
        return exoPlayer
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            startProgressTracking()
                        } else {
                            progressJob?.cancel()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val duration = if (duration > 0) duration else 0
                        val isReady = playbackState == Player.STATE_READY
                        val sessionId = audioSessionId
                        _state.value = _state.value.copy(
                            durationMs = duration,
                            isReady = isReady,
                            audioSessionId = sessionId
                        )
                        if (sessionId > 0) {
                            onSessionReady(sessionId)
                        }
                    }
                })
            }

            // No media is loaded until Play, Reference Signal, or Pick File is used.
            // This keeps startup independent of Media3 and device-specific audio effects.
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ExoPlayer: ${e.message}", e)
        }
    }

    fun play() {
        val player = ensurePlayer() ?: return
        if (player.mediaItemCount == 0) {
            loadReferenceTrack()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    fun playCustomUri(uri: Uri, fileName: String) {
        try {
            val mediaItem = MediaItem.fromUri(uri)
            val player = ensurePlayer() ?: return
            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            val sessionId = player.audioSessionId
            _state.value = _state.value.copy(
                currentTitle = fileName,
                audioSessionId = sessionId,
                currentPositionMs = 0
            )
            if (sessionId > 0) {
                onSessionReady(sessionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom uri: ${e.message}", e)
        }
    }

    fun loadReferenceTrack() {
        if (ensurePlayer() == null) return
        scope.launch {
            try {
                val sampleFile = withContext(Dispatchers.IO) { getOrCreateReferenceWav() }
                if (sampleFile.exists() && exoPlayer != null) {
                    playCustomUri(Uri.fromFile(sampleFile), "Harmonic Reference Track")
                }
            } catch (e: Exception) {
                // Coroutine exceptions would otherwise kill the app process on some devices.
                Log.e(TAG, "Unable to initialize reference audio", e)
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _state.value = _state.value.copy(
                        currentPositionMs = player.currentPosition,
                        durationMs = if (player.duration > 0) player.duration else _state.value.durationMs
                    )
                }
                delay(200)
            }
        }
    }

    /**
     * Synthesizes a clean 10-second rich musical test tone (low sub-bass + warm mid chords + high harmonics)
     * as a standard PCM 16-bit WAV file so users can hear real equalizer changes instantly offline.
     */
    private fun getOrCreateReferenceWav(): File {
        val file = File(context.cacheDir, "aura_harmonic_reference.wav")
        if (file.exists() && file.length() > 1000) return file

        try {
            val sampleRate = 44100
            val durationSec = 10
            val numSamples = sampleRate * durationSec
            val buffer = ShortArray(numSamples)

            val fBass = 80.0       // Bass band
            val fLowMid = 220.0    // Low-mid band
            val fMid = 440.0       // Vocal clarity / mid
            val fHighMid = 1800.0  // Treble presence
            val fTreble = 6000.0   // Sparkle/highs

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Subtle rhythmic beat envelope (every 0.5s)
                val beatEnv = 0.7 + 0.3 * Math.sin(2 * Math.PI * 2.0 * t)

                val sBass = Math.sin(2 * Math.PI * fBass * t) * 0.45
                val sLowMid = Math.sin(2 * Math.PI * fLowMid * t) * 0.25
                val sMid = Math.sin(2 * Math.PI * fMid * t) * 0.2
                val sHighMid = Math.sin(2 * Math.PI * fHighMid * t) * 0.15
                val sTreble = Math.sin(2 * Math.PI * fTreble * t) * 0.1

                val total = (sBass + sLowMid + sMid + sHighMid + sTreble) * beatEnv * Short.MAX_VALUE * 0.8
                buffer[i] = total.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            FileOutputStream(file).use { fos ->
                val byteBuffer = ByteBuffer.allocate(44 + numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
                val totalDataLen = numSamples * 2 + 36
                val totalAudioLen = numSamples * 2

                // WAV Header
                byteBuffer.put("RIFF".toByteArray())
                byteBuffer.putInt(totalDataLen)
                byteBuffer.put("WAVE".toByteArray())
                byteBuffer.put("fmt ".toByteArray())
                byteBuffer.putInt(16) // Subchunk1Size (16 for PCM)
                byteBuffer.putShort(1.toShort()) // AudioFormat (1 for PCM)
                byteBuffer.putShort(1.toShort()) // NumChannels (1 mono)
                byteBuffer.putInt(sampleRate) // SampleRate
                byteBuffer.putInt(sampleRate * 2) // ByteRate
                byteBuffer.putShort(2.toShort()) // BlockAlign
                byteBuffer.putShort(16.toShort()) // BitsPerSample
                byteBuffer.put("data".toByteArray())
                byteBuffer.putInt(totalAudioLen)

                for (s in buffer) {
                    byteBuffer.putShort(s)
                }

                fos.write(byteBuffer.array())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating reference wav", e)
        }
        return file
    }

    fun release() {
        progressJob?.cancel()
        scope.cancel()
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
    }
}
