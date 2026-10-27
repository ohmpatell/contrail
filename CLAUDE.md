# Contrail

Personal Android flight tracking app for one aviation enthusiast (me). Flighty-grade trip companion plus a live radar, built only on free data. The full product spec is in `docs/DESIGN.md`. Read it before starting any feature, and follow it unless I say otherwise.

## Non-negotiables

- **Free only.** Only free APIs and free tiers (see the Data sources table in `docs/DESIGN.md`). No paid services, no Firebase, no backend server.
- **No secrets in the repo.** Release builds get API keys from GitHub Secrets at build time (`CONTRAIL_LOGOSTREAM_API_KEY`, `CONTRAIL_OPENSKY_CLIENT_ID`, `CONTRAIL_OPENSKY_CLIENT_SECRET`), so the published APK works with no setup (my choice, knowing the public APK makes them extractable). Debug builds read them from the git-ignored `local.properties`. A key saved in the app's Settings (encrypted, DataStore + Android Keystore) overrides the built-in one. Never hardcode keys and never commit them.
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
- OpenSky calls go through a credit guard that tracks the daily budget, and history results are cached on device.
- Package by feature: `feature/radar`, `feature/trips`, `feature/aircraft`, `feature/airport`, `feature/passport`, `feature/sky`, `feature/airports`, `feature/settings`, plus `core/designsystem`, `core/data`, `core/network`, `core/database`.

## Design system

- Tokens (colors, type, spacing, shapes, motion specs) live in `core/designsystem` and are the only source of styling. No hardcoded colors or sizes in feature code.
- Fonts: Space Grotesk (display), Inter (UI), JetBrains Mono (numbers). Bundle them as resources.
- Dark "night cockpit" theme first. Airline accent colors come from a per-airline livery config.
- Respect the system "remove animations" setting.
- Radar aircraft motion is interpolated inside MapLibre layers, not recomposed in Compose every frame.

## Data source etiquette

- Send a descriptive User-Agent on every request (Planespotters requires identifying requests).
- Show photographer credit and link for every Planespotters photo.
- Respect rate limits: adsb.lol and airplanes.live about 1 request per second max, OpenSky credit budget. Cache aggressively.
- Label callsign-derived routes as "route from callsign" unless they come from your own ticket. Label every rotation and timing estimate as "estimated".
- logostream (free plan): show "Logos by logostream" linked to https://logostream.dev, never cache logo images on disk, and track the monthly request quota from its `X-RateLimit-*` headers.
- Credit OpenStreetMap (ODbL) wherever Overpass geometry is drawn, and link every news item back to its source.

## How to work with me (autonomous mode)

This is a side project I work on around a full-time job. I want minimal involvement. Work autonomously for long stretches and don't wait on me.

- **Decide, don't ask.** When something is ambiguous, pick the option that best fits `docs/DESIGN.md`, write the decision and the reason in `docs/DECISIONS.md`, and keep going.
- **Pause and ask me only for these:** I need to register for a free API key or account, a feature can't work without a service I haven't set up, something would cost money, or you'd delete my data. When that happens:
    - Stop and tell me exactly which service, the sign-up link, which plan to pick (must be free), and where to put the key (`local.properties` with the exact variable name).
    - Wait for me to say it's done before continuing with anything that depends on it. Keep working on other parts of the stage in the meantime if you can, and ask all such questions together rather than one at a time.
- **Work fast.** Speed matters; I'm waiting on you.
    - During development, compile only what changed: `.\gradlew.bat :app:compileDebugKotlin` or the affected module, not a full build every step.
    - Run only the unit tests for the code you changed, with `--tests`. Run the full test suite once at the end of the stage.
    - Keep Gradle fast: enable the configuration cache, build cache and parallel builds in `gradle.properties`, and keep the Gradle daemon running.
    - Don't re-read `docs/DESIGN.md` in full each step; read only the section you're working on.
    - Don't refactor or polish working code outside the current task.
- **Verify efficiently.** For every stage:
    - Unit tests for data, parsing and logic, kept green.
    - Roborazzi screenshot tests for each main screen in its main state only, not every variant. Record and inspect them once per screen when that screen is finished, not after every change.
    - Start the emulator, install, and take adb screenshots only once, at the end of the stage. Fix what looks wrong, then finish.
    - Run `.\gradlew.bat lint` once at the end of the stage and fix real issues.
- **Use fakes when keys are missing.** If a feature needs an API key I haven't added yet, build it against a fake data source with realistic sample data so the UI still works, ask me for the key as above, and note it in `docs/STATUS.md`.
- **Commit often.** Make small, clear git commits after each working step, and push to `origin` if a remote is set.
- **Keep a status file.** `docs/STATUS.md` lists what's done, what's in progress, what's next, and anything waiting on me. Update it as you go. It's how I catch up.
- **Check in only at phase ends.** Tell me when a roadmap phase is complete, with a short list of what to look at on my phone. Don't ask me to test individual steps.
- **Work through the phases in order** from the Roadmap in `docs/DESIGN.md`. When a phase is done and verified, start the next one.
- You may add well-known, actively maintained, free libraries when they clearly help (e.g. Roborazzi, Hilt, Turbine, MockK). Record each addition in `docs/DECISIONS.md`.
- I'm new to Android Studio, so when I do need to do something by hand, give me exact click-by-click steps.

## Delivery

- Set up a GitHub Actions workflow early in Phase 1 that builds a signed release APK on every push to `main` and attaches it to a GitHub Release, so I can install updates from my phone with Obtainium.
- The signing keystore and its passwords come from GitHub Secrets. Never commit the keystore. Tell me the exact secrets to add, once.

## Commands (Windows PowerShell: use .\gradlew.bat, never chain with &&)

- Quick compile check: `.\gradlew.bat :app:compileDebugKotlin`
- Build debug APK: `.\gradlew.bat assembleDebug`
- Install on emulator or phone: `.\gradlew.bat installDebug`
- Unit tests for one class: `.\gradlew.bat testDebugUnitTest --tests "*ClassName*"`
- All unit tests: `.\gradlew.bat testDebugUnitTest`
- Lint: `.\gradlew.bat lint`