# Status

Last updated Oct 21, 2026.

## Waiting on you

**1. A free OpenSky Network account** (for the cockpit-visit planner's real history). Until then it shows clearly labelled sample data; everything else works.
1. Go to https://opensky-network.org → **Login** (top right) → **Register**. Accounts are free; there's no plan to pick. Confirm the email.
2. Log in and open **My OpenSky → Account** (https://opensky-network.org/my-opensky/account).
3. In **API Client**, click **Create** (or **Reset Credential**) and download `credentials.json`. It has a `clientId` and a `clientSecret`.
4. For debug builds, add two lines at the bottom of `local.properties` in the project root (Android Studio: **Project** view → `local.properties`):
   ```
   OPENSKY_CLIENT_ID=your-client-id
   OPENSKY_CLIENT_SECRET=your-client-secret
   ```
5. On the phone (release build): **Airports → gear → OpenSky client ID → Add key**, paste, **Save**; then the same for **OpenSky client secret**.

Tell me when it's done and I'll check the planner against real data.

**2. Your logostream key on the phone** (from Stage 2, if not done yet): **Airports → gear → logostream → Add key**.

**Optional:** airplanes.live needs approval by email (contact@airplanes.live). adsb.fi and adsb.lol already cover live positions.

Releases: every push to `master` publishes a signed APK to GitHub Releases for Obtainium. Keep `C:\Users\ohm20\contrail-signing\contrail-release.jks` and its password backed up.

## Done

### Stage 1: foundation and live radar
- Project structure, night-cockpit design system, glass tab bar, live radar with interpolated motion, aircraft sheet, GitHub Actions release pipeline.

### Stage 2: aircraft, airports, logostream, liveries
- Type-accurate radar icons, ground clutter rules, full route on selection, watchlist.
- Airports tab, airport page (weather, OSM diagram, ADS-B boards, destinations), aircraft page (livery render or cutout, rarity), encrypted Settings, logostream with live quota.

### Stage 3: trips, ticket import, lounges, flight detail, Passport
- **Trips are journeys of segments.** Connections show time, terminal change, and a tight / comfortable / long badge. Layover or stopover is guessed (over 24 h, or overnight with a hotel) and switched with one tap. Stopovers get their own section: when you land and leave, live local time, weather (METAR), and a countdown.
- **Trips tab** (now the start tab): next-flight hero with the floating livery, split-flap codes, self-drawing arc and countdown; trip cards; past trips.
- **Ticket import, all on the phone:** live barcode scan (CameraX + ML Kit), screenshot or photo, PDF (PdfRenderer text layer + OCR + barcodes), pasted email text, or by hand (typing a flight number fills its route). **Share to Contrail** from any app's share sheet. Always a review screen first, with missing fields flagged, connections previewed, and trip splits you can change.
- Parsers tested against made-up Etihad e-ticket, Cathay Pacific email, Air Canada email and Cathay boarding-pass OCR samples, plus IATA BCBP barcodes.
- **Flight detail:** route over the real night map (live plane once airborne), status, gate-to-gate timeline with labelled estimates, aircraft card with hand-entered tail and "times flown on this tail", booking card, lounges.
- **Cockpit-visit planner (free sources only):** your flight live on adsb.fi/adsb.lol by ICAO callsign (EY 141 → ETD141); your aircraft live by tail, flagged as your inbound with an ETA when it's heading to your departure airport; earlier legs and a 7-day turnaround estimate from OpenSky with relaxed / maybe / tight badges (thresholds in Settings). Every estimate is labelled.
- **Lounges:** log a visit on any flight or connection (lounge, terminal, date, times, access, 1–5 stars, note). Suggestions from a curated list for YYZ, AUH, HKG, MNL, DEL and BOM plus OpenStreetMap; lounges you add are remembered.
- **Passport:** dotted globe you can spin with every route, odometer totals, visited vs transited airports, records, the 42-type livery collection with holographic rare cards, a Lounges section (visits, hours, ratings, favourites, collection) and a swipeable **Year in Review**.
- FlightAware AeroAPI removed from the plan and docs (no card-required services).
- 132 unit and screenshot tests, lint clean. Verified on the Pixel 9 emulator, including a real share-sheet import.

## In progress
- Nothing. Stage 3 is complete.

## Next (Stage 4)
- Sky tab: closest plane now, overhead list, Sky View (camera + compass), proximity alerts.
- Home-screen widgets (next-flight countdown, plane overhead).

## Known limits
- No free schedule API: flight times come from your ticket and only update once the flight is seen on ADS-B. Tail numbers are entered by hand.
- OpenSky publishes history the day after, so the turnaround uses the last 7 completed days.
- Lounge lists go stale; correct them by adding lounges by hand. OSM coverage is uneven (good at HKG and MNL, thin at YYZ and DEL).
- Radar coverage is 250 nm around the map centre per query; adsb.lol caps clients at 500 requests a day, so it's the backup feed.
- logostream's free plan has no liveries, airport logos, airport data or news; free sources fill each gap. Its logos load live each session.
- The ML Kit cutout model downloads through Play services on first use.
