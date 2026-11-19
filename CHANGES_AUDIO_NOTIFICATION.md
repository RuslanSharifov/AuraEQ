# Changes: playback gaps, album art, media notification, logging

## Playback gaps (audible stutter while music plays)
- AudioEffectManager: effect parameters are written only when they change
  (per-session caches). `enabled` is toggled only on change. Live effects are
  no longer re-created or re-toggled on every ExoPlayer state callback.
- Audio session is attached once per session ID (`onAudioSessionIdChanged`),
  not on every state change.
- Duplicate flow collectors removed from AuraViewModel (AuraApplication owns them).
- AuraApplication reacts only to real changes of enable flag and policies.
- SpatialPcmEngine: reset() is lock-free (flag consumed by the audio thread);
  sine LFO uses a lookup table; mode strings compared once per call.
- SpatialAudioProcessor: mono expansion reuses a scratch buffer (no per-buffer allocation).

## Album art
- Artwork is passed to every MediaItem path (queue, playNext, addToQueue).
- Existing tracks need a library rescan to extract embedded covers.

## Media notification
- Custom RemoteViews notification (PlaybackNotificationProvider + layout
  notification_player.xml): artwork, title, artist/album, favorite, previous,
  play/pause, next, repeat (off/all/one), progress bar with times.
- Refreshed once per second while playing.
- Glass look: translucent rounded panel tinted by the cover's average colour.
  Android notifications cannot blur the content behind them.
- Actions: PlaybackNotificationReceiver (registered in the manifest).

## Logging
- AuraLog: Logcat tags AuraEQ/<area>; last 400 lines kept in memory,
  available via AuraLog.dump().

## Not verified in this environment
- No Kotlin compiler or Android SDK was available. Brace balance and XML
  well-formedness were checked; the project was not built or run on a device.
- Dependency added: com.google.guava:guava:33.3.1-android (for ImmutableList).
