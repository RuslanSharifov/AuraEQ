# External 8D capture — experimental

## Files
- `app/src/main/java/com/example/audio/SpatialPcmEngine.kt`: shared PCM16 8D implementation.
- `app/src/main/java/com/example/audio/SpatialAudioProcessor.kt`: Media3 adapter for local playback.
- `app/src/main/java/com/example/audio/External8dCaptureService.kt`: opt-in foreground MediaProjection AudioRecord -> PCM 8D -> AudioTrack.
- `app/src/main/java/com/example/MainActivity.kt`: ActivityResult permission launchers.
- `app/src/main/java/com/example/ui/screens/EqualizerScreen.kt`: start/stop and status.
- `app/src/main/java/com/example/viewmodel/AuraViewModel.kt`: live SpatialSettings propagation.
- `app/src/main/AndroidManifest.xml`: service/permissions.

## Important limitations
1. Android 10+ only. Android 8/9 use the built-in player and supported native audio-session EQ.
2. Android captures media/game/unknown usage only if source permits capture (manifest and AudioAttributes), same user profile, not protected content.
3. This cannot mute or replace the original audio. Enabling AudioTrack replay may produce double sound/echo and additional latency. Prefer headphones and low volume; do not market as universal system 8D.
4. Spotify/YouTube may forbid capture; permission alone cannot override the source policy. Test compatibility; silence is not a code defect when source refuses capture.
5. User must grant RECORD_AUDIO and MediaProjection consent each session. Foreground notification has STOP action; no recording is saved or transmitted.
6. Own process UID is excluded from capture and re-output uses ALLOW_CAPTURE_BY_NONE to prevent feedback.
7. On supported native sessions, conventional Equalizer/BassBoost/Virtualizer continue their previous behavior; external 8D is independent of global session EQ.

## Manual verification (physical Android 10+)
1. Connect headphones, begin at low volume. Launch AuraEQ, enable master EQ and 8D, select dynamic mode (e.g. 4–8 seconds).
2. Tap 'Grant permission and start 8D capture'. Grant RECORD_AUDIO if prompted and approve Android screen/audio capture. Check the persistent capture notification and stop action. Toggle depth/speed/ear levels while running.
3. Play a local unprotected MP3/MP4 in a player with playback capture enabled. Observe distinct left/right movement. Compare with capture stopped. Listen for duplicate direct-path audio/echo: this limitation is expected.
4. Test VLC local MP3/MP4; some versions/settings may disallow capture. If 0 PCM data is delivered, the processed output stays silent.
5. Test Spotify and YouTube: audio may be blocked by policy; original audio continues without processed output. Do not claim success if it is blocked.
6. Test app exclusion settings, pause/seek, headset plug/unplug, incoming phone call, lock screen, Android cast/projection termination, app swipe/force-stop, and service STOP action. Verify no crash and native audio resources released.
7. On Android 8/9, the start button must be disabled with an explanatory message; normal in-app 8D still works.
8. Verify denial of RECORD_AUDIO, denial of projection consent, revocation from Quick Settings, and denial of notification permission do not crash the app.
9. Run Gradle checks: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` and CI emulator first-launch smoke test.

## Fundamental limitation
A capture-and-replay loop is **not** an OS output insert effect. Android does not allow third-party ordinary apps to replace Spotify's or YouTube's audio output. For lossless single-path system-wide post-processing, the OEM audio HAL/framework or root-level audio pipeline is required.

## Status values
- `running`: PCM with signal is being processed.
- `no_signal`: capture is active but has delivered only silence for 3 seconds. Either the source is paused, or the source app refuses playback capture (Spotify and similar apps do this). The service keeps listening and returns to `running` once audio arrives. Original playback is never affected.
- `error`: capture could not start or was interrupted (permission, consent, or AudioRecord/AudioTrack failure).
- `unsupported`: Android 9 or lower, or RECORD_AUDIO missing.
