package com.contrail.contrail.testing

import com.contrail.contrail.core.data.model.LiveAircraft

/** A plausible airliner at cruise over Toronto; override what a test cares about. */
fun aircraft(
    hex: String = "c00e9d",
    callsign: String? = "ACA1117",
    registration: String? = "C-FFNW",
    typeCode: String? = "B38M",
    lat: Double = 43.7,
    lon: Double = -79.6,
    altitudeFt: Int? = 34_000,
    onGround: Boolean = false,
    groundSpeedKt: Float? = 450f,
    trackDeg: Float? = 90f,
    trackRateDegS: Float? = null,
    verticalRateFpm: Int? = 0,
    squawk: String? = "1234",
    military: Boolean = false,
    positionTimeMs: Long = 0L,
    fetchedAtMs: Long = positionTimeMs,
    category: String? = "A3",
) = LiveAircraft(
    hex = hex,
    callsign = callsign,
    registration = registration,
    typeCode = typeCode,
    typeDescription = null,
    category = category,
    lat = lat,
    lon = lon,
    altitudeFt = altitudeFt,
    geometricAltitudeFt = altitudeFt?.plus(500),
    onGround = onGround,
    groundSpeedKt = groundSpeedKt,
    trackDeg = trackDeg,
    trackRateDegS = trackRateDegS,
    verticalRateFpm = verticalRateFpm,
    squawk = squawk,
    emergency = "none",
    military = military,
    positionTimeMs = positionTimeMs,
    fetchedAtMs = fetchedAtMs,
    source = "test",
)

/** Trimmed real adsb.lol /v2/point response (Oct 2026), including a ground target. */
const val ADSB_LOL_JSON = """
{"ac":[
{"hex":"aaa90c","type":"adsb_icao","flight":"RPA3549 ","r":"N786YX","t":"E75L","alt_baro":34000,"alt_geom":35025,"gs":455.8,"track":240.57,"baro_rate":0,"squawk":"1003","emergency":"none","category":"A3","lat":43.623871,"lon":-81.817868,"seen_pos":0.244,"seen":0.0,"rssi":-17.1},
{"hex":"c06b28","type":"adsb_icao","flight":"CGOPB   ","r":"C-GOPB","t":"FA6X","dbFlags":1,"alt_baro":38700,"gs":478.6,"track":69.07,"baro_rate":1344,"squawk":"5756","category":"A3","lat":43.177713,"lon":-81.579764,"seen_pos":2.5},
{"hex":"c0123a","flight":"CGQIR","r":"C-GQIR","t":"P28A","alt_baro":"ground","gs":3.1,"track":120.0,"lat":43.68,"lon":-79.63,"seen_pos":1.0},
{"hex":"~2a0b1c","flight":"","alt_baro":12000,"seen":4.0}
],"msg":"No error","now":1792535702000,"total":4,"ctime":1792535702000,"ptime":0}
"""

/** adsb.fi names the list "aircraft" and sends "now" in fractional seconds. */
const val ADSB_FI_JSON = """
{"now": 1792542738.001
,"aircraft":[
{"hex":"a48392","type":"adsb_icao","flight":"N390JA  ","r":"N390JA","t":"DV20","desc":"DIAMOND DA-20","alt_baro":3500,"gs":101.2,"track":10.5,"lat":43.4,"lon":-79.9,"seen_pos":0.4}
]
,"resultCount": 1
,"ptime": 0.151
}
"""

const val ADSBDB_ROUTE_JSON = """
{"response":{"flightroute":{"callsign":"ACA856","callsign_icao":"ACA856","callsign_iata":"AC856","airline":{"name":"Air Canada","icao":"ACA","iata":"AC","country":"Canada","country_iso":"CA","callsign":"AIR CANADA"},"origin":{"country_iso_name":"CA","country_name":"Canada","elevation":569,"iata_code":"YYZ","icao_code":"CYYZ","latitude":43.6772,"longitude":-79.6306,"municipality":"Toronto","name":"Lester B. Pearson International Airport"},"destination":{"country_iso_name":"GB","country_name":"United Kingdom","elevation":83,"iata_code":"LHR","icao_code":"EGLL","latitude":51.4706,"longitude":-0.46194,"municipality":"London","name":"London Heathrow Airport"}}}}
"""

const val ADSBDB_UNKNOWN_JSON = """{"response":"unknown callsign"}"""

const val PLANESPOTTERS_JSON = """
{"photos":[{"id":"1961022","thumbnail":{"src":"https:\/\/t.plnspttrs.net\/03891\/1961022_fd0a9f3f36_t.jpg","size":{"width":200,"height":133}},"thumbnail_large":{"src":"https:\/\/t.plnspttrs.net\/03891\/1961022_fd0a9f3f36_280.jpg","size":{"width":420,"height":280}},"link":"https:\/\/www.planespotters.net\/photo\/1961022\/c-ffnw-air-canada-boeing-737-8-max?utm_source=api","photographer":"Frederick Tremblay"}]}
"""

const val PLANESPOTTERS_EMPTY_JSON = """{"photos":[]}"""
