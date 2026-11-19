package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.util.AuraLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Media3 adapter around the independent SpatialPcmEngine. */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {
    @Volatile private var settings = SpatialSettings()
    @Volatile private var masterEnabled = false
    @Volatile private var engine = SpatialPcmEngine(44100)
    private var channels = 2

    /** Reused for mono-to-stereo expansion so the audio thread never allocates. */
    private var monoScratch: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    fun update(settings: SpatialSettings, enabled: Boolean) {
        this.settings = settings.sanitized()
        masterEnabled = enabled
        engine.update(this.settings, enabled)
    }

    fun clearState() = engine.reset()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            AuraLog.w("Spatial", "Unsupported format ${inputAudioFormat}, bypassing 8D")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        engine = SpatialPcmEngine(inputAudioFormat.sampleRate).also {
            it.update(settings, masterEnabled)
        }
        AuraLog.i("Spatial", "Configured ${inputAudioFormat.sampleRate}Hz ch=$channels")
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() = engine.reset()
    override fun onReset() = engine.reset()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (channels * 2)
        val out = replaceOutputBuffer(frames * 4).order(ByteOrder.nativeOrder())
        val source = if (channels == 1) {
            expandMonoToStereo(inputBuffer, frames)
        } else {
            inputBuffer.order(ByteOrder.nativeOrder())
        }
        engine.process(source, out)
        out.flip()
    }

    private fun expandMonoToStereo(inputBuffer: ByteBuffer, frames: Int): ByteBuffer {
        val needed = frames * 4
        if (monoScratch.capacity() < needed) {
            monoScratch = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder())
        }
        monoScratch.clear()
        repeat(frames) {
            val sample = inputBuffer.short
            monoScratch.putShort(sample)
            monoScratch.putShort(sample)
        }
        monoScratch.flip()
        return monoScratch
    }
}
