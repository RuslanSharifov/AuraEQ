package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private var hasDelayHistory = false
    private var ring = FloatArray(1)
    private var ringPosition = 0

    fun update(strength: Int, enabled: Boolean) {
        amount = if (enabled) strength.coerceIn(0, 1000) / 1000f else 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount != 2
        ) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun onFlush() {
        // Apply the delay for the active format only after Media3 drains the old one.
        val delayFrames = (inputAudioFormat.sampleRate * 0.007f).toInt().coerceAtLeast(1)
        if (ring.size == delayFrames) ring.fill(0f) else ring = FloatArray(delayFrames)
        ringPosition = 0
        hasDelayHistory = false
    }

    override fun onReset() {
        ring = FloatArray(1)
        ringPosition = 0
        hasDelayHistory = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val depth = amount
        val out = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        if (depth == 0f) {
            if (hasDelayHistory) {
                ring.fill(0f)
                ringPosition = 0
                hasDelayHistory = false
            }
            out.put(inputBuffer).flip()
            return
        }

        hasDelayHistory = true
        inputBuffer.order(ByteOrder.nativeOrder())
        val widened = 1f + depth * 0.72f
        val midMix = 1f - depth * 0.05f
        val delayedMix = depth * 0.48f
        val headroom = 1f / (1f + depth * 0.52f)
        while (inputBuffer.remaining() >= 4) {
            val left = inputBuffer.short.toFloat()
            val right = inputBuffer.short.toFloat()
            val delayedRight = ring[ringPosition]
            ring[ringPosition] = right
            if (++ringPosition == ring.size) ringPosition = 0

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
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
