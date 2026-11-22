package com.contrail.contrail.feature.radar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.hangar.HangarPlane
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A pinned plane that's off screen: where on the screen's edge to point from, and how far it is. */
data class EdgeMarker(val plane: HangarPlane, val x: Float, val y: Float, val screenBearingDeg: Float, val km: Double)

object HangarEdges {
    /** Pinned planes seen in the last day that are off screen, placed where a ray from the middle leaves the clear area. */
    fun place(
        planes: List<HangarPlane>,
        center: LatLon,
        mapBearingDeg: Double,
        clear: IntArray,
        marginPx: Float,
        now: Long,
        inView: (LatLon) -> Boolean,
    ): List<EdgeMarker> {
        val cx = (clear[0] + clear[2]) / 2f
        val cy = (clear[1] + clear[3]) / 2f
        val halfW = (clear[2] - clear[0]) / 2f - marginPx
        val halfH = (clear[3] - clear[1]) / 2f - marginPx
        if (halfW <= 0 || halfH <= 0) return emptyList()
        return planes.mapNotNull { p ->
            val lat = p.lastLat ?: return@mapNotNull null
            val lon = p.lastLon ?: return@mapNotNull null
            if (now - (p.lastSeenMs ?: 0L) > RECENT_MS) return@mapNotNull null
            val at = LatLon(lat, lon)
            if (inView(at)) return@mapNotNull null
            val bearing = Geo.bearingDeg(center, at) - mapBearingDeg
            val rad = Geo.toRad(bearing)
            val dx = sin(rad).toFloat()
            val dy = -cos(rad).toFloat()
            val t = min(halfW / abs(dx).coerceAtLeast(1e-4f), halfH / abs(dy).coerceAtLeast(1e-4f))
            EdgeMarker(p, cx + dx * t, cy + dy * t, bearing.toFloat(), Geo.distanceM(center, at) / 1000)
        }
    }

    private const val RECENT_MS = 24 * 3_600_000L
}

/** Gold pills on the screen's edge, each pointing towards a pinned plane. */
@Composable
fun HangarEdgeLayer(markers: List<EdgeMarker>, onTap: (HangarPlane) -> Unit, modifier: Modifier = Modifier) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val gold = RarityPalette.Legendary.first()
    Box(modifier.fillMaxSize()) {
        markers.forEach { m ->
            key(m.plane.registration) {
                Row(
                    Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val pad = 10.dp.roundToPx()
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                // Centred on its spot, but never past the screen's edge.
                                val x = (m.x - placeable.width / 2f).roundToInt().coerceIn(pad, (constraints.maxWidth - placeable.width - pad).coerceAtLeast(pad))
                                val y = (m.y - placeable.height / 2f).roundToInt().coerceIn(pad, (constraints.maxHeight - placeable.height - pad).coerceAtLeast(pad))
                                placeable.place(x, y)
                            }
                        }
                        .clip(ContrailTheme.shapes.chip)
                        .background(colors.canvasRaised.copy(alpha = 0.92f))
                        .border(ContrailTheme.sizes.hairline, gold.copy(alpha = 0.8f), ContrailTheme.shapes.chip)
                        .clickable(role = Role.Button, onClickLabel = "Fly to ${m.plane.registration}") { onTap(m.plane) }
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    // ArrowRight points east (90°); turn it to face the plane.
                    Icon(ContrailIcons.ArrowRight, null, tint = gold, modifier = Modifier.size(14.dp).rotate(m.screenBearingDeg - 90f))
                    Column {
                        Text(m.plane.registration, style = ContrailTheme.type.numericSmall, color = colors.textPrimary)
                        Text(
                            if (m.km >= 10) String.format(Locale.US, "%,.0f km", m.km) else String.format(Locale.US, "%.1f km", m.km),
                            style = ContrailTheme.type.eyebrow,
                            color = gold,
                        )
                    }
                }
            }
        }
    }
}
