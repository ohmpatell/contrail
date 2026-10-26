"""Builds app/src/main/assets/airlines.json from OpenFlights airlines.dat (ODbL).

Keeps airlines with a three-letter ICAO code that have an IATA code or are active, so old
flight logs (Jet Airways, Alitalia) still resolve. Rows: [icao, iata, name, active].

Usage: python tools/gen_airlines.py <airlines.dat> <out.json>
"""
import csv
import json
import re
import sys

src, out = sys.argv[1], sys.argv[2]
rows = []
seen = set()
with open(src, encoding="utf-8") as f:
    for r in csv.reader(f):
        if len(r) < 8:
            continue
        name, iata, icao, active = r[1], r[3], r[4], r[7]
        iata = "" if iata in (r"\N", "-") else iata.strip().upper()
        icao = "" if icao in (r"\N", "N/A") else icao.strip().upper()
        if not re.fullmatch(r"[A-Z]{3}", icao):
            continue
        if not re.fullmatch(r"[A-Z0-9]{2}", iata):
            iata = ""
        if not iata and active != "Y":
            continue
        if icao in seen:
            continue
        seen.add(icao)
        rows.append([icao, iata, name.strip(), 1 if active == "Y" else 0])
# Active airlines first, so a reused IATA code resolves to the airline flying today.
rows.sort(key=lambda r: (-r[3], r[0]))
with open(out, "w", encoding="utf-8") as f:
    json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))
print(len(rows), "airlines")
