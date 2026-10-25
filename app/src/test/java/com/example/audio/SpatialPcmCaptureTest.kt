package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialPcmCaptureTest {
    private fun samples(count: Int): ByteBuffer =
        ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).apply {
            repeat(count) {
                putShort(12000.toShort())
                putShort(12000.toShort())
            }
            flip()
        }

    @Test fun pcmEngineWithoutMedia3BipassesWhenOff() {
        val processor = SpatialPcmEngine(8000)
        processor.update(SpatialSettings(is8dEnabled = false, mode = "dynamic"), true)
        val source = samples(16)
        val output = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder())
        assertEquals(64, processor.process(source, output))
        output.flip()
        repeat(16) {
            assertEquals(12000, output.short.toInt())
            assertEquals(12000, output.short.toInt())
        }
    }

    @Test fun pcmEngineWithoutMedia3MovesFromOneEarToAnother() {
        val processor = SpatialPcmEngine(8000)
        processor.update(SpatialSettings(is8dEnabled = true, mode = "dynamic", depth = 100, periodSeconds = 2f), true)
        val source = samples(16000)
        val output = ByteBuffer.allocateDirect(64000).order(ByteOrder.nativeOrder())
        assertEquals(64000, processor.process(source, output))
        output.flip()
        val rightDominantLeft = output.getShort(4000 * 4).toInt()
        val rightDominantRight = output.getShort(4000 * 4 + 2).toInt()
        val leftDominantLeft = output.getShort(12000 * 4).toInt()
        val leftDominantRight = output.getShort(12000 * 4 + 2).toInt()
        assertTrue(rightDominantRight > rightDominantLeft)
        assertTrue(leftDominantLeft > leftDominantRight)
    }

    @Test fun resetStopsPanAndRestoresStereo() {
        val engine = SpatialPcmEngine(8000)
        engine.update(SpatialSettings(is8dEnabled = true, mode = "static", staticPan = 100), true)
        engine.process(samples(10), ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder()))
        engine.update(SpatialSettings(), true)
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            .putShort(4000.toShort()).putShort((-2800).toShort()).apply { flip() }
        val output = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        engine.process(input, output)
        output.flip()
        assertEquals(4000, output.short.toInt())
        assertEquals(-2800, output.short.toInt())
    }
}
