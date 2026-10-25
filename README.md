# AURA EQ — Premium Android Sound Equalizer

AURA EQ is a high-fidelity native Android audio equalizer application engineered with Jetpack Compose, Material 3 glassmorphism design, and real-time hardware audio digital signal processing (DSP) via Android `AudioEffect` APIs.

---

## 🌟 Key Features

1. **Precision Equalizer Engine**:
   - Probes and attaches to actual device audio hardware capabilities (e.g. 5-band Equalizer).
   - Real-time millibel control with dynamic Bezier frequency response curve.
   - Smooth vertical band sliders with central zero-detents and haptic clicks.
2. **Dedicated Bass Boost & Treble Glass Dials**:
   - Hardware BassBoost integration with strength range (0..1000).
   - Treble presence controller calculating high-frequency shelf distribution.
   - 3D Surround sound Virtualizer effect.
3. **Per-App Sound Profiles**:
   - Assign tailored presets to specific applications (Spotify, YouTube, VLC, SoundCloud, etc.).
   - Transparent, honest status indicators:
     - Active session: `"Active audio processing"` (green)
     - Standard device limitation: `"Profile saved — audio control is not supported for this app on this device."`
4. **Mastered Audio Presets**:
   - Factory Presets: Normal/Flat, Deep Bass, Bass Boost, Vocal Clarity, Rock, Pop, Electronic, Jazz, Classical, Podcast, Gaming, Movie.
   - Create, rename, duplicate, and delete custom user presets.
5. **Integrated Audio Test Player (AndroidX Media3)**:
   - High-precision audio playback with Media3 ExoPlayer.
   - Local document file picker (`GetContent("audio/*")`).
   - Bundled harmonic reference test signal for instant live auditioning without external files.
6. **Device Route Detection & Profiles**:
   - Detects output routing via `AudioDeviceCallback` (Phone Speaker, Wired Headphones, Bluetooth Audio, USB Audio).
   - Optional automatic profile switching when headphones or Bluetooth connect.
7. **Glassmorphism Design System**:
   - Translucent frosted glass surfaces with subtle gradients, soft ambient shadows, and luminous accents.
   - Light theme (Pearl White default) & Dark glass theme options.
8. **Bilingual Localization (Azerbaijani & English)**:
   - Initial default language: Azerbaijani (`az`).
   - English (`en`) full localization.
   - In-app language toggle.
9. **Zero-Tracking & 100% Offline Privacy**:
   - No internet permission declared.
   - No external trackers or invasive background services.
   - Export and import profiles using standard JSON files.

---

## 🏗️ Architecture

- **UI Layer**: Jetpack Compose, Material 3, custom Canvas Beziers, edge-to-edge `WindowInsets`.
- **ViewModel**: MVVM with `StateFlow` and `collectAsStateWithLifecycle`.
- **Audio Engine**: `AudioEffectManager`, `AudioSessionReceiver`, `TestAudioPlayerManager` (Media3 ExoPlayer).
- **Data Persistence**: Jetpack DataStore Preferences for settings, presets, app profiles, and routing rules.

---

## 🚀 Building & Testing

### Assemble Debug APK
```bash
gradle :app:assembleDebug
```

### Assemble Release APK
```bash
gradle :app:assembleRelease
```

### Build Android App Bundle (AAB) for Google Play
```bash
gradle :app:bundleRelease
```

### Run Unit and Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
