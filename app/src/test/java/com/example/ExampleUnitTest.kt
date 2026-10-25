package com.example

import com.example.data.model.AppProfile
import com.example.data.model.EqualizerBand
import com.example.data.model.EqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun testFactoryPresets() {
        val factoryPresets = EqualizerPreset.getFactoryPresets()
        assertTrue("Factory presets should not be empty", factoryPresets.isNotEmpty())
        assertEquals(12, factoryPresets.size)

        // Ensure all presets have unique IDs
        val uniqueIds = factoryPresets.map { it.id }.toSet()
        assertEquals(factoryPresets.size, uniqueIds.size)

        // Flat preset test
        val flat = factoryPresets.first()
        assertEquals("flat", flat.id)
        assertEquals(0, flat.bassBoost)
        assertEquals(0, flat.treble)
    }

    @Test
    fun testPresetJsonSerialization() {
        val original = EqualizerPreset(
            id = "test_custom_1",
            name = "Studio Acoustic",
            gains = listOf(200, 400, 100, -100, 300),
            bassBoost = 350,
            treble = 200,
            virtualizer = 150,
            isCustom = true
        )
        val json = original.toJson()
        val parsed = EqualizerPreset.fromJson(json)

        assertEquals(original.id, parsed.id)
        assertEquals(original.name, parsed.name)
        assertEquals(original.gains, parsed.gains)
        assertEquals(original.bassBoost, parsed.bassBoost)
        assertEquals(original.treble, parsed.treble)
        assertEquals(original.virtualizer, parsed.virtualizer)
        assertTrue(parsed.isCustom)
    }

    @Test
    fun testEqualizerBandFormatting() {
        val subBassBand = EqualizerBand(
            index = 0,
            centerFreqHz = 60,
            minMilliBel = -1500,
            maxMilliBel = 1500,
            currentGainMilliBel = 600
        )
        assertEquals("60Hz", subBassBand.centerFreqFormatted)
        assertEquals(6.0f, subBassBand.gainDb, 0.01f)

        val trebleBand = EqualizerBand(
            index = 4,
            centerFreqHz = 14000,
            minMilliBel = -1500,
            maxMilliBel = 1500,
            currentGainMilliBel = -350
        )
        assertEquals("14k", trebleBand.centerFreqFormatted)
        assertEquals(-3.5f, trebleBand.gainDb, 0.01f)
    }

    @Test
    fun testAppProfileModel() {
        val profile = AppProfile(
            packageName = "com.spotify.music",
            appName = "Spotify",
            presetId = "deep_bass",
            isEnabled = true
        )
        val json = profile.toJson()
        val restored = AppProfile.fromJson(json)

        assertEquals("com.spotify.music", restored.packageName)
        assertEquals("Spotify", restored.appName)
        assertEquals("deep_bass", restored.presetId)
        assertTrue(restored.isEnabled)
    }
}
