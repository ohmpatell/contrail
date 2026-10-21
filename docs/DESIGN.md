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
| Storage | Room (SQLite) | Your flights, watchlist, favourite airports, caches, all offline |
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
3. **Livery profile renders.** One consistent vector side-profile style per aircraft type (A380, 747, 777, 787, A350, A330, A320 family, 737, E-Jets, CRJ, turboprops, business jets and more). Each drawing has paint zones (fuselage, belly, cheatline, tail, engines, winglets). For the "floating plane" images on flight cards, aircraft pages, the Passport and the collection, the livery comes from, in order:
   1. A logostream livery image, where the plan includes it.
   2. A data-driven livery: airline colours, a cheatline and the airline's logostream tail art on the fin. Etihad, Cathay Pacific and Air Canada get hand-tuned schemes; every other airline gets a tinted scheme from its brand colours.
   3. The photo cutout, as the last fallback.
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

Contrail has five tabs: Trips, Radar, Sky, Airports and Passport. Aircraft and airport pages open from anywhere.

### Trips (home)

A **trip** is a journey ("Toronto to Manila") made of one or more flight **segments**. The Trips tab opens on your next trip.

- The next flight is a large glass card: floating aircraft, self-drawing route arc, split-flap airport codes and a countdown to departure.
- Each trip is a glass card with its segments stacked as a journey. Each segment shows flight number, route, local times, type, tail number once known, terminal, seat, cabin and the aircraft's rarity badge.
- **Connections** sit between segments and show:
  - the connection time, with a **tight** (under 90 minutes), **comfortable** (90 minutes to 4 hours) or **long** (over 4 hours) badge;
  - any terminal change ("T1 → T3");
  - whether it's a **layover** (you stay airside, e.g. Hong Kong on the way to Manila) or a **stopover** (you leave the airport, e.g. Abu Dhabi on the way to India).
- **Layover or stopover is guessed automatically:** over 24 hours, or an overnight connection with a hotel, is a stopover; everything else is a layover. One tap on the badge switches it, and the choice sticks.
- **A stopover gets its own mini-section:** arrival and departure times, the city's live local time and weather, and a countdown to the next flight.
- Add a trip by importing a ticket (below) or by entering a flight by number and date. Typing a flight number fills in its usual route from the free callsign database; the times come from you.

### Ticket import

Import fills in a whole trip from whatever you have, entirely on the phone.

- **Sources:** a PDF e-ticket or itinerary, a screenshot or photo of a boarding pass, a live barcode scan, or pasted confirmation-email text.
- **Share to Contrail:** Contrail appears in the Android share sheet for PDFs, images and text, so you can share an e-ticket straight from email or the Etihad or Cathay Pacific app.
- **On-device parsing, no network:**
  - Boarding-pass barcodes (IATA BCBP) are read with ML Kit Barcode Scanning.
  - Text in screenshots, photos and PDFs is read with ML Kit Text Recognition. PDFs are rendered page by page with Android's `PdfRenderer` and scanned for both barcodes and text.
- **What it extracts:** booking reference (PNR), every segment (flight number, date, route, departure and arrival times, terminals), cabin, seat, fare class, aircraft type and passenger name. Segments are grouped into one trip, and connections are classified as above.
- **A review screen always comes first.** It shows everything that was found, flags anything missing or unsure, and lets you fix any field before saving.
- The parsers are tested against realistic, made-up sample tickets in Etihad, Cathay Pacific and Air Canada formats.

### Lounges

- **Log a lounge visit** on any segment or connection: lounge name, airport, terminal, date, time in and out, how you got in (cabin, status, card or paid), a 1 to 5 rating and a short note.
- **Suggestions** for the airport and terminal come from a curated list for your main airports (YYZ, AUH, HKG, MNL, DEL, BOM), plus OpenStreetMap lounges (`amenity=lounge`, credited under ODbL) everywhere else. There is no free lounge database with an API, and OSM coverage is uneven (Hong Kong is well mapped, Toronto has a single lounge), so the curated list leads and OSM fills in.
- **Add a lounge by hand** when it isn't listed. Contrail remembers it for next time at that airport.
- Lounge visits feed the Passport's Lounges section.

### Flight detail

- Hero: the floating aircraft over a map with the great-circle route drawing itself.
- Timeline: gate out, takeoff, landing and gate in. Scheduled times come from your ticket. Once the flight is seen on ADS-B, takeoff and landing get live estimates, each clearly labelled "estimated".
- Live map once airborne, with altitude, speed and a progress arc. The flight is found on adsb.fi (adsb.lol as backup) by its ICAO callsign: the ticket's IATA flight number is converted, e.g. EY 141 → ETD141.
- Aircraft card: registration, type, rarity, seat map link, and how many times you've flown on this exact tail. Until you enter a tail number, the card shows the type from the ticket.
- Lounge visits for this flight, and a button to log one.

### Aircraft rotation (the cockpit-visit planner)

This panel shows your aircraft's whole day so you can judge whether the crew will have time for a cockpit visit after landing. It's built only from free sources, and every estimate is labelled "estimated".

- **Tail number:** you can enter it by hand (from the airline app or the gate). Until then, the panel shows the type from the ticket and "aircraft not assigned yet".
- **Where it's coming from:** with a tail, Contrail finds the aircraft live on adsb.fi or adsb.lol. If its current flight is heading to your departure airport, it's shown as your inbound, live on a mini-map with an estimated arrival time. A late inbound usually means a late departure for you.
- **Where it went before that:** its earlier legs, from OpenSky's flights-by-aircraft history (free account).
- **Turnaround at your arrival:** Contrail looks up the same flight number on each of the last 7 days on OpenSky, measures how long that day's aircraft stayed on the ground at your destination before its next departure, and shows the typical turnaround as a big number with a badge:
  - **Relaxed:** 2 hours or more, or the aircraft usually overnights at your destination. Good time to ask.
  - **Maybe:** 60 to 120 minutes. Ask politely, accept a no.
  - **Tight:** under 60 minutes. Don't ask.
  - These thresholds are defaults; you can change them in Settings.
- Long-haul widebodies like Etihad's and Cathay's often fly a simple out-and-back, so their turnarounds are usually long. Short-haul narrowbodies are where the tight turns happen.

### Radar

- Full-screen dark map of live aircraft, coloured by altitude, gliding smoothly with fading trails.
- Tap a plane for a bottom sheet: photo or cutout, callsign, route, type, registration, altitude, speed, squawk, vertical-speed sparkline.
- Type-accurate icons: every ICAO type code maps to an icon class with the right shape, engine count and relative size (A380 and 747 large four-engined, 777/A350/787/A330 widebody twins, A320/737 narrowbodies, regional jets, turboprops, business jets, helicopters, light aircraft). The ADS-B emitter category is the fallback when the type is unknown.
- Ground clutter: ground vehicles and obstacles (emitter categories C1–C3, and ground targets with no aircraft type) are never drawn. Parked aircraft show only once you zoom into an airport, unless "Show ground traffic" is on in the filters.
- Full route on selection: the selected aircraft's whole route is drawn, the flown part from its track and the rest as a dashed great-circle arc to the destination. Without route data, its trail is shown as usual.
- Filters: airline, type, altitude band, military only, rare types only, watchlist only, show ground traffic.
- **Watchlist** (in the filters): tail numbers, types or airlines you care about. Matches get a glowing ring on the map and, later, a notification when they go airborne or come near.
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

- Hero: the floating aircraft (livery render or cutout) over its real photo, with the photographer's credit, airline logo and rarity badge.
- Registration, type, operator, transponder hex, and today's route.
- Its flights over the last few days, from the free data.
- Your history with it: flown on it, spotted it, or on your watchlist.

### Airport page

- Header: airport logo (or a styled code monogram), name, city, live local time.
- Live departure and arrival boards with split-flap animation, built from live ADS-B: aircraft climbing out of or descending into the airport, with routes from their callsigns. (No free schedule API is left: AeroDataBox dropped its free tier.)
- Terminals, airlines served and a destinations map of every nonstop route.
- Nearby traffic on a mini-radar.
- Decoded weather (METAR and TAF) with a flight-category badge (VFR, MVFR, IFR, LIFR).
- Runway diagram with the likely active runway, and a mini-radar of nearby traffic.
- Quick link to LiveATC for that airport's tower and approach audio.

### Passport

- Lifetime globe of every route flown, drawn as glowing arcs on a dotted globe you can spin.
- Totals: flights, distance, hours aloft, airports, countries, airlines, aircraft types and individual tails.
- **Visited and transited are separate.** Airports where you only made a layover count as "transited". Origins, destinations and stopover cities count as "visited".
- Records: longest flight, highest-rarity aircraft, most-flown route, most-flown tail.
- **Collection:** a grid of every aircraft type as livery renders, greyed out until you fly on it. Rare types unlock with the holographic card.
- **Lounges:** lounges visited, total hours in lounges, favourites, average ratings, and a "lounge collection" grid.
- Year in Review: a swipeable story of the year's flying.
- Import past flights the same way as trips: ticket import or by flight number and date.

### Airports

- Search every airport with scheduled service by code, name or city, instantly and offline.
- Favourites and visited airports, one tap from the list.
- Aviation news feed, filterable by airline and airport (your favourites appear as quick filters).

### Home-screen widgets

- Next-flight countdown with the floating aircraft.
- Plane-overhead widget showing the closest aircraft right now.

## Data sources and free budget

Everything comes from free sources, and nothing needs a payment card. Your flights' schedules come from your own tickets; live tracking, inbound aircraft and rotation history come from free community ADS-B feeds and a free OpenSky account. No paid or card-required service is used anywhere (FlightAware AeroAPI was dropped in Stage 3 for that reason).

| Source | Used for | Free allowance | Key |
| --- | --- | --- | --- |
| [adsb.fi](https://github.com/adsbfi/opendata) | Radar, Sky, your flight live by callsign, your aircraft by registration | Free open data for personal use, 1 request per second | None |
| [adsb.lol](https://api.adsb.lol/) | Backup live positions and callsign lookups | Free, open data (ODbL), 500 requests a day | None |
| [airplanes.live](https://airplanes.live/) | Optional third live provider | Now requires approval by email | Approval |
| [OpenSky Network](https://openskynetwork.github.io/opensky-api/rest.html) | Flight tracks, an aircraft's earlier legs, 7-day turnaround history for your flight number | 400 credits a day anonymous, 4,000 with a free account | Free account (OAuth client) |
| [adsbdb](https://www.adsbdb.com/) | Callsign → route, registration → transponder hex and type | Free | None |
| [Planespotters photo API](https://planespotters.net/photo/api) | Aircraft photos | Free, credit and link back required | None |
| [aviationweather.gov](https://aviationweather.gov/data/api/) | METAR and TAF | Free | None |
| [OurAirports](https://ourairports.com/data/) | Airports, runways, frequencies (bundled in the app) | Public domain | None |
| [logostream](https://airline.logostream.dev/) (Community) | Airline logos and tail art, airline data, routes, airport stats (timezone, airlines served, destinations) | 20,000 requests a month; logos can't be cached; "Logos by logostream" attribution required. Liveries, airport logos, airport data and news need a paid plan | Free key |
| [OpenStreetMap Overpass](https://overpass-api.de/) | Terminal and runway geometry for airport diagrams, lounge suggestions | Free, fair use, ODbL attribution | None |
| Google ML Kit (on device) | Boarding-pass barcodes, text in tickets and screenshots, photo cutouts | Free, runs offline | None |
| [Natural Earth](https://www.naturalearthdata.com/) (bundled) | Land outline for the Passport globe | Public domain | None |
| Aviation RSS (Simple Flying, AeroTime, FlightGlobal, AirlineGeeks, Aviation Week, Google News) | News feed, filtered by airline and airport | Free; headlines and links back to the source | None |
| [RainViewer](https://www.rainviewer.com/api.html) | Weather radar layer | Free for personal use | None |
| [OpenFreeMap](https://openfreemap.org/) | Map tiles | Free | None |
| [Open-Meteo](https://open-meteo.com/) | Winds and weather layers | Free for non-commercial use | None |

How the free budgets are spent:

- OpenSky history is fetched only when you open a flight, about 15 small requests the first time, and every answer is cached on the phone. Its data is published the day after, so a day's result never changes once stored and is never fetched twice.
- Live lookups of your flight and your aircraft run only while you have the flight open, at most every 15 seconds, through the same polite throttles as the radar.

Callsign-based routes from free databases are sometimes wrong, especially for airlines that change callsigns daily, so the app labels them as "route from callsign" rather than confirmed. Your own flights use the route on your ticket.

## Architecture

Screens never call the network directly. They read from the on-phone database, and repositories refresh it in the background. That keeps every screen instant and lets the app work offline on a plane.

&#91;embedded content: app architecture · 4 layers plus on-device stores\]

- **Offline first:** your trips, Passport, favourite airports and cached aircraft pages all work in airplane mode.
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
| Tail number assigned late | Rotation panel shows only the type and the 7-day turnaround until you enter a tail | Clear "not assigned yet" state; the turnaround history needs no tail |
| No free schedule or tail-assignment API | Times only update once the flight is on ADS-B; tail must be entered by hand | Times from your ticket, live estimates once airborne, every estimate labelled |
| OpenSky credit budget used up | Rotation history stops refreshing until the next day | Cache every day's result forever (history never changes), fetch only for upcoming flights |
| No ADS-B coverage over oceans and remote areas | Plane disappears mid-flight (e.g. over the Arabian Sea or Bay of Bengal) | Show an estimated position along the route, clearly marked "estimated" |
| Community APIs change or throttle | Radar slows or shows gaps | Three live providers behind one interface, switch automatically |
| Wrong routes from callsign databases | Odd origin or destination on the radar | Labelled "route from callsign"; your own flights use the ticket's route |
| Rough photo cutouts | Jagged edges on busy ramp photos | Try the next photo, or switch to the livery render |
| Planespotters terms | Photo use must stay credited and unchanged when shared | Credits always shown, cutouts never leave the phone |
| ML Kit segmentation is still beta | API could change in an update | Isolated in one module, livery render as fallback |
| Android background limits | Proximity checks run about every 15 minutes in the background | Live mode with a foreground service when you want real-time alerts |
| Some aircraft are filtered or hidden | Certain military or blocked aircraft never appear | Use adsb.fi and adsb.lol, which don't filter |
| Ticket formats vary | A field is missed or misread on import | The review screen always shows every field before saving, and missing ones are flagged |
