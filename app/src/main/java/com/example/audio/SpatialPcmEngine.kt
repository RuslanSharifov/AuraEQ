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
 * Audio-thread rules:
 *  - [process] takes no lock. A UI-thread [reset] only sets a flag that the next
 *    [process] call consumes, so a slider change can never block playback.
 *  - The LFO uses a precomputed sine table instead of Math.sin per sample.
 *  - Mode strings are compared once per call, not once per sample.
 */
class SpatialPcmEngine(private val sampleRate: Int) {
    @Volatile private var settings = SpatialSettings()
    @Volatile private var enabled = false
    @Volatile private var resetPending = false

    private var phase = 0.0
    private var lowL = 0f
    private var lowR = 0f

    fun update(value: SpatialSettings, masterEnabled: Boolean) {
        settings = value.sanitized()
        enabled = masterEnabled && settings.is8dEnabled
        if (!enabled) reset()
    }

    /** Safe to call from any thread. Takes effect at the next [process] call. */
    fun reset() {
        resetPending = true
    }

    fun process(source: ByteBuffer, destination: ByteBuffer): Int {
        require(source.order() == ByteOrder.nativeOrder() &&
            destination.order() == ByteOrder.nativeOrder())
        if (resetPending) {
            resetPending = false
            phase = 0.0
            lowL = 0f
            lowR = 0f
        }
        val frames = min(source.remaining(), destination.remaining()) / 4
        val config = settings
        val active = enabled
        val leftVolume = config.leftPercent / 100f
        val rightVolume = config.rightPercent / 100f
        val leftTone = if (active) Math.pow(2.0, config.leftTone / 100.0).toFloat() else 1f
        val rightTone = if (active) Math.pow(2.0, config.rightTone / 100.0).toFloat() else 1f
        val alpha = exp(-2.0 * PI * 1100.0 / sampleRate).toFloat()
        val oneMinusAlpha = 1f - alpha

        val isStatic = config.mode == "static"
        val isDynamic = config.mode == "dynamic"
        val staticPan = config.staticPan * config.depth / 10000f
        val depthScale = config.depth / 100f
        val delta = 2.0 * PI / (config.periodSeconds * sampleRate)

        for (i in 0 until frames) {
            val left = source.short.toFloat()
            val right = source.short.toFloat()
            var processedLeft = left
            var processedRight = right
            if (active) {
                if (isStatic || isDynamic) {
                    val lfo = if (isDynamic) lfoSample(phase) else 0f
                    val pan = (if (isStatic) staticPan else lfo * depthScale).coerceIn(-1f, 1f)
                    if (isDynamic) phase = (phase + delta) % (2 * PI)
                    val center = (left + right) * 0.5f
                    processedLeft = center * (1f - max(0f, pan))
                    processedRight = center * (1f + min(0f, pan))
                }
                lowL = lowL * alpha + processedLeft * oneMinusAlpha
                lowR = lowR * alpha + processedRight * oneMinusAlpha
                processedLeft = (lowL + (processedLeft - lowL) * leftTone) * leftVolume
                processedRight = (lowR + (processedRight - lowR) * rightTone) * rightVolume
            }
            destination.putShort(processedLeft.toInt().coerceIn(-32768, 32767).toShort())
            destination.putShort(processedRight.toInt().coerceIn(-32768, 32767).toShort())
        }
        return frames * 4
    }

    private companion object {
        const val TABLE_SIZE = 4096
        const val TABLE_MASK = TABLE_SIZE - 1
        val SIN_TABLE = FloatArray(TABLE_SIZE) { i -> sin(2.0 * PI * i / TABLE_SIZE).toFloat() }
        val TABLE_SCALE = TABLE_SIZE / (2.0 * PI)

        /** [phase] is always in [0, 2π), so the index is non-negative. */
        fun lfoSample(phase: Double): Float = SIN_TABLE[(phase * TABLE_SCALE).toInt() and TABLE_MASK]
    }
}
