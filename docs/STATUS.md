# Status

Last updated Nov 7, 2026.

## Waiting on you

Two free sign-ups and one Supabase setting for Stage 5, needed only from step 4 (sync) onward. Steps 1 to 3 don't wait on them. Put each value in `local.properties`; Claude adds the GitHub Secrets.

- **Supabase:** token added. Still to do: turn off "Confirm email" and add `CONTRAIL_SUPABASE_KEY` (the publishable key).
- **AeroDataBox on RapidAPI (Basic, free):** `CONTRAIL_AERODATABOX_API_KEY`.

Releases: every push to `master` publishes a signed APK to GitHub Releases for Obtainium. Keep `C:\Users\ohm20\contrail-signing\contrail-release.jks` and its password backed up.

## Done

### Stage 1: foundation and live radar
- Project structure, night-cockpit design system, glass tab bar, live radar with interpolated motion, aircraft sheet, GitHub Actions release pipeline.

### Stage 2: aircraft, airports, logostream, liveries
- Type-accurate radar icons, ground clutter rules, full route on selection, watchlist.
- Airports tab, airport page (weather, OSM diagram, ADS-B boards, destinations), aircraft page (livery render or cutout, rarity), encrypted Settings, logostream with live quota.

### Stage 3: trips, ticket import, lounges, flight detail, Passport
- Trips as journeys with connections and stopovers, ticket import (barcode, PDF, screenshot, text, share sheet), flight detail with live tracking and the cockpit-visit planner, lounge log, first Passport.

### Stage 4: your flying, the Passport, Sky and widgets
- **Import a flight log:** myFlightradar24, Flighty or OpenFlights CSV. Pick the file or share it to Contrail. A preview globe and per-year chart come first, duplicates are skipped, and missing times are estimated and labelled.
- **Add a flight:** flight number and day, with the route filled in automatically and a live boarding pass in the airline's colours. Airports by code or city, a glass calendar, aircraft quick picks, seat, cabin, reason and notes. Edit or delete any flight from its page.
- **Add screen reorganised:** Add a flight and Import a flight log lead; ticket reading moved to "From a ticket · Beta".
- **Passport rebuilt:**
  - a year filter and a replay globe that flies your history;
  - headline totals (×Earth, % of the way to the Moon, days in the air);
  - a searchable year-by-year **Logbook** and a swipeable **Stamp book** (gold cover, identity page, inked stamps);
  - 22 **achievement medals**;
  - charts: flights per year, flying calendar, top airlines, airports, routes and aircraft, how you fly, edges of your map, countries with flags.
- **Flown flights** open as a keepsake: distance, time, speed, boarding pass, notes and an Edit button.
- **Sky tab:**
  - a live heading-up scope of everything within 5–100 km;
  - the closest plane, with where to look and a countdown to its closest approach, plus an overhead list;
  - **Sky View** (camera and compass labels, with arrows to off-screen planes);
  - **proximity alerts** (rare, military, emergency, watchlist, below 3,000 ft).
- **Home-screen widgets:** next-flight countdown, plane overhead, Passport totals.
- **Share your Passport:** a story-sized card (globe, totals, flags, latest stamps) sent through the share sheet.
- 156 unit and screenshot tests green, lint clean. Verified on the Pixel 9 emulator: a real myFlightradar24-format CSV imported through the file picker, the Passport replay, Add a flight with live route lookup, a flown-flight page, and Sky with live ADS-B traffic.

## In progress: Stage 5, Contrail everywhere (plan in `DESIGN.md` → Roadmap)
- ✅ **Live Update:** from three hours before departure until you land, your flight sits on the lock screen and as a status-bar chip, showing phase, a progress bar with a plane and a ticking countdown. It uses the live ADS-B position once you're near departure and the timetable otherwise, always labelled. Tap it to open the flight. Switch: Settings → Live Update.
- ✅ **New widgets**, painted at each widget's exact size:
  - Next flight: a split-flap departure board with gate, seat and terminal flaps, and a progress bar in the air.
  - Overhead: a radar scope with every nearby plane coloured by altitude.
  - Passport: this year as a ring around the Earth, with all-time totals.
- ✅ **Themes** (Settings → Appearance, with a live preview of each):
  - **Night Cockpit**, the original.
  - **Chart:** a light, paper-chart look with its own map style.
  - **Golden Hour:** follows the real sun where you last opened Sky.
  - **Glass Cockpit:** black glass, magenta and cyan, mono headlines, a flight-deck map.

  **Livery mode** tints the app in your airline's colour while a flight is under way. Widgets follow the theme too.

Still to do:

3. Tablet layouts
4. Optional username-and-password sign-in and sync (Supabase)
5. Real departures and arrivals (AeroDataBox)
6. Automatic actual takeoff and landing times
7. Follow a flight, shared trips, flights together

## Known limits
- No free schedule API: flight times come from your ticket or log, or are estimated from distance (always labelled). Tail numbers are entered by hand.
- myFlightradar24 can only export from its website (Settings → Export → Download CSV), not from its app.
- Alerts and the overhead widget use the last place you opened Sky, not live background location.
- Sky View accuracy depends on the phone's compass. Wave the phone in a figure eight if labels sit off to one side.
- OpenSky publishes history the day after, so the turnaround uses the last 7 completed days.
- Radar coverage is 250 nm around the map centre per query; adsb.lol caps clients at 500 requests a day, so it's the backup feed.
- logostream's free plan has no liveries, airport logos, airport data or news; free sources fill each gap. Its logos load live each session.
