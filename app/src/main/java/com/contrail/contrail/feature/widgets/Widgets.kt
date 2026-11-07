package com.contrail.contrail.feature.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.contrail.contrail.MainActivity
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.sky.SkyContact
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.trip.FlightPhase
import com.contrail.contrail.core.data.trip.FlightProgressCalc
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.NightCockpitColors
import com.contrail.contrail.feature.sky.alerts.ProximityAlerts
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** What the background check saw at your spot, saved for the overhead widget. */
data class OverheadSnapshot(
    val blips: List<Blip>,
    val callsign: String?,
    val type: String?,
    val altitudeFt: Int?,
    val distanceKm: Double,
    val bearingDeg: Double,
    val count: Int,
    val radiusKm: Int,
    val spotName: String?,
    val atMs: Long,
)

@Singleton
class OverheadSnapshotStore @Inject constructor(@Named("sky") private val store: DataStore<Preferences>) {
    private val blips = stringPreferencesKey("ov.blips")
    private val closest = stringPreferencesKey("ov.closest")
    private val count = intPreferencesKey("ov.count")
    private val radius = intPreferencesKey("ov.radius")
    private val spot = stringPreferencesKey("ov.spot")
    private val at = longPreferencesKey("ov.at")

    /** Keeps the nearest 30 contacts: enough to fill the scope without bloating preferences. */
    suspend fun save(contacts: List<SkyContact>, radiusKm: Int, spotName: String?, nowMs: Long) = store.edit { p ->
        p[blips] = contacts.take(MAX_BLIPS).joinToString(";") { c ->
            val a = c.aircraft
            listOf(
                "%.1f".format(Locale.US, c.bearingDeg),
                "%.2f".format(Locale.US, c.distanceKm),
                a.altitudeFt?.toString() ?: "",
                a.trackDeg?.let { "%.0f".format(Locale.US, it) } ?: "",
            ).joinToString(",")
        }
        contacts.firstOrNull()?.let { p[closest] = "${it.aircraft.displayCallsign}|${it.aircraft.typeName.orEmpty()}" } ?: p.remove(closest)
        p[count] = contacts.size
        p[radius] = radiusKm
        spotName?.let { p[spot] = it }
        p[at] = nowMs
    }

    suspend fun load(): OverheadSnapshot? {
        val p = store.data.first()
        val at = p[at] ?: return null
        val list = p[blips].orEmpty().split(";").filter { it.isNotBlank() }.mapIndexedNotNull { i, row ->
            val f = row.split(",")
            val brg = f.getOrNull(0)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
            val dist = f.getOrNull(1)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
            Blip(brg, dist, f.getOrNull(2)?.toIntOrNull(), f.getOrNull(3)?.toFloatOrNull(), closest = i == 0)
        }
        val (cs, type) = p[closest]?.split("|", limit = 2)?.let { it[0] to it.getOrNull(1)?.ifBlank { null } } ?: (null to null)
        val first = list.firstOrNull()
        return OverheadSnapshot(list, cs, type, first?.altitudeFt, first?.distanceKm ?: 0.0, first?.bearingDeg ?: 0.0, p[count] ?: 0, p[radius] ?: 25, p[spot], at)
    }

    private companion object {
        const val MAX_BLIPS = 30
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun trips(): TripRepository
    fun airports(): AirportRepository
    fun overhead(): OverheadSnapshotStore
}

object Widgets {
    suspend fun updateOverhead(context: Context) = runCatching { OverheadWidget().updateAll(context) }
    suspend fun updateTrips(context: Context) = runCatching {
        NextFlightWidget().updateAll(context)
        PassportWidget().updateAll(context)
    }

    suspend fun hasOverhead(context: Context): Boolean =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(OverheadWidget::class.java).isNotEmpty() }.getOrDefault(false)

    fun entry(context: Context): WidgetEntryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    /** The colours widgets draw with. Follows the app theme once themes can be chosen. */
    fun colors(@Suppress("UNUSED_PARAMETER") context: Context): ContrailColors = NightCockpitColors

    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    /** The departure board for your next flight, or the empty board. */
    fun board(next: FlightSegment?, dir: AirportDirectory, now: Instant, colors: ContrailColors = NightCockpitColors): BoardModel {
        if (next == null) return BoardModel(header = "Contrail · Departures", accent = colors.accent, from = null, to = null)
        val airline = Airlines.byIata(next.airlineIata)
        val accent = airline?.let { Color(it.accentArgb) } ?: colors.accent
        val p = FlightProgressCalc.compute(next, now)
        val status = when (p.phase) {
            FlightPhase.Upcoming -> "Scheduled" to colors.textSecondary
            FlightPhase.Boarding -> "Boarding" to colors.statusDelayed
            FlightPhase.Departing -> "Departing" to colors.statusOnTime
            FlightPhase.Airborne -> "In the air" to colors.statusAirborne
            FlightPhase.Landing -> "Landing" to colors.statusAirborne
            FlightPhase.Landed -> "Landed" to colors.statusOnTime
        }
        val cells = listOfNotNull(
            "Flight" to (next.airlineIata + next.flightNumber).uppercase(Locale.UK),
            "Dep" to TIME.format(next.departureLocal),
            next.departureTerminal?.let { "Term" to it.uppercase(Locale.UK) },
            next.gate?.let { "Gate" to it.uppercase(Locale.UK) },
            next.seat?.let { "Seat" to it.uppercase(Locale.UK) },
        )
        val underway = p.phase >= FlightPhase.Departing
        val untilDep = Duration.between(now, next.departure)
        val footer = when {
            underway && p.phase != FlightPhase.Landed -> "Lands ${TIME.format(next.arrivalLocal)}" + if (next.timesEstimated) " · estimated" else ""
            p.phase == FlightPhase.Landed -> "Landed ${TIME.format(next.arrivalLocal)}"
            else -> "Departs in " + countdown(untilDep)
        }
        return BoardModel(
            header = "${airline?.name ?: next.airlineName ?: next.airlineIata} · ${next.designator}",
            accent = accent,
            from = next.origin,
            to = next.destination,
            fromCity = dir.find(next.origin)?.city ?: next.origin,
            toCity = dir.find(next.destination)?.city ?: next.destination,
            status = status.first,
            statusColor = status.second,
            cells = cells,
            progress = if (underway) p.fraction else null,
            footer = footer,
            footnote = "${DAY.format(next.departureLocal)} · ${TIME.format(next.departureLocal)}",
        )
    }

    fun countdown(d: Duration): String = when {
        d.toDays() >= 2 -> "${d.toDays()} days"
        d.toHours() >= 1 -> "${d.toHours()} h ${d.toMinutesPart()} min"
        else -> "${d.toMinutes().coerceAtLeast(0)} min"
    }

    fun scope(snap: OverheadSnapshot?): ScopeModel {
        if (snap == null) {
            return ScopeModel(emptyList(), null, null, null, null, null, null, emptyTitle = "Open Sky once", emptySubtitle = "so Contrail knows where you are")
        }
        val detail = snap.callsign?.let {
            String.format(Locale.US, "%.1f km %s", snap.distanceKm, compass(snap.bearingDeg)) +
                (snap.altitudeFt?.let { a -> String.format(Locale.US, " · %,d ft", a) } ?: "")
        }
        val time = Instant.ofEpochMilli(snap.atMs).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME)
        return ScopeModel(
            blips = snap.blips,
            radiusKm = snap.radiusKm,
            spotName = snap.spotName?.substringBefore(" ("),
            closestCallsign = snap.callsign,
            closestType = snap.type,
            closestDetail = detail,
            footer = "${snap.count} nearby · $time",
        )
    }

    private fun compass(b: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((b + 22.5) % 360) / 45).toInt()]

    /** This year's flights and distance against a lap of the Earth, plus lifetime totals. */
    fun ring(segments: List<FlightSegment>, dir: AirportDirectory, now: Instant): RingModel {
        val flown = segments.filter { it.arrival.isBefore(now) }
        val year = LocalDate.ofInstant(now, ZoneId.systemDefault()).year
        fun km(s: FlightSegment): Double {
            val a = dir.find(s.origin) ?: return 0.0
            val b = dir.find(s.destination) ?: return 0.0
            return Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000
        }
        val thisYear = flown.filter { it.departureLocal.year == year }
        return RingModel(
            year = year,
            yearFlights = thisYear.size,
            yearKm = thisYear.sumOf(::km),
            lifetimeFlights = flown.size,
            lifetimeKm = flown.sumOf(::km).toLong(),
            countries = flown.flatMap { listOf(it.origin, it.destination) }.mapNotNull { dir.find(it)?.countryIso }.toSet().size,
            favourite = flown.mapNotNull { it.aircraftType }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { AircraftTypes.nameFor(it) ?: it },
        )
    }
}

private fun open(context: Context, tab: String): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** A widget that is one painted image at its exact size. */
@Composable
private fun Painted(bitmap: Bitmap, description: String, onClick: Action) {
    Image(
        ImageProvider(bitmap),
        contentDescription = description,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).clickable(onClick),
    )
}

/** Your next flight as a split-flap departure board, with progress once it's under way. */
class NextFlightWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = Widgets.entry(context)
        val now = Instant.now()
        val next = e.trips().trips.first().flatMap { it.segments }.filter { it.arrival.isAfter(now - FlightProgressCalc.SHOW_AFTER) }.minByOrNull { it.departure }
        val model = Widgets.board(next, e.airports().directory(), now, Widgets.colors(context))
        val art = WidgetArt(context, Widgets.colors(context))
        provideContent {
            val size = LocalSize.current
            val description = if (model.from == null) "No flights booked" else "${model.header}, ${model.fromCity} to ${model.toCity}, ${model.status}, ${model.footer}"
            Painted(art.board(size.width.value, size.height.value, model), description, open(context, "Trips"))
        }
    }
}

/** A small radar scope of what's around your spot, from the last background check. */
class OverheadWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.scope(Widgets.entry(context).overhead().load())
        val art = WidgetArt(context, Widgets.colors(context))
        provideContent {
            val size = LocalSize.current
            val description = model.closestCallsign?.let { "Closest aircraft $it, ${model.closestDetail}" } ?: model.emptyTitle
            Painted(art.scope(size.width.value, size.height.value, model), description, open(context, "Sky"))
        }
    }
}

/** Your year as a ring around the Earth, with lifetime totals beside it. */
class PassportWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = Widgets.entry(context)
        val model = Widgets.ring(e.trips().trips.first().flatMap { it.segments }, e.airports().directory(), Instant.now())
        val art = WidgetArt(context, Widgets.colors(context))
        provideContent {
            val size = LocalSize.current
            Painted(
                art.yearRing(size.width.value, size.height.value, model),
                "${model.yearFlights} flights in ${model.year}, ${model.lifetimeFlights} in total",
                open(context, "Passport"),
            )
        }
    }
}

class NextFlightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextFlightWidget()
}

class OverheadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverheadWidget()

    /** The widget needs the background check running even when alerts are off. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ProximityAlerts(context).schedule(true)
    }
}

class PassportWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PassportWidget()
}
