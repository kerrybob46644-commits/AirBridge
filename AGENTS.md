# AGENTS.md

Single-module Android (Kotlin) app: an AirPlay 2 audio **sender** that streams device audio to HomePod / Apple TV over Wi-Fi. All protocol logic is hand-rolled — there are no AirPlay/networking libraries, and the App UI is Compose.

## Build & verify

- Use the wrapper: `./gradlew` (Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21, JDK 17). The README's bare `gradle` commands work but the wrapper is the reliable path.
- First-time setup requires an SDK path: `local.properties` with `sdk.dir=...` (file is gitignored). compileSdk/targetSdk 36, minSdk 29.
- CI (`.github/workflows/build.yml`) runs exactly: `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon`. Run this before pushing; pushes go to `master` via PRs.
- Unit tests live in `app/src/test/...` and are pure JVM JUnit 4 (no Android framework, no instrumentation/androidTest). Run one class with:
  `./gradlew :app:testDebugUnitTest --tests "com.airbridge.app.airplay.AlacCodecTest"`
- No lint, detekt, or formatter tasks are configured.

## Architecture

- `airplay/RaopSender.kt` (~1900 lines) is the protocol state machine: pairing, RTSP session setup, RTP streaming, packet-loss retransmit, clock sync. Most nontrivial behavior lives here.
- `airplay/AirplayCrypto.kt` also contains the `Tlv8` and `Bplist` serializers — they are objects in the same file.
- `CastController` (top-level `object` in the `com.airbridge.app` package) is the global state singleton (a `StateFlow<UiState>`). Both `MainActivity` and `CastService` read/write it directly; there is no DI layer.
- `CastService` is a foreground MediaProjection service driven by intent actions (`START` / `STOP` / `TEST_TONE`). Stop must go through `ACTION_STOP` (see `CastController.stop()`) so the teardown path runs — calling `stopSelf()` directly skips cleanup of locks/projection/RaopSender.
- `AudioCapture` falls back from 44.1kHz to 48kHz capture and normalizes everything into a 44.1kHz clock domain via `AudioMasterClock`.
- UI strings live in `res/values/strings.xml` (English, default) and `res/values-zh/strings.xml` (中文). A "Language" selector in the settings card switches between them at runtime: `LocaleHelper` applies the stored locale via `attachBaseContext` in `MainActivity` and `CastService`, then the activity recreates. Status text and log strings are English-only (not localized).
- Code comments are Chinese; log/status/exception strings are English.

## Gotchas
- Manually added devices use the `_manual` type (`CastController.TYPE_MANUAL`) so `saveManual` persistence and the delete button work; discovered devices get their mDNS type. The discovery listener dedupes by `host:port` because both NsdManager and the raw mDNS socket can report the same device.
- `airplay/AlacCodec.kt` is a port of FFmpeg's ALAC encoder and is licensed LGPL-2.1+ (the rest of the repo is Apache-2.0) — see the README licensing note. The codec is covered by round-trip unit tests; keep them passing when touching it.
- Release builds enable R8 (minify + shrinkResources). `proguard-rules.pro` keeps BouncyCastle intact by name — preserve that if touching crypto. Release falls back to the debug signing config when `keystore.properties` is absent, so release APKs are still installable.
- `RaopSender` deliberately runs many named worker threads (session, pacer, sync, feedback, volume-flush, udp control/timing, event). Volume writes and RTSP control are intentionally off the main thread and throttled; rework threading with care.