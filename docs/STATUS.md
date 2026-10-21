# Status

Last updated Oct 21, 2026.

## Waiting on you

**1. Your logostream key on the phone.** Release builds never embed keys. On the phone: Airports tab → gear (top right) → logostream → **Add key**, paste the key from `local.properties`, **Save**. Debug builds read it from `local.properties` automatically.

**2. Optional:**
- A free **OpenSky Network** account (https://opensky-network.org), for an aircraft's flights over the last few days. Without it, the aircraft page shows the flights Contrail itself has seen.
- **airplanes.live** now needs approval by email (contact@airplanes.live). adsb.fi and adsb.lol cover live positions without it.

Releases: signing secrets are set and every push to `master` publishes a signed APK to GitHub Releases for Obtainium. Keep `C:\Users\ohm20\contrail-signing\contrail-release.jks` and its password backed up.

## Done

### Stage 1: foundation and live radar
- Project structure, version catalog, Hilt, Room, DataStore, Ktor, Coil, MapLibre.
- Night-cockpit design system: tokens, bundled fonts, spring motion that respects "remove animations", AGSL aurora, Haze frosted glass, split-flap text, custom icons.
- Floating glass tab bar and styled placeholder tabs.
- Live radar: interpolated motion in MapLibre, altitude colours, fading trails, filters, layers, follow mode, aircraft sheet with Planespotters photos and callsign routes.
- GitHub Actions: tests, lint, signed release APK on every push.

### Stage 2: aircraft, airports, logostream, liveries
- Radar:
  - Type-accurate icons for every ICAO type (17 classes with the right engines, layout and relative size; emitter category as fallback).
  - Vehicles and obstacles removed; parked aircraft only when zoomed into an airport, plus a "Show ground traffic" toggle.
  - The selected aircraft's whole route: flown track solid (OpenSky), the rest a dashed great circle to its destination.
  - Watchlist (tails, types, airlines) in the filters, with glowing rings on the map.
  - adsb.fi primary feed with adsb.lol as backup; airline logos in the sheet; a livery render when there's no photo.
- Airports tab (replaces Logbook): offline search over 5,332 airports, favourites with live local time, visited airports, and an aviation news feed filterable by your airports and airlines.
- Airport page:
  - Monogram and live local time.
  - METAR/TAF with flight-category badge.
  - OpenStreetMap airfield diagram with terminals and the likely active runway.
  - Live ADS-B departures and arrivals board, and nearby-traffic scope.
  - Airlines served with logostream logos, and a destinations map of every nonstop route.
  - Radio frequencies and a LiveATC link.
- Aircraft page:
  - Floating livery render with the airline's real tail art, or an ML Kit cutout, over the blurred photo with credit.
  - Holographic rarity badge, today's route with progress, sightings, and watch toggles.
- Settings: encrypted API keys, the live logostream quota (read from its headers), and every source's credit including "Logos by logostream".
- 101 unit tests and 19 Roborazzi screenshots, lint clean. Verified on the Pixel 9 emulator.

## In progress
- Nothing. Stage 2 is complete.

## Next (Stage 3)
- Trips: add a flight by number and date or boarding-pass scan, glass flight cards, flight detail with a self-drawing route, aircraft rotation and the cockpit-visit planner. FlightAware AeroAPI goes behind the spend-cap guard and needs your free AeroAPI key.
- Airport boards stay ADS-B based (AeroDataBox no longer has a free tier). AeroAPI could optionally feed them within its free monthly credit.

## Known limits
- Radar coverage is 250 nm around the map centre per query (API cap). Zoomed far out, the HUD says "zoom in for more".
- adsb.lol caps clients at 500 requests a day, so it's only the backup feed.
- logostream's free plan has no liveries, airport logos, airport data or news; free sources fill each gap (see DECISIONS.md). Its logos can't be cached, so they load live each session.
- RainViewer weather tiles stop at zoom 7 and are stretched beyond that.
- The ML Kit cutout model downloads through Play services on first use; the first cutout may need a second tap.
