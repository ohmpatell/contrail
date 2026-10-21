"""Builds app/src/main/assets/land_dots.bin: a dot grid over land for the Passport globe.

Input: Natural Earth 110m land polygons (public domain), e.g.
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson

Dots sit on rows every 1.8 degrees of latitude, spaced by the same ground distance along each
row, so the globe looks evenly stippled. Output: big-endian int16 pairs (lat*100, lon*100).

Usage: python tools/gen_land_dots.py ne_110m_land.geojson app/src/main/assets/land_dots.bin
"""
import json
import math
import struct
import sys

STEP = 1.8


def rings(geom):
    if geom["type"] == "Polygon":
        yield geom["coordinates"]
    elif geom["type"] == "MultiPolygon":
        yield from geom["coordinates"]


def inside(lon, lat, ring):
    hit = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i]
        xj, yj = ring[j]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def main(src, dst):
    polys = []
    for f in json.load(open(src, encoding="utf-8"))["features"]:
        for poly in rings(f["geometry"]):
            outer = poly[0]
            xs = [p[0] for p in outer]
            ys = [p[1] for p in outer]
            polys.append((min(xs), max(xs), min(ys), max(ys), poly))
    dots = []
    lat = -56.0
    while lat <= 82.0:
        step_lon = STEP / max(math.cos(math.radians(lat)), 0.05)
        lon = -180.0 + (step_lon / 2 if int(round((lat + 90) / STEP)) % 2 else 0)
        while lon < 180.0:
            for x0, x1, y0, y1, poly in polys:
                if x0 <= lon <= x1 and y0 <= lat <= y1 and inside(lon, lat, poly[0]) and not any(inside(lon, lat, h) for h in poly[1:]):
                    dots.append((lat, lon))
                    break
            lon += step_lon
        lat += STEP
    with open(dst, "wb") as out:
        for la, lo in dots:
            out.write(struct.pack(">hh", int(round(la * 100)), int(round(lo * 100))))
    print(f"{len(dots)} dots, {len(dots) * 4} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
