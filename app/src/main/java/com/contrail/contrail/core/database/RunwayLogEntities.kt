package com.contrail.contrail.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * A plane seen landing on or taking off from a runway, with the wind then. Recent ones say what's
 * in use now; older ones teach the guess this airport's habits. Kept 60 days.
 */
@Entity(
    tableName = "runway_sighting",
    primaryKeys = ["icao", "runway", "kind", "hex", "hourBucket"],
    indices = [Index("icao", "timeMs")],
)
data class RunwaySightingEntity(
    val icao: String,
    val runway: String,
    /** "arr" or "dep". */
    val kind: String,
    val hex: String,
    /** The hour it was seen, so one plane counts once per pass. */
    val hourBucket: Long,
    val timeMs: Long,
    val windFromDeg: Double?,
)

data class RunwayHabitRow(val runway: String, val kind: String, val windFromDeg: Double?, val count: Int)

@Dao
interface RunwayLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<RunwaySightingEntity>)

    @Query("SELECT * FROM runway_sighting WHERE icao = :icao AND timeMs >= :since")
    suspend fun since(icao: String, since: Long): List<RunwaySightingEntity>

    /** Past uses grouped by runway and 30° wind sector (calm counts as its own sector). */
    @Query(
        """
        SELECT runway, kind, AVG(windFromDeg) AS windFromDeg, COUNT(*) AS count FROM runway_sighting
        WHERE icao = :icao GROUP BY runway, kind, CASE WHEN windFromDeg IS NULL THEN -1 ELSE CAST(windFromDeg / 30 AS INTEGER) END
        """,
    )
    suspend fun habits(icao: String): List<RunwayHabitRow>

    @Query("DELETE FROM runway_sighting WHERE timeMs < :before")
    suspend fun prune(before: Long)
}
