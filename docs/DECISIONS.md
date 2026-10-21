# Decisions

Choices made while building, with the reason. Newest at the bottom of each stage.

## Stage 1 — foundation and live radar (Oct 20, 2026)

### Build and project
- **Single `app` module, packaged by feature.** `feature/radar`, `feature/trips`, … plus `core/designsystem`, `core/data`, `core/network`, `core/database`, as CLAUDE.md lists. Separate Gradle modules would slow every build and add boilerplate for a one-person app; packages give the same boundaries. Revisit if build times hurt.
- **Toolchain: AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00.** The latest stable versions on Google Maven / Maven Central today. KGP is declared at the root so AGP's built-in Kotlin uses 2.4.21 instead of its bundled 2.2.
- **JDK 25** (the Gradle daemon toolchain the project was created with). Locally that's Android Studio's bundled JBR; CI uses Temurin 25.
- **Whole-app R8 for release.** AGP 9's package-scoped optimisation (`androidx/kotlin/kotlinx` only) crashed the release build at startup: OkHttp could no longer reach rewritten `kotlin.*` classes. Full-app R8 works with the libraries' own keep rules and was verified on the emulator.
- **Release APK ships arm64-v8a and x86_64 native code only.** MapLibre's native libraries dominate APK size. arm64 covers the phone and x86_64 keeps the release build testable on the emulator. That drops 32-bit ABIs nobody here needs.
- **Workflow triggers on `master` as well as `main`.** The GitHub repo's default branch is `master`; renaming it is outward-facing, so the workflow accepts both.
- **No signing secrets means tests still run but no release.** The workflow passes with a warning until the four secrets exist, instead of failing every push.

### Libraries added (all free, actively maintained)
| Library | Why |
| --- | --- |
| Haze 2.0 (`haze`, `haze-blur`) | Real backdrop blur for the frosted-glass surfaces, including over the map |
| MapLibre Android 13.7.1 | Required by the spec; latest stable |
| Ktor 3.6 + OkHttp 5.5 | Spec networking; one shared OkHttp client also serves MapLibre tiles and Coil, so every request carries the User-Agent |
| Coil 3.6 + `coil-network-ktor3` | Spec image loading through the shared client |
| Room 2.8, DataStore 1.2 | Offline-first storage and radar preferences |
| Roborazzi 1.76, Robolectric 4.17, Turbine, `ktor-client-mock`, `coil-test` | Screenshot and unit testing |
| `androidx.core:core-splashscreen` | Night-canvas splash, no white flash |

### Design system
- **Fonts are the variable TTFs from google/fonts** (Space Grotesk, Inter, JetBrains Mono), with a weight axis per `FontWeight`. OFL licences are in `assets/licenses`.
- **Custom icon set** (`ContrailIcons`) drawn as vector paths instead of material-icons, so nothing looks stock.
- **Aurora canvas is an AGSL shader** with a static gradient fallback for previews, tests and "remove animations".
- **Two Haze layers.** A *backdrop* layer (aurora, or the map on the radar) feeds glass cards and overlays, and a *chrome* layer (the whole tab) feeds the nav bar.
- **Reduced motion** reads `ANIMATOR_DURATION_SCALE == 0`. Every motion token then snaps, split-flaps show their final text, and radar aircraft are drawn at their reported positions without dead reckoning or pulsing.

### Navigation
- **Custom tab shell instead of Navigation 3.** Five top-level tabs with a saveable state holder and directional slide transitions. Activity-scoped view models keep the radar polling state across tab switches. Navigation 3 comes in when aircraft and airport pages need a back stack (Stage 2+).
- **The app opens on Radar.** Trips is "home" in the spec, but it's a placeholder until Stage 2. Switch `TopLevelDestination.Start` back to Trips when Trips has content.
- **The nav bar slides away while the aircraft sheet is open**, via `LocalShellChrome`, so the sheet gets the full height.

### Radar
- **Live traffic: adsb.lol first, then adsb.fi.** airplanes.live now answers every unregistered client with HTTP 403 ("contact us with a project description"), so it can't serve as a keyless backup. adsb.fi's open-data API speaks the same readsb format and is free for personal use. airplanes.live stays in code (`ReadsbApiSource.airplanesLive`) for if you register. OpenSky (credit budget) is left for Stage 2 when it's needed for airport flights.
- **Polite polling.** Every 4 s while the radar is visible, never closer than 2.5 s even when a pan restarts the loop, plus a 1.1 s per-host throttle. Failures back off 4 → 8 → 16 → 30 s. HTTP 429 honours `Retry-After` (default 2 min) before that provider is tried again.
- **Live data goes through Room** (offline-first rule), pruned after 75 s without an update. Trails keep 4 minutes, thinned to points where the aircraft turns, climbs or descends, lands or lifts off, or every 20 s.
- **Motion runs in MapLibre, not Compose.** A `Choreographer` loop dead-reckons each contact (ground speed, track, turn rate, capped at 30 s) and pushes one GeoJSON update per frame. New fixes don't jump: the difference becomes an offset that decays over about 0.9 s. If building a frame gets expensive, the loop drops to every 2nd or 3rd vsync.
- **Aircraft icons are SDF silhouettes generated on device** (nine shapes, Felzenszwalb distance transform), so one bitmap per shape can be tinted by the altitude ramp and outlined with a halo.
- **Ground traffic is hidden below zoom 8.5** (fades in by 10). At regional zoom it piled into an unreadable blob at airports.
- **"Rare types only" means Legendary + Rare.** Uncommon includes the A220, which is everywhere in Canada.
- **Cargo operators downgrade a 747's tier to Uncommon.** The table rates types by passenger service.
- **Weather layer uses RainViewer**, which isn't in the DESIGN data table. Open-Meteo has no radar tiles, and RainViewer's public API is free and keyless for personal use. Off by default.
- **Airport labels come from the OpenMapTiles `aerodrome_label` layer** in OpenFreeMap tiles rather than bundled OurAirports data. That's zero app size and toggleable. OurAirports will be bundled when airport pages need runways and frequencies.
- **Route lookups use adsbdb, labelled "Route from callsign".** Its hex → aircraft lookup is skipped because adsb.lol already sends registration and type.
- **Map attribution is a small line of text** (OpenFreeMap, OpenMapTiles, OSM, adsb.lol ODbL) instead of MapLibre's (i) button, to keep the map clean while still crediting sources.
- **The map uses a TextureView** so Haze can blur it under the glass overlays.
- **Follow mode eases the camera every frame** rather than calling `animateCamera`, which the per-frame move would cancel. Any pan gesture exits follow.

### Testing
- **Robolectric runs tests with a plain `Application`** (`robolectric.properties`). The real one loads MapLibre's native code, which can't run on the JVM.
- **Screenshot tests swap the MapLibre map for a drawn backdrop** (`FakeRadarBackdrop`) and serve a painted photo through Coil's fake engine. The real map is checked on the emulator.
- **Robolectric needs `--add-opens`** for `jdk.internal.access` on JDK 25 (set in `app/build.gradle.kts`).
