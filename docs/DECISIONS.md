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

## Plan change before Stage 2 (Oct 20, 2026)

Requested by you: Logbook tab and spotting log removed; watchlist moved into the radar filters; new Airports tab (search, favourites, visited, news); logostream added as a data source; livery renders become the main "floating plane" image; three radar fixes (type-accurate icons, ground clutter, full route on selection). DESIGN.md and CLAUDE.md were updated to match.

### What logostream's free plan actually allows
Checked against the OpenAPI spec, the terms (Sept 3, 2026) and live probes with your key:
- **Free ("Community"):** airline logos and tail art, airline data, routes (filtered, 50 per page), airport stats (timezone, airlines served, destinations, hubs), nearby airports, countries.
- **Startup plan only ($40/month):** aircraft liveries, airport logos, airport data, news, aircraft types, airport diagrams, route maps. Free-only is a non-negotiable, so each gets a free replacement:
  - Airport data → bundled OurAirports (public domain); timezone, airlines served and destinations come from logostream's free airport stats and routes.
  - Terminals and runway geometry → OpenStreetMap via the Overpass API (ODbL, credited).
  - News → publisher RSS (Simple Flying, AeroTime, FlightGlobal, AirlineGeeks, Aviation Week) plus Google News RSS searches for airline and airport filters. Headlines link back to the source.
  - Liveries → data-driven vector renders with the airline's free logostream tail art on the fin. The livery source sits behind an interface, so a paid plan would slot in without UI changes.
  - Airport logos → a styled IATA monogram.
- **Caching follows the terms, not the original request.** Free-plan logos must be fetched live on each use, so logo images use Coil's memory cache only and never touch disk. Data responses are cached as you asked: airline data and airport stats permanently, routes weekly. News refreshes at most hourly.
- **Attribution.** "Logos by logostream", linked to https://logostream.dev, is shown in Settings → About, as the terms allow.
- **Usage counter** reads `X-RateLimit-Remaining` and `X-RateLimit-Reset` from every response, so Settings shows the real monthly quota instead of an estimate. A guard stops calling logostream when fewer than 500 requests remain.
- **Key handling.** Debug builds read `LOGOSTREAM_API_KEY` from `local.properties` into BuildConfig. Release builds never embed it: the key is entered in Settings and stored with DataStore, encrypted with an Android Keystore AES-GCM key.

## Stage 2 (Oct 21, 2026)

### Radar fixes
- **Icon classes come from ICAO Doc 8643.** The Mictronics readsb copy (2,788 designators) is bundled as `assets/aircraft_types.json`. Its descriptor (e.g. `L2J`) and wake category map every type to one of 17 classes: Jumbo, Quad, WideTri, Widebody, Narrowbody, RearJet, Regional, Bizjet, Fighter, TurbopropQuad, Turboprop, TurbopropSingle, LightTwin, Light, Helicopter, Glider, Generic. Name patterns separate engine layouts the descriptor can't (CRJ rear engines vs E-Jet underwing, bizjets vs airliners). A per-type wingspan scale sizes variants within a class, and the emitter category is the fallback. `ZZZZ` (ICAO's "no designator") counts as unknown.
- **Clutter is dropped at ingest.** Categories C1–C5 (vehicles and obstacles, including the C4/C5 obstacle classes beyond the C1–C3 you named) and typeless ground targets never reach Room.
- **"Show ground traffic" semantics.** On (default): parked and taxiing aircraft appear once you zoom into an airport (fading in from zoom 8.5 to 10). Off: never shown.
- **Full route uses OpenSky's anonymous `tracks/all`** for the flown part. adsb.lol traces sit behind a browser verification page and can't be called from the app. With no track, a great circle runs from the origin to where our trail begins. The remaining part is a dashed great circle that re-draws twice a second from the interpolated position. Tracks are cached in memory for 90 s; they're live data, so they don't go into Room.
- **adsb.fi is now the primary feed and adsb.lol the backup.** adsb.lol's headers show 4/s, 60/min and **500 per day** per client, which a few hours of radar would exhaust. adsb.fi allows 1/s with no daily cap for personal use and asks for a citation, which is in the attribution line and Settings. Polling is every 5 s, with a 4 s minimum gap.

### Navigation and screens
- **Navigation 3 with an overlay scene for detail pages.** Aircraft, airport and settings pages slide in over the tab shell instead of replacing it, so the MapLibre view, camera and polling survive. Detail view models are assisted-injected per key.
- **Logbook removed.** Watchlist is in the radar filters (tails, types, airlines; glowing rings). The spotting log is gone.
- **Airports tab.** Search runs offline over bundled OurAirports data: 5,332 airports with IATA codes and scheduled service, 7,122 runways, 14,992 frequencies, and an IANA timezone computed at build time with `timezonefinder` (`tools/gen_airports.py`). It's imported into Room on first use.
- **Airport page sources.**
  - Weather: METAR/TAF from aviationweather.gov, cached 10 min.
  - Diagram: OpenStreetMap runways, taxiways, aprons and terminals via Overpass (45 s timeout), cached 30 days, falling back to OurAirports runway ends. The likely active runway is the end most into the wind.
  - Airline and route data: logostream stats (permanent cache) and routes (weekly cache).
  - Live movements: the board and mini-radar come from ADS-B around the airport, polled every 10 s while the page is open.
  - Destinations map: a MapLibre snapshot with a camera we compute ourselves, so the Web Mercator projection of each arc is exact.
- **The live board is built from ADS-B.** It shows aircraft climbing out or descending in within 60 km, plus any whose callsign route starts or ends here, labelled "from ADS-B · route from callsign".
- **AeroDataBox is removed (Oct 21, 2026).** You confirmed it no longer has a free tier, so it's gone from DESIGN.md, Settings and the build. Scheduled boards would need a paid source, so the ADS-B board is the board. A free route stays open for later: FlightAware AeroAPI (planned for Trips in Stage 3) could optionally power boards within its $5 monthly cap. That's left off by default so the budget goes to your own flights.
- **logostream's route data has ambiguous airline codes.** IATA codes are shared (RV is Rouge, Reeve Aleutian and Caspian) and some routes carry the wrong ICAO (Rouge as CPN). Airlines served are grouped by IATA and named from our curated table first, and a logostream profile is used only when its IATA matches.
- **Aircraft page "recent flights" are Contrail's own sightings** (`sighting` table). OpenSky's historical flights need an account (HTTP 403 anonymously).
- **Settings is reachable from the gear on the Airports tab.** Keys are AES-GCM encrypted with a non-exportable Android Keystore key and stored in DataStore.

### Imagery
- **The floating-plane order is: logostream livery (paid plan, not available) → data-driven livery render → photo cutout.** The render is a single parametric side-profile style per icon class: fuselage length and height, a 747 hump or A380 double deck, engine layout with pylons, T-tail, props. Each airline's livery fills fuselage, belly, cheatline, fin and engines. Etihad, Cathay Pacific, Air Canada, Rouge, WestJet, Emirates, Qatar, BA and Lufthansa are hand-tuned; others get white with their brand colour. logostream's free tail art is drawn into the fin.
- **When there's no airline** (private, military), the aircraft page defaults to the cutout instead of a plain white render.
- **ML Kit Subject Segmentation (16.0.0-beta1)** is declared in the manifest so Play services downloads the model with the app. If it isn't ready yet, tapping Cutout retries. Cutouts are stored in app-private files and never shared.
- **Rarity badges shimmer** with an animated gradient: gold for Legendary, silver for Rare, blue for Uncommon (static).

### Libraries added
| Library | Why |
| --- | --- |
| Navigation 3 (`navigation3-runtime`, `navigation3-ui`, `lifecycle-viewmodel-navigation3`) | Detail pages with per-entry view models |
| ML Kit Subject Segmentation (Play services, beta) | On-device photo cutouts, as DESIGN.md specifies |
