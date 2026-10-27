package com.contrail.contrail.feature.trips

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.rotation.TurnBadge
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.Connection
import com.contrail.contrail.core.data.trip.ConnectionKind
import com.contrail.contrail.core.data.trip.ConnectionLength
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.data.trip.Trip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2

/** How trip times read everywhere: local airport time, 24-hour, short day names. */
object TripFormat {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val dayYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    fun time(t: LocalDateTime): String = time.format(t)
    fun day(t: LocalDateTime): String = day.format(t)
    fun dayYear(t: LocalDateTime): String = dayYear.format(t)

    /** "12 – 15 Dec 2026", "28 Dec 2026 – 3 Jan 2027". */
    fun range(trip: Trip): String {
        val a = trip.first.departureLocal.toLocalDate()
        val b = trip.last.arrivalLocal.toLocalDate()
        return when {
            a == b -> dayYear.format(a)
            a.year != b.year -> "${dayYear.format(a)} – ${dayYear.format(b)}"
            a.month == b.month -> "${a.dayOfMonth} – ${dayYear.format(b)}"
            else -> "${dayMonth.format(a)} – ${dayYear.format(b)}"
        }
    }

    /** "45m", "3h 35m", "1d 6h 40m". */
    fun duration(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(0)
        val days = m / 1_440
        val h = (m % 1_440) / 60
        val min = m % 60
        return when {
            days > 0 -> "${days}d ${h}h ${min}m"
            h > 0 -> "${h}h ${min}m"
            else -> "${min}m"
        }
    }

    /** "52d 04:12:09" or "04:12:09". */
    fun countdown(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        val days = s / 86_400
        val clock = String.format(Locale.US, "%02d:%02d:%02d", (s % 86_400) / 3_600, (s % 3_600) / 60, s % 60)
        return if (days > 0) "${days}d $clock" else clock
    }

    /** "+1" when a flight lands on a later local day than it left. */
    fun dayShift(s: FlightSegment): String? {
        val d = java.time.temporal.ChronoUnit.DAYS.between(s.departureLocal.toLocalDate(), s.arrivalLocal.toLocalDate())
        return if (d == 0L) null else if (d > 0) "+$d" else "$d"
    }

    fun city(code: String, places: Map<String, AirportInfo>): String = places[code]?.city ?: code
}

fun ContrailColors.length(l: ConnectionLength): Color = when (l) {
    ConnectionLength.Tight -> statusDelayed
    ConnectionLength.Comfortable -> statusOnTime
    ConnectionLength.Long -> statusAirborne
}

fun ContrailColors.kind(k: ConnectionKind): Color = if (k == ConnectionKind.Stopover) passport else accent

fun ContrailColors.turn(b: TurnBadge): Color = when (b) {
    TurnBadge.Relaxed -> statusOnTime
    TurnBadge.Maybe -> statusDelayed
    TurnBadge.Tight -> statusCancelled
}

/** The airline's livery accent, or the app accent. */
@Composable
fun airlineAccent(iata: String?): Color =
    Airlines.byIata(iata)?.let { Color(it.accentArgb) } ?: ContrailTheme.colors.accent

/** The current instant, ticking once a second (frozen in previews and screenshot tests). */
@Composable
fun rememberNow(fixed: Instant? = null): State<Instant> {
    val inspection = LocalInspectionMode.current
    if (fixed != null || inspection) return remember { mutableStateOf(fixed ?: Instant.now()) }
    return produceState(Instant.now()) {
        while (true) {
            delay(1_000 - (System.currentTimeMillis() % 1_000))
            value = Instant.now()
        }
    }
}

/** A small rounded tag: "Comfortable", "Layover", "Estimated". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(ContrailTheme.shapes.chip)
            .background(if (filled) color.copy(alpha = 0.24f) else color.copy(alpha = 0.1f))
            .border(ContrailTheme.sizes.hairline, color.copy(alpha = 0.55f), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.sm, vertical = ContrailTheme.spacing.xxs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text.uppercase(), style = ContrailTheme.type.eyebrow, color = color, maxLines = 1)
    }
}

/** A code on split-flap tiles over its city name. */
@Composable
fun AirportFlap(code: String, city: String, modifier: Modifier = Modifier, alignEnd: Boolean = false, time: String? = null) {
    Column(
        modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs),
    ) {
        SplitFlapText(code, style = ContrailTheme.type.flap)
        Text(city, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (time != null) Text(time, style = ContrailTheme.type.numeric, color = ContrailTheme.colors.textPrimary)
    }
}

/** A great-circle style arc that draws itself, with a plane riding its tip. [progress] pins it (live flights). */
@Composable
fun RouteArc(accent: Color, modifier: Modifier = Modifier, progress: Float? = null, delayMs: Long = 350) {
    val motion = ContrailTheme.motion
    val drawIn = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) {
            delay(delayMs)
            drawIn.animateTo(1f, tween(1400))
        }
    }
    val hairline = ContrailTheme.colors.hairline
    val plane = rememberVectorPainter(ContrailIcons.Plane)
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.8f)
            quadraticTo(size.width / 2f, -size.height * 0.45f, size.width, size.height * 0.8f)
        }
        drawPath(path, hairline, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
        val measure = PathMeasure().apply { setPath(path, false) }
        val p = (progress ?: 1f) * drawIn.value
        val length = measure.length * p
        val drawn = Path()
        measure.getSegment(0f, length, drawn, true)
        drawPath(drawn, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.2f), accent)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val at = length.coerceAtLeast(0.01f)
        val tip = measure.getPosition(at)
        val tangent = measure.getTangent(at)
        val angle = Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat() + 90f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), center = tip, radius = 16.dp.toPx()), radius = 16.dp.toPx(), center = tip)
        val s = 18.dp.toPx()
        rotate(angle, pivot = tip) {
            translate(tip.x - s / 2, tip.y - s / 2) {
                with(plane) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
            }
        }
        drawCircle(accent, radius = 3.dp.toPx(), center = Offset(0f, size.height * 0.8f))
        drawCircle(accent.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = Offset(size.width, size.height * 0.8f))
    }
}

/** One flight in a trip card: times, codes, a thin route line, flight number and cabin. */
@Composable
fun SegmentRow(s: FlightSegment, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(s.airlineIata)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp)) {
                Text(s.origin, style = ContrailTheme.type.headline, color = colors.textPrimary)
                Text(TripFormat.time(s.departureLocal), style = ContrailTheme.type.numeric, color = colors.textSecondary)
            }
            Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2
                    drawLine(colors.hairline, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                    drawCircle(accent, 2.5.dp.toPx(), Offset(0f, y))
                    drawCircle(accent.copy(alpha = 0.6f), 2.5.dp.toPx(), Offset(size.width, y))
                }
                Text(
                    TripFormat.duration(s.duration),
                    style = ContrailTheme.type.numericSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.background(colors.canvasRaised.copy(alpha = 0.9f), ContrailTheme.shapes.chip).padding(horizontal = spacing.sm),
                )
            }
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                Text(s.destination, style = ContrailTheme.type.headline, color = colors.textPrimary)
                Row {
                    Text(TripFormat.time(s.arrivalLocal), style = ContrailTheme.type.numeric, color = colors.textSecondary)
                    TripFormat.dayShift(s)?.let { Text(it, style = ContrailTheme.type.numericSmall, color = accent) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(s.designator, style = ContrailTheme.type.label, color = accent)
            Text(
                listOfNotNull(TripFormat.day(s.departureLocal), s.cabin?.label, s.seat?.let { "Seat $it" }).joinToString(" · "),
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The connection between two flights, compact: "3h 35m in Hong Kong · Layover · Comfortable". */
@Composable
fun ConnectionPill(c: Connection, places: Map<String, AirportInfo>, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = spacing.xl)
            .clip(ContrailTheme.shapes.small)
            .background(colors.kind(c.kind).copy(alpha = 0.08f))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(if (c.kind == ConnectionKind.Stopover) ContrailIcons.Bed else ContrailIcons.Clock, null, tint = colors.kind(c.kind), modifier = Modifier.size(14.dp))
        Text(
            "${TripFormat.duration(c.duration)} in ${TripFormat.city(c.airport, places)}",
            style = ContrailTheme.type.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Tag(c.kind.name, colors.kind(c.kind))
        if (c.kind == ConnectionKind.Layover) Tag(c.length.label, colors.length(c.length))
    }
}

/** Eyebrow plus a value, for detail grids. */
@Composable
fun Field(label: String, value: String?, modifier: Modifier = Modifier, valueColor: Color = ContrailTheme.colors.textPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Eyebrow(label)
        Text(value ?: "—", style = ContrailTheme.type.numeric, color = if (value == null) ContrailTheme.colors.textDisabled else valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
