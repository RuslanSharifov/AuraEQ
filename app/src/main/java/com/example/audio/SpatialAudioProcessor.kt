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
    @Volatile private var settings = SpatialSettings()
    @Volatile private var masterEnabled = false
    private var engine = SpatialPcmEngine(44100)
    private var channels = 2

    fun update(settings: SpatialSettings, enabled: Boolean) {
        this.settings = settings.sanitized()
        masterEnabled = enabled
        engine.update(this.settings, enabled)
    }

    fun clearState() = engine.reset()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        engine = SpatialPcmEngine(inputAudioFormat.sampleRate)
        engine.update(settings, masterEnabled)
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() = engine.reset()
    override fun onReset() = engine.reset()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (channels * 2)
        val out = replaceOutputBuffer(frames * 4).order(ByteOrder.nativeOrder())
        val source = if (channels == 1) {
            ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
                repeat(frames) {
                    val sample = inputBuffer.short
                    putShort(sample)
                    putShort(sample)
                }
                flip()
            }
        } else inputBuffer.order(ByteOrder.nativeOrder())
        engine.process(source, out)
        out.flip()
    }
}
