package com.contrail.contrail.feature.radar.map

import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.LiveAircraft
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Where every aircraft is *right now*, between feed updates.
 *
 * Each contact is dead-reckoned from its last fix using ground speed, track and turn rate.
 * When a new fix arrives, the difference between where we were drawing the plane and where
 * the new fix says it is becomes an offset that decays smoothly, so planes glide into their
 * corrected path instead of jumping.
 */
class MotionModel(
    /** Dead reckoning on/off. Off (reduced motion) draws each fix exactly where it was reported. */
    private val extrapolate: Boolean = true,
    private val correctionTauMs: Double = 900.0,
) {
    /** Reusable output so the per-frame loop allocates nothing. */
    class Sample {
        var lat = 0.0
        var lon = 0.0
        var headingDeg = 0.0
        var altitudeFt = 0.0
        var onGround = false
        /** 1 when fresh, fading to 0 as the contact goes stale. */
        var freshness = 1.0
    }

    private class Track(
        var fix: LiveAircraft,
        var offsetLat: Double = 0.0,
        var offsetLon: Double = 0.0,
        var offsetHeading: Double = 0.0,
        var correctionStartMs: Long = 0L,
    )

    private val tracks = LinkedHashMap<String, Track>()
    private val scratch = Sample()

    val size: Int get() = tracks.size
    val hexes: Set<String> get() = tracks.keys

    /** Replace the set of contacts with [aircraft], merging new fixes into existing tracks. */
    fun update(aircraft: List<LiveAircraft>, nowMs: Long) {
        val incoming = HashSet<String>(aircraft.size * 2)
        for (a in aircraft) {
            incoming += a.hex
            val track = tracks[a.hex]
            if (track == null) {
                tracks[a.hex] = Track(a)
                continue
            }
            if (a.positionTimeMs <= track.fix.positionTimeMs) {
                // Same position, newer metadata (callsign, squawk...). Keep the motion untouched.
                track.fix = a.copy(positionTimeMs = track.fix.positionTimeMs, lat = track.fix.lat, lon = track.fix.lon)
                continue
            }
            // Where we are drawing it now, including any correction still in progress.
            sampleTrack(track, nowMs, scratch)
            val oldLat = scratch.lat
            val oldLon = scratch.lon
            val oldHeading = scratch.headingDeg
            track.fix = a
            track.offsetLat = 0.0
            track.offsetLon = 0.0
            track.offsetHeading = 0.0
            sampleTrack(track, nowMs, scratch)
            val jumpM = Geo.distanceM(oldLat, oldLon, scratch.lat, scratch.lon)
            if (extrapolate && jumpM < MAX_SMOOTHED_JUMP_M) {
                track.offsetLat = oldLat - scratch.lat
                track.offsetLon = Geo.headingDelta(scratch.lon, oldLon)
                track.offsetHeading = Geo.headingDelta(scratch.headingDeg, oldHeading)
                track.correctionStartMs = nowMs
            }
        }
        tracks.keys.retainAll(incoming)
    }

    /** Writes the position of [hex] at [nowMs] into [out]. Returns false if unknown. */
    fun sample(hex: String, nowMs: Long, out: Sample): Boolean {
        val track = tracks[hex] ?: return false
        sampleTrack(track, nowMs, out)
        return true
    }

    /** Samples every contact at [nowMs], reusing [out] for each. */
    fun forEach(nowMs: Long, out: Sample, block: (LiveAircraft, Sample) -> Unit) {
        for (track in tracks.values) {
            sampleTrack(track, nowMs, out)
            block(track.fix, out)
        }
    }

    fun fix(hex: String): LiveAircraft? = tracks[hex]?.fix

    private fun sampleTrack(track: Track, nowMs: Long, out: Sample) {
        val fix = track.fix
        predict(fix, nowMs, out)
        if (track.correctionStartMs > 0) {
            val decay = exp(-(nowMs - track.correctionStartMs) / correctionTauMs)
            if (decay > 1e-3) {
                out.lat += track.offsetLat * decay
                out.lon = Geo.normalizeLon(out.lon + track.offsetLon * decay)
                out.headingDeg = Geo.normalizeHeading(out.headingDeg + track.offsetHeading * decay)
            }
        }
        val staleMs = nowMs - fix.fetchedAtMs
        out.freshness = when {
            staleMs <= FADE_START_MS -> 1.0
            staleMs >= FADE_END_MS -> 0.0
            else -> 1.0 - (staleMs - FADE_START_MS).toDouble() / (FADE_END_MS - FADE_START_MS)
        }
    }

    private fun predict(fix: LiveAircraft, nowMs: Long, out: Sample) {
        out.onGround = fix.onGround
        out.headingDeg = (fix.trackDeg ?: 0f).toDouble()
        out.altitudeFt = (fix.altitudeFt ?: 0).toDouble()
        out.lat = fix.lat
        out.lon = fix.lon
        if (!extrapolate) return
        val dtS = ((nowMs - fix.positionTimeMs) / 1000.0).coerceIn(0.0, MAX_EXTRAPOLATION_S)
        val speedMps = (fix.groundSpeedKt ?: 0f) * Geo.MPS_PER_KNOT
        if (dtS <= 0.0 || speedMps < 1.0 || fix.trackDeg == null) return

        val h0 = Geo.toRad(fix.trackDeg.toDouble())
        val rate = (fix.trackRateDegS ?: 0f).toDouble().coerceIn(-MAX_TURN_RATE, MAX_TURN_RATE)
        // Assume a turn continues for a limited time, then the aircraft rolls out.
        val turnS = if (abs(rate) > 0.05) minOf(dtS, MAX_TURN_S) else 0.0
        var east: Double
        var north: Double
        val heading: Double
        if (turnS > 0.0) {
            val w = Geo.toRad(rate)
            val h1 = h0 + w * turnS
            east = speedMps / w * (cos(h0) - cos(h1))
            north = speedMps / w * (sin(h1) - sin(h0))
            val straight = dtS - turnS
            east += speedMps * straight * sin(h1)
            north += speedMps * straight * cos(h1)
            heading = Geo.toDeg(h1)
        } else {
            east = speedMps * dtS * sin(h0)
            north = speedMps * dtS * cos(h0)
            heading = fix.trackDeg.toDouble()
        }
        out.lat = fix.lat + north / Geo.METERS_PER_DEG_LAT
        out.lon = Geo.normalizeLon(fix.lon + east / (Geo.METERS_PER_DEG_LAT * cos(Geo.toRad(fix.lat)).coerceAtLeast(0.01)))
        out.headingDeg = Geo.normalizeHeading(heading)
        val vr = (fix.verticalRateFpm ?: 0).toDouble()
        out.altitudeFt = (out.altitudeFt + vr * dtS / 60.0).coerceIn(out.altitudeFt - 3_000, out.altitudeFt + 3_000).coerceAtLeast(0.0)
    }

    companion object {
        /** Never extrapolate further than this past the last fix. */
        const val MAX_EXTRAPOLATION_S = 30.0
        const val MAX_TURN_S = 15.0
        const val MAX_TURN_RATE = 6.0
        /** Corrections larger than this snap instead of gliding (e.g. a bad fix). */
        const val MAX_SMOOTHED_JUMP_M = 6_000.0
        const val FADE_START_MS = 25_000L
        const val FADE_END_MS = 60_000L
    }
}
