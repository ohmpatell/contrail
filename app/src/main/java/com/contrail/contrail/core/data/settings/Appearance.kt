package com.contrail.contrail.core.data.settings

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.common.Sun
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.sky.SkySettingsRepository
import com.contrail.contrail.core.data.trip.FlightProgressCalc
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.core.designsystem.theme.ContrailColors
import com.contrail.contrail.core.designsystem.theme.ContrailTypography
import com.contrail.contrail.core.designsystem.theme.ThemeChoice
import com.contrail.contrail.core.designsystem.theme.colorsFor
import com.contrail.contrail.core.designsystem.theme.typeFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** What you picked in Settings → Appearance. */
data class Appearance(val choice: ThemeChoice = ThemeChoice.NightCockpit, val livery: Boolean = true)

@Singleton
class AppearanceRepository @Inject constructor(@Named("appearance") private val store: DataStore<Preferences>) {
    private val theme = stringPreferencesKey("theme")
    private val livery = booleanPreferencesKey("livery")

    val appearance: Flow<Appearance> = store.data.map { p ->
        Appearance(
            choice = ThemeChoice.entries.firstOrNull { it.name == p[theme] } ?: ThemeChoice.NightCockpit,
            livery = p[livery] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setTheme(choice: ThemeChoice) = store.edit { it[theme] = choice.name }

    suspend fun setLivery(on: Boolean) = store.edit { it[livery] = on }
}

/** The look the app should have right now. */
data class ResolvedTheme(
    val choice: ThemeChoice,
    val colors: ContrailColors,
    val type: ContrailTypography,
    val sunElevationDeg: Double?,
    /** The airline tinting the app while one of your flights is under way, if livery mode is on. */
    val liveryAirline: String?,
)

/**
 * Turns your choice into colours. Golden Hour follows the sun at the place you last opened Sky,
 * and livery mode takes the airline's colour while a flight is in its Live Update window.
 * Re-evaluated every few minutes, so dusk arrives on its own.
 */
@Singleton
class ThemeResolver @Inject constructor(
    appearance: AppearanceRepository,
    sky: SkySettingsRepository,
    trips: TripRepository,
    private val clock: Clock,
) {
    private val ticks: Flow<Long> = flow {
        while (true) {
            emit(clock.nowMs())
            delay(TICK_MS)
        }
    }

    val theme: Flow<ResolvedTheme> = combine(
        appearance.appearance,
        sky.settings.map { it.spot }.distinctUntilChanged(),
        trips.trips.map { list -> list.flatMap { it.segments } },
        ticks,
    ) { a, spot, segments, now -> resolve(a, spot, segments, now) }.distinctUntilChanged()

    companion object {
        private const val TICK_MS = 5 * 60_000L

        fun resolve(a: Appearance, spot: LatLon?, segments: List<FlightSegment>, nowMs: Long): ResolvedTheme {
            val sun = spot?.let { Sun.elevationDeg(nowMs, it.lat, it.lon) }
            // Rounded so the palette only changes when the sun has visibly moved.
            val rounded = sun?.let { Math.round(it * 2) / 2.0 }
            var colors = colorsFor(a.choice, rounded)
            val flight = if (a.livery) FlightProgressCalc.current(segments, Instant.ofEpochMilli(nowMs)) else null
            val airline = flight?.let { Airlines.byIata(it.airlineIata) }
            if (airline != null) colors = colors.copy(accent = Color(airline.accentArgb))
            return ResolvedTheme(a.choice, colors, typeFor(a.choice), rounded, airline?.name)
        }
    }
}
