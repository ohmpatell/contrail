package com.contrail.contrail.feature.passport

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contrail.contrail.core.data.reference.AircraftTypes
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.designsystem.component.AuroraBackground
import com.contrail.contrail.core.designsystem.component.Eyebrow
import com.contrail.contrail.core.designsystem.component.GlassIconButton
import com.contrail.contrail.core.designsystem.icon.ContrailIcons
import com.contrail.contrail.core.designsystem.livery.Liveries
import com.contrail.contrail.core.designsystem.livery.LiveryRender
import com.contrail.contrail.core.designsystem.livery.Profiles
import com.contrail.contrail.core.designsystem.theme.ContrailTheme
import com.contrail.contrail.feature.trips.add.flag
import com.contrail.contrail.navigation.LocalNavigator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun StampBookRoute(viewModel: PassportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StampBookScreen(state, onBack = LocalNavigator.current::back)
}

private const val PER_PAGE = 6

/** Your passport as a book: the cover, an identity page, then every stamp, page by page. */
@Composable
fun StampBookScreen(state: PassportUiState, onBack: () -> Unit, startPage: Int = 0) {
    val s = state.stats
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val pages = remember(s.stamps) { s.stamps.chunked(PER_PAGE) }
    val pager = rememberPagerState(initialPage = startPage) { 2 + pages.size.coerceAtLeast(1) }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(ContrailIcons.Back, "Back", onBack)
                Column(Modifier.weight(1f).padding(start = spacing.md)) {
                    Eyebrow("${s.stamps.count { !it.transit }} entries · ${s.stamps.count { it.transit }} transits", color = colors.passport)
                    Text("Stamp book", style = ContrailTheme.type.displaySmall, color = colors.textPrimary)
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.xxl, vertical = spacing.lg),
                pageSpacing = spacing.lg,
            ) { page ->
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().pageFlip(pager, page), contentAlignment = Alignment.Center) {
                    // As big as fits, in a real passport page's proportions.
                    val w = minOf(maxWidth, maxHeight * 0.68f)
                    val sheet = Modifier.width(w).height(w / 0.68f)
                    when (page) {
                        0 -> Cover(sheet)
                        1 -> Identity(state, sheet)
                        else -> StampPage(pages.getOrNull(page - 2).orEmpty(), page - 1, sheet, animate = pager.currentPage == page)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = spacing.lg), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                repeat(pager.pageCount) { i ->
                    val on = i == pager.currentPage
                    Box(Modifier.size(if (on) 18.dp else 6.dp, 6.dp).clip(ContrailTheme.shapes.chip).background(if (on) colors.passport else colors.textDisabled))
                }
            }
        }
    }
}

/** Pages turn like a book: they swing about their spine and dim as they fold away. */
private fun Modifier.pageFlip(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val o = offset.coerceIn(-1f, 1f)
    rotationY = o * 62f
    transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
    cameraDistance = 18f * density
    alpha = lerp(1f, 0.35f, abs(o))
    val sc = lerp(1f, 0.92f, abs(o))
    scaleX = sc; scaleY = sc
}

/** Navy cover with gold foil, a globe crest and a slow foil glint. */
@Composable
private fun Cover(modifier: Modifier) {
    val motion = ContrailTheme.motion
    val glint by if (motion.ambientEnabled) {
        rememberInfiniteTransition(label = "foil").animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart), label = "glint")
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    val gold = Color(0xFFE9C46A)
    val goldLight = Color(0xFFFFF1C2)
    Box(
        modifier
            .clip(ContrailTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(Color(0xFF14204A), Color(0xFF0B1230), Color(0xFF070B1F))))
            .border(1.dp, gold.copy(alpha = 0.25f), ContrailTheme.shapes.medium),
    ) {
        // Leather grain.
        Canvas(Modifier.matchParentSize()) {
            val r = Random(7)
            repeat(1_400) {
                drawCircle(Color.White.copy(alpha = r.nextFloat() * 0.035f), r.nextFloat() * 2.2f * density, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height))
            }
            // Spine shadow.
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endX = size.width * 0.1f))
        }
        Column(
            Modifier.matchParentSize().padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Foil("CONTRAIL", 30, gold, goldLight, glint, spacingEm = 0.28f)
            Canvas(Modifier.size(150.dp)) { crest(gold, goldLight, glint) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Foil("FLIGHT PASSPORT", 18, gold, goldLight, glint, spacingEm = 0.24f)
                Foil("PASSEPORT DE VOL", 11, gold, goldLight, glint, spacingEm = 0.3f)
                Canvas(Modifier.width(44.dp).height(30.dp)) {
                    // The e-passport chip symbol.
                    val w = size.width
                    val h = size.height
                    drawRoundRect(gold, style = Stroke(1.6f * density), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * density))
                    drawCircle(gold, h * 0.22f, Offset(w / 2, h / 2), style = Stroke(1.6f * density))
                    drawLine(gold, Offset(0f, h / 2), Offset(w / 2 - h * 0.22f, h / 2), 1.6f * density)
                    drawLine(gold, Offset(w / 2 + h * 0.22f, h / 2), Offset(w, h / 2), 1.6f * density)
                }
            }
        }
    }
}

@Composable
private fun Foil(text: String, sizeSp: Int, gold: Color, light: Color, glint: Float, spacingEm: Float) {
    Text(
        text,
        style = ContrailTheme.type.displaySmall.copy(
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(spacingEm, androidx.compose.ui.unit.TextUnitType.Em),
            brush = Brush.linearGradient(
                0f to gold, (glint - 0.12f).coerceIn(0f, 1f) to gold, glint.coerceIn(0f, 1f) to light, (glint + 0.12f).coerceIn(0f, 1f) to gold, 1f to gold,
                start = Offset(0f, 0f), end = Offset(600f, 120f),
            ),
        ),
        textAlign = TextAlign.Center,
    )
}

/** A globe with meridians and a contrail swooshing round it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.crest(gold: Color, light: Color, glint: Float) {
    val c = center
    val r = size.minDimension / 2 * 0.62f
    val brush = Brush.linearGradient(listOf(gold, light, gold), start = Offset(size.width * glint - 100, 0f), end = Offset(size.width * glint + 100, size.height))
    val stroke = Stroke(2f * density)
    drawCircle(brush, r, c, style = stroke)
    for (k in 1..2) {
        val w = r * (1 - k * 0.38f)
        drawOval(brush, Offset(c.x - w, c.y - r), androidx.compose.ui.geometry.Size(w * 2, r * 2), style = stroke)
    }
    drawLine(brush, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f * density)
    for (lat in listOf(-0.5f, 0f, 0.5f)) {
        val y = c.y + r * lat
        val half = r * kotlin.math.sqrt(1 - lat * lat)
        drawLine(brush, Offset(c.x - half, y), Offset(c.x + half, y), 2f * density)
    }
    // The contrail: a swoosh ring with a plane at its tip.
    val swoosh = Path().apply {
        moveTo(c.x - r * 1.45f, c.y + r * 0.55f)
        cubicTo(c.x - r * 0.6f, c.y + r * 1.25f, c.x + r * 1.2f, c.y + r * 0.6f, c.x + r * 1.3f, c.y - r * 0.75f)
    }
    drawPath(swoosh, brush, style = Stroke(3f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    val tip = Offset(c.x + r * 1.3f, c.y - r * 0.75f)
    drawCircle(light, 4f * density, tip)
    // Laurel dots under the globe.
    for (i in 0 until 9) {
        val a = PI * (0.15 + i * 0.0875)
        drawCircle(brush, 2.2f * density, Offset(c.x + (kotlin.math.cos(a) * r * 1.22f).toFloat(), c.y + (sin(a) * r * 1.22f).toFloat()))
    }
}

/** Fine wavy security lines, like the background print of a passport page. */
@Composable
private fun PassportPaper(modifier: Modifier, page: Int, content: @Composable () -> Unit) {
    val colors = ContrailTheme.colors
    Box(
        modifier
            .clip(ContrailTheme.shapes.medium)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2347), Color(0xFF121A38), Color(0xFF0E1530))))
            .border(1.dp, colors.passport.copy(alpha = 0.25f), ContrailTheme.shapes.medium),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val lines = 26
            for (i in 0 until lines) {
                val y0 = size.height * (i + 0.5f) / lines
                val path = Path()
                var x = 0f
                path.moveTo(0f, y0)
                while (x <= size.width) {
                    val y = y0 + sin((x / size.width * 6 * PI) + i * 0.5 + page).toFloat() * 7f * density
                    path.lineTo(x, y)
                    x += 6f
                }
                drawPath(path, colors.passport.copy(alpha = 0.06f), style = Stroke(1f * density))
            }
            // A big faint globe watermark.
            drawCircle(colors.passport.copy(alpha = 0.05f), size.minDimension * 0.36f, center, style = Stroke(1.5f * density))
            drawCircle(Brush.radialGradient(listOf(colors.passport.copy(alpha = 0.06f), Color.Transparent), center, size.minDimension * 0.5f), size.minDimension * 0.5f, center, blendMode = BlendMode.Plus)
        }
        content()
    }
}

/** The data page: your flying identity, with a machine-readable zone at the bottom. */
@Composable
private fun Identity(state: PassportUiState, modifier: Modifier) {
    val s = state.stats
    val colors = ContrailTheme.colors
    val spacing = ContrailTheme.spacing
    val home = s.flown.groupingBy { it.origin }.eachCount().maxByOrNull { it.value }?.key
    val since = s.years.minOrNull()
    val favType = s.topType?.first
    val favAirline = s.insights.topAirlines.firstOrNull()
    PassportPaper(modifier, 1) {
        Column(Modifier.fillMaxSize().padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Passport · Passeport", Modifier.weight(1f), color = colors.passport)
                Text("P", style = ContrailTheme.type.numeric, color = colors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                // The "photo": your favourite aircraft in your favourite airline's colours.
                Box(
                    Modifier.width(110.dp).aspectRatio(0.78f).clip(ContrailTheme.shapes.small)
                        .background(Brush.verticalGradient(listOf(colors.passport.copy(alpha = 0.25f), colors.canvasSunken)))
                        .border(1.dp, colors.passport.copy(alpha = 0.4f), ContrailTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    val airline = Airlines.byIata(s.flown.firstOrNull { it.aircraftType == favType }?.airlineIata)
                    LiveryRender(
                        Profiles.forType(favType ?: "B789"),
                        Liveries.forAirline(airline?.icao, accentArgb = airline?.accentArgb),
                        Modifier.fillMaxWidth().padding(spacing.xs).graphicsLayer { rotationZ = -18f; scaleX = 1.25f; scaleY = 1.25f },
                        floating = false,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    IdField("Home airport", home ?: "—")
                    IdField("Flying since", since?.toString() ?: "—")
                    IdField("Favourite aircraft", AircraftTypes.nameFor(favType) ?: favType ?: "—")
                    IdField("Favourite airline", favAirline?.label ?: "—")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                IdField("Flights", "%,d".format(s.flights), Modifier.weight(1f))
                IdField("Countries", "${s.countries.size}", Modifier.weight(1f))
                IdField("Airports", "${s.visited.size + s.transited.size}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                IdField("Distance", String.format(java.util.Locale.US, "%,d km", s.distanceKm), Modifier.weight(1f))
                IdField("Time aloft", "${s.minutesAloft / 60} h", Modifier.weight(1f))
                IdField("Continents", "${s.insights.continents.size}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                IdField("Longest flight", s.longest?.let { "${it.origin}–${it.destination}" } ?: "—", Modifier.weight(1f))
                IdField("Most visited", s.insights.topAirports.firstOrNull { it.key != home }?.label ?: "—", Modifier.weight(1f))
            }
            Eyebrow("Visas · ${s.countries.size} countries")
            Text(
                s.countries.sorted().joinToString(" ") { flag(it) }.ifEmpty { " " },
                style = ContrailTheme.type.headline,
                maxLines = 3,
            )
            Spacer(Modifier.weight(1f))
            val mrz1 = ("P<CTR" + (home ?: "XXX") + "<<FLIGHTS<" + s.flights + "<KM<" + s.distanceKm).padEnd(40, '<').take(40)
            val mrz2 = ((since ?: 0).toString() + "<" + s.countries.size + "CTRY<" + s.types.size + "TYPE<" + s.airlines.size + "ALN").padEnd(40, '<').take(40)
            Column(Modifier.fillMaxWidth().clip(ContrailTheme.shapes.small).background(Color.Black.copy(alpha = 0.18f)).padding(spacing.sm)) {
                Text(mrz1, style = ContrailTheme.type.numericSmall.copy(fontSize = 10.sp, letterSpacing = 0.05.sp), color = colors.textSecondary, maxLines = 1)
                Text(mrz2, style = ContrailTheme.type.numericSmall.copy(fontSize = 10.sp, letterSpacing = 0.05.sp), color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun IdField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = ContrailTheme.type.eyebrow.copy(fontSize = 9.sp), color = ContrailTheme.colors.textTertiary, maxLines = 1)
        Text(value, style = ContrailTheme.type.title, color = ContrailTheme.colors.textPrimary, maxLines = 1)
    }
}

/** Six stamps scattered like a real page: a loose grid, each a little off. */
@Composable
private fun StampPage(stamps: List<Stamp>, number: Int, modifier: Modifier, animate: Boolean) {
    val colors = ContrailTheme.colors
    PassportPaper(modifier, number) {
        Column(Modifier.fillMaxSize().padding(ContrailTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Entries · Entrées", Modifier.weight(1f), color = colors.passport.copy(alpha = 0.8f))
                Text("$number", style = ContrailTheme.type.numericSmall, color = colors.textTertiary)
            }
            if (stamps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Your stamps land here as you fly.", style = ContrailTheme.type.body, color = colors.textTertiary, textAlign = TextAlign.Center)
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
                    stamps.chunked(2).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            pair.forEachIndexed { col, st ->
                                val jitter = remember(st.airport) { Random(st.airport.hashCode()) }
                                val dx = remember(st.airport) { (jitter.nextFloat() - 0.5f) * 18f }
                                val dy = remember(st.airport) { (jitter.nextFloat() - 0.5f) * 14f }
                                PassportStamp(
                                    st,
                                    Modifier.size(118.dp).offset(dx.dp, dy.dp),
                                    delayMs = (row * 2 + col) * 140L + 150,
                                    play = animate,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
