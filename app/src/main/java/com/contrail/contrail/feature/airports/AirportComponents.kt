package com.contrail.contrail.feature.airports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.network.news.NewsArticle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The airport's mark. Airport logos need a paid logostream plan, so every airport gets a
 * consistent monogram: its IATA code on a glowing glass tile.
 */
@Composable
fun AirportMonogram(iata: String, modifier: Modifier = Modifier, size: Dp = 56.dp, accent: Color = ContrailTheme.colors.accent) {
    Box(
        modifier
            .size(size)
            .clip(ContrailTheme.shapes.small)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), ContrailTheme.colors.flapTile)))
            .border(ContrailTheme.sizes.hairline, Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent)), ContrailTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iata,
            style = if (size >= 56.dp) ContrailTheme.type.headline else ContrailTheme.type.title,
            color = ContrailTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** A clock for another timezone that ticks every second (minute when motion is reduced). */
@Composable
fun rememberLocalTime(timezone: String, pattern: String = "HH:mm"): String {
    val zone = remember(timezone) { runCatching { ZoneId.of(timezone) }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(zone) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (zone == null) return "--:--"
    return remember(now / 1_000, zone, pattern) {
        DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(now).atZone(zone))
    }
}

/** UTC offset label like "UTC−4". */
fun utcOffset(timezone: String): String = runCatching {
    val secs = ZoneId.of(timezone).rules.getOffset(Instant.now()).totalSeconds
    val h = secs / 3600
    val m = kotlin.math.abs(secs % 3600) / 60
    "UTC" + (if (secs >= 0) "+" else "−") + kotlin.math.abs(h) + if (m > 0) ":%02d".format(m) else ""
}.getOrDefault("")

@Composable
fun AirportRow(
    iata: String,
    name: String,
    place: String,
    timezone: String,
    favourite: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    GlassSurface(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), shape = ContrailTheme.shapes.medium) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            AirportMonogram(iata, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rememberLocalTime(timezone), style = ContrailTheme.type.numeric, color = colors.textSecondary)
                Eyebrow(utcOffset(timezone))
            }
            Icon(
                if (favourite) ContrailIcons.StarFilled else ContrailIcons.Star,
                contentDescription = if (favourite) "Remove favourite" else "Add favourite",
                tint = if (favourite) colors.statusDelayed else colors.textTertiary,
                modifier = Modifier
                    .size(ContrailTheme.sizes.touchTarget - spacing.md)
                    .clip(ContrailTheme.shapes.chip)
                    .clickable(role = Role.Button, onClick = onToggleFavourite)
                    .padding(spacing.sm),
            )
        }
    }
}

/** One headline: image, title, source and age. Tapping opens the original article. */
@Composable
fun NewsCard(article: NewsArticle, nowMs: Long, modifier: Modifier = Modifier) {
    val spacing = ContrailTheme.spacing
    val colors = ContrailTheme.colors
    val uri = LocalUriHandler.current
    GlassSurface(
        modifier.fillMaxWidth().clickable(role = Role.Button) { uri.openUri(article.link) },
        shape = ContrailTheme.shapes.medium,
    ) {
        Row(Modifier.padding(spacing.md), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Eyebrow("${article.source} · ${age(nowMs, article.publishedAtMs)}", color = colors.accent)
                Text(article.title, style = ContrailTheme.type.title, color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (article.summary.isNotBlank()) {
                    Text(article.summary, style = ContrailTheme.type.bodySmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp).aspectRatio(1f).clip(ContrailTheme.shapes.small).background(colors.canvasRaised),
                )
            }
        }
    }
}

fun age(nowMs: Long, thenMs: Long): String {
    val m = ((nowMs - thenMs) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 1_440 -> "${m / 60}h"
        else -> "${m / 1_440}d"
    }
}
