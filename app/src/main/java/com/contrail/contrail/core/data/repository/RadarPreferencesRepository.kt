package com.contrail.contrail.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RadarCamera(val lat: Double, val lon: Double, val zoom: Double) {
    companion object {
        /** Toronto Pearson, where the radar opens before it knows anything better. */
        val Default = RadarCamera(43.6777, -79.6248, 7.2)
    }
}

data class RadarLayers(
    val airportLabels: Boolean = true,
    val weather: Boolean = false,
    val dayNight: Boolean = true,
    val trails: Boolean = true,
    val callsigns: Boolean = true,
)

interface RadarPreferencesRepository {
    val camera: Flow<RadarCamera>
    val layers: Flow<RadarLayers>
    suspend fun saveCamera(camera: RadarCamera)
    suspend fun saveLayers(layers: RadarLayers)
}

@Singleton
class DataStoreRadarPreferences @Inject constructor(
    private val store: DataStore<Preferences>,
) : RadarPreferencesRepository {

    override val camera: Flow<RadarCamera> = store.data.map { p ->
        val lat = p[CAM_LAT]
        val lon = p[CAM_LON]
        val zoom = p[CAM_ZOOM]
        if (lat != null && lon != null && zoom != null) RadarCamera(lat, lon, zoom) else RadarCamera.Default
    }

    override val layers: Flow<RadarLayers> = store.data.map { p ->
        val d = RadarLayers()
        RadarLayers(
            airportLabels = p[L_AIRPORTS] ?: d.airportLabels,
            weather = p[L_WEATHER] ?: d.weather,
            dayNight = p[L_DAYNIGHT] ?: d.dayNight,
            trails = p[L_TRAILS] ?: d.trails,
            callsigns = p[L_CALLSIGNS] ?: d.callsigns,
        )
    }

    override suspend fun saveCamera(camera: RadarCamera) {
        store.edit {
            it[CAM_LAT] = camera.lat
            it[CAM_LON] = camera.lon
            it[CAM_ZOOM] = camera.zoom
        }
    }

    override suspend fun saveLayers(layers: RadarLayers) {
        store.edit {
            it[L_AIRPORTS] = layers.airportLabels
            it[L_WEATHER] = layers.weather
            it[L_DAYNIGHT] = layers.dayNight
            it[L_TRAILS] = layers.trails
            it[L_CALLSIGNS] = layers.callsigns
        }
    }

    private companion object {
        val CAM_LAT = doublePreferencesKey("radar.camera.lat")
        val CAM_LON = doublePreferencesKey("radar.camera.lon")
        val CAM_ZOOM = doublePreferencesKey("radar.camera.zoom")
        val L_AIRPORTS = booleanPreferencesKey("radar.layer.airports")
        val L_WEATHER = booleanPreferencesKey("radar.layer.weather")
        val L_DAYNIGHT = booleanPreferencesKey("radar.layer.daynight")
        val L_TRAILS = booleanPreferencesKey("radar.layer.trails")
        val L_CALLSIGNS = booleanPreferencesKey("radar.layer.callsigns")
    }
}
