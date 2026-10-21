package com.contrail.contrail.feature.radar.map

import androidx.compose.ui.graphics.toArgb
import com.contrail.contrail.core.designsystem.theme.AltitudeRamp
import com.contrail.contrail.core.designsystem.theme.NightCockpitColors
import com.contrail.contrail.core.designsystem.theme.RarityPalette
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/** Ids and builders for the radar's runtime MapLibre layers. Colours come from the design tokens. */
internal object RadarLayers {
    const val SRC_AIRCRAFT = "contrail-aircraft"
    const val SRC_TRAILS = "contrail-trails"
    const val SRC_NIGHT = "contrail-night"
    const val SRC_ME = "contrail-me"
    const val SRC_WEATHER = "contrail-weather"

    const val L_WEATHER = "contrail-weather"
    const val L_NIGHT = "contrail-night"
    const val L_TRAILS = "contrail-trails"
    const val L_TRAIL_HEADS = "contrail-trail-heads"
    const val L_RARE_GLOW = "contrail-rare-glow"
    const val L_RARE_RING = "contrail-rare-ring"
    const val L_SELECTED = "contrail-selected"
    const val L_EMERGENCY = "contrail-emergency"
    const val L_AIRCRAFT = "contrail-aircraft"
    const val L_LABELS = "contrail-labels"
    const val L_ME = "contrail-me"
    const val SRC_ROUTE = "contrail-route"
    const val L_WATCH = "contrail-watch"
    const val L_ROUTE_GLOW = "contrail-route-glow"
    const val L_ROUTE_FLOWN = "contrail-route-flown"
    const val L_ROUTE_REMAINING = "contrail-route-remaining"
    const val L_ROUTE_ENDS = "contrail-route-ends"
    const val L_ROUTE_LABELS = "contrail-route-labels"
    const val P_PART = "part"

    /** Base-style layers toggled by "airport labels". */
    val AIRPORT_LAYERS = listOf("airport-label", "airport-dot")

    /** Runtime layers are inserted below this base-style layer so place names stay on top of weather. */
    const val BELOW_LABELS = "water-name"

    const val P_HEX = "hex"
    const val P_ICON = "icon"
    const val P_HEADING = "hdg"
    const val P_ALT = "alt"
    const val P_GROUND = "gnd"
    const val P_FRESH = "fresh"
    const val P_SELECTED = "sel"
    const val P_EMERGENCY = "emg"
    const val P_RARITY = "rare"
    const val P_LABEL = "label"
    const val P_LABEL2 = "label2"
    const val P_AGE = "age"
    const val P_SORT = "sort"
    const val P_SCALE = "scale"
    const val P_WATCHED = "watch"

    private val colors = NightCockpitColors
    private val legendary = RarityPalette.Legendary.first().toArgb()
    private val rare = RarityPalette.Rare.first().toArgb()

    private val isPoint: Expression = Expression.eq(Expression.geometryType(), literal("Point"))
    private val notSelectedRare: Expression
        get() = Expression.all(
            isPoint,
            Expression.gte(Expression.toNumber(get(P_RARITY)), literal(1)),
            Expression.not(Expression.toBool(get(P_SELECTED))),
        )

    private val isLine: Expression = Expression.eq(Expression.geometryType(), literal("LineString"))

    /** Altitude colour ramp as a MapLibre expression, with ground traffic in grey. */
    val altitudeColor: Expression = Expression.switchCase(
        Expression.toBool(get(P_GROUND)),
        Expression.color(AltitudeRamp.Ground.toArgb()),
        Expression.interpolate(
            Expression.linear(),
            Expression.toNumber(get(P_ALT)),
            *AltitudeRamp.stops.map { (alt, c) -> stop(alt, Expression.color(c.toArgb())) }.toTypedArray(),
        ),
    )

    private fun byZoom(vararg stops: Pair<Number, Any>): Expression = Expression.interpolate(
        Expression.exponential(1.4f),
        Expression.zoom(),
        *stops.map { (z, v) -> stop(z, if (v is Expression) v else literal(v as Number)) }.toTypedArray(),
    )

    /** Zoom below which ground traffic is hidden: at regional zoom it piles up into a blob at airports. */
    const val GROUND_HIDDEN_BELOW_ZOOM = 8.5f
    private const val GROUND_VISIBLE_FROM_ZOOM = 10f

    private val fresh: Expression get() = Expression.toNumber(get(P_FRESH))

    private val airborneOnly: Expression
        get() = Expression.switchCase(Expression.toBool(get(P_GROUND)), literal(0f), literal(1f))

    /**
     * Wraps an opacity so ground traffic fades in only once zoomed into an airport, or never
     * when "Show ground traffic" is off.
     */
    private fun groundFaded(inner: Expression, showGround: Boolean = true): Expression =
        if (!showGround) {
            Expression.product(inner, airborneOnly)
        } else {
            Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                stop(GROUND_HIDDEN_BELOW_ZOOM, Expression.product(inner, airborneOnly)),
                stop(GROUND_VISIBLE_FROM_ZOOM, inner),
            )
        }

    private fun trailOpacity(showGround: Boolean) = groundFaded(
        Expression.product(
            Expression.interpolate(Expression.linear(), Expression.toNumber(get(P_AGE)), stop(0, 0.9f), stop(0.5, 0.4f), stop(1, 0f)),
            Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(1f), literal(0.7f)),
        ),
        showGround,
    )

    private fun selectedScale(base: Float): Expression =
        Expression.product(
            literal(base),
            Expression.toNumber(Expression.coalesce(get(P_SCALE), literal(1f))),
            Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(1.2f), literal(1f)),
        )

    fun night(): FillLayer = FillLayer(L_NIGHT, SRC_NIGHT).withProperties(
        PropertyFactory.fillColor(Expression.color(colors.canvasSunken.toArgb())),
        PropertyFactory.fillOpacity(0.16f),
        PropertyFactory.fillAntialias(false),
    )

    fun trails(): LineLayer = LineLayer(L_TRAILS, SRC_TRAILS).withProperties(
        PropertyFactory.lineColor(altitudeColor),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineWidth(
            byZoom(
                3 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(2.2f), literal(0.8f)),
                8 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(3.2f), literal(1.6f)),
                13 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(4.5f), literal(2.6f)),
            ),
        ),
        // Trails fade from the altitude colour to transparent as they age.
        PropertyFactory.lineOpacity(trailOpacity(showGround = true)),
    )

    fun trailHeads(): LineLayer = LineLayer(L_TRAIL_HEADS, SRC_AIRCRAFT).withProperties(
        PropertyFactory.lineColor(altitudeColor),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineWidth(
            byZoom(
                3 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(2.2f), literal(0.8f)),
                8 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(3.2f), literal(1.6f)),
                13 to Expression.switchCase(Expression.toBool(get(P_SELECTED)), literal(4.5f), literal(2.6f)),
            ),
        ),
        PropertyFactory.lineOpacity(groundFaded(Expression.product(fresh, literal(0.85f)))),
    ).withFilter(isLine)

    fun rareGlow(): CircleLayer = CircleLayer(L_RARE_GLOW, SRC_AIRCRAFT).withProperties(
        PropertyFactory.circleRadius(byZoom(3 to 10f, 8 to 18f, 13 to 28f)),
        PropertyFactory.circleColor(rarityColor()),
        PropertyFactory.circleOpacity(groundFaded(Expression.product(literal(0.28f), fresh))),
        PropertyFactory.circleBlur(0.9f),
        PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
    ).withFilter(notSelectedRare)

    fun rareRing(): CircleLayer = CircleLayer(L_RARE_RING, SRC_AIRCRAFT).withProperties(
        PropertyFactory.circleRadius(byZoom(3 to 7f, 8 to 13f, 13 to 20f)),
        PropertyFactory.circleOpacity(0f),
        PropertyFactory.circleStrokeColor(rarityColor()),
        PropertyFactory.circleStrokeWidth(1.4f),
        PropertyFactory.circleStrokeOpacity(groundFaded(Expression.product(literal(0.85f), fresh))),
    ).withFilter(notSelectedRare)

    fun selected(): CircleLayer = CircleLayer(L_SELECTED, SRC_AIRCRAFT).withProperties(
        PropertyFactory.circleRadius(byZoom(3 to 12f, 8 to 20f, 13 to 30f)),
        PropertyFactory.circleColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleOpacity(0.14f),
        PropertyFactory.circleStrokeColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleStrokeWidth(1.6f),
        PropertyFactory.circleStrokeOpacity(0.9f),
    ).withFilter(Expression.all(isPoint, Expression.toBool(get(P_SELECTED))))

    fun emergency(): CircleLayer = CircleLayer(L_EMERGENCY, SRC_AIRCRAFT).withProperties(
        PropertyFactory.circleRadius(18f),
        PropertyFactory.circleColor(Expression.color(colors.emergency.toArgb())),
        PropertyFactory.circleOpacity(0.25f),
        PropertyFactory.circleStrokeColor(Expression.color(colors.emergency.toArgb())),
        PropertyFactory.circleStrokeWidth(2f),
        PropertyFactory.circleStrokeOpacity(0.9f),
    ).withFilter(Expression.all(isPoint, Expression.toBool(get(P_EMERGENCY))))

    fun aircraft(): SymbolLayer = SymbolLayer(L_AIRCRAFT, SRC_AIRCRAFT).withProperties(
        PropertyFactory.iconImage(get(P_ICON)),
        PropertyFactory.iconRotate(Expression.toNumber(get(P_HEADING))),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconSize(byZoom(2 to selectedScale(0.36f), 6 to selectedScale(0.52f), 9 to selectedScale(0.72f), 12 to selectedScale(0.95f), 15 to selectedScale(1.2f))),
        PropertyFactory.iconColor(altitudeColor),
        PropertyFactory.iconHaloColor(Expression.color(colors.canvasSunken.toArgb())),
        PropertyFactory.iconHaloWidth(1.3f),
        PropertyFactory.iconOpacity(groundFaded(fresh)),
        PropertyFactory.symbolSortKey(Expression.toNumber(get(P_SORT))),
        PropertyFactory.symbolZOrder(Property.SYMBOL_Z_ORDER_SOURCE),
    ).withFilter(isPoint)

    fun labels(): SymbolLayer = SymbolLayer(L_LABELS, SRC_AIRCRAFT).withProperties(
        PropertyFactory.textField(
            Expression.step(
                Expression.zoom(), get(P_LABEL),
                stop(
                    10,
                    Expression.format(
                        Expression.formatEntry(get(P_LABEL)),
                        Expression.formatEntry("\n"),
                        Expression.formatEntry(get(P_LABEL2), Expression.FormatOption.formatFontScale(0.82)),
                    ),
                ),
            ),
        ),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(byZoom(8 to 10f, 12 to 12f)),
        PropertyFactory.textLetterSpacing(0.04f),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
        PropertyFactory.textColor(Expression.color(colors.textSecondary.toArgb())),
        PropertyFactory.textHaloColor(Expression.color(colors.canvas.toArgb())),
        PropertyFactory.textHaloWidth(1.4f),
        PropertyFactory.textOpacity(groundFaded(fresh)),
        PropertyFactory.textOptional(true),
        PropertyFactory.textPadding(4f),
        PropertyFactory.symbolSortKey(Expression.toNumber(get(P_SORT))),
    ).withFilter(Expression.all(isPoint, Expression.has(P_LABEL))).also { it.minZoom = 7.5f }

    fun me(): CircleLayer = CircleLayer(L_ME, SRC_ME).withProperties(
        PropertyFactory.circleRadius(6f),
        PropertyFactory.circleColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleStrokeColor(Expression.color(colors.textPrimary.toArgb())),
        PropertyFactory.circleStrokeWidth(2.5f),
    )

    /** Watched tails, types and airlines get an accent ring that glows. */
    fun watchRing(): CircleLayer = CircleLayer(L_WATCH, SRC_AIRCRAFT).withProperties(
        PropertyFactory.circleRadius(byZoom(3 to 9f, 8 to 15f, 13 to 23f)),
        PropertyFactory.circleColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleOpacity(Expression.product(literal(0.18f), fresh)),
        PropertyFactory.circleBlur(0.6f),
        PropertyFactory.circleStrokeColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleStrokeWidth(1.6f),
        PropertyFactory.circleStrokeOpacity(Expression.product(literal(0.9f), fresh)),
    ).withFilter(
        Expression.all(isPoint, Expression.toBool(get(P_WATCHED)), Expression.not(Expression.toBool(get(P_SELECTED)))),
    )

    private val isRoutePart = { part: String -> Expression.eq(get(P_PART), literal(part)) }

    /** Soft glow under the flown part of the selected aircraft's route. */
    fun routeGlow(): LineLayer = LineLayer(L_ROUTE_GLOW, SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.lineWidth(byZoom(3 to 5f, 10 to 9f)),
        PropertyFactory.lineBlur(4f),
        PropertyFactory.lineOpacity(0.28f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ).withFilter(isRoutePart("flown"))

    fun routeFlown(): LineLayer = LineLayer(L_ROUTE_FLOWN, SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.lineWidth(byZoom(3 to 1.8f, 10 to 3f)),
        PropertyFactory.lineOpacity(0.95f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ).withFilter(isRoutePart("flown"))

    /** The rest of the trip: a dashed great circle to the destination. */
    fun routeRemaining(): LineLayer = LineLayer(L_ROUTE_REMAINING, SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(Expression.color(colors.textPrimary.toArgb())),
        PropertyFactory.lineWidth(byZoom(3 to 1.4f, 10 to 2.2f)),
        PropertyFactory.lineOpacity(0.7f),
        PropertyFactory.lineDasharray(arrayOf(1.2f, 2.4f)),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    ).withFilter(isRoutePart("remaining"))

    fun routeEnds(): CircleLayer = CircleLayer(L_ROUTE_ENDS, SRC_ROUTE).withProperties(
        PropertyFactory.circleRadius(4.5f),
        PropertyFactory.circleColor(Expression.color(colors.canvas.toArgb())),
        PropertyFactory.circleStrokeColor(Expression.color(colors.accent.toArgb())),
        PropertyFactory.circleStrokeWidth(2.2f),
    ).withFilter(isRoutePart("end"))

    fun routeLabels(): SymbolLayer = SymbolLayer(L_ROUTE_LABELS, SRC_ROUTE).withProperties(
        PropertyFactory.textField(get(P_LABEL)),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(13f),
        PropertyFactory.textLetterSpacing(0.08f),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, 0.8f)),
        PropertyFactory.textColor(Expression.color(colors.textPrimary.toArgb())),
        PropertyFactory.textHaloColor(Expression.color(colors.canvas.toArgb())),
        PropertyFactory.textHaloWidth(1.6f),
        PropertyFactory.textAllowOverlap(true),
    ).withFilter(isRoutePart("end"))

    private fun rarityColor(): Expression = Expression.switchCase(
        Expression.gte(Expression.toNumber(get(P_RARITY)), literal(2)),
        Expression.color(legendary),
        Expression.color(rare),
    )
}
