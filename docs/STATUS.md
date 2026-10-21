# Status

Last updated Oct 20, 2026.

## Waiting on you

**Add four GitHub Secrets so CI can publish signed APKs.** I generated the release keystore on your PC, outside the repo, in `C:\Users\ohm20\contrail-signing\`. Each secret's value is in the text file of the same name in that folder.

1. Open https://github.com/ohmpatell/contrail/settings/secrets/actions
2. Click **New repository secret**.
3. For each row: type the **Name**, open the matching `.txt` file in Notepad, press Ctrl+A, Ctrl+C, paste into **Secret**, click **Add secret**.

| Name | Value comes from |
| --- | --- |
| `CONTRAIL_KEYSTORE_BASE64` | `CONTRAIL_KEYSTORE_BASE64.txt` |
| `CONTRAIL_KEYSTORE_PASSWORD` | `CONTRAIL_KEYSTORE_PASSWORD.txt` |
| `CONTRAIL_KEY_ALIAS` | `CONTRAIL_KEY_ALIAS.txt` |
| `CONTRAIL_KEY_PASSWORD` | `CONTRAIL_KEY_PASSWORD.txt` |

4. Back up `contrail-release.jks` and the password file somewhere safe (e.g. a password manager). Without that keystore, future updates can't install over the app.
5. Re-run the workflow: **Actions** tab → **Release APK** → **Run workflow**. A release `v0.1.N` appears under **Releases**.

**Install with Obtainium:** in Obtainium, tap **Add app**, paste `https://github.com/ohmpatell/contrail`, tap **Add**, then **Install**.

**Optional:** airplanes.live now needs approval (email contact@airplanes.live with a project description). adsb.fi covers the backup role meanwhile.

## Done

### Stage 1: foundation and live radar
- Project structure, version catalog, Hilt, Room, DataStore, Ktor, Coil, MapLibre.
- Design system (`core/designsystem`): night-cockpit colour tokens, altitude ramp, rarity palette, bundled Space Grotesk, Inter and JetBrains Mono, spacing, shapes, spring motion tokens that respect "remove animations", the AGSL aurora canvas, Haze frosted glass, split-flap text with haptic ticks, custom icons, chips, stat tiles, sparkline, live pulse, rarity badges.
- App shell: five tabs (Trips, Radar, Sky, Passport, Logbook) behind a floating glass nav bar with a springing indicator and directional tab transitions. Night splash and a custom adaptive and themed launcher icon.
- Placeholder tabs styled to match, each with an animated hero: a split-flap boarding pass, a sweeping proximity scope, a globe with blooming routes, a spotting viewfinder.
- Live radar:
  - Custom dark OpenFreeMap style with runways and airport codes.
  - adsb.lol traffic with adsb.fi failover.
  - Planes glide between updates (dead reckoning plus smoothed corrections, in the MapLibre layer).
  - Altitude colours, fading 4-minute trails, rare-type glow rings, pulsing red emergency squawks, an emergency banner.
  - Filters: airline, type, altitude band, military, rare.
  - Layers: airport labels, RainViewer weather, day/night terminator with twilight bands, trails, callsigns.
  - Locate button.
  - Aircraft sheet: Planespotters photo with credit and link, split-flap callsign, rarity badge, "route from callsign", altitude, speed, V/S, squawk, vertical-speed sparkline, details.
  - Follow mode with cockpit heading tape and readouts.
- GitHub Actions: tests, lint, signed release APK, GitHub Release on every push.
- 56 unit tests and 14 Roborazzi screenshots (`app/src/test/screenshots`). Verified on the Pixel 9 emulator (API 37) in debug and minified release builds.

## In progress
- Nothing. Stage 1 is complete.

## Next (Stage 2)
- Trips: add a flight by number and date, glass flight cards, flight detail with a self-drawing route, AeroAPI behind the spend-cap quota guard (needs your free AeroAPI key in Settings).
- Settings screen with encrypted key storage.
- Aircraft page (opened from the radar sheet), with Navigation 3 for detail screens.

## Known limits
- Radar coverage is limited to 250 nm around the map centre per query (API cap). Zoomed far out, the HUD says "zoom in for more".
- Weather tiles from RainViewer stop at zoom 7 and are stretched beyond that.
