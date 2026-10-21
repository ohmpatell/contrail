package com.contrail.contrail.feature.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.DottedGlobe
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.MapArc
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.Tag
import com.contrail.contrail.feature.trips.TripFormat
import com.contrail.contrail.navigation.LocalNavigator
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun YearInReviewRoute(year: Int) {
    val viewModel = hiltViewModel<YearInReviewViewModel, YearInReviewViewModel.Factory>(key = "year-$year") { it.create(year) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    YearInReviewScreen(state, year, onClose = LocalNavigator.current::back)
}

private enum class Story { Intro, Distance, Time, Places, Longest, Aircraft, Lounges, Outro }

/** A swipeable story of the year's flying. Pages advance on their own every few seconds. */
@Composable
fun YearInReviewScreen(state: PassportUiState, year: Int, onClose: () -> Unit, startPage: Int = 0) {
    val s = state.stats
    val motion = ContrailTheme.motion
    val stories = remember(s) {
        buildList {
            add(Story.Intro)
            if (s.flights > 0) {
                add(Story.Distance); add(Story.Time); add(Story.Places)
                if (s.longest != null) add(Story.Longest)
                if (s.topType != null) add(Story.Aircraft)
            }
            if (s.lounges.visits > 0) add(Story.Lounges)
            add(Story.Outro)
        }
    }
    val pager = rememberPagerState(initialPage = startPage) { stories.size }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pager.currentPage, stories.size) {
        progress.snapTo(0f)
        if (motion.ambientEnabled && pager.currentPage < stories.size - 1) {
            progress.animateTo(1f, tween(5_500, easing = LinearEasing))
            pager.animateScrollToPage(pager.currentPage + 1)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            StoryPage(stories[page], state, year)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = ContrailTheme.spacing.gutter, vertical = ContrailTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.indices.forEach { i ->
                    val fill = when {
                        i < pager.currentPage -> 1f
                        i == pager.currentPage -> if (motion.ambientEnabled) progress.value else 1f
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(ContrailTheme.shapes.chip).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Contrail · $year", Modifier.weight(1f), color = ContrailTheme.colors.textSecondary)
                GlassIconButton(ContrailIcons.Close, "Close", onClose)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryPage(story: Story, state: PassportUiState, year: Int) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val s = state.stats
    val tint = when (story) {
        Story.Intro, Story.Outro -> colors.passport
        Story.Distance, Story.Places -> colors.accent
        Story.Time -> colors.statusAirborne
        Story.Longest, Story.Aircraft -> s.longest?.let { Airlines.byIata(it.airlineIata) }?.let { Color(it.accentArgb) } ?: colors.accent
        Story.Lounges -> colors.statusDelayed
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), Color.Transparent, tint.copy(alpha = 0.18f))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = 96.dp, bottom = spacing.xxxl),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically)) {
            when (story) {
                Story.Intro -> {
                    Eyebrow("Your year in the air", color = tint)
                    SplitFlapText(year.toString(), style = ContrailTheme.type.hero.copy(fontSize = ContrailTheme.type.hero.fontSize * 0.8f), color = colors.textPrimary)
                    Text(
                        if (s.flights == 0) "No flights landed this year yet. Your story starts with the next one." else "${s.flights} flights. Here's how they added up.",
                        style = ContrailTheme.type.displaySmall,
                        color = colors.textSecondary,
                    )
                }
                Story.Distance -> {
                    Eyebrow("Distance", color = tint)
                    Odometer("kilometres flown", s.distanceKm, big = true)
                    YearGlobe(state, Modifier.fillMaxWidth().aspectRatio(1.3f))
                    Text(String.format(Locale.US, "%.1f× around the Earth, on %d flights.", s.laps, s.flights), style = ContrailTheme.type.headline, color = colors.textPrimary)
                }
                Story.Time -> {
                    Eyebrow("Time aloft", color = tint)
                    Odometer("hours in the sky", s.minutesAloft / 60, big = true)
                    val days = s.minutesAloft / 1_440.0
                    Text(
                        if (days >= 1) String.format(Locale.US, "That's %.1f whole days off the ground.", days) else "Every one of them by the window, hopefully.",
                        style = ContrailTheme.type.headline,
                        color = colors.textPrimary,
                    )
                    s.busiestMonth?.let { (m, n) ->
                        Text("Busiest month: ${m.getDisplayName(TextStyle.FULL, Locale.UK)}, with $n flights.", style = ContrailTheme.type.body, color = colors.textSecondary)
                    }
                }
                Story.Places -> {
                    Eyebrow("Places", color = tint)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
                        Odometer("airports visited", s.visited.size.toLong(), big = true)
                        Odometer("countries", s.countries.size.toLong(), big = true)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        s.visited.forEach { Tag(TripFormat.city(it, state.places), tint, filled = true) }
                        s.transited.forEach { Tag("$it · transit", colors.textTertiary) }
                    }
                }
                Story.Longest -> {
                    val l = s.longest!!
                    val airline = Airlines.byIata(l.airlineIata)
                    Eyebrow("Longest flight", color = tint)
                    Text(l.designator, style = ContrailTheme.type.hero, color = colors.textPrimary)
                    Text("${TripFormat.city(l.origin, state.places)} → ${TripFormat.city(l.destination, state.places)}", style = ContrailTheme.type.displaySmall, color = colors.textSecondary)
                    LiveryRender(Profiles.forType(l.aircraftType ?: "B789"), Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb))
                    Text(TripFormat.duration(l.duration) + " in the air", style = ContrailTheme.type.numericLarge, color = tint)
                }
                Story.Aircraft -> {
                    val (type, n) = s.topType!!
                    val first = s.collection.firstOrNull { it.code == type }
                    val airline = Airlines.byIcao(first?.firstAirlineIcao)
                    Eyebrow("Your aircraft", color = tint)
                    LiveryRender(Profiles.forType(type), Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb))
                    Text(AircraftTypes.nameFor(type) ?: type, style = ContrailTheme.type.displayMedium, color = colors.textPrimary)
                    Text("$n flight${if (n == 1) "" else "s"} on it this year, and ${s.types.size} types in all.", style = ContrailTheme.type.headline, color = colors.textSecondary)
                }
                Story.Lounges -> {
                    val l = s.lounges
                    Eyebrow("Lounges", color = tint)
                    Odometer("lounge visits", l.visits.toLong(), big = true)
                    Text("${PassportCalculator.hours(l.minutes)} with free coffee.", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                    l.collection.firstOrNull()?.let { Text("Best: ${it.name}, ${it.airport}", style = ContrailTheme.type.headline, color = tint) }
                }
                Story.Outro -> {
                    Eyebrow("That's a wrap", color = tint)
                    Text("See you up there in ${year + 1}.", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
                    Text("Every route, type and lounge is saved on your phone, in your Passport.", style = ContrailTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Start)
                }
            }
        }
    }
}

@Composable
private fun YearGlobe(state: PassportUiState, modifier: Modifier) {
    val arcs = remember(state.stats.legs) {
        state.stats.legs.mapNotNull { (a, b) ->
            val p = state.places[a] ?: return@mapNotNull null
            val q = state.places[b] ?: return@mapNotNull null
            MapArc(Geo.greatCircle(LatLon(p.lat, p.lon), LatLon(q.lat, q.lon), 48), emphasis = true)
        }
    }
    val home = state.stats.visited.firstOrNull()?.let { state.places[it] }
    DottedGlobe(arcs, centerLon = (home?.lon?.toFloat() ?: 0f) + 60f, centerLat = 25f, modifier = modifier)
}
