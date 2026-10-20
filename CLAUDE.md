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

## How to work with me (autonomous mode)

This is a side project I work on around a full-time job. I want minimal involvement. Work autonomously for long stretches and don't wait on me.

- **Decide, don't ask.** When something is ambiguous, pick the option that best fits `docs/DESIGN.md`, write the decision and the reason in `docs/DECISIONS.md`, and keep going. Only stop for something that truly needs me: an API key, a paid service, or deleting my data.
- **Verify it yourself instead of asking me to test.** For every feature:
    - The project must build: `./gradlew assembleDebug`.
    - Write unit tests for data, parsing and logic, and keep `./gradlew test` green.
    - Use Roborazzi screenshot tests for every screen and key state, then look at the generated PNGs yourself to judge layout and visual quality. Fix what looks wrong before moving on.
    - Run `./gradlew lint` and fix real issues.
- **Use fakes when keys are missing.** If a feature needs an API key I haven't added yet, build it against a fake data source with realistic sample data, so the UI and screenshots still work. Note in `docs/STATUS.md` that it needs a key.
- **Commit often.** Make small, clear git commits after each working step, and push to `origin` if a remote is set.
- **Keep a status file.** `docs/STATUS.md` lists what's done, what's in progress, what's next, and anything waiting on me. Update it as you go. It's how I catch up.
- **Check in only at phase ends.** Tell me when a roadmap phase is complete, with a short list of what to look at on my phone. Don't ask me to test individual steps.
- **Work through the phases in order** from the Roadmap in `docs/DESIGN.md`. When a phase is done and verified, start the next one.
- You may add well-known, actively maintained, free libraries when they clearly help (e.g. Roborazzi, Hilt, Turbine, MockK). Record each addition in `docs/DECISIONS.md`.
- I'm new to Android Studio, so when I do need to do something by hand, give me exact click-by-click steps.

## Delivery

- Set up a GitHub Actions workflow early in Phase 1 that builds a signed release APK on every push to `main` and attaches it to a GitHub Release, so I can install updates from my phone with Obtainium.
- The signing keystore and its passwords come from GitHub Secrets. Never commit the keystore. Tell me the exact secrets to add, once.

## Commands

- Build debug APK: `./gradlew assembleDebug`
- Install on connected phone: `./gradlew installDebug`
- Unit tests: `./gradlew test`
- Lint: `./gradlew lint`
