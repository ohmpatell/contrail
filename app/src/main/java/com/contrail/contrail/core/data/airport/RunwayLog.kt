package com.contrail.contrail.core.data.airport

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.model.TrailPoint
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.RunwayEntity
import com.contrail.contrail.core.database.RunwaySightingEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runways in use at an airport, worked out from planes seen landing and taking off, and a log of
 * those sightings so the airport's habits shape the guess when nothing's flying.
 */
@Singleton
class RunwayLog @Inject constructor(db: ContrailDatabase, private val clock: Clock) {
    private val dao = db.runwayLogDao()

    /**
     * Records what [aircraft] and their [trails] show about [icao]'s runways, then answers which
     * are in use: seen in the last 20 minutes, or a guess from the wind and past habits.
     */
    suspend fun update(
        icao: String,
        elevationFt: Int?,
        runways: List<RunwayEntity>,
        aircraft: List<LiveAircraft>,
        trails: Map<String, List<TrailPoint>>,
        windFromDeg: Double?,
        windKt: Int?,
    ): RunwaysInUse? {
        val now = clock.nowMs()
        val ends = RunwayUse.ends(runways)
        val seen = RunwayUse.sightings(ends, elevationFt ?: 0, aircraft, trails, now)
        if (seen.isNotEmpty()) {
            dao.insert(
                seen.map {
                    RunwaySightingEntity(
                        icao, it.runway, if (it.kind == RunwaySighting.Kind.Arrival) "arr" else "dep", it.hex,
                        hourBucket = it.timeMs / 3_600_000, timeMs = it.timeMs, windFromDeg = windFromDeg,
                    )
                },
            )
        }
        val recent = dao.since(icao, now - RunwayUse.WINDOW_MS).map {
            RunwaySighting(it.runway, if (it.kind == "arr") RunwaySighting.Kind.Arrival else RunwaySighting.Kind.Departure, it.hex, it.timeMs)
        }
        RunwayUse.inUse(recent, now)?.let { return it }
        val habits = dao.habits(icao).map {
            RunwayHabit(it.runway, if (it.kind == "arr") RunwaySighting.Kind.Arrival else RunwaySighting.Kind.Departure, it.windFromDeg, it.count)
        }
        return RunwayUse.guess(ends, windFromDeg, windKt, habits)
    }

    suspend fun prune() = dao.prune(clock.nowMs() - 60L * 86_400_000)
}
