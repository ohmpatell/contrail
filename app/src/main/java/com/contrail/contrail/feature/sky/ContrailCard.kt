package com.contrail.contrail.feature.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.data.sky.ContrailForecast
import com.contrail.contrail.core.data.sky.ContrailOutlook
import com.contrail.contrail.core.data.sky.ContrailPhysics
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun outlookColor(o: ContrailOutlook): Color {
    val colors = ContrailTheme.colors
    return when (o) {
        ContrailOutlook.None -> colors.textTertiary
        ContrailOutlook.ShortLived -> colors.statusAirborne
        ContrailOutlook.Possible -> colors.accent
        ContrailOutlook.LongLasting -> Color.White
    }
}

/** "Contrails today": now at each cruise height, and the next twelve hours. */
@Composable
fun ContrailCard(forecast: ContrailForecast, nowMs: Long) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val hour = forecast.at(nowMs) ?: return
    val best = hour.best
    val highest = hour.levels.filter { it.outlook == best }.minByOrNull { it.flightLevel }
    GlassSurface(Modifier.fillMaxWidth(), glow = outlookColor(best).copy(alpha = 0.35f)) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Contrails today", Modifier.weight(1f))
                Eyebrow("forecast")
            }
            Text(
                when (best) {
                    ContrailOutlook.None -> "Too warm up there: planes won't leave contrails"
                    ContrailOutlook.ShortLived -> "Short contrails that fade in seconds"
                    ContrailOutlook.Possible -> "Contrails may linger above FL${highest?.flightLevel ?: 340}"
                    ContrailOutlook.LongLasting -> "Long-lasting contrails above FL${highest?.flightLevel ?: 340}: look up"
                },
                style = ContrailTheme.type.headline,
                color = colors.textPrimary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                hour.levels.sortedByDescending { it.flightLevel }.forEach { l ->
                    val tint = outlookColor(l.outlook)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(ContrailTheme.shapes.small)
                            .background(tint.copy(alpha = 0.1f))
                            .border(ContrailTheme.sizes.hairline, tint.copy(alpha = 0.5f), ContrailTheme.shapes.small)
                            .padding(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text("FL${l.flightLevel}", style = ContrailTheme.type.numeric, color = colors.textPrimary)
                        Text(
                            when (l.outlook) {
                                ContrailOutlook.None -> "none"
                                ContrailOutlook.ShortLived -> "short"
                                ContrailOutlook.Possible -> "may linger"
                                ContrailOutlook.LongLasting -> "long-lasting"
                            },
                            style = ContrailTheme.type.label, color = tint,
                        )
                        Text(String.format(Locale.US, "%.0f°C · %.0f%% ice", l.tempC, l.rhIce), style = ContrailTheme.type.eyebrow, color = colors.textTertiary)
                    }
                }
            }
            // The next twelve hours, one bar per hour, coloured by the best outlook.
            val next = forecast.hours.filter { it.timeMs >= nowMs - 3_600_000 }.take(12)
            if (next.size > 1) {
                Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    next.forEach { h ->
                        val o = h.best
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.width(6.dp).height((6 + o.ordinal * 7).dp).clip(ContrailTheme.shapes.chip)
                                    .background(outlookColor(o).copy(alpha = if (o == ContrailOutlook.None) 0.3f else 0.9f)),
                            )
                        }
                    }
                }
                val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(ZoneId.systemDefault())
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt.format(Instant.ofEpochMilli(next.first().timeMs)), style = ContrailTheme.type.eyebrow, color = colors.textTertiary, modifier = Modifier.weight(1f))
                    Text(fmt.format(Instant.ofEpochMilli(next.last().timeMs)), style = ContrailTheme.type.eyebrow, color = colors.textTertiary)
                }
            }
            Text(
                "From the temperature and humidity at cruise heights (the Schmidt-Appleman criterion). Humidity this high is hard to forecast, so it's a guide. Weather data by Open-Meteo.com.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

/** A line for one plane overhead: is it likely leaving a trail? Null below cruise heights. */
fun trailLine(forecast: ContrailForecast?, a: LiveAircraft, nowMs: Long): String? {
    val level = forecast?.at(nowMs)?.let { ContrailPhysics.nearest(it.levels, a.altitudeFt) } ?: return null
    return when (level.outlook) {
        ContrailOutlook.None -> "Probably no contrail at this height"
        ContrailOutlook.ShortLived -> "Should be leaving a short contrail"
        ContrailOutlook.Possible -> "Its contrail may linger"
        ContrailOutlook.LongLasting -> "Should be leaving a long contrail: look up"
    }
}
