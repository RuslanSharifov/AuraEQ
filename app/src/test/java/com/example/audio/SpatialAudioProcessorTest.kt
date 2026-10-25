package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class SpatialAudioProcessorTest {
    private fun input(vararg values: Short): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putShort(it) }
            flip()
        }

    private fun makeProcessor(rate: Int = 44100): SpatialAudioProcessor =
        SpatialAudioProcessor().apply {
            val result = configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
            assertEquals(2, result.channelCount)
            flush()
        }

    @Test
    fun disabled_keeps_stereo_pcm_unchanged() {
        val processor = makeProcessor()
        processor.update(SpatialSettings(mode = "dynamic", leftPercent = 0), false)
        processor.queueInput(input(12000, (-7000).toShort()))
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(12000, out.short.toInt())
        assertEquals(-7000, out.short.toInt())
    }

    @Test
    fun per_ear_volume_changes_each_channel_independently() {
        val processor = makeProcessor()
        processor.update(SpatialSettings(leftPercent = 40, rightPercent = 70), true)
        processor.queueInput(input(10000, 10000))
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(4000, out.short.toInt())
        assertEquals(7000, out.short.toInt())
    }

    @Test
    fun static_pan_at_left_extreme_silences_right_ear() {
        val processor = makeProcessor()
        processor.update(SpatialSettings(mode = "static", staticPan = -100), true)
        processor.queueInput(input(12000, 12000))
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(12000, out.short.toInt())
        assertEquals(0, out.short.toInt())
    }

    @Test
    fun dynamic_pan_moves_right_then_left_over_cycle() {
        val processor = makeProcessor(8000)
        processor.update(SpatialSettings(mode = "dynamic", periodSeconds = 2f, depth = 100), true)
        val data = ByteBuffer.allocateDirect(8000 * 4).order(ByteOrder.nativeOrder())
        repeat(8000) {
            data.putShort(10000)
            data.putShort(10000)
        }
        data.flip()
        processor.queueInput(data)
        val out = processor.output.order(ByteOrder.nativeOrder())
        // At 1/4 cycle the signal is panned right, at 3/4 cycle it is panned left.
        val quarterLeft = out.getShort(2000 * 4).toInt()
        val quarterRight = out.getShort(2000 * 4 + 2).toInt()
        val threeQuarterLeft = out.getShort(6000 * 4).toInt()
        val threeQuarterRight = out.getShort(6000 * 4 + 2).toInt()
        assertTrue(quarterRight > quarterLeft)
        assertTrue(threeQuarterLeft > threeQuarterRight)
    }

    @Test
    fun reset_to_off_stops_previous_pan_effect() {
        val processor = makeProcessor()
        processor.update(SpatialSettings(mode = "static", staticPan = 100), true)
        processor.queueInput(input(10000, 10000))
        processor.output
        processor.update(SpatialSettings(), true)
        processor.queueInput(input(6000, 2000))
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(6000, out.short.toInt())
        assertEquals(2000, out.short.toInt())
    }
}
