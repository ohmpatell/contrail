"""Builds app/src/main/assets/airports.json from OurAirports (public domain).

Keeps airports with an IATA code that are medium/large or have scheduled service, plus their
runways and frequencies, and adds an IANA timezone per airport (timezonefinder) so local time
works offline.

Usage: python tools/gen_airports.py <dir with airports.csv runways.csv airport-frequencies.csv countries.csv> <out.json>
"""
import csv
import json
import os
import sys

from timezonefinder import TimezoneFinder

src, out = sys.argv[1], sys.argv[2]


def rows(name):
    with open(os.path.join(src, name), encoding="utf-8") as f:
        yield from csv.DictReader(f)


def num(v, cast=float):
    try:
        return cast(v)
    except (TypeError, ValueError):
        return None


countries = {r["code"]: r["name"] for r in rows("countries.csv")}
tf = TimezoneFinder()
TYPE = {"large_airport": "L", "medium_airport": "M", "small_airport": "S"}

airports = []
keep = {}
for r in rows("airports.csv"):
    iata = (r["iata_code"] or "").strip()
    kind = TYPE.get(r["type"])
    if not iata or kind is None:
        continue
    scheduled = r["scheduled_service"] == "yes"
    if kind == "S" and not scheduled:
        continue
    icao = (r["icao_code"] or r["gps_code"] or r["ident"]).strip()
    lat, lon = float(r["latitude_deg"]), float(r["longitude_deg"])
    tz = tf.timezone_at(lng=lon, lat=lat) or ""
    airports.append([
        icao, iata, r["name"], r["municipality"] or "", r["iso_country"],
        round(lat, 5), round(lon, 5), num(r["elevation_ft"], int), kind, 1 if scheduled else 0, tz,
    ])
    keep[r["ident"]] = icao

runways = []
for r in rows("runways.csv"):
    icao = keep.get(r["airport_ident"])
    if icao is None or r["closed"] == "1":
        continue
    runways.append([
        icao, r["le_ident"], r["he_ident"], num(r["length_ft"], int), num(r["width_ft"], int), r["surface"],
        num(r["le_latitude_deg"]), num(r["le_longitude_deg"]), num(r["he_latitude_deg"]), num(r["he_longitude_deg"]),
        num(r["le_heading_degT"]), num(r["he_heading_degT"]),
    ])

freqs = []
for r in rows("airport-frequencies.csv"):
    icao = keep.get(r["airport_ident"])
    if icao is None:
        continue
    freqs.append([icao, r["type"], r["description"], num(r["frequency_mhz"])])

used = {a[4] for a in airports}
with open(out, "w", encoding="utf-8") as f:
    json.dump({
        "airports": airports,
        "runways": runways,
        "frequencies": freqs,
        "countries": {k: v for k, v in countries.items() if k in used},
    }, f, separators=(",", ":"), ensure_ascii=False)
print(len(airports), "airports,", len(runways), "runways,", len(freqs), "frequencies")
