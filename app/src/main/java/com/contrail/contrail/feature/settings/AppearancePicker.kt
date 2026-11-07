package com.contrail.contrail.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.component.SplitFlapText
import com.contrail.contrail.core.designsystem.component.pressScale
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.core.designsystem.theme.GoldenHour
import com.contrail.contrail.core.designsystem.theme.ThemeChoice
import com.contrail.contrail.core.designsystem.theme.colorsFor
import com.contrail.contrail.core.designsystem.theme.typeFor
import com.contrail.contrail.feature.radar.components.GlassToggle
import java.util.Locale

/** Four live previews, each drawn in its own theme, plus livery mode. */
@Composable
fun AppearancePicker(
    selected: ThemeChoice,
    sunElevationDeg: Double?,
    livery: Boolean,
    liveryAirline: String?,
    onTheme: (ThemeChoice) -> Unit,
    onLivery: (Boolean) -> Unit,
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    ThemeChoice.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            row.forEach { choice ->
                ThemeCard(choice, sunElevationDeg, choice == selected, Modifier.weight(1f)) { onTheme(choice) }
            }
        }
    }
    val chosen = ThemeChoice.entries.first { it == selected }
    Text(chosen.blurb, style = ContrailTheme.type.bodySmall, color = colors.textSecondary)
    if (selected == ThemeChoice.GoldenHour) {
        val now = GoldenHour.phase(sunElevationDeg) +
            (sunElevationDeg?.let { String.format(Locale.US, " now · sun %+.0f°", it) } ?: "")
        Text(now, style = ContrailTheme.type.label, color = colors.accent)
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onLivery(!livery) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Livery mode", style = ContrailTheme.type.title, color = colors.textPrimary)
            Text(
                liveryAirline?.let { "Tinted for $it right now." }
                    ?: "While a flight is under way, the app takes on your airline's colour.",
                style = ContrailTheme.type.bodySmall,
                color = colors.textTertiary,
            )
        }
        GlassToggle(livery)
    }
}

/** A tiny screen in the theme: its canvas and aurora, a route arc, a flap and the name in its type. */
@Composable
private fun ThemeCard(choice: ThemeChoice, sun: Double?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Without a known sun, Golden Hour previews at golden hour so the card shows what it is.
    val palette = remember(choice, sun) { colorsFor(choice, sun ?: if (choice == ThemeChoice.GoldenHour) 3.0 else null) }
    val outer = ContrailTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) outer.accent else outer.hairline, ContrailTheme.motion.fade(), label = "ring")
    val ringWidth by animateDpAsState(if (selected) 2.dp else 1.dp, ContrailTheme.motion.gentle(), label = "ringWidth")
    val shape = ContrailTheme.shapes.medium
    Box(
        modifier
            .height(132.dp)
            .pressScale(interaction)
            .clip(shape)
            .border(ringWidth, ring, shape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        ContrailTheme(palette = palette, typography = typeFor(choice)) {
            val c = ContrailTheme.colors
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(c.canvasRaised, c.canvas, c.canvasSunken)))
                    .background(Brush.radialGradient(listOf(c.auroraA.copy(alpha = 0.9f), c.auroraA.copy(alpha = 0f)), center = Offset(0f, 0f), radius = 420f))
                    .background(Brush.radialGradient(listOf(c.auroraC.copy(alpha = 0.7f), c.auroraC.copy(alpha = 0f)), center = Offset(900f, 380f), radius = 380f)),
            ) {
                // A route arc in the accent, with the airport dots at each end.
                Canvas(Modifier.fillMaxSize()) {
                    val a = Offset(size.width * 0.14f, size.height * 0.58f)
                    val b = Offset(size.width * 0.86f, size.height * 0.36f)
                    val ctrl = Offset(size.width * 0.5f, -size.height * 0.05f)
                    val path = Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
                    drawPath(path, c.accent.copy(alpha = 0.85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(c.accent, 3.dp.toPx(), a)
                    drawCircle(c.accent, 3.dp.toPx(), b)
                }
                Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    SplitFlapText("YYZ", style = ContrailTheme.type.flap.copy(fontSize = ContrailTheme.type.numericSmall.fontSize), gap = 2.dp)
                    Column {
                        Text(choice.label, style = ContrailTheme.type.title, color = c.textPrimary, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(c.accent, c.accentDeep, c.statusOnTime, c.passport).forEach {
                                Box(Modifier.size(8.dp).clip(ContrailTheme.shapes.chip).background(it))
                            }
                        }
                    }
                }
            }
        }
    }
}
