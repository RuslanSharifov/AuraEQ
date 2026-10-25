# Google Play Store Publishing & Readiness Documentation
**Project**: AURA EQ — Premium Android Sound Equalizer  
**Package / Application ID**: `com.auraeq.audio` (Project ID: `com.aistudio.auraeq.pkvmrx`)  
**Target SDK**: 36 (Android 16) | **Min SDK**: 26 (Android 8.0)

---

## 1. Store Metadata & Listings

### English Listing (en-US)
* **Title (max 30 chars)**: AURA EQ: Sound Equalizer
* **Short Description (max 80 chars)**: Premium audio equalizer with glass design, deep bass boost and app profiles.
* **Full Description**:
Experience studio-grade sound on your Android device with AURA EQ. Built with a translucent pearl glassmorphism aesthetic and genuine Android audio DSP hardware acceleration, AURA EQ allows you to sculpt your sound frequency curve with precision.

Key Features:
• Multi-Band Hardware Equalizer: Real-time frequency band response curve with smooth cubic Bezier visualization.
• Deep Bass Boost & Treble: Dedicated glass arc dials for bass boost and high-frequency sparkle.
• 3D Virtualizer: Immersive spatial surround sound simulation.
• Per-App Audio Profiles: Configure distinct sound tunings for Spotify, YouTube, VLC, and your favorite music players.
• Mastered Presets: Includes Flat, Deep Bass, Vocal Clarity, Rock, Pop, Electronic, Jazz, Classical, Podcast, Gaming, and Movie presets.
• Audio Route Memory: Automatically adjusts EQ curves when switching between Phone Speaker, Wired Headphones, and Bluetooth.
• Built-in Test Player: Test your equalization curves live with local files or built-in reference tones.
• 100% Offline & Private: No accounts, no data collection, no ads, and no internet permission required.

---

### Azerbaijani Listing (az-AZ)
* **Title (max 30 chars)**: AURA EQ: Səs Ekvalayzeri
* **Short Description (max 80 chars)**: Şüşə dizaynlı, dərin bas gücləndirici və fərdi profilli premial ekvalayzer.
* **Full Description**:
AURA EQ ilə Android cihazınızda studiya keyfiyyətində səs əldə edin. Zərif parlaq şüşə (glassmorphism) dizaynı və real Android audio DSP aparat sürətləndirməsi ilə təchiz olunmuş AURA EQ, səs tezliklərini yüksək dəqiqliklə kökləməyə imkan verir.

Əsas Üstünlüklər:
• Çoxzolaqlı Aparat Ekvalayzeri: Real vaxt rejimində axıcı Bezier əyrisi ilə tezlik zolaqlarının idarə olunması.
• Dərin Bas və Tiz Sürgüləri: Dərin sub-bas və aydın yüksək tezliklər üçün xüsusi şüşə idarəetmə siferblatları.
• 3D Əhatəli Səs (Virtualizer): Genişləndirilmiş akustik məkan effekti.
• Fərdi Tətbiq Profilləri: Spotify, YouTube, VLC və digər sevimli pleyerləriniz üçün fərdi səs profilləri təyin edin.
• Hazır Studiya Profilləri: Düz/Normal, Dərin Bas, Aydın Vokal, Rok, Pop, Elektronik, Caz, Klassik, Podkast, Oyun və Film profilləri.
• Qurğuya Uyğun Avtomatik Keçid: Qulaqlıq, Bluetooth və ya daxili dinamik qoşulduqda uyğun profilin avtomatik aktivləşməsi.
• Daxili Sınaq Pleyeri: Canlı audio fayllarınız və ya daxili sınaq siqnalı ilə effektləri dərhal yoxlayın.
• 100% Oflayn və Şəxsi: Hesab tələb olunmur, heç bir məlumat toplanmır və internet icazəsi yoxdur.

---

## 2. Data Safety Questionnaire Draft
* **Does your app collect or share any user data?** No.
* **Data Collected**: None (Zero personal, audio, financial, device ID, or location data).
* **Data Shared**: None.
* **Security Practices**: All audio processing happens in real-time in device volatile memory via native `android.media.audiofx` APIs. Settings are stored on-device via Android Jetpack DataStore Preferences.

---

## 3. Privacy Policy Draft
**AURA EQ Privacy Policy**  
*Last updated: 2026-10-25*

AURA EQ ("we", "our", or "the application") respects your privacy.
1. **Information Collection**: AURA EQ does not collect, transmit, store on remote servers, or share any personal information, analytics, telemetry, or audio content.
2. **Audio Processing**: All audio manipulation (equalization, bass boost, treble shelf, and virtualizer) is processed entirely locally on your device via standard Android AudioEffect APIs.
3. **Storage Access**: When you select an audio track in the Built-in Test Player, the app uses Android's zero-permission system document picker (`ACTION_GET_CONTENT`). The app does not request or require broad storage read/write permissions.
4. **Network Access**: The application does not declare the `android.permission.INTERNET` permission and operates 100% offline.

---

## 4. Google Play Asset Specifications
* **App Icon**: 512 x 512 px PNG, 32-bit color (generated adaptive icon with solid `#1A163A` background).
* **Feature Graphic**: 1024 x 500 px JPG/PNG without alpha. Displays glowing holographic audio waveform on deep violet glass surface with "AURA EQ — Pure Sonic Clarity".
* **Screenshot Plan (Phone & Tablet)**:
  1. *Dashboard*: Equalizer visual curve, sliders, Bass & Treble dials in light pearl glass.
  2. *Per-App Profiles*: Clean list showing Spotify, YouTube with custom preset badges.
  3. *Audio Presets*: Rich grid of studio presets (Deep Bass, Vocal Clarity, Rock, Movie).
  4. *Integrated Test Player*: Test player playing reference wave with active session ID badge.
  5. *Night Glass Aesthetics*: Dark glass theme mode showcase.

---

## 5. Pre-Launch & Release Checklist
- [x] Unique package identifier configured.
- [x] Edge-to-edge system insets verified (`WindowInsets.statusBars` and `navigationBarsPadding`).
- [x] Zero broad storage permissions (`READ_EXTERNAL_STORAGE` omitted; Photo/Document picker used).
- [x] Policy-compliant `<queries>` for music players without `QUERY_ALL_PACKAGES`.
- [x] Full Azerbaijani & English localization in XML resources.
- [x] Automated Robolectric and JVM unit tests implemented and passing.
