package com.contrail.contrail.feature.radar.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.model.LiveAircraft
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.component.GlassLevel
import com.contrail.contrail.core.designsystem.component.GlassSurface
import com.contrail.contrail.core.designsystem.component.LivePulse
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.radar.RadarFormat

/** Cockpit-style readout shown while the camera is locked onto one aircraft. */
@Composable
fun FollowHud(aircraft: LiveAircraft, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    GlassSurface(modifier.fillMaxWidth(), level = GlassLevel.Strong, glow = colors.accent) {
        Column(Modifier.padding(vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(Modifier.padding(horizontal = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                LivePulse(color = colors.accent)
                Column(Modifier.weight(1f).padding(start = spacing.xs)) {
                    Eyebrow("Following", color = colors.accent)
                    Text(aircraft.displayCallsign, style = ContrailTheme.type.headline, color = colors.textPrimary)
                }
                GlassIconButton(ContrailIcons.Close, "Stop following", onStop, size = ContrailTheme.sizes.touchTarget - spacing.sm)
            }
            HeadingTape(aircraft.trackDeg?.toDouble() ?: 0.0, Modifier.fillMaxWidth().height(40.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                val alt = RadarFormat.altitude(aircraft)
                Readout("ALT", alt.value, alt.unit, AltitudeRamp.colorFor(if (aircraft.onGround) null else aircraft.altitudeFt))
                val gs = RadarFormat.speed(aircraft)
                Readout("GS", gs.value, gs.unit, colors.textPrimary)
                val vs = RadarFormat.verticalRate(aircraft.verticalRateFpm)
                Readout("V/S", vs.value, vs.unit, colors.textPrimary)
                val hdg = RadarFormat.heading(aircraft)
                Readout("TRK", hdg.value, hdg.unit, colors.textPrimary)
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, unit: String?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ContrailTheme.type.numeric, color = color)
            if (unit != null) Text(unit, style = ContrailTheme.type.numericSmall, color = ContrailTheme.colors.textTertiary)
        }
    }
}

/** A scrolling compass tape that glides to each new heading the short way round. */
@Composable
private fun HeadingTape(headingDeg: Double, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val motion = ContrailTheme.motion
    val type = ContrailTheme.type
    val measurer = rememberTextMeasurer()
    val anim = remember { Animatable(headingDeg.toFloat()) }
    LaunchedEffect(headingDeg) {
        val target = anim.value + Geo.headingDelta(anim.value.toDouble(), headingDeg).toFloat()
        anim.animateTo(target, motion.gentle())
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val pxPerDeg = 4.dp.toPx()
            val center = size.width / 2f
            val h = anim.value
            val first = ((h - center / pxPerDeg) / 5f).toInt() * 5 - 5
            val last = (h + center / pxPerDeg).toInt() + 5
            for (deg in first..last step 5) {
                val x = center + (deg - h) * pxPerDeg
                val major = deg % 30 == 0
                val tick = if (major) 12.dp.toPx() else if (deg % 10 == 0) 8.dp.toPx() else 4.dp.toPx()
                drawLine(colors.textTertiary, Offset(x, size.height - tick), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                if (major) {
                    val norm = ((deg % 360) + 360) % 360
                    val label = when (norm) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> (norm / 10).toString().padStart(2, '0') }
                    val layout = measurer.measure(label, type.numericSmall.copy(color = colors.textSecondary))
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 0f))
                }
            }
            // Edge fades and the lubber line.
            drawRect(Brush.horizontalGradient(0f to colors.canvasRaised, 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to colors.canvasRaised))
            val pointer = Path().apply {
                moveTo(center - 6.dp.toPx(), size.height * 0.48f)
                lineTo(center + 6.dp.toPx(), size.height * 0.48f)
                lineTo(center, size.height * 0.48f + 8.dp.toPx())
                close()
            }
            drawPath(pointer, colors.accent)
            drawLine(colors.accent, Offset(center, size.height * 0.48f), Offset(center, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}
