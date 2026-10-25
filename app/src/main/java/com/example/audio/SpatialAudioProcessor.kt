package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Processed locally on PCM from AuraEQ's own player, not other apps or system-wide audio. */
data class SpatialSettings(
    val mode: String = "off", // off, static, dynamic
    val leftPercent: Int = 100,
    val rightPercent: Int = 100,
    val leftTone: Int = 0, // -100..100, treble shading
    val rightTone: Int = 0,
    val staticPan: Int = 0, // -100 left, +100 right
    val depth: Int = 100, // 0..100
    val periodSeconds: Float = 8f // 2..30 seconds for a full left/right cycle
) {
    fun sanitized() = copy(
        mode = if (mode in listOf("off", "static", "dynamic")) mode else "off",
        leftPercent = leftPercent.coerceIn(0, 100),
        rightPercent = rightPercent.coerceIn(0, 100),
        leftTone = leftTone.coerceIn(-100, 100),
        rightTone = rightTone.coerceIn(-100, 100),
        staticPan = staticPan.coerceIn(-100, 100),
        depth = depth.coerceIn(0, 100),
        periodSeconds = periodSeconds.coerceIn(2f, 30f)
    )
}

/**
 * Stereo PCM16 processing. Uses bounded output buffers, preserves playback duration
 * and never amplifies above full-scale. For stereo 8D, a center signal is panned
 * between channels; with "off" the original L/R separation is preserved.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {
    @Volatile private var active = false
    @Volatile private var options = SpatialSettings()
    private var channels = 2
    private var sampleRate = 44100
    private var phase = 0.0
    private var lowLeft = 0f
    private var lowRight = 0f

    fun update(settings: SpatialSettings, enabled: Boolean) {
        options = settings.sanitized()
        active = enabled
        if (!enabled) clearState()
    }

    fun clearState() {
        phase = 0.0
        lowLeft = 0f
        lowRight = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2) {
            // Unsupported source formats remain untouched.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() = clearState()
    override fun onReset() = clearState()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = channels * 2
        val frames = inputBuffer.remaining() / bytesPerFrame
        val out = replaceOutputBuffer(frames * 4)
        val state = options
        val processing = active
        val leftVolume = state.leftPercent / 100f
        val rightVolume = state.rightPercent / 100f
        val toneLeft = if (processing) Math.pow(2.0, state.leftTone / 100.0).toFloat() else 1f
        val toneRight = if (processing) Math.pow(2.0, state.rightTone / 100.0).toFloat() else 1f
        val alpha = exp(-2.0 * PI * 1100.0 / sampleRate).toFloat()
        val phaseDelta = 2.0 * PI / (state.periodSeconds * sampleRate)
        // Calculate pan once every 32 frames, then interpolate to avoid per-sample trig.
        var panFrom = 0f
        var panTo = 0f
        for (frame in 0 until frames) {
            val left = inputBuffer.short.toFloat()
            val right = if (channels == 2) inputBuffer.short.toFloat() else left
            var outputLeft = left
            var outputRight = right
            if (processing) {
                if (state.mode != "off") {
                    val pan = if (state.mode == "static") {
                        (state.staticPan * state.depth / 10000f).coerceIn(-1f, 1f)
                    } else {
                        val fraction = (frame % 32) / 32f
                        if (frame % 32 == 0) {
                            panFrom = sin(phase).toFloat() * (state.depth / 100f)
                            panTo = sin(phase + phaseDelta * 32).toFloat() * (state.depth / 100f)
                        }
                        phase += phaseDelta
                        (panFrom + (panTo - panFrom) * fraction).coerceIn(-1f, 1f)
                    }
                    val center = (left + right) * 0.5f
                    outputLeft = center * (1f - max(0f, pan))
                    outputRight = center * (1f + min(0f, pan))
                }
                lowLeft = lowLeft * alpha + outputLeft * (1f - alpha)
                lowRight = lowRight * alpha + outputRight * (1f - alpha)
                outputLeft = (lowLeft + (outputLeft - lowLeft) * toneLeft) * leftVolume
                outputRight = (lowRight + (outputRight - lowRight) * toneRight) * rightVolume
            }
            out.putShort(outputLeft.toInt().coerceIn(-32768, 32767).toShort())
            out.putShort(outputRight.toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }
}
