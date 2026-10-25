package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class Stereo3DAudioProcessorTest {
    private fun processor(): Stereo3DAudioProcessor =
        Stereo3DAudioProcessor().apply {
            val output = configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
            assertEquals(2, output.channelCount)
            flush()
        }

    private fun frames(count: Int, left: Short, right: Short): ByteBuffer =
        ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).apply {
            repeat(count) {
                putShort(left)
                putShort(right)
            }
            flip()
        }

    @Test fun zeroVirtualizerLeavesStereoBitExact() {
        val processor = processor()
        processor.update(0, true)
        processor.queueInput(frames(50, 12345, (-8765).toShort()))
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(50 * 4, output.remaining())
        repeat(50) {
            assertEquals(12345, output.short.toInt())
            assertEquals(-8765, output.short.toInt())
        }
    }

    @Test fun disabledMasterSwitchLeavesStereoBitExact() {
        val processor = processor()
        processor.update(1000, false)
        processor.queueInput(frames(10, 8000, 8000))
        val out = processor.output.order(ByteOrder.nativeOrder())
        repeat(10) {
            assertEquals(8000, out.short.toInt())
            assertEquals(8000, out.short.toInt())
        }
    }

    @Test fun monoInputProducesAudibleLeftRightDifferenceAtHighIntensity() {
        val processor = processor()
        processor.update(1000, true)
        processor.queueInput(frames(500, 12000, 12000))
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(500 * 4, out.remaining())
        val left = out.short.toInt()
        val right = out.short.toInt()
        assertNotEquals(left, right)
        assertTrue(left > right)
        // The delayed ear catches up after ~7 ms; streaming remains constant length.
        assertTrue(out.getShort(450 * 4 + 2).toInt() > right)
    }

    @Test fun turning3dOffRestoresOriginalStereoAndClearsDelay() {
        val processor = processor()
        processor.update(1000, true)
        processor.queueInput(frames(500, 25000, 25000))
        processor.output
        processor.update(0, true)
        processor.queueInput(frames(30, 6000, (-7000).toShort()))
        val out = processor.output.order(ByteOrder.nativeOrder())
        repeat(30) {
            assertEquals(6000, out.short.toInt())
            assertEquals(-7000, out.short.toInt())
        }
    }
}
