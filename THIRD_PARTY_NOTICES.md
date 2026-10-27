# Third-party dependency notices

AuraEQ uses libraries that are **not** relicensed by the repository's Apache-2.0 license.

Notable direct dependencies include AndroidX Core, Activity, Compose, Material 3, Lifecycle, DataStore, Navigation, Media3/ExoPlayer, Kotlin and kotlinx.coroutines, Google Android Gradle tooling, and **Haze / Haze Glass** (`dev.chrisbanes.haze`).

Consult each library's published license and the complete resolved dependency tree when distributing APKs:

```sh
gradle :app:dependencies --configuration releaseRuntimeClasspath
```

This file is a preliminary inventory, **not** a complete transitive third-party notice bundle. Before publishing a production release, inspect the resolved library licenses and include all required notices and any license texts that require distribution. Keep existing upstream copyright notices intact.
