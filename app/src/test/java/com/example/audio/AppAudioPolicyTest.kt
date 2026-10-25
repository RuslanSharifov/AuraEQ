package com.example.audio

import com.example.data.model.AppProfile
import com.example.data.model.EqualizerPreset
import org.junit.Assert.*
import org.junit.Test

class AppAudioPolicyTest {
  private val spotify = "com.spotify.music"
  private val youtube = "com.google.android.youtube"
  private val presets = EqualizerPreset.getFactoryPresets().associateBy { it.id }
  @Test fun globalDefaultForAnyKnownSession() {
    assertTrue(AppAudioPolicy.shouldProcess(spotify, emptyMap()))
    assertTrue(AppAudioPolicy.shouldProcess(youtube, emptyMap()))
    assertNull(AppAudioPolicy.presetFor(spotify, emptyMap(), presets))
  }
  @Test fun excludedAppDoesNotChangeOtherApp() {
    val map = mapOf(spotify to AppProfile(spotify,"Spotify","rock",isEnabled=false))
    assertFalse(AppAudioPolicy.shouldProcess(spotify,map))
    assertTrue(AppAudioPolicy.shouldProcess(youtube,map))
  }
  @Test fun perAppOverrideIsExplicit() {
    val profile = AppProfile(spotify,"Spotify","rock",useCustomPreset=true)
    val map = mapOf(spotify to profile)
    assertEquals(presets["rock"],AppAudioPolicy.presetFor(spotify,map,presets))
    assertNull(AppAudioPolicy.presetFor(youtube,map,presets))
  }
  @Test fun legacyPresetWithoutOptInUsesGlobal() {
    val map = mapOf(spotify to AppProfile(spotify,"Spotify","rock"))
    assertNull(AppAudioPolicy.presetFor(spotify,map,presets))
  }
}