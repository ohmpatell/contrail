# Contrail — Flight App Design Doc

Oct 20, 2026 · @Ohm

## Vision and principles

Contrail is a personal Android app for one aviation enthusiast: a Flighty-grade trip companion plus a live radar, built only on free data and free tools. Airline apps (Etihad, Cathay Pacific) already push gate and delay changes, so Contrail spends its effort on the aircraft itself, its rotation, and the sky around you.

Every screen follows three rules:

1. **The airplane is the hero.** Every flight, tail number and radar contact shows a real photo, a floating cutout or a livery render. No generic icons where a real aircraft can be shown.
2. **Motion explains.** Every animation shows where something came from or where it is going: a card flies into its detail screen, a route draws itself, a plane glides between radar updates. Nothing moves just to decorate.
3. **Free and private.** No account, no paid backend, no ads, no tracking. Keys for the few services that need one live only on your phone.

## Technology

Contrail is a native Android app in Kotlin with Jetpack Compose, using MapLibre Native for maps. Native gives the smoothest animation, direct access to the compass and camera, home-screen widgets, and no browser restrictions on which APIs it can call.

| Option | Animation and smoothness | Maps | Compass, camera, widgets | Verdict |
| --- | --- | --- | --- | --- |
| Kotlin + Jetpack Compose | Highest: GPU shaders, shared-element transitions, spring physics, 120 Hz | MapLibre Native, 3D globe | Full access | **Chosen** |
| Flutter | High, but its own renderer, so it feels slightly less native | MapLibre plugin, fewer features | Through plugins | Good second choice |
| Web app (PWA) | Good, but heavy maps and blur effects drop frames | MapLibre GL JS | Limited, no real widgets | Too limiting for this app |

| Layer | Library | Why |
| --- | --- | --- |
| UI | Jetpack Compose with Material 3, fully custom theme | Modern declarative UI, best animation APIs on Android |
| Motion | Compose animation, `SharedTransitionLayout`, spring specs | Card-to-detail flights, physics-based gestures |
| Visual effects | `RenderEffect` blur and AGSL shaders | Frosted glass, holographic shimmer, aurora backgrounds |
| Maps | MapLibre Native Android + OpenFreeMap vector tiles | Free, no key, custom dark style, globe view |
| Networking | Ktor client + kotlinx.serialization | Lightweight, coroutine-native |
| Images | Coil 3 | Fast loading, disk cache, crossfades |
| Image cutouts | ML Kit Subject Segmentation (on-device) | Turns aircraft photos into floating cutouts |
| Storage | Room (SQLite) | Your flights, logbook, caches, all offline |
| Background work | WorkManager | Rotation refresh, proximity checks |
| Widgets | Jetpack Glance | Next-flight countdown, plane-overhead widget |
| Sensors and camera | Rotation-vector sensor + CameraX | Sky View: point at a plane to identify it |
| Barcode | ML Kit Barcode Scanning | Boarding pass import, works offline |
| 3D (optional, later) | Filament | Rotatable 3D aircraft models |

## Design system and motion

The look is "night cockpit": a deep blue-black canvas, frosted-glass cards, and one accent colour per screen pulled from the airline's livery. Etihad screens glow warm gold, Cathay screens glow jade, and the radar uses an altitude colour ramp.

| Token | Value | Used for |
| --- | --- | --- |
| Canvas | Near-black navy, subtle animated aurora gradient | App background |
| Glass surface | 12% white tint + background blur | Cards, sheets, map overlays |
| Airline accent | Sampled from livery colours per airline | Headers, progress arcs, glow |
| Status | Green on time, amber delayed, red cancelled, sky blue airborne | Status chips |
| Altitude ramp | Ground grey → low teal → mid blue → cruise violet → high magenta | Radar icons and trails |
| Display type | Space Grotesk (free, Google Fonts) | Flight numbers, airport codes |
| UI type | Inter | Body text, labels |
| Numeric type | JetBrains Mono | Times, altitudes, speeds, squawks |

Signature animations:

- **Split-flap boards.** Airport codes, times and gates flip in like a departure board, with a soft haptic tick per character.
- **Card-to-detail flight.** Tapping a flight card makes the aircraft image fly into the detail hero, and the card expands around it.
- **Self-drawing routes.** Great-circle arcs draw from origin to destination, and a small plane rides the arc to show live progress.
- **Floating hero aircraft.** The cutout plane hovers with a slow bob and soft shadow, and tilts with the phone's gyroscope for parallax.
- **Smooth radar.** Planes glide between data updates using speed and heading, so the map never jumps. Trails fade from the altitude colour to transparent.
- **Globe intro.** On launch, the globe spins to your next destination.
- **Rare-aircraft unlock.** A holographic shimmer card (AGSL shader) reveals a new type in your collection.
- **Passport bloom.** Lifetime routes bloom across the globe and stat counters roll up like an odometer.
- **Takeoff refresh.** Pull to refresh lifts a tiny plane off a runway.

Performance rules: aim for 120 Hz with no dropped frames. Radar motion runs inside MapLibre's GPU layers, not in Compose, and baseline profiles keep start-up fast. Every animation respects Android's "remove animations" setting.

## Aircraft imagery

Contrail uses four kinds of aircraft image. Each one falls back to the next when it isn't available, so every screen always has a plane.

1. **Real photos of the exact aircraft.** The [Planespotters photo API](https://planespotters.net/photo/api) is free with no key and looks up the latest photo by registration or transponder hex code. Each photo must show the photographer's credit and link back to its Planespotters page. If no photo exists, the app falls back to a Wikimedia Commons photo of the same type.
2. **Floating cutouts (the "PNG plane" look).** The app runs Google's [ML Kit Subject Segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android) on the photo, entirely on the phone, to lift the aircraft off its background. The result is a transparent image of the real tail, in its real livery, that floats over the glass UI with a shadow. It takes about 200 ms per photo and is cached forever.
   - Works best on side-on shots against sky. Busy ramp photos can leave rough edges, so you can tap to try the next photo or switch to the livery render.
   - A cutout changes the photographer's image, so it stays on your phone only, always with the credit shown.
3. **Livery profile renders.** Clean side-view vector drawings, one per aircraft type (A350, A380, 777, 787, A330, A320 family, 737, E-Jets and more). Each drawing has paint zones (fuselage, belly, cheatline, tail, engines, winglets), and a small livery file per airline fills them with colours and a tail logo. These give perfectly consistent "floating plane" images for flight cards, the Passport and the collection. I'd build detailed liveries for the airlines you fly most (Etihad, Cathay Pacific) and use a tinted generic scheme for the rest.
4. **3D models (later).** Free, openly licensed 3D aircraft models rendered with Filament, so you can spin your next aircraft in your hand. This is a stretch goal, because good free models with accurate liveries are rare.

**Rarity and "I flew on something special".** Every aircraft type gets a rarity tier from a curated table in the app, based on how many are still flying with passengers:

| Tier | Examples | Badge |
| --- | --- | --- |
| Legendary | Boeing 747-8I, 747-400 passenger, Airbus A340 | Gold holographic |
| Rare | Airbus A380, Boeing 777-200, A330-200 on long haul | Silver shimmer |
| Uncommon | 777X when it enters service, A220, A321XLR | Blue |
| Common | A320 family, 737, 787, A350 | None |

The tiers are a starting point you can edit. The table only goes stale slowly, so a manual update a few times a year keeps it honest. You can also mark a flight as a special livery, retro scheme or delivery flight, and it gets its own badge in the Passport.

## Screens and features

Contrail has five tabs: Trips, Radar, Sky, Passport and Logbook. Aircraft and airport pages open from anywhere.

### Trips (home)

- Upcoming flights as large glass cards: floating aircraft, route arc, split-flap countdown to departure.
- Add a flight by number and date, or scan the boarding pass barcode (works offline).
- Each card shows type, tail number once assigned, terminal, gate when known, and the aircraft's rarity badge.

### Flight detail

- Hero: the floating aircraft over a map with the great-circle route drawing itself.
- Timeline: scheduled, estimated and actual times for gate out, takeoff, landing and gate in.
- Live map once airborne, with altitude, speed and a progress arc.
- Aircraft card: registration, type, age if known, engines, seat map link, and how many times you've flown on this exact tail.

### Aircraft rotation (the cockpit-visit planner)

This panel shows your aircraft's whole day so you can judge whether the crew will have time for a cockpit visit after landing.

- **Where it's coming from:** the inbound flight that brings your aircraft, live on a mini-map with its ETA. A late inbound usually means a late departure for you.
- **Where it went before that:** the earlier legs of the day, back to its overnight stop.
- **Where it goes after you:** the next leg after you get off, with its scheduled departure time.
- **Turnaround at your arrival:** next leg's departure minus your scheduled arrival, shown as a big number with a badge:
  - **Relaxed:** 2 hours or more, or the aircraft overnights at your destination. Good time to ask.
  - **Maybe:** 60 to 120 minutes. Ask politely, accept a no.
  - **Tight:** under 60 minutes. Don't ask.
  - These thresholds are defaults; you can change them in Settings.
- Airlines often assign the tail number only a day or so before departure. Until then, the panel shows "aircraft not assigned yet" and checks again on its own.
- Long-haul widebodies like Etihad's and Cathay's often fly a simple out-and-back, so their turnarounds are usually long. Short-haul narrowbodies are where the tight turns happen.

### Radar

- Full-screen dark map of live aircraft, coloured by altitude, gliding smoothly with fading trails.
- Tap a plane for a bottom sheet: photo or cutout, callsign, route, type, registration, altitude, speed, squawk, vertical-speed sparkline.
- Filters: airline, type, altitude band, military only, rare types only.
- Layers: airport labels, weather radar, day/night terminator line.
- Highlights: emergency squawks (7700, 7600, 7500) pulse red; rare types and your watchlist get a glowing ring.
- Follow mode: lock the camera on one aircraft, with a cockpit-style heading and altitude readout.

### Sky (overhead and proximity)

- **Closest plane now:** a big card for the nearest aircraft, with distance, bearing arrow, altitude and a countdown to its closest approach.
- **Overhead list:** everything within your chosen radius (5 to 100 km), sorted by distance.
- **Sky View:** point the camera at the sky and labels pin to each aircraft using GPS, compass and live positions.
- **Proximity alerts:** a notification when something worth looking up passes within your radius: a rare type, military, an emergency squawk, a watched tail, or anything below an altitude you set. You choose which.
- **Approach watch:** at an airport, it shows the next arrivals lining up and which runway is likely in use from the wind.

### Aircraft page

- Hero photo or cutout, with photographer credit.
- Registration, type, operator, transponder hex, and today's route.
- Its flights over the last few days, from the free data.
- Your history with it: flown on it, spotted it, or on your watchlist.

### Airport page

- Live departure and arrival boards with split-flap animation.
- Decoded weather (METAR and TAF) with a flight-category badge (VFR, MVFR, IFR, LIFR).
- Runway diagram with the likely active runway, and a mini-radar of nearby traffic.
- Quick link to LiveATC for that airport's tower and approach audio.

### Passport

- Lifetime globe of every route flown, drawn as glowing arcs.
- Totals: flights, distance, hours aloft, airports, countries, airlines, aircraft types and individual tails.
- Records: longest flight, highest-rarity aircraft, most-flown route, most-flown tail.
- **Collection:** a grid of every aircraft type as livery renders, greyed out until you fly on it. Rare types unlock with the holographic card.
- Year in Review: a swipeable story of the year's flying.
- Import past flights by boarding pass scan or by flight number and date.

### Logbook (spotter tools)

- **Spotting log:** take or import a photo and the app uses its time and location to suggest which aircraft it was, from live and recent positions.
- **Watchlist:** tail numbers, types or airlines that trigger a notification when they go airborne or come near.
- Stats for spotted types and airlines, separate from flown ones.

### Home-screen widgets

- Next-flight countdown with the floating aircraft.
- Plane-overhead widget showing the closest aircraft right now.

## Data sources and free budget

Everything except trip details comes from free, keyless community sources. Your own flights and aircraft rotations use FlightAware AeroAPI's personal tier, which includes [up to $5 of free queries a month](https://flightaware.com/commercial/aeroapi/) ($10 if you run an ADS-B feeder). The app tracks spend and stops at a cap you set, so it never bills you.

| Source | Used for | Free allowance | Key |
| --- | --- | --- | --- |
| [adsb.lol](https://api.adsb.lol/) | Radar, Sky, proximity alerts | Free, open data (ODbL), throttles heavy bursts | None |
| [airplanes.live](https://airplanes.live/) | Backup live positions | Free for non-commercial use, about 1 request per second | None |
| [OpenSky Network](https://openskynetwork.github.io/opensky-api/rest.html) | Backup positions, recent airport flights, flight tracks | 400 credits a day anonymous, 4,000 with a free account | Free account |
| [adsbdb](https://www.adsbdb.com/) | Callsign → route, hex → registration and type | Free | None |
| [Planespotters photo API](https://planespotters.net/photo/api) | Aircraft photos | Free, credit and link back required | None |
| [FlightAware AeroAPI](https://flightaware.com/commercial/aeroapi/) (Personal) | Your flights, tail assignment, inbound and next legs | Up to $5 of queries a month free | Free key |
| [AeroDataBox](https://aerodatabox.com/) (RapidAPI Basic) | Airport boards, schedule backup | About 600 units a month, hard stop with no overage | Free key |
| [AviationStack](https://aviationstack.com/pricing) | Last-resort flight status | 100 requests a month | Free key |
| [aviationweather.gov](https://aviationweather.gov/data/api/) | METAR and TAF | Free | None |
| [OurAirports](https://ourairports.com/data/) | Airports, runways, frequencies (bundled in the app) | Public domain | None |
| [OpenFreeMap](https://openfreemap.org/) | Map tiles | Free | None |
| [Open-Meteo](https://open-meteo.com/) | Winds and weather layers | Free for non-commercial use | None |

How the AeroAPI budget is spent:

- Rotation data is fetched only for flights departing within the next 48 hours, plus whenever you open that flight.
- Every response is cached in the phone's database, so revisiting a flight costs nothing.
- A spend meter in Settings shows this month's estimated cost against your cap. At the cap, the app falls back to AeroDataBox and the free live feeds.
- Once your aircraft is airborne, live tracking switches to adsb.lol, which is free.

Callsign-based routes from free databases are sometimes wrong, especially for airlines that change callsigns daily, so the app labels them as "route from callsign" rather than confirmed.

## Architecture

Screens never call the network directly. They read from the on-phone database, and repositories refresh it in the background. That keeps every screen instant and lets the app work offline on a plane.

&#91;embedded content: app architecture · 4 layers plus on-device stores\]

- **Offline first:** your trips, Passport, logbook and cached aircraft pages all work in airplane mode.
- **No server:** a native app can call every API directly, so there's no proxy, no Firebase and nothing to host.
- **Swappable providers:** each source sits behind one interface, so a dead API can be replaced without touching the screens.

## Build and install

The app is built for free in the cloud by GitHub Actions, and you install the APK straight onto your phone. You don't need Android Studio or the Play Store.

1. The code lives in a GitHub repository on a free account.
2. Every push triggers a GitHub Actions workflow that builds a signed release APK and attaches it to a GitHub Release.
3. The signing key is stored in GitHub Secrets, so every update installs over the previous version and keeps your data.
4. On your phone, the free [Obtainium](https://github.com/ImranR98/Obtainium) app watches the repository and offers each new release as an update.
5. API keys are never in the code. You paste them into Contrail's Settings once, and they're stored encrypted on the phone.

Android's new developer verification rules affect sideloaded apps. Google's [timeline](https://support.google.com/android-developer-console/answer/16561738) applies them first in Brazil, Indonesia, Singapore and Thailand, with a [global rollout planned for 2027](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/), so installs in Canada work as normal today. To stay covered later, register the app under Google's free limited-distribution account for hobbyists. It allows installs on up to 20 devices with no fee or government ID.

## Roadmap

The build starts with the radar because it needs no keys and shows off the design system immediately. Every phase ends with an installable APK you can use.

&#91;embedded content: build roadmap · 5 phases\]

The optional 3D models come after phase 5, once the core app is done.

## Known limits and risks

The biggest gap in free data is the aircraft rotation before a tail is assigned. Everything else has a workable fallback.

| Risk | What you'd notice | Mitigation |
| --- | --- | --- |
| Tail number assigned late | Rotation panel empty until about a day before | Clear "not assigned yet" state, automatic recheck |
| AeroAPI free credit used up | Rotation and status stop refreshing for the month | Hard spend cap, cache everything, fall back to AeroDataBox and live feeds |
| No ADS-B coverage over oceans and remote areas | Plane disappears mid-flight (e.g. over the Arabian Sea or Bay of Bengal) | Show an estimated position along the route, clearly marked "estimated" |
| Community APIs change or throttle | Radar slows or shows gaps | Three live providers behind one interface, switch automatically |
| Wrong routes from callsign databases | Odd origin or destination on the radar | Labelled "route from callsign", corrected once AeroAPI confirms |
| Rough photo cutouts | Jagged edges on busy ramp photos | Try the next photo, or switch to the livery render |
| Planespotters terms | Photo use must stay credited and unchanged when shared | Credits always shown, cutouts never leave the phone |
| ML Kit segmentation is still beta | API could change in an update | Isolated in one module, livery render as fallback |
| Android background limits | Proximity checks run about every 15 minutes in the background | Live mode with a foreground service when you want real-time alerts |
| Some aircraft are filtered or hidden | Certain military or blocked aircraft never appear | Use adsb.lol and airplanes.live, which don't filter |
