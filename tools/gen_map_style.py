"""Generates app/src/main/assets/map/contrail_dark.json (the radar's night cockpit style)."""
import json
import sys


def zi(stops):
    out = ["interpolate", ["exponential", 1.5], ["zoom"]]
    for z, v in stops:
        out += [z, v]
    return out


FONT_R = ["Noto Sans Regular"]
FONT_B = ["Noto Sans Bold"]
BG = "#060B16"
WATER = "#0A1F3A"

layers = [
    {"id": "background", "type": "background", "paint": {"background-color": BG}},
    {"id": "landcover-ice", "type": "fill", "source": "omt", "source-layer": "landcover",
     "filter": ["==", ["get", "class"], "ice"], "paint": {"fill-color": "#0C1628", "fill-opacity": 0.8}},
    {"id": "landcover-wood", "type": "fill", "source": "omt", "source-layer": "landcover",
     "filter": ["==", ["get", "class"], "wood"], "paint": {"fill-color": "#08121C", "fill-opacity": zi([[4, 0.4], [10, 0.7]])}},
    {"id": "landuse-residential", "type": "fill", "source": "omt", "source-layer": "landuse", "minzoom": 6,
     "filter": ["in", ["get", "class"], ["literal", ["residential", "suburb", "neighbourhood"]]],
     "paint": {"fill-color": "#0B1426", "fill-opacity": zi([[6, 0.4], [12, 0.8]])}},
    {"id": "park", "type": "fill", "source": "omt", "source-layer": "park", "minzoom": 8,
     "paint": {"fill-color": "#08131D", "fill-opacity": 0.7}},
    {"id": "water", "type": "fill", "source": "omt", "source-layer": "water",
     "filter": ["!=", ["get", "brunnel"], "tunnel"], "paint": {"fill-color": WATER}},
    {"id": "waterway", "type": "line", "source": "omt", "source-layer": "waterway", "minzoom": 7,
     "paint": {"line-color": WATER, "line-width": zi([[8, 0.6], [14, 2.5]])}},
    {"id": "water-edge", "type": "line", "source": "omt", "source-layer": "water", "minzoom": 3,
     "paint": {"line-color": "#173A63", "line-opacity": 0.7, "line-width": zi([[3, 0.5], [12, 1.4]])}},
    {"id": "building", "type": "fill", "source": "omt", "source-layer": "building", "minzoom": 13,
     "paint": {"fill-color": "#0D1830", "fill-opacity": zi([[13, 0], [15, 0.7]])}},
    {"id": "aeroway-area", "type": "fill", "source": "omt", "source-layer": "aeroway", "minzoom": 9,
     "filter": ["in", ["get", "class"], ["literal", ["aerodrome", "apron"]]],
     "paint": {"fill-color": "#0E1A31", "fill-opacity": zi([[9, 0.5], [13, 0.9]])}},
    {"id": "aeroway-taxiway", "type": "line", "source": "omt", "source-layer": "aeroway", "minzoom": 11,
     "filter": ["==", ["get", "class"], "taxiway"], "layout": {"line-cap": "round", "line-join": "round"},
     "paint": {"line-color": "#1A2846", "line-width": zi([[11, 0.6], [14, 3], [17, 10]])}},
    {"id": "aeroway-runway", "type": "line", "source": "omt", "source-layer": "aeroway", "minzoom": 8,
     "filter": ["==", ["get", "class"], "runway"], "layout": {"line-cap": "butt", "line-join": "round"},
     "paint": {"line-color": "#24365C", "line-width": zi([[8, 1], [11, 4], [14, 18], [17, 60]])}},
    {"id": "road-minor", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 11,
     "filter": ["in", ["get", "class"], ["literal", ["minor", "service", "tertiary"]]],
     "layout": {"line-cap": "round", "line-join": "round"},
     "paint": {"line-color": "#0F1A2E", "line-width": zi([[11, 0.4], [16, 4]])}},
    {"id": "road-major", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 7,
     "filter": ["in", ["get", "class"], ["literal", ["primary", "secondary", "trunk"]]],
     "layout": {"line-cap": "round", "line-join": "round"},
     "paint": {"line-color": "#132039", "line-width": zi([[7, 0.5], [12, 1.6], [16, 6]])}},
    {"id": "road-motorway", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 5,
     "filter": ["==", ["get", "class"], "motorway"], "layout": {"line-cap": "round", "line-join": "round"},
     "paint": {"line-color": "#18284A", "line-width": zi([[5, 0.4], [10, 1.4], [16, 8]])}},
    {"id": "rail", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 10,
     "filter": ["==", ["get", "class"], "rail"],
     "paint": {"line-color": "#121C30", "line-width": zi([[10, 0.4], [16, 2]]), "line-dasharray": [3, 3]}},
    {"id": "boundary-state", "type": "line", "source": "omt", "source-layer": "boundary", "minzoom": 3,
     "filter": ["all", [">=", ["get", "admin_level"], 3], ["<=", ["get", "admin_level"], 4], ["!=", ["get", "maritime"], 1]],
     "paint": {"line-color": "#1C2A48", "line-width": zi([[3, 0.5], [10, 1.2]]), "line-dasharray": [2, 2]}},
    {"id": "boundary-country", "type": "line", "source": "omt", "source-layer": "boundary",
     "filter": ["all", ["<=", ["get", "admin_level"], 2], ["!=", ["get", "maritime"], 1], ["!=", ["get", "disputed"], 1]],
     "layout": {"line-join": "round"},
     "paint": {"line-color": "#2B3C63", "line-width": zi([[1, 0.6], [6, 1.2], [10, 2]])}},
    {"id": "water-name", "type": "symbol", "source": "omt", "source-layer": "water_name", "minzoom": 3,
     "layout": {"text-field": ["coalesce", ["get", "name:en"], ["get", "name"]], "text-font": ["Noto Sans Italic"],
                "text-size": zi([[3, 10], [8, 13]]), "text-letter-spacing": 0.15, "text-max-width": 8},
     "paint": {"text-color": "#2C4870", "text-halo-color": WATER, "text-halo-width": 1}},
    {"id": "place-town", "type": "symbol", "source": "omt", "source-layer": "place", "minzoom": 8,
     "filter": ["in", ["get", "class"], ["literal", ["town", "village"]]],
     "layout": {"text-field": ["coalesce", ["get", "name:en"], ["get", "name"]], "text-font": FONT_R,
                "text-size": zi([[8, 10], [14, 14]]), "text-max-width": 8},
     "paint": {"text-color": "#56627F", "text-halo-color": BG, "text-halo-width": 1.2}},
    {"id": "place-city", "type": "symbol", "source": "omt", "source-layer": "place", "minzoom": 4,
     "filter": ["==", ["get", "class"], "city"],
     "layout": {"text-field": ["coalesce", ["get", "name:en"], ["get", "name"]], "text-font": FONT_R,
                "text-size": zi([[4, 10], [8, 13], [12, 17]]), "text-max-width": 8, "symbol-sort-key": ["get", "rank"]},
     "paint": {"text-color": "#7D89A8", "text-halo-color": BG, "text-halo-width": 1.4}},
    {"id": "place-state", "type": "symbol", "source": "omt", "source-layer": "place", "minzoom": 4, "maxzoom": 8,
     "filter": ["==", ["get", "class"], "state"],
     "layout": {"text-field": ["upcase", ["coalesce", ["get", "name:en"], ["get", "name"]]], "text-font": FONT_R,
                "text-size": 10, "text-letter-spacing": 0.25, "text-max-width": 8},
     "paint": {"text-color": "#3C4A6B", "text-halo-color": BG, "text-halo-width": 1}},
    {"id": "place-country", "type": "symbol", "source": "omt", "source-layer": "place", "maxzoom": 7,
     "filter": ["==", ["get", "class"], "country"],
     "layout": {"text-field": ["upcase", ["coalesce", ["get", "name:en"], ["get", "name"]]], "text-font": FONT_B,
                "text-size": zi([[1, 9], [6, 13]]), "text-letter-spacing": 0.3, "text-max-width": 7},
     "paint": {"text-color": "#56627F", "text-halo-color": BG, "text-halo-width": 1.4}},
    # Airport labels: IATA code, plus the name once zoomed in. Toggled by the Layers panel.
    {"id": "airport-dot", "type": "circle", "source": "omt", "source-layer": "aerodrome_label", "minzoom": 6,
     "filter": ["has", "iata"],
     "paint": {"circle-radius": zi([[6, 1.6], [12, 3]]), "circle-color": "#8FD8FF", "circle-opacity": 0.8,
               "circle-stroke-color": BG, "circle-stroke-width": 1}},
    {"id": "airport-label", "type": "symbol", "source": "omt", "source-layer": "aerodrome_label", "minzoom": 6,
     "filter": ["has", "iata"],
     "layout": {
         "text-field": ["step", ["zoom"],
                        ["get", "iata"],
                        11, ["format",
                             ["get", "iata"], {},
                             "\n", {},
                             ["coalesce", ["get", "name:en"], ["get", "name"]], {"font-scale": 0.72, "text-font": ["literal", FONT_R]}]],
         "text-font": FONT_B, "text-size": zi([[6, 10], [10, 12], [14, 14]]), "text-letter-spacing": 0.08,
         "text-max-width": 10, "text-anchor": "top", "text-offset": [0, 0.5]},
     "paint": {"text-color": "#8FD8FF", "text-opacity": zi([[6, 0.7], [9, 0.95]]), "text-halo-color": BG,
               "text-halo-width": 1.6}},
]

style = {
    "version": 8,
    "name": "Contrail Night Cockpit",
    "sources": {"omt": {"type": "vector", "url": "https://tiles.openfreemap.org/planet",
                        "attribution": "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"}},
    "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
    "layers": layers,
}

with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(style, f, indent=1)
print(len(layers), "layers")
