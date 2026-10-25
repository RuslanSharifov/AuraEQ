package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class EqBandTuningTest {
    @Test fun treble_only_affects_final_two_bands() {
        val gains = listOf(120, -180, 90, 200, 300)
        val applied = gains.indices.map {
            EqBandTuning.effectiveGain(gains[it], 500, it, 5, -1500, 1500).toInt()
        }
        assertEquals(listOf(120, -180, 90, 350, 600), applied)
        // Display/save data is never replaced by the effective hardware levels.
        assertEquals(listOf(120, -180, 90, 200, 300), gains)
    }

    @Test fun changing_another_band_does_not_reboost_36k_or_14k() {
        val gains = mutableListOf(0, 0, 0, 250, 350)
        val treble = 450
        val before = (3..4).map {
            EqBandTuning.effectiveGain(gains[it], treble, it, 5, -1500, 1500)
        }
        repeat(50) { i ->
            gains[0] = i * 10
            gains[1] = 300 - i
            val after = (3..4).map {
                EqBandTuning.effectiveGain(gains[it], treble, it, 5, -1500, 1500)
            }
            assertEquals(before, after)
            assertEquals(250, gains[3])
            assertEquals(350, gains[4])
        }
    }

    @Test fun treble_cannot_exceed_hardware_range() {
        assertEquals(1500, EqBandTuning.effectiveGain(1400, 1000, 4, 5, -1500, 1500).toInt())
        assertEquals(-1500, EqBandTuning.effectiveGain(-1400, -1000, 4, 5, -1500, 1500).toInt())
    }
}
