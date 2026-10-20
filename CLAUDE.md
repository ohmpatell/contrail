# Contrail

Personal Android flight tracking app for one aviation enthusiast (me). Flighty-grade trip companion plus a live radar, built only on free data. The full product spec is in `docs/DESIGN.md`. Read it before starting any feature, and follow it unless I say otherwise.

## Non-negotiables

- **Free only.** Only free APIs and free tiers (see the Data sources table in `docs/DESIGN.md`). No paid services, no Firebase, no backend server.
- **No secrets in the repo.** API keys are entered by me in the app's Settings screen and stored encrypted on device (EncryptedSharedPreferences or DataStore + Keystore). Never hardcode keys, never commit them, never put them in `local.properties` that gets committed.
- **Design is the top priority.** This app should look and feel stunning: smooth 120 Hz motion, shared-element transitions, spring physics, frosted glass, split-flap text. No default Material look, no placeholder-quality UI left behind. If a screen works but looks plain, it isn't done.
- **No login, no accounts, no analytics, no ads.**
- **Offline first.** Screens read from the Room database; repositories refresh it from the network.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 components with a fully custom theme
- MapLibre Native Android + OpenFreeMap vector tiles (custom dark style)
- Ktor client + kotlinx.serialization for networking
- Coil 3 for images
- Room for local storage
- WorkManager for background jobs, Jetpack Glance for widgets
- ML Kit Subject Segmentation (aircraft photo cutouts), ML Kit Barcode Scanning (boarding passes)
- CameraX + rotation-vector sensor for Sky View
- Hilt for dependency injection
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Architecture

- Layers: UI (Compose screens) → ViewModels (StateFlow UI state) → Repositories → data sources + Room.
- Screens never call the network directly.
- Each external API sits behind an interface so providers can be swapped (e.g. `LiveTrafficSource` implemented by adsb.lol, airplanes.live, OpenSky).
- FlightAware AeroAPI calls go through a quota guard that tracks estimated monthly spend and stops at the cap set in Settings.
- Package by feature: `feature/radar`, `feature/trips`, `feature/aircraft`, `feature/airport`, `feature/passport`, `feature/sky`, `feature/logbook`, plus `core/designsystem`, `core/data`, `core/network`, `core/database`.

## Design system

- Tokens (colors, type, spacing, shapes, motion specs) live in `core/designsystem` and are the only source of styling. No hardcoded colors or sizes in feature code.
- Fonts: Space Grotesk (display), Inter (UI), JetBrains Mono (numbers). Bundle them as resources.
- Dark "night cockpit" theme first. Airline accent colors come from a per-airline livery config.
- Respect the system "remove animations" setting.
- Radar aircraft motion is interpolated inside MapLibre layers, not recomposed in Compose every frame.

## Data source etiquette

- Send a descriptive User-Agent on every request (Planespotters requires identifying requests).
- Show photographer credit and link for every Planespotters photo.
- Respect rate limits: adsb.lol and airplanes.live about 1 request per second max, OpenSky credit budget, AeroAPI spend cap. Cache aggressively.
- Label callsign-derived routes as "route from callsign" unless confirmed by AeroAPI.

## How to work with me

- I'm experienced with code but new to Android Studio, so explain Android-specific steps briefly when they come up (Gradle sync, run configurations, device setup).
- Work in small steps. After each meaningful chunk, tell me what to run on my phone and what I should see.
- Make sure the project builds (`./gradlew assembleDebug`) before saying a step is done.
- Ask before adding a new third-party dependency that isn't listed above.
- Follow the roadmap phases in `docs/DESIGN.md` in order unless I say otherwise.

## Commands

- Build debug APK: `./gradlew assembleDebug`
- Install on connected phone: `./gradlew installDebug`
- Unit tests: `./gradlew test`
- Lint: `./gradlew lint`
