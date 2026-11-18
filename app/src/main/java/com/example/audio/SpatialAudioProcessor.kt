package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Media3 adapter around the independent SpatialPcmEngine. */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {
    private data class Configuration(val settings: SpatialSettings, val enabled: Boolean)

    @Volatile private var configuration = Configuration(SpatialSettings(), false)
    private var engine: SpatialPcmEngine? = null
    private var applied: Configuration? = null

    fun update(settings: SpatialSettings, enabled: Boolean) {
        configuration = Configuration(settings.sanitized(), enabled)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() {
        // Media3 can configure the next stream before the current PCM has drained.
        engine = inputAudioFormat.sampleRate.takeIf { it > 0 }?.let(::SpatialPcmEngine)
        applied = null
    }

    override fun onReset() {
        engine = null
        applied = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val processor = checkNotNull(engine)
        val next = configuration
        if (applied != next) {
            processor.update(next.settings, next.enabled)
            applied = next
        }
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (channels * 2)
        val out = replaceOutputBuffer(frames * 4).order(ByteOrder.nativeOrder())
        processor.process(inputBuffer.order(ByteOrder.nativeOrder()), out, channels)
        out.flip()
    }
}
