package com.contrail.contrail.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.contrail.contrail.MainActivity
import com.contrail.contrail.R
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.sky.SkyContact
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.feature.sky.alerts.ProximityAlerts
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** The closest plane at your spot, saved by the background check for the overhead widget. */
data class OverheadSnapshot(
    val callsign: String,
    val type: String?,
    val altitudeFt: Int?,
    val distanceKm: Double,
    val bearingDeg: Double,
    val count: Int,
    val spotName: String?,
    val atMs: Long,
)

@Singleton
class OverheadSnapshotStore @Inject constructor(@Named("sky") private val store: DataStore<Preferences>) {
    private val callsign = stringPreferencesKey("ov.callsign")
    private val type = stringPreferencesKey("ov.type")
    private val alt = intPreferencesKey("ov.alt")
    private val dist = doublePreferencesKey("ov.dist")
    private val bearing = doublePreferencesKey("ov.bearing")
    private val count = intPreferencesKey("ov.count")
    private val spot = stringPreferencesKey("ov.spot")
    private val at = longPreferencesKey("ov.at")

    suspend fun save(c: SkyContact?, n: Int, spotName: String?, nowMs: Long) = store.edit { p ->
        if (c == null) p.remove(callsign) else {
            p[callsign] = c.aircraft.displayCallsign
            c.aircraft.typeName?.let { p[type] = it } ?: p.remove(type)
            p[alt] = c.aircraft.altitudeFt ?: -1
            p[dist] = c.distanceKm
            p[bearing] = c.bearingDeg
        }
        p[count] = n
        spotName?.let { p[spot] = it }
        p[at] = nowMs
    }

    suspend fun load(): OverheadSnapshot? {
        val p = store.data.first()
        val at = p[at] ?: return null
        val cs = p[callsign] ?: return OverheadSnapshot("", null, null, 0.0, 0.0, p[count] ?: 0, p[spot], at)
        return OverheadSnapshot(cs, p[type], p[alt]?.takeIf { it >= 0 }, p[dist] ?: 0.0, p[bearing] ?: 0.0, p[count] ?: 0, p[spot], at)
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
}

// Night-cockpit colours, repeated here because Glance can't read Compose theme tokens.
private val Frost = Color(0xFFF2F6FF)
private val FrostDim = Color(0xFFB9C4DE)
private val FrostFaint = Color(0xFF7D89A8)
private val Sky = Color(0xFF8FD8FF)
private val Violet = Color(0xFFB08CFF)

private fun open(context: Context, tab: String) =
    actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
private fun Label(text: String, color: Color = FrostFaint) {
    Text(text.uppercase(), style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
private fun Big(text: String, size: Int = 26, color: Color = Frost) {
    Text(text, style = TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
private fun Small(text: String, color: Color = FrostDim) {
    Text(text, style = TextStyle(color = ColorProvider(color), fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun Card(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).cornerRadius(24.dp).padding(14.dp),
    ) { content() }
}

/** Countdown to your next flight, in its airline's colour. */
class NextFlightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = Widgets.entry(context)
        val now = Instant.now()
        val next: FlightSegment? = e.trips().trips.first().flatMap { it.segments }.filter { it.arrival.isAfter(now) }.minByOrNull { it.departure }
        val dir = e.airports().directory()
        provideContent {
            Card(GlanceModifier.clickable(open(context, "Trips"))) {
                if (next == null) {
                    Column {
                        Label("Contrail")
                        Spacer(GlanceModifier.height(6.dp))
                        Big("No flights booked", 18)
                        Small("Add one in the Trips tab")
                    }
                } else {
                    val airline = Airlines.byIata(next.airlineIata)
                    val accent = airline?.let { Color(it.accentArgb) } ?: Sky
                    val inAir = next.departure.isBefore(now)
                    val d = Duration.between(now, if (inAir) next.arrival else next.departure)
                    Column(GlanceModifier.fillMaxWidth()) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Label("${airline?.name ?: next.airlineName ?: next.airlineIata} · ${next.designator}", accent)
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Big(next.origin, 28)
                            Spacer(GlanceModifier.width(8.dp))
                            Image(ImageProvider(R.drawable.ic_widget_plane), contentDescription = null, modifier = GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)))
                            Spacer(GlanceModifier.width(8.dp))
                            Big(next.destination, 28)
                        }
                        Small("${dir.find(next.origin)?.city ?: next.origin} → ${dir.find(next.destination)?.city ?: next.destination}")
                        Spacer(GlanceModifier.height(6.dp))
                        Label(if (inAir) "Lands in" else "Departs in")
                        Big(countdown(d), 22, accent)
                        Small("${DAY.format(next.departureLocal)} · ${TIME.format(next.departureLocal)}" + (next.seat?.let { " · Seat $it" } ?: ""), FrostFaint)
                    }
                }
            }
        }
    }

    private fun countdown(d: Duration): String = when {
        d.toDays() >= 2 -> "${d.toDays()} days"
        d.toHours() >= 1 -> "${d.toHours()} h ${d.toMinutesPart()} min"
        else -> "${d.toMinutes().coerceAtLeast(0)} min"
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    }
}

/** The closest plane at your spot, from the last background check. */
class OverheadWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = Widgets.entry(context).overhead().load()
        provideContent {
            Card(GlanceModifier.clickable(open(context, "Sky"))) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Label("Overhead" + (snap?.spotName?.let { " · ${it.substringBefore(" (")}" } ?: ""), Sky)
                    Spacer(GlanceModifier.height(6.dp))
                    when {
                        snap == null -> {
                            Big("Open Sky once", 16)
                            Small("so Contrail knows where you are")
                        }
                        snap.callsign.isEmpty() -> {
                            Big("Clear skies", 20)
                            Small("Nothing nearby right now")
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(ImageProvider(R.drawable.ic_widget_plane), contentDescription = null, modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(ColorProvider(Sky)))
                                Spacer(GlanceModifier.width(6.dp))
                                Big(snap.callsign, 22)
                            }
                            snap.type?.let { Small(it) }
                            Spacer(GlanceModifier.height(4.dp))
                            Small(
                                String.format(Locale.US, "%.1f km %s", snap.distanceKm, compass(snap.bearingDeg)) +
                                    (snap.altitudeFt?.let { String.format(Locale.US, " · %,d ft", it) } ?: ""),
                                Frost,
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    snap?.let { Small("${it.count} nearby · ${Instant.ofEpochMilli(it.atMs).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}", FrostFaint) }
                }
            }
        }
    }

    private fun compass(b: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((b + 22.5) % 360) / 45).toInt()]
}

/** Lifetime flying: flights, distance, countries. */
class PassportWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = Widgets.entry(context)
        val now = Instant.now()
        val flown = e.trips().trips.first().flatMap { it.segments }.filter { it.arrival.isBefore(now) }
        val dir = e.airports().directory()
        val km = flown.sumOf { s ->
            val a = dir.find(s.origin)
            val b = dir.find(s.destination)
            if (a != null && b != null) Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000 else 0.0
        }.toLong()
        val countries = flown.flatMap { listOf(it.origin, it.destination) }.mapNotNull { dir.find(it)?.countryIso }.toSet().size
        val fav = flown.mapNotNull { it.aircraftType }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        provideContent {
            Card(GlanceModifier.clickable(open(context, "Passport"))) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Label("Passport", Violet)
                    Spacer(GlanceModifier.height(4.dp))
                    Big(String.format(Locale.US, "%,d", flown.size), 30)
                    Small(if (flown.size == 1) "flight" else "flights", FrostFaint)
                    Spacer(GlanceModifier.height(4.dp))
                    Small(String.format(Locale.US, "%,d km · %d countries", km, countries), Frost)
                    fav?.let { Small("Favourite: ${AircraftTypes.nameFor(it) ?: it}", FrostFaint) }
                }
            }
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
