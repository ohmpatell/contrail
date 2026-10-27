package com.contrail.contrail.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of days on glass. Months slide in from the side you paged toward; the chosen day
 * fills with [accent] and today wears a thin ring. Weeks start on Monday.
 */
@Composable
fun GlassCalendar(
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    accent: Color = ContrailTheme.colors.accent,
    marked: Set<LocalDate> = emptySet(),
) {
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val motion = ContrailTheme.motion
    var month by remember { mutableStateOf(YearMonth.from(selected ?: today)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)),
                style = ContrailTheme.type.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(ContrailIcons.Back, "Previous month", { month = month.minusMonths(1) }, size = 36.dp)
            Box(Modifier.size(spacing.sm))
            GlassIconButton(ContrailIcons.ChevronRight, "Next month", { month = month.plusMonths(1) }, size = 36.dp)
        }
        Row {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.UK),
                    style = ContrailTheme.type.eyebrow,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(motion.gentle()) { it * dir / 3 } + fadeIn(motion.fade(180))) togetherWith
                    (slideOutHorizontally(motion.gentle()) { -it * dir / 3 } + fadeOut(motion.fade(120)))
            },
            label = "month",
        ) { m ->
            val first = m.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val cells = (0 until 42).map { i -> first.plusDays((i - lead).toLong()) }
            val rows = cells.chunked(7).filter { week -> week.any { YearMonth.from(it) == m } }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                rows.forEach { week ->
                    Row {
                        week.forEach { day ->
                            DayCell(day, inMonth = YearMonth.from(day) == m, selected = day == selected, today = day == today, marked = day in marked, accent = accent, onClick = { onSelect(day) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, selected: Boolean, today: Boolean, marked: Boolean, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    val colors = ContrailTheme.colors
    val fill by animateColorAsState(if (selected) accent else Color.Transparent, ContrailTheme.motion.fade(160), label = "dayFill")
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ContrailTheme.shapes.chip)
                .background(fill)
                .then(if (today && !selected) Modifier.border(ContrailTheme.sizes.hairline, accent.copy(alpha = 0.8f), ContrailTheme.shapes.chip) else Modifier)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = ContrailTheme.type.numeric,
                color = when {
                    selected -> colors.onAccent
                    !inMonth -> colors.textDisabled.copy(alpha = 0.5f)
                    else -> colors.textPrimary
                },
                modifier = Modifier.graphicsLayer { alpha = if (inMonth) 1f else 0.6f },
            )
            if (marked && !selected) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(ContrailTheme.shapes.chip).background(accent))
            }
        }
    }
}

/** Small chevron-free date chip row: quick picks above a calendar. */
@Composable
fun DateQuickPicks(today: LocalDate, selected: LocalDate?, onSelect: (LocalDate) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ContrailTheme.spacing.sm)) {
        listOf("Yesterday" to today.minusDays(1), "Today" to today, "Tomorrow" to today.plusDays(1)).forEach { (label, d) ->
            ContrailChip(label, selected == d, onClick = { onSelect(d) }, accent = accent)
        }
    }
}
