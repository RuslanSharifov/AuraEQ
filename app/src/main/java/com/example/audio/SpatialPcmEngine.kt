package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Platform-neutral 16-bit interleaved stereo PCM processor, shared by Media3
 * and the user-consented external audio playback capture monitor.
 *
 * The input and output buffers must have native byte order. Each call processes
 * exactly complete stereo frames and never changes their count.
 */
class SpatialPcmEngine(private val sampleRate: Int) {
    @Volatile private var settings = SpatialSettings()
    @Volatile private var enabled = false
    private var phase = 0.0
    private var lowL = 0f
    private var lowR = 0f

    fun update(value: SpatialSettings, masterEnabled: Boolean) {
        settings = value.sanitized()
        enabled = masterEnabled && settings.is8dEnabled
        if (!enabled) reset()
    }

    @Synchronized fun reset() {
        phase = 0.0
        lowL = 0f
        lowR = 0f
    }

    @Synchronized fun process(source: ByteBuffer, destination: ByteBuffer): Int {
        require(source.order() == ByteOrder.nativeOrder() &&
            destination.order() == ByteOrder.nativeOrder())
        val frames = min(source.remaining(), destination.remaining()) / 4
        val config = settings
        val active = enabled
        val leftVolume = config.leftPercent / 100f
        val rightVolume = config.rightPercent / 100f
        val leftTone = if (active) Math.pow(2.0, config.leftTone / 100.0).toFloat() else 1f
        val rightTone = if (active) Math.pow(2.0, config.rightTone / 100.0).toFloat() else 1f
        val alpha = exp(-2.0 * PI * 1100.0 / sampleRate).toFloat()
        val delta = 2.0 * PI / (config.periodSeconds * sampleRate)
        for (i in 0 until frames) {
            val left = source.short.toFloat()
            val right = source.short.toFloat()
            var processedLeft = left
            var processedRight = right
            if (active) {
                if (config.mode != "off") {
                    val pan = if (config.mode == "static") {
                        config.staticPan * config.depth / 10000f
                    } else {
                        sin(phase).toFloat() * (config.depth / 100f)
                    }.coerceIn(-1f, 1f)
                    if (config.mode == "dynamic") phase = (phase + delta) % (2 * PI)
                    val center = (left + right) * 0.5f
                    processedLeft = center * (1f - max(0f, pan))
                    processedRight = center * (1f + min(0f, pan))
                }
                lowL = lowL * alpha + processedLeft * (1f - alpha)
                lowR = lowR * alpha + processedRight * (1f - alpha)
                processedLeft = (lowL + (processedLeft - lowL) * leftTone) * leftVolume
                processedRight = (lowR + (processedRight - lowR) * rightTone) * rightVolume
            }
            destination.putShort(processedLeft.toInt().coerceIn(-32768, 32767).toShort())
            destination.putShort(processedRight.toInt().coerceIn(-32768, 32767).toShort())
        }
        return frames * 4
    }
}
