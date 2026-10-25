package com.example.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.example.MainActivity
import com.example.AuraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Playback capture is permitted only for apps that explicitly allow capture.
 * It is a SECOND output and cannot mute/replace the source player's output.
 */
object External8dCaptureState {
    val status: StateFlow<String> get() = _status
    private val _status = MutableStateFlow("stopped")
    internal fun set(value: String) { _status.value = value }
}

class External8dCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.audio.CAPTURE_START"
        const val ACTION_STOP = "com.example.audio.CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "aura_capture"
        private const val NOTIFICATION_ID = 2208
        @Volatile var current: External8dCaptureService? = null
            private set

        fun stop(context: Context) {
            context.startService(Intent(context, External8dCaptureService::class.java).setAction(ACTION_STOP))
        }
    }

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var playback: AudioTrack? = null
    private var projection: MediaProjection? = null
    private var engine = SpatialPcmEngine(48000)
    private var settings = SpatialSettings()
    private var masterEnabled = false
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    fun updateSettings(value: SpatialSettings, enabled: Boolean) {
        settings = value.sanitized()
        masterEnabled = enabled
        engine.update(settings, enabled)
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        val prefs = (application as AuraApplication).preferencesRepository
        settingsScope.launch {
            combine(prefs.spatialSettingsFlow, prefs.isEnabledFlow) { spatial, enabled ->
                spatial to enabled
            }.collect { (spatial, enabled) -> updateSettings(spatial, enabled) }
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "8D audio capture", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            External8dCaptureState.set("unsupported")
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.get()) return START_NOT_STICKY

        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AuraEQ · Xarici 8D capture")
            .setContentText("Yalnız capture icazəsi verən tətbiqlər · Dayandırmaq üçün toxun")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .addAction(Notification.Action.Builder(
                null, "Dayandır",
                PendingIntent.getService(
                    this, 1, Intent(this, External8dCaptureService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(NOTIFICATION_ID, notification)
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
            if (data == null) throw IllegalArgumentException("Missing projection consent")
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val token = manager.getMediaProjection(resultCode, data)
                ?: throw SecurityException("Projection was rejected")
            projection = token
            token.registerCallback(projectionCallback, null)
            startPipeline(token)
        } catch (e: Exception) {
            Log.e("AuraExternal8D", "Capture unavailable", e)
            External8dCaptureState.set("error")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPipeline(token: MediaProjection) {
        val rate = 48000
        val mask = AudioFormat.CHANNEL_IN_STEREO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(mask)
            .build()
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(token)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()
        val minimumInput = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        require(minimumInput > 0)
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimumInput, rate / 5 * 4))
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
        require(record.state == AudioRecord.STATE_INITIALIZED)
        recorder = record

        val minOutput = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minOutput > 0)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .build()
        val outputFormat = AudioFormat.Builder()
            .setSampleRate(rate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(outputFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minOutput * 2, rate / 10 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        require(track.state == AudioTrack.STATE_INITIALIZED)
        playback = track
        engine = SpatialPcmEngine(rate)
        engine.update(settings, masterEnabled)

        // Blocking read/write is deliberately isolated from the main thread.
        // No audio is stored, networked or recorded to disk.
        running.set(true)
        External8dCaptureState.set("running")
        worker = thread(name = "AuraExternal8DAudio", priority = Thread.MAX_PRIORITY) {
            val src = ByteBuffer.allocateDirect(1920 * 4).order(ByteOrder.nativeOrder())
            val dst = ByteBuffer.allocateDirect(1920 * 4).order(ByteOrder.nativeOrder())
            try {
                record.startRecording()
                track.play()
                while (running.get()) {
                    src.clear()
                    val bytes = record.read(src, src.capacity(), AudioRecord.READ_BLOCKING)
                    if (bytes <= 0) continue
                    src.position(0)
                    src.limit(bytes - bytes % 4)
                    dst.clear()
                    val processed = engine.process(src, dst)
                    dst.flip()
                    var sent = 0
                    while (sent < processed && running.get()) {
                        val written = track.write(dst, processed - sent, AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) break
                        sent += written
                    }
                }
            } catch (e: Exception) {
                Log.w("AuraExternal8D", "Audio streaming terminated", e)
                External8dCaptureState.set("error")
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        settingsScope.cancel()
        running.set(false)
        val r = recorder
        val t = playback
        recorder = null
        playback = null
        runCatching { r?.stop() }
        runCatching { t?.pause() }
        runCatching { t?.flush() }
        runCatching { r?.release() }
        runCatching { t?.release() }
        worker?.let { if (it != Thread.currentThread()) runCatching { it.join(250) } }
        worker = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null
        if (current === this) current = null
        External8dCaptureState.set("stopped")
        super.onDestroy()
    }
}
