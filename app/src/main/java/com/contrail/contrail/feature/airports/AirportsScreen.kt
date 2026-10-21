package com.contrail.contrail.feature.airports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.database.AirportWithUser
import com.contrail.contrail.core.designsystem.component.ContrailChip
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.component.RiseIn
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.navigation.LocalNavigator

@Composable
fun AirportsRoute(viewModel: AirportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    AirportsScreen(
        state = state,
        onQuery = viewModel::onQuery,
        onOpen = { nav.openAirport(it.icao) },
        onToggleFavourite = viewModel::toggleFavourite,
        onFilter = viewModel::onFilter,
        onSettings = nav::openSettings,
    )
}

@Composable
fun AirportsScreen(
    state: AirportsUiState,
    onQuery: (String) -> Unit,
    onOpen: (AirportWithUser) -> Unit,
    onToggleFavourite: (AirportWithUser) -> Unit,
    onFilter: (NewsFilter) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = spacing.gutter)
            .padding(top = spacing.lg, bottom = spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LivePulse(color = colors.accent)
                    Eyebrow("${state.favourites.size} favourites · ${state.visited.size} visited", color = colors.accent)
                }
                Text("Airports", style = ContrailTheme.type.displayLarge, color = colors.textPrimary)
            }
            GlassIconButton(ContrailIcons.Settings, "Settings", onSettings)
        }

        SearchField(state.query, onQuery)

        if (state.query.isNotBlank()) {
            if (state.results.isEmpty()) {
                Text("No airports match “${state.query}”", style = ContrailTheme.type.body, color = colors.textTertiary)
            }
            state.results.forEachIndexed { i, a ->
                RiseIn(i.coerceAtMost(6)) { Row(a, onOpen, onToggleFavourite) }
            }
            return@Column
        }

        if (state.favourites.isNotEmpty()) {
            Eyebrow("Favourites")
            state.favourites.forEach { a -> Row(a, onOpen, onToggleFavourite) }
        } else {
            EmptyFavourites()
        }

        if (state.visited.isNotEmpty()) {
            Eyebrow("Visited")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.visited.forEach { a ->
                    ContrailChip(a.iata, selected = false, onClick = { onOpen(a) }, leading = {
                        Icon(ContrailIcons.Check, null, Modifier.size(ContrailTheme.sizes.iconSmall), tint = colors.statusOnTime)
                    })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(ContrailIcons.News, null, tint = colors.accent, modifier = Modifier.size(ContrailTheme.sizes.icon))
            Text("Aviation news", style = ContrailTheme.type.headline, color = colors.textPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            state.filters.forEach { f ->
                ContrailChip(f.label, selected = f.feed.key == state.selectedFilter.feed.key, onClick = { onFilter(f) })
            }
        }
        if (state.news.isEmpty()) {
            Text(
                if (state.newsLoading) "Fetching the latest headlines…" else "No headlines right now. Check back soon.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        state.news.take(30).forEachIndexed { i, article ->
            RiseIn(i.coerceAtMost(6)) { NewsCard(article, nowMs) }
        }
    }
}

@Composable
private fun Row(a: AirportWithUser, onOpen: (AirportWithUser) -> Unit, onToggleFavourite: (AirportWithUser) -> Unit) {
    AirportRow(
        iata = a.iata,
        name = a.name,
        place = listOf(a.city, a.countryName).filter { it.isNotBlank() }.joinToString(", "),
        timezone = a.timezone,
        favourite = a.favourite == true,
        onClick = { onOpen(a) },
        onToggleFavourite = { onToggleFavourite(a) },
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.chip) {
        Row(Modifier.padding(horizontal = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(ContrailIcons.Search, null, tint = colors.textTertiary, modifier = Modifier.size(ContrailTheme.sizes.icon))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = ContrailTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(horizontal = spacing.md, vertical = spacing.lg),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search by code, name or city", style = ContrailTheme.type.body, color = colors.textTertiary)
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    ContrailIcons.Close, "Clear", tint = colors.textSecondary,
                    modifier = Modifier.size(ContrailTheme.sizes.icon).clip(ContrailTheme.shapes.chip).clickable(role = Role.Button) { onQuery("") },
                )
            }
        }
    }
}

@Composable
private fun EmptyFavourites() {
    val colors = ContrailTheme.colors
    GlassSurface(Modifier.fillMaxWidth(), shape = ContrailTheme.shapes.medium, glow = colors.accent) {
        Row(Modifier.padding(ContrailTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.md)) {
            AirportMonogram("YYZ", size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("Star the airports you fly from", style = ContrailTheme.type.title, color = colors.textPrimary)
                Text("They'll appear here with their local time, and as news filters.", style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
