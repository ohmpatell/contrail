package com.contrail.contrail.core.data.moments

import android.content.Context
import android.net.Uri
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.MomentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What kind of moment. The first six are ones you write; the rest Contrail notices. */
enum class MomentKind(val key: String, val label: String) {
    Cockpit("cockpit", "Cockpit visit"),
    Upgrade("upgrade", "Upgrade"),
    Livery("livery", "Special livery"),
    Crew("crew", "Met the crew"),
    View("view", "Great view"),
    Other("other", "Something else"),
    // Noticed from your logbook.
    FirstFlight("first-flight", "First flight"),
    FirstType("first-type", "First on a type"),
    FirstAirline("first-airline", "First with an airline"),
    Milestone("milestone", "Milestone"),
    Crossing("crossing", "Crossing"),
    HangarFlight("hangar-flight", "Flew on a Hangar plane"),
    // Captured in flight by Cabin mode.
    Sunset("sunset", "Sunset aloft"),
    Sunrise("sunrise", "Sunrise aloft"),
    Border("border", "Crossed a border"),
    Takeoff("takeoff", "Wheels up"),
    Landing("landing", "Touchdown"),
    ;

    val yours: Boolean get() = ordinal <= Other.ordinal

    companion object {
        val writable = entries.filter { it.yours }
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Other
    }
}

/** One moment, written by you, captured in flight, or noticed in your logbook. */
data class Moment(
    val id: String,
    val kind: MomentKind,
    val title: String,
    val note: String?,
    val date: LocalDate,
    /** Your flight it belongs to, if any (local id). */
    val segmentId: Long?,
    val registration: String?,
    val photoPath: String?,
    /** Written by you (editable) rather than noticed by Contrail. */
    val mine: Boolean,
    val captured: Boolean = false,
)

@Singleton
class MomentRepository @Inject constructor(
    private val db: ContrailDatabase,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val dao = db.momentDao()
    private val photos = File(context.filesDir, "moments").apply { mkdirs() }

    /** Moments you wrote or Cabin mode captured, with their flight's local id. */
    fun observeStored(): Flow<List<Moment>> = dao.observeAll().map { rows ->
        val segIds = db.syncDao().segments().associate { it.uid to it.id }
        rows.map { r ->
            Moment(
                id = r.uid, kind = MomentKind.of(r.kind), title = r.title, note = r.note, date = LocalDate.parse(r.date),
                segmentId = r.segmentUid?.let(segIds::get), registration = r.registration, photoPath = r.photoPath,
                mine = r.source == "you", captured = r.source == "cabin",
            )
        }
    }

    /** Saves a new moment (or replaces [id]), copying [photo] into private storage. */
    suspend fun save(
        id: String?,
        kind: MomentKind,
        title: String,
        note: String?,
        date: LocalDate,
        segmentId: Long?,
        registration: String?,
        photo: Uri?,
        source: String = "you",
        lat: Double? = null,
        lon: Double? = null,
    ): String {
        val uid = id ?: UUID.randomUUID().toString()
        val existing = dao.get(uid)
        val photoPath = photo?.let { copyPhoto(uid, it) } ?: existing?.photoPath
        val segUid = segmentId?.let { sid -> db.syncDao().segments().firstOrNull { it.id == sid }?.uid }
        dao.upsert(
            MomentEntity(
                uid, kind.key, title.trim().ifEmpty { kind.label }, note?.trim()?.ifEmpty { null }, date.toString(),
                segUid, registration?.trim()?.uppercase()?.ifEmpty { null }, photoPath, source, lat, lon, existing?.createdAtMs ?: clock.nowMs(),
            ),
        )
        return uid
    }

    suspend fun delete(id: String) {
        dao.get(id)?.photoPath?.let { File(it).delete() }
        dao.delete(id)
    }

    private suspend fun copyPhoto(uid: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val out = File(photos, "$uid-${clock.nowMs()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        out.absolutePath
    }
}
