package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** PCM16 spatial processing shared by the local player and playback capture. */
class SpatialPcmEngine(private val sampleRate: Int) {
    private data class Configuration(val settings: SpatialSettings, val enabled: Boolean)

    @Volatile private var configuration = Configuration(SpatialSettings(), false)
    private val alpha = exp(-2.0 * PI * 1100.0 / sampleRate).toFloat()
    private var phase = 0.0
    private var lowL = 0f
    private var lowR = 0f

    fun update(value: SpatialSettings, masterEnabled: Boolean) {
        val settings = value.sanitized()
        configuration = Configuration(settings, masterEnabled && settings.is8dEnabled)
    }

    /** Each call consumes complete mono or stereo frames and writes the same number of stereo frames. */
    fun process(source: ByteBuffer, destination: ByteBuffer, channels: Int = 2): Int {
        require(source.order() == ByteOrder.nativeOrder() &&
            destination.order() == ByteOrder.nativeOrder())
        require(channels in 1..2)
        val frames = min(source.remaining() / (channels * 2), destination.remaining() / 4)
        val config = configuration
        if (!config.enabled) {
            phase = 0.0
            lowL = 0f
            lowR = 0f
            if (channels == 2 && frames * 4 == source.remaining()) {
                destination.put(source)
            } else {
                repeat(frames) {
                    val left = source.short
                    val right = if (channels == 2) source.short else left
                    destination.putShort(left).putShort(right)
                }
            }
            return frames * 4
        }

        val settings = config.settings
        val leftVolume = settings.leftPercent / 100f
        val rightVolume = settings.rightPercent / 100f
        val leftTone = Math.pow(2.0, settings.leftTone / 100.0).toFloat()
        val rightTone = Math.pow(2.0, settings.rightTone / 100.0).toFloat()
        val dynamic = settings.mode == "dynamic"
        val staticPan = if (settings.mode == "static") settings.staticPan * settings.depth / 10000f else 0f
        val depth = settings.depth / 100f
        val cycle = 2.0 * PI
        val delta = cycle / (settings.periodSeconds * sampleRate)

        repeat(frames) {
            val left = source.short.toFloat()
            val right = if (channels == 2) source.short.toFloat() else left
            val pan = if (dynamic) {
                val value = sin(phase).toFloat() * depth
                phase += delta
                if (phase >= cycle) phase -= cycle
                value
            } else staticPan
            // Balance the existing channels to preserve stereo separation and out-of-phase audio.
            val pannedLeft = left * (1f - max(0f, pan))
            val pannedRight = right * (1f + min(0f, pan))
            lowL = lowL * alpha + pannedLeft * (1f - alpha)
            lowR = lowR * alpha + pannedRight * (1f - alpha)
            val processedLeft = (pannedLeft + (pannedLeft - lowL) * (leftTone - 1f)) * leftVolume
            val processedRight = (pannedRight + (pannedRight - lowR) * (rightTone - 1f)) * rightVolume
            destination.putShort(processedLeft.toInt().coerceIn(-32768, 32767).toShort())
            destination.putShort(processedRight.toInt().coerceIn(-32768, 32767).toShort())
        }
        return frames * 4
    }
}
