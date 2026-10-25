package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Audible, deterministic stereo widening for AuraEQ's own Media3 output.
 *
 * Boosts the side signal and decorrelates one ear with a bounded ~7 ms Haas
 * delay. Unlike hardware Virtualizer this still creates perceived stereo space
 * for mono sources. The effect never changes playback speed or output length.
 * It cannot touch protected PCM of Spotify or other apps.
 */
@UnstableApi
class Stereo3DAudioProcessor : BaseAudioProcessor() {
    @Volatile private var amount = 0f
    @Volatile private var resetDelay = true
    private var ring = FloatArray(1)
    private var ringPosition = 0

    fun update(strength: Int, enabled: Boolean) {
        val next = if (enabled) strength.coerceIn(0, 1000) / 1000f else 0f
        if (next == 0f && amount != 0f) resetDelay = true
        amount = next
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount != 2
        ) return AudioProcessor.AudioFormat.NOT_SET
        // Fixed ~7 ms echo on the right ear; short enough not to alter rhythm.
        ring = FloatArray((inputAudioFormat.sampleRate * 0.007f).toInt().coerceAtLeast(1))
        resetDelay = true
        return inputAudioFormat
    }

    override fun onFlush() {
        ring.fill(0f)
        ringPosition = 0
        resetDelay = false
    }

    override fun onReset() {
        ring = FloatArray(1)
        ringPosition = 0
        resetDelay = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (resetDelay) {
            ring.fill(0f)
            ringPosition = 0
            resetDelay = false
        }
        val frames = inputBuffer.remaining() / 4
        val out = replaceOutputBuffer(frames * 4)
        val depth = amount
        for (i in 0 until frames) {
            val left = inputBuffer.short.toFloat()
            val right = inputBuffer.short.toFloat()
            if (depth <= 0f) {
                out.putShort(left.toInt().toShort())
                out.putShort(right.toInt().toShort())
                continue
            }
            val delayedRight = ring[ringPosition]
            ring[ringPosition] = right
            ringPosition = (ringPosition + 1) % ring.size

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
            val widened = 1f + depth * 0.72f
            val midMix = 1f - depth * 0.05f
            val delayedMix = depth * 0.48f
            val headroom = 1f / (1f + depth * 0.52f)
            val newLeft = (mid * midMix + side * widened) * headroom
            val directRight = mid * midMix - side * widened
            val newRight = (
                directRight * (1f - delayedMix) + delayedRight * delayedMix
            ) * headroom

            out.putShort(newLeft.toInt().coerceIn(-32768, 32767).toShort())
            out.putShort(newRight.toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }
}
