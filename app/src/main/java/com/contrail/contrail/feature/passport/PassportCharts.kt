package com.contrail.contrail.feature.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.feature.trips.add.flag
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** A 0→1 value that grows once when a chart first appears. */
@Composable
private fun rememberGrow(delayMs: Int = 200): Float {
    val motion = ContrailTheme.motion
    val grow = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) grow.animateTo(1f, tween(1_100, delayMillis = delayMs, easing = FastOutSlowInEasing)) }
    return grow.value
}

/** Flights per year as rising glass bars, with the busiest year labelled. */
@Composable
fun YearsChart(i: Insights) {
    if (i.perYear.isEmpty()) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val grow = rememberGrow()
    val first = i.perYear.first().first
    val last = i.perYear.last().first
    val years = (maxOf(first, last - 15)..last).toList()
    val counts = i.perYear.toMap()
    val max = counts.values.max().coerceAtLeast(1)
    val best = counts.maxByOrNull { it.value }
    SectionCard("Flights per year", trailing = {
        best?.let { Text("Busiest: ${it.key}", style = ContrailTheme.type.numericSmall, color = colors.passport) }
    }) {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            years.forEach { y ->
                val n = counts[y] ?: 0
                val top = n == best?.value && n > 0
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (n > 0) Text("$n", style = ContrailTheme.type.numericSmall.copy(fontSize = 10.sp), color = if (top) colors.passport else colors.textTertiary, maxLines = 1)
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height((100.dp * (n.toFloat() / max) * grow).coerceAtLeast(3.dp))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(
                                if (n == 0) Brush.verticalGradient(listOf(colors.hairline, colors.hairline))
                                else Brush.verticalGradient(listOf(if (top) colors.passport else colors.accent, colors.accent.copy(alpha = 0.25f))),
                            ),
                    )
                }
            }
        }
        Row {
            Text("${years.first()}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
            Text("${years.last()}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
        }
    }
}

/** Years down, months across: brighter cells are busier months. */
@Composable
fun FlyingCalendar(i: Insights) {
    if (i.calendar.isEmpty()) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val rows = i.calendar.entries.sortedByDescending { it.key }.take(8)
    val max = rows.maxOf { it.value.max() }.coerceAtLeast(1)
    val busiestMonth = (0 until 12).maxByOrNull { m -> i.calendar.values.sumOf { it[m] } }
    SectionCard("Flying calendar", trailing = {
        busiestMonth?.let { Text("Peak: ${Month.of(it + 1).getDisplayName(TextStyle.FULL, Locale.UK)}", style = ContrailTheme.type.numericSmall, color = colors.passport) }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(36.dp))
            (1..12).forEach { m ->
                Text(
                    Month.of(m).getDisplayName(TextStyle.NARROW, Locale.UK), style = ContrailTheme.type.eyebrow,
                    color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { (year, months) ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$year", style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.width(36.dp))
                months.forEach { n ->
                    val t = n.toFloat() / max
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(if (n == 0) colors.glassTint.copy(alpha = 0.05f) else colors.passport.copy(alpha = 0.2f + 0.8f * t)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (n > 1) Text("$n", style = ContrailTheme.type.numericSmall.copy(fontSize = 9.sp), color = colors.onAccent.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/** A ranked list drawn as bars, label above, count at the end. */
@Composable
fun RankedCard(title: String, items: List<Ranked>, tintFor: @Composable (Ranked) -> Color = { ContrailTheme.colors.accent }, onClick: ((Ranked) -> Unit)? = null) {
    if (items.isEmpty()) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val grow = rememberGrow(300)
    val max = items.maxOf { it.count }.coerceAtLeast(1)
    SectionCard(title) {
        items.forEachIndexed { idx, r ->
            val tint = tintFor(r)
            Column(
                Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(ContrailTheme.shapes.small).clickable(role = Role.Button) { onClick(r) } else Modifier),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("${idx + 1}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary, modifier = Modifier.width(14.dp))
                    Text(r.label, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    r.sub?.let { Text(it, style = ContrailTheme.type.numericSmall, color = colors.textTertiary) }
                    Text("${r.count}×", style = ContrailTheme.type.numeric, color = tint)
                }
                Box(Modifier.padding(start = 22.dp).fillMaxWidth().height(6.dp).clip(ContrailTheme.shapes.chip).background(colors.glassTint.copy(alpha = 0.06f))) {
                    Box(
                        Modifier.fillMaxWidth((r.count.toFloat() / max) * grow).height(6.dp).clip(ContrailTheme.shapes.chip)
                            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.5f), tint))),
                    )
                }
            }
        }
    }
}

/** The airline's livery colour for a ranked airline row. */
@Composable
fun airlineTint(r: Ranked): Color = Airlines.byIata(r.key)?.let { Color(it.accentArgb) } ?: ContrailTheme.colors.accent

data class Slice(val label: String, val count: Int, val color: Color)

/** One bar split into coloured parts, with a legend: cabins, seats, domestic vs international. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitBar(title: String, slices: List<Slice>, modifier: Modifier = Modifier) {
    val parts = slices.filter { it.count > 0 }
    if (parts.isEmpty()) return
    val total = parts.sumOf { it.count }
    val grow = rememberGrow(250)
    val spacing = ContrailTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Eyebrow(title)
        Row(Modifier.fillMaxWidth().height(12.dp).clip(ContrailTheme.shapes.chip), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { p ->
                Box(Modifier.weight(p.count.toFloat()).fillMaxHeight().background(p.color.copy(alpha = 0.35f + 0.65f * grow)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            parts.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Box(Modifier.size(8.dp).clip(ContrailTheme.shapes.chip).background(p.color))
                    Text(p.label, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textSecondary)
                    Text("${(p.count * 100f / total).toInt()}%", style = ContrailTheme.type.numericSmall, color = ContrailTheme.colors.textTertiary)
                }
            }
        }
    }
}

/** How you fly: cabins, seats, red-eyes and borders crossed. */
@Composable
fun HowYouFlyCard(i: Insights, flights: Int) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("How you fly") {
        SplitBar(
            "Cabin",
            listOf(
                Slice("First", i.cabins[com.contrail.contrail.core.data.trip.Cabin.First] ?: 0, Color(0xFFF2C14E)),
                Slice("Business", i.cabins[com.contrail.contrail.core.data.trip.Cabin.Business] ?: 0, colors.passport),
                Slice("Premium", i.cabins[com.contrail.contrail.core.data.trip.Cabin.PremiumEconomy] ?: 0, colors.statusOnTime),
                Slice("Economy", i.cabins[com.contrail.contrail.core.data.trip.Cabin.Economy] ?: 0, colors.accent),
            ),
        )
        SplitBar(
            "Seat",
            listOf(
                Slice("Window", i.seats[com.contrail.contrail.core.data.trip.SeatType.Window] ?: 0, colors.accent),
                Slice("Middle", i.seats[com.contrail.contrail.core.data.trip.SeatType.Middle] ?: 0, colors.textTertiary),
                Slice("Aisle", i.seats[com.contrail.contrail.core.data.trip.SeatType.Aisle] ?: 0, colors.statusDelayed),
            ),
        )
        SplitBar("Borders", listOf(Slice("International", i.international, colors.passport), Slice("Domestic", i.domestic, colors.statusAirborne)))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            MiniStat("Red-eyes", "${i.redEyes}", Modifier.weight(1f))
            MiniStat("Continents", "${i.continents.size}", Modifier.weight(1f))
            MiniStat("Per year", if (i.perYear.isEmpty()) "—" else String.format(Locale.US, "%.1f", flights.toFloat() / i.perYear.size), Modifier.weight(1f))
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, tint: Color = ContrailTheme.colors.textPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Text(value, style = ContrailTheme.type.numericLarge, color = tint, maxLines = 1)
        Eyebrow(label)
    }
}

/** The four corners of your map, round a compass rose. */
@Composable
fun ExtremesCard(i: Insights, places: Map<String, AirportInfo>) {
    val e = i.extremes
    if (e.north == null) return
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Edges of your map") {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Canvas(Modifier.size(120.dp).align(Alignment.Center)) {
                val c = center
                val r = size.minDimension / 2
                drawCircle(colors.passport.copy(alpha = 0.08f), r, c)
                drawCircle(colors.passport.copy(alpha = 0.4f), r, c, style = Stroke(1.dp.toPx()))
                drawCircle(colors.hairline, r * 0.6f, c, style = Stroke(1.dp.toPx()))
                listOf(0f, 90f, 180f, 270f).forEach { deg ->
                    val a = Math.toRadians(deg.toDouble() - 90)
                    val tip = Offset(c.x + (kotlin.math.cos(a) * r * 0.95f).toFloat(), c.y + (kotlin.math.sin(a) * r * 0.95f).toFloat())
                    val l = Math.toRadians(deg.toDouble() - 90 - 90)
                    val side = Offset((kotlin.math.cos(l) * r * 0.12f).toFloat(), (kotlin.math.sin(l) * r * 0.12f).toFloat())
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(tip.x, tip.y); lineTo(c.x + side.x, c.y + side.y); lineTo(c.x - side.x, c.y - side.y); close()
                    }
                    drawPath(path, if (deg == 0f) colors.passport else colors.textSecondary.copy(alpha = 0.6f))
                }
            }
            Corner("North", e.north, Modifier.align(Alignment.TopCenter), TextAlign.Center)
            Corner("South", e.south, Modifier.align(Alignment.BottomCenter), TextAlign.Center)
            Corner("West", e.west, Modifier.align(Alignment.CenterStart).width(100.dp), TextAlign.Start)
            Corner("East", e.east, Modifier.align(Alignment.CenterEnd).width(100.dp), TextAlign.End)
        }
        i.longestByDistance?.let { (s, km) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Furthest flight")
                    Text("${TripFormat.city(s.origin, places)} → ${TripFormat.city(s.destination, places)}", style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(String.format(Locale.US, "%,d km", km), style = ContrailTheme.type.numeric, color = colors.passport)
            }
        }
        i.shortest?.let { (s, km) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Shortest hop")
                    Text("${TripFormat.city(s.origin, places)} → ${TripFormat.city(s.destination, places)}", style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(String.format(Locale.US, "%,d km", km), style = ContrailTheme.type.numeric, color = colors.accent)
            }
        }
    }
}

@Composable
private fun Corner(label: String, value: String?, modifier: Modifier, align: TextAlign) {
    Column(modifier, horizontalAlignment = when (align) { TextAlign.Start -> Alignment.Start; TextAlign.End -> Alignment.End; else -> Alignment.CenterHorizontally }) {
        Eyebrow(label, color = ContrailTheme.colors.passport)
        Text(value ?: "—", style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textPrimary, textAlign = align, maxLines = 2)
    }
}

/** Every country as a flag tile with how often you landed there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountriesCard(countries: List<Pair<String, Int>>) {
    if (countries.isEmpty()) return
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    SectionCard("Countries", trailing = { Text("${countries.size}", style = ContrailTheme.type.numeric, color = colors.passport) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            countries.forEach { (iso, n) ->
                Column(
                    Modifier.width(72.dp).clip(ContrailTheme.shapes.small).background(colors.glassTint.copy(alpha = 0.07f)).padding(vertical = spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(flag(iso), style = ContrailTheme.type.displaySmall)
                    Text(countryName(iso), style = ContrailTheme.type.label.copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = spacing.xs))
                    if (n > 0) Text("$n×", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
                }
            }
        }
    }
}

/** "JP" → "Japan", in British English like the rest of the app. */
fun countryName(iso: String): String = SHORT_NAMES[iso.uppercase()] ?: fullCountryName(iso)

/** Long official names shortened so they fit a tile. */
private val SHORT_NAMES = mapOf(
    "AE" to "UAE", "GB" to "UK", "US" to "USA", "HK" to "Hong Kong", "MO" to "Macau", "KR" to "South Korea",
    "KP" to "North Korea", "CD" to "DR Congo", "DO" to "Dominican Rep.", "CZ" to "Czechia", "BA" to "Bosnia",
    "TT" to "Trinidad", "VC" to "St Vincent", "SA" to "Saudi Arabia", "NZ" to "New Zealand", "PS" to "Palestine",
)

fun fullCountryName(iso: String): String =
    runCatching { Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.UK) }.getOrNull()?.ifEmpty { null } ?: iso
