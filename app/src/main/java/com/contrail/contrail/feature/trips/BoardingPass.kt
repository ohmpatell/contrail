package com.contrail.contrail.feature.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** What a boarding pass shows. Every field can be a placeholder while you're still typing. */
@Immutable
data class PassData(
    val airline: String,
    val designator: String,
    val from: String,
    val fromCity: String,
    val to: String,
    val toCity: String,
    val date: String,
    val year: String = "",
    val departs: String,
    val arrives: String,
    val duration: String,
    val seat: String?,
    val cabin: String?,
    val aircraft: String?,
    val airlineIata: String?,
    val estimated: Boolean = false,
) {
    companion object {
        private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

        fun of(s: FlightSegment, places: Map<String, AirportInfo>) = PassData(
            airline = Airlines.byIata(s.airlineIata)?.name ?: s.airlineName ?: s.airlineIata.ifEmpty { "Flight" },
            designator = s.designator.ifEmpty { "—" },
            from = s.origin,
            fromCity = TripFormat.city(s.origin, places),
            to = s.destination,
            toCity = TripFormat.city(s.destination, places),
            date = DATE.format(s.departureLocal).uppercase(),
            year = s.departureLocal.year.toString(),
            departs = TripFormat.time(s.departureLocal),
            arrives = TripFormat.time(s.arrivalLocal) + (TripFormat.dayShift(s) ?: ""),
            duration = TripFormat.duration(s.duration),
            seat = s.seat,
            cabin = s.cabin?.label,
            aircraft = AircraftTypes.nameFor(s.aircraftType) ?: s.aircraftType,
            airlineIata = s.airlineIata,
            estimated = s.timesEstimated,
        )
    }
}

/** A rounded ticket with two semicircle notches where the stub tears off. */
class TicketShape(private val stubAt: Float, private val notch: Dp = 11.dp, private val corner: Dp = 22.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notch.toPx() }
        val c = with(density) { corner.toPx() }
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(c))) }
        val x = size.width * stubAt
        val holes = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(x, 0f), r))
            addOval(androidx.compose.ui.geometry.Rect(Offset(x, size.height), r))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}

/**
 * A glass boarding pass in the airline's colour: codes as split-flap tiles, a dotted arc
 * between them, and a stub with the seat and a barcode drawn from the flight number.
 */
@Composable
fun BoardingPass(pass: PassData, modifier: Modifier = Modifier, flap: Boolean = true) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val accent = airlineAccent(pass.airlineIata)
    val stubAt = 0.74f
    GlassSurface(modifier.fillMaxWidth(), shape = remember { TicketShape(stubAt) }, glow = accent.copy(alpha = 0.8f)) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.06f), Color.Transparent), start = Offset.Zero, end = Offset(900f, 700f))),
        )
        // Perforation along the stub.
        Canvas(Modifier.matchParentSize()) {
            val x = size.width * stubAt
            drawLine(
                colors.textPrimary.copy(alpha = 0.22f), Offset(x, 14.dp.toPx()), Offset(x, size.height - 14.dp.toPx()), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(stubAt).padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(pass.airline, Modifier.weight(1f), color = accent)
                    Text(pass.designator, style = ContrailTheme.type.numeric, color = colors.textPrimary, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (flap) SplitFlapText(pass.from, style = ContrailTheme.type.flap) else Text(pass.from, style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                        Text(pass.fromCity, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.width(56.dp).height(28.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                            drawLine(
                                accent.copy(alpha = 0.6f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                        Icon(ContrailIcons.Plane, null, tint = accent, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f })
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        if (flap) SplitFlapText(pass.to, style = ContrailTheme.type.flap) else Text(pass.to, style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                        Text(pass.toCity, style = ContrailTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    PassField(if (pass.year.isEmpty()) "Date" else "Date · ${pass.year}", pass.date, Modifier.weight(1.15f))
                    PassField("Departs", pass.departs, Modifier.weight(0.95f), estimated = pass.estimated)
                    PassField("Arrives", pass.arrives, Modifier.weight(1.1f), estimated = pass.estimated)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Icon(ContrailIcons.Clock, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    Text(
                        pass.duration + if (pass.estimated) " · estimated" else "",
                        style = ContrailTheme.type.numericSmall,
                        color = colors.textTertiary,
                    )
                    pass.aircraft?.let {
                        Text("·  $it", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(
                Modifier.weight(1f - stubAt).padding(vertical = spacing.lg, horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Eyebrow("Seat")
                Text(pass.seat ?: "—", style = ContrailTheme.type.displaySmall, color = if (pass.seat != null) colors.textPrimary else colors.textDisabled, maxLines = 1)
                Eyebrow(pass.cabin ?: "Cabin", color = if (pass.cabin != null) accent else colors.textDisabled)
                Barcode(pass.designator + pass.date, colors.textPrimary.copy(alpha = 0.55f), Modifier.fillMaxWidth().height(34.dp))
            }
        }
    }
}

@Composable
private fun PassField(label: String, value: String, modifier: Modifier, estimated: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Eyebrow(label)
        Text(
            value,
            style = ContrailTheme.type.numeric.copy(fontSize = 15.sp),
            softWrap = false,
            color = if (estimated) ContrailTheme.colors.textTertiary else ContrailTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** Bars drawn from a seed, so each pass has its own stable barcode. */
@Composable
fun Barcode(seed: String, color: Color, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val bars = remember(seed) {
        val r = Random(seed.hashCode())
        List(34) { r.nextInt(1, 4) to (r.nextFloat() > 0.35f) }
    }
    Canvas(modifier) {
        val total = bars.sumOf { it.first }
        val unit = (if (vertical) size.height else size.width) / total
        var pos = 0f
        bars.forEach { (w, on) ->
            if (on) {
                if (vertical) drawRect(color, Offset(0f, pos), Size(size.width, unit * w * 0.85f))
                else drawRect(color, Offset(pos, 0f), Size(unit * w * 0.85f, size.height))
            }
            pos += unit * w
        }
    }
}

/** Placeholder pass while the Add flight form is still empty. */
fun emptyPass(flight: String, from: String, to: String, airlineIata: String?) = PassData(
    airline = Airlines.byIata(airlineIata)?.name ?: "Your flight",
    designator = flight.ifBlank { "—" },
    from = from.uppercase().take(3).padEnd(3, '·'),
    fromCity = "From",
    to = to.uppercase().take(3).padEnd(3, '·'),
    toCity = "To",
    date = "—",
    departs = "--:--",
    arrives = "--:--",
    duration = "—",
    seat = null,
    cabin = null,
    aircraft = null,
    airlineIata = airlineIata,
)
