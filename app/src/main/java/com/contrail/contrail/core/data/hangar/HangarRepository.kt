package com.contrail.contrail.core.data.hangar

import android.content.Context
import android.net.Uri
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.rotation.EarlierLeg
import com.contrail.contrail.core.data.rotation.RotationRepository
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.HangarPlaneEntity
import com.contrail.contrail.core.database.HangarRow
import com.contrail.contrail.core.database.HangarSeenEntity
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A pinned plane and what Contrail last knew about it. */
data class HangarPlane(
    val registration: String,
    val hex: String?,
    val typeCode: String?,
    val airlineIcao: String?,
    val operator: String?,
    val story: String?,
    val photoPath: String?,
    val addedAtMs: Long,
    val alerts: Boolean,
    val lastSeenMs: Long?,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastCallsign: String?,
    val lastAirborne: Boolean?,
) {
    val airlineName: String? get() = operator ?: Airlines.byIcao(airlineIcao)?.name
}

/** Something one of your pinned planes just did. */
data class HangarEvent(val plane: HangarPlane, val kind: Kind, val aircraft: LiveAircraft) {
    enum class Kind { TookOff, Landed }
}

/**
 * My Hangar: the planes that mean something to you. Each is looked up live anywhere in the
 * world (by transponder code once known, by registration until then) and fed into the radar's
 * live picture, so it shows on the map wherever you're looking.
 */
@Singleton
class HangarRepository @Inject constructor(
    db: ContrailDatabase,
    private val lookup: AircraftLookupSource,
    private val traffic: LiveTrafficRepository,
    private val rotation: RotationRepository,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val dao = db.hangarDao()
    private val photos = File(context.filesDir, "hangar").apply { mkdirs() }

    fun observe(): Flow<List<HangarPlane>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun observe(registration: String): Flow<HangarPlane?> = observe().map { list -> list.firstOrNull { it.registration == registration.uppercase() } }

    suspend fun pin(registration: String, hex: String?, typeCode: String?, airlineIcao: String?, operator: String?) {
        val reg = registration.trim().uppercase()
        val existing = dao.get(reg)
        dao.upsert(
            existing?.copy(hex = hex ?: existing.hex, typeCode = typeCode ?: existing.typeCode, airlineIcao = airlineIcao ?: existing.airlineIcao)
                ?: HangarPlaneEntity(
                    registration = reg,
                    hex = hex ?: rotation.hexForTail(reg),
                    typeCode = typeCode,
                    airlineIcao = airlineIcao,
                    operator = operator,
                    addedAtMs = clock.nowMs(),
                ),
        )
    }

    suspend fun unpin(registration: String) {
        dao.get(registration.uppercase())?.photoPath?.let { File(it).delete() }
        dao.delete(registration.uppercase())
        dao.deleteSeen(registration.uppercase())
    }

    suspend fun setStory(registration: String, story: String) {
        val p = dao.get(registration.uppercase()) ?: return
        dao.upsert(p.copy(story = story.trim().ifEmpty { null }))
    }

    suspend fun setAlerts(registration: String, on: Boolean) {
        val p = dao.get(registration.uppercase()) ?: return
        dao.upsert(p.copy(alerts = on))
    }

    /** Keeps your own photo of the plane, copied into the app's private files. */
    suspend fun setPhoto(registration: String, uri: Uri) = withContext(Dispatchers.IO) {
        val p = dao.get(registration.uppercase()) ?: return@withContext
        val out = File(photos, "${p.registration}-${clock.nowMs()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@withContext
        p.photoPath?.let { File(it).delete() }
        dao.upsert(p.copy(photoPath = out.absolutePath))
    }

    /**
     * Looks every pinned plane up live and adds the ones flying to the radar. Returns what changed
     * since the last look: take-offs and landings, for alerts and Happening now.
     */
    suspend fun refreshLive(): List<HangarEvent> {
        val events = mutableListOf<HangarEvent>()
        for (p in dao.all()) {
            val found = try {
                (p.hex?.let { lookup.byHex(it) } ?: lookup.byRegistration(p.registration)).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            traffic.addFound(listOf(found))
            val airborne = !found.onGround
            val before = dao.seen(p.registration)
            val model = p.toModel(before)
            when {
                before?.airborne == false && airborne -> events += HangarEvent(model, HangarEvent.Kind.TookOff, found)
                before?.airborne == true && !airborne -> events += HangarEvent(model, HangarEvent.Kind.Landed, found)
            }
            dao.upsertSeen(HangarSeenEntity(p.registration, found.hex, clock.nowMs(), found.lat, found.lon, found.callsign?.trim()?.ifEmpty { null }, airborne))
            if (p.hex == null) dao.upsert(p.copy(hex = found.hex))
        }
        return events
    }

    /** The pinned plane's flights over the last week (OpenSky; empty without an account). */
    suspend fun recentFlights(plane: HangarPlane): List<EarlierLeg> {
        val hex = plane.hex ?: rotation.hexForTail(plane.registration) ?: return emptyList()
        return rotation.recentFlights(hex)
    }

    private fun HangarPlaneEntity.toModel(seen: HangarSeenEntity?) = HangarPlane(
        registration, hex ?: seen?.hex, typeCode, airlineIcao, operator, story, photoPath, addedAtMs, alerts,
        seen?.seenMs, seen?.lat, seen?.lon, seen?.callsign, seen?.airborne,
    )

    private fun HangarRow.toModel() = HangarPlane(
        registration, hex, typeCode, airlineIcao, operator, story, photoPath, addedAtMs, alerts,
        seenMs, lat, lon, callsign, airborne,
    )
}
