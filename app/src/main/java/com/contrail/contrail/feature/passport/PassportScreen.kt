package com.contrail.contrail.feature.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.contrail.contrail.core.designsystem.component.AccentButton
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.Rarity
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.designsystem.component.DottedGlobe
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.MapArc
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.component.SectionCard
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.Livery
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import com.contrail.contrail.feature.trips.Stars
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.navigation.LocalNavigator
import java.util.Locale

@Composable
fun PassportRoute(viewModel: PassportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    PassportScreen(
        state,
        actions = PassportActions(
            onYearInReview = nav::openYearInReview,
            onImport = nav::openLogImport,
            onAdd = { nav.openAddFlight() },
            onLogbook = nav::openLogbook,
            onStamps = nav::openStampBook,
            onFlight = nav::openFlight,
            onAirport = nav::openAirport,
            onSelectYear = viewModel::selectYear,
        ),
    )
}

/** Everything the Passport can open. */
data class PassportActions(
    val onYearInReview: (Int) -> Unit = {},
    val onImport: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onLogbook: () -> Unit = {},
    val onStamps: () -> Unit = {},
    val onFlight: (Long) -> Unit = {},
    val onAirport: (String) -> Unit = {},
    val onSelectYear: (Int?) -> Unit = {},
)

@Composable
fun PassportScreen(state: PassportUiState, actions: PassportActions, modifier: Modifier = Modifier, autoplay: Boolean = false) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val s = state.stats
    val legs = remember(s.flown, state.places) { globeLegs(s.flown, state.places) }
    val markers = remember(s.visited, s.transited, state.places) { (s.visited + s.transited).mapNotNull { state.places[it]?.let { p -> LatLon(p.lat, p.lon) } } }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = spacing.xl, bottom = spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LivePulse(color = colors.passport)
                    Eyebrow(s.years.lastOrNull()?.let { "Flying since $it" } ?: "Your flying life", color = colors.passport)
                }
                Text("Passport", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                GlassIconButton(ContrailIcons.List, "Logbook", actions.onLogbook)
                GlassIconButton(ContrailIcons.Plus, "Add a flight", actions.onAdd)
            }
        }
        if (s.years.size > 1 || state.selectedYear != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ContrailChip("All time", state.selectedYear == null, onClick = { actions.onSelectYear(null) }, accent = colors.passport)
                s.years.forEach { y -> ContrailChip("$y", state.selectedYear == y, onClick = { actions.onSelectYear(y) }, accent = colors.passport) }
            }
        }
        if (s.flights == 0 && !state.loading) {
            RiseIn(0) { EmptyPassport(state, actions) }
            if (s.achievements.isNotEmpty()) RiseIn(1) { AchievementsCard(s.achievements) }
        } else if (s.flights > 0) {
            RiseIn(0) { PassportGlobe(legs, markers, autoplay = autoplay) }
            RiseIn(1) { Headline(s) }
            RiseIn(2) { LogbookPreview(s, state.places, actions) }
            if (s.stamps.isNotEmpty()) RiseIn(3) { StampStrip(s.stamps, actions.onStamps) }
            RiseIn(4) { AchievementsCard(s.achievements) }
            s.years.firstOrNull()?.let { y -> RiseIn(5) { YearCard(state.selectedYear ?: y, s.years, actions.onYearInReview) } }
            if (state.selectedYear == null) RiseIn(6) { YearsChart(s.insights) }
            RiseIn(7) { FlyingCalendar(s.insights) }
            RiseIn(8) { RankedCard("Top airlines", s.insights.topAirlines, tintFor = { airlineTint(it) }) }
            RiseIn(9) {
                RankedCard("Top airports", s.insights.topAirports, tintFor = { colors.passport }) { r ->
                    state.places[r.key]?.icao?.let(actions.onAirport)
                }
            }
            RiseIn(10) { RankedCard("Top routes", s.insights.topRoutes, tintFor = { colors.statusAirborne }) }
            RiseIn(11) { RankedCard("Aircraft you fly most", s.insights.topTypes, tintFor = { colors.statusOnTime }) }
            RiseIn(12) { HowYouFlyCard(s.insights, s.flights) }
            RiseIn(13) { ExtremesCard(s.insights, state.places) }
            RiseIn(14) { CountriesCard(s.insights.countries) }
            RiseIn(15) { Places(s, state.places) }
            RiseIn(16) { Records(s, state.places) }
            RiseIn(17) { Collection(s) }
        }
        if (s.lounges.visits > 0) RiseIn(18) { Lounges(s.lounges) }
    }
}

/** No flights yet: the two ways in, over an empty globe. */
@Composable
private fun EmptyPassport(state: PassportUiState, actions: PassportActions) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(Modifier.fillMaxWidth(), glow = colors.passport.copy(alpha = 0.6f)) {
        Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                DottedGlobe(arcs = emptyList(), centerLon = -40f, modifier = Modifier.fillMaxSize())
            }
            Text("Your Passport fills in as you fly", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
            Text(
                if (state.upcomingFlights > 0) "${state.upcomingFlights} upcoming flight${if (state.upcomingFlights == 1) "" else "s"} will count once they land. Bring in the flights you've already taken:"
                else "Bring in every flight you've already taken. Stamps, medals and your globe build themselves.",
                style = ContrailTheme.type.body,
                color = colors.textSecondary,
            )
            AccentButton("Import from myFlightradar24", actions.onImport, icon = ContrailIcons.Import, accent = colors.passport, modifier = Modifier.fillMaxWidth())
            AccentButton("Add a flight", actions.onAdd, icon = ContrailIcons.Plus, accent = colors.passport, secondary = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The big numbers, and what they add up to. */
@Composable
private fun Headline(s: PassportStats) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(colors.passport.copy(alpha = 0.16f), Color.Transparent, colors.accent.copy(alpha = 0.08f)))))
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Odometer(if (s.flights == 1) "Flight" else "Flights", s.flights.toLong(), Modifier.weight(1f), big = true)
                Odometer("Kilometres", s.distanceKm)
            }
            Row {
                Odometer("Hours aloft", s.minutesAloft / 60, Modifier.weight(1f))
                Odometer("Countries", s.countries.size.toLong(), Modifier.weight(1f))
                Odometer("Airports", (s.visited.size + s.transited.size).toLong(), Modifier.weight(1f))
            }
            Row {
                Odometer("Airlines", s.airlines.size.toLong(), Modifier.weight(1f))
                Odometer("Types", s.types.size.toLong(), Modifier.weight(1f))
                Odometer("Tails", s.tails.size.toLong(), Modifier.weight(1f))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FactChip(String.format(Locale.US, "%.1f× around the Earth", s.laps), colors.passport)
                FactChip(String.format(Locale.US, "%.1f%% of the way to the Moon", s.distanceKm * 100f / PassportInsights.MOON_KM), colors.accent)
                val days = s.minutesAloft / 1_440f
                if (days >= 0.5f) FactChip(String.format(Locale.US, "%.1f days in the air", days), colors.statusOnTime)
            }
        }
    }
}

@Composable
private fun FactChip(text: String, tint: Color) {
    Box(
        Modifier
            .clip(ContrailTheme.shapes.chip)
            .background(tint.copy(alpha = 0.12f))
            .border(ContrailTheme.sizes.hairline, tint.copy(alpha = 0.5f), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.xs),
    ) { Text(text, style = ContrailTheme.type.label, color = ContrailTheme.colors.textPrimary, maxLines = 1) }
}

/** Your three latest flights, and the way into the full logbook. */
@Composable
private fun LogbookPreview(s: PassportStats, places: Map<String, AirportInfo>, actions: PassportActions) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Logbook", Modifier.weight(1f))
            Text(
                "All ${s.flights} flights",
                style = ContrailTheme.type.label,
                color = colors.passport,
                modifier = Modifier.clip(ContrailTheme.shapes.chip).clickable(role = Role.Button, onClick = actions.onLogbook).padding(spacing.xs),
            )
        }
        s.flown.take(3).forEach { f -> LogbookRow(f, places, onClick = { actions.onFlight(f.id) }) }
    }
}

/** The latest stamps in a row; tap to open the stamp book. */
@Composable
private fun StampStrip(stamps: List<Stamp>, onOpen: () -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier.fillMaxWidth().pressScale(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onOpen),
        shape = ContrailTheme.shapes.medium,
    ) {
        Column(Modifier.padding(vertical = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(Modifier.padding(horizontal = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Passport stamps", Modifier.weight(1f))
                Text("${stamps.size}", style = ContrailTheme.type.numeric, color = colors.passport)
                Icon(ContrailIcons.ChevronRight, "Open stamp book", tint = colors.passport, modifier = Modifier.padding(start = spacing.xs).size(18.dp))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                stamps.asReversed().take(12).forEachIndexed { i, st ->
                    PassportStamp(st, Modifier.size(104.dp), delayMs = 250L + i * 110L)
                }
            }
        }
    }
}

/** A number that rolls up from zero like an odometer when it first appears. */
@Composable
fun Odometer(label: String, value: Long, modifier: Modifier = Modifier, big: Boolean = false) {
    val motion = ContrailTheme.motion
    val shown = remember { Animatable(if (motion.reduced) value.toFloat() else 0f) }
    LaunchedEffect(value) { if (motion.reduced) shown.snapTo(value.toFloat()) else shown.animateTo(value.toFloat(), tween(1_600, easing = FastOutSlowInEasing)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xxs)) {
        Text(
            String.format(Locale.US, "%,d", shown.value.toLong()),
            style = if (big) ContrailTheme.type.hero else ContrailTheme.type.numericLarge,
            color = ContrailTheme.colors.textPrimary,
            maxLines = 1,
        )
        Eyebrow(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Places(s: PassportStats, places: Map<String, AirportInfo>) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    SectionCard("Airports") {
        Eyebrow("Visited · ${s.visited.size}", color = colors.passport)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            s.visited.forEach { code -> PlaceChip(code, places[code]?.city, colors.passport, solid = true) }
        }
        if (s.transited.isNotEmpty()) {
            Eyebrow("Transited · ${s.transited.size}", color = colors.accent)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                s.transited.forEach { code -> PlaceChip(code, places[code]?.city, colors.accent, solid = false) }
            }
            Text("Layover airports count as transited. Stopover cities count as visited.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
        }
    }
}

@Composable
private fun PlaceChip(code: String, city: String?, tint: Color, solid: Boolean) {
    Row(
        Modifier
            .clip(ContrailTheme.shapes.chip)
            .background(tint.copy(alpha = if (solid) 0.18f else 0.06f))
            .border(ContrailTheme.sizes.hairline, tint.copy(alpha = if (solid) 0.7f else 0.4f), ContrailTheme.shapes.chip)
            .padding(horizontal = ContrailTheme.spacing.md, vertical = ContrailTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(code, style = ContrailTheme.type.numeric, color = ContrailTheme.colors.textPrimary)
        city?.let { Text(it, style = ContrailTheme.type.bodySmall, color = ContrailTheme.colors.textTertiary) }
    }
}

@Composable
private fun Records(s: PassportStats, places: Map<String, AirportInfo>) {
    val colors = ContrailTheme.colors
    SectionCard("Records") {
        s.longest?.let { l ->
            Record("Longest flight", "${l.designator} · ${l.origin} → ${l.destination}", TripFormat.duration(l.duration))
        }
        s.rarest?.let { r ->
            val rarity = AircraftTypes.rarityFor(r.aircraftType, Airlines.byIata(r.airlineIata))
            Record("Rarest aircraft", AircraftTypes.nameFor(r.aircraftType) ?: r.aircraftType.orEmpty(), rarity.label, if (rarity < Rarity.Common) RarityPalette.Legendary.first() else null)
        }
        s.topRoute?.let { (route, n) -> Record("Most-flown route", route.split("–").joinToString(" ⇄ ") { TripFormat.city(it, places) }, "${n}×") }
        s.topTail?.let { (tail, n) -> Record("Most-flown tail", tail, "${n}×") }
        s.topType?.let { (type, n) -> Record("Favourite type", AircraftTypes.nameFor(type) ?: type, "${n}×") }
        if (s.longest == null) Text("—", color = colors.textDisabled)
    }
}

@Composable
private fun Record(label: String, value: String, trailing: String, tint: Color? = null) {
    val colors = ContrailTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(label)
            Text(value, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = ContrailTheme.type.numeric, color = tint ?: colors.passport)
    }
}

@Composable
private fun YearCard(year: Int, years: List<Int>, onYear: (Int) -> Unit) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button) { onYear(year) },
        shape = ContrailTheme.shapes.medium,
        glow = colors.passport,
    ) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(colors.passport.copy(alpha = 0.3f), colors.accent.copy(alpha = 0.12f), Color.Transparent))))
        Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Year in Review", color = colors.passport)
                Text("Your $year in the air", style = ContrailTheme.type.headline, color = colors.textPrimary)
                if (years.size > 1) Text("Also: ${years.drop(1).joinToString(", ")}", style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
            Icon(ContrailIcons.Sparkle, null, tint = colors.passport, modifier = Modifier.size(ContrailTheme.sizes.iconLarge))
        }
    }
}

/** Every collectible type as a livery render: lit up in your airline's colours once flown, a grey ghost until then. */
@Composable
private fun Collection(s: PassportStats) {
    val spacing = ContrailTheme.spacing
    val flown = s.collection.count { it.flights > 0 }
    SectionCard("Collection", trailing = { Text("$flown of ${s.collection.size}", style = ContrailTheme.type.numeric, color = ContrailTheme.colors.passport) }) {
        s.collection.sortedByDescending { it.flights > 0 }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { t -> TypeCell(t, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeCell(t: CollectedType, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    val flown = t.flights > 0
    val holo = flown && t.rarity < Rarity.Uncommon
    val sweep by if (holo && motion.ambientEnabled) {
        rememberInfiniteTransition(label = "holo").animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    val palette = when (t.rarity) { Rarity.Legendary -> RarityPalette.Legendary; Rarity.Rare -> RarityPalette.Rare; else -> RarityPalette.Uncommon }
    val ghost = remember { Livery(Color(0xFF2A3350), Color(0xFF222A42), null, Color(0xFF2E3858), Color(0xFF283152), Color(0xFF1B2333), useTailArt = false) }
    Column(
        modifier
            .clip(ContrailTheme.shapes.small)
            .background(if (flown) colors.glassTint.copy(alpha = 0.1f) else colors.canvasSunken.copy(alpha = 0.4f))
            .border(
                ContrailTheme.sizes.hairline,
                if (holo) Brush.linearGradient(palette + palette.first(), start = androidx.compose.ui.geometry.Offset(sweep * 600f - 300f, 0f), end = androidx.compose.ui.geometry.Offset(sweep * 600f, 200f))
                else Brush.linearGradient(listOf(colors.hairline, colors.hairline)),
                ContrailTheme.shapes.small,
            )
            .drawWithContent {
                drawContent()
                if (holo) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, palette[1].copy(alpha = 0.16f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(size.width * (sweep * 2 - 1), 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width * (sweep * 2 - 0.4f), size.height),
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Box(Modifier.graphicsLayer { alpha = if (flown) 1f else 0.55f }) {
            val airline = Airlines.byIcao(t.firstAirlineIcao)
            LiveryRender(
                Profiles.forType(t.code),
                if (flown) Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb) else ghost,
                floating = false,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.name.removePrefix("Airbus ").removePrefix("Boeing ").removePrefix("Embraer ").removePrefix("Bombardier ").removePrefix("De Havilland "),
                style = ContrailTheme.type.label,
                color = if (flown) colors.textPrimary else colors.textDisabled,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (flown) Text("${t.flights}×", style = ContrailTheme.type.numericSmall, color = colors.passport)
        }
        if (holo) Eyebrow(t.rarity.label, color = palette.first())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lounges(l: LoungeStats) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = colors.statusDelayed
    SectionCard("Lounges", trailing = { Icon(ContrailIcons.Lounge, null, tint = gold, modifier = Modifier.size(18.dp)) }) {
        Row {
            Odometer("Lounges", l.lounges.toLong(), Modifier.weight(1f))
            Odometer("Visits", l.visits.toLong(), Modifier.weight(1f))
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(PassportCalculator.hours(l.minutes), style = ContrailTheme.type.numericLarge, color = colors.textPrimary, maxLines = 1)
                Eyebrow("Time inside")
            }
        }
        l.averageRating?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Stars(Math.round(it))
                Text(String.format(Locale.US, "%.1f average", it), style = ContrailTheme.type.bodySmall, color = colors.textTertiary)
            }
        }
        if (l.favourites.isNotEmpty()) {
            Eyebrow("Favourites", color = gold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                l.favourites.forEach { Tag("${it.airport} · ${it.name}", gold, filled = true, icon = ContrailIcons.StarFilled) }
            }
        }
        Eyebrow("Lounge collection")
        l.collection.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { c -> LoungeTile(c, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LoungeTile(c: LoungeCard, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = colors.statusDelayed
    Column(
        modifier
            .clip(ContrailTheme.shapes.small)
            .background(Brush.verticalGradient(listOf(gold.copy(alpha = 0.14f), colors.canvasSunken.copy(alpha = 0.3f))))
            .border(ContrailTheme.sizes.hairline, gold.copy(alpha = if (c.favourite) 0.8f else 0.25f), ContrailTheme.shapes.small)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.airport, style = ContrailTheme.type.headline, color = gold, modifier = Modifier.weight(1f))
            if (c.favourite) Icon(ContrailIcons.StarFilled, "Favourite", tint = gold, modifier = Modifier.size(14.dp))
        }
        Text(c.name, style = ContrailTheme.type.label, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Stars(c.bestRating, size = 11.dp)
        Text("${c.visits} visit${if (c.visits == 1) "" else "s"} · ${PassportCalculator.hours(c.minutes)}", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
    }
}
