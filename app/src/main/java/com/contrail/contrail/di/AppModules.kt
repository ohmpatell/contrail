package com.contrail.contrail.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.contrail.contrail.BuildConfig
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import com.contrail.contrail.core.data.repository.AirportsAsset
import com.contrail.contrail.core.data.repository.DataStoreRadarPreferences
import com.contrail.contrail.core.data.repository.DefaultAircraftDetailsRepository
import com.contrail.contrail.core.data.repository.DefaultLiveTrafficRepository
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.RadarPreferencesRepository
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.ContrailHttp
import com.contrail.contrail.core.network.photo.PhotoSource
import com.contrail.contrail.core.network.photo.PlanespottersPhotoSource
import com.contrail.contrail.core.network.route.AdsbdbRouteSource
import com.contrail.contrail.core.network.route.RouteSource
import com.contrail.contrail.core.network.traffic.FailoverLiveTrafficSource
import com.contrail.contrail.core.network.traffic.LiveTrafficSource
import com.contrail.contrail.core.network.traffic.ReadsbApiSource
import com.contrail.contrail.core.data.settings.KeystoreCipher
import com.contrail.contrail.core.data.settings.SecretCipher
import com.contrail.contrail.core.network.airport.AviationWeatherSource
import com.contrail.contrail.core.network.airport.OverpassSource
import com.contrail.contrail.core.network.logostream.LogostreamSource
import com.contrail.contrail.core.network.news.RssNewsSource
import com.contrail.contrail.core.network.track.OpenSkyTrackSource
import com.contrail.contrail.core.network.track.TrackSource
import com.contrail.contrail.core.network.weather.RainViewerSource
import com.contrail.contrail.core.network.weather.WeatherRadarSource
import com.contrail.contrail.core.data.settings.ApiKeys
import com.contrail.contrail.core.data.settings.ApiService
import com.contrail.contrail.core.data.settings.UsageTracker
import com.contrail.contrail.core.network.opensky.FlightHistorySource
import com.contrail.contrail.core.network.opensky.OpenSkyCredentials
import com.contrail.contrail.core.network.opensky.OpenSkyHistorySource
import com.contrail.contrail.core.network.route.AdsbdbAircraftSource
import com.contrail.contrail.core.network.traffic.AircraftLookupSource
import com.contrail.contrail.core.network.traffic.FailoverLookupSource
import com.contrail.contrail.core.network.traffic.ReadsbLookupSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

private val Context.radarDataStore: DataStore<Preferences> by preferencesDataStore(name = "radar")
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure")
private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "usage")
private val Context.tripsDataStore: DataStore<Preferences> by preferencesDataStore(name = "trips")
private val Context.skyDataStore: DataStore<Preferences> by preferencesDataStore(name = "sky")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = ContrailHttp.json

    @Provides @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient =
        ContrailHttp.okHttp(ContrailHttp.userAgent(BuildConfig.VERSION_NAME), context.cacheDir)

    @Provides @Singleton
    fun httpClient(okHttp: OkHttpClient): HttpClient = ContrailHttp.ktor(okHttp)

    @Provides @Singleton
    fun liveTraffic(client: HttpClient): LiveTrafficSource = FailoverLiveTrafficSource(
        listOf(ReadsbApiSource.adsbFi(client), ReadsbApiSource.adsbLol(client)),
        onProviderFailed = { name, e -> android.util.Log.w("ContrailTraffic", "$name failed: $e") },
    )

    @Provides @Singleton
    fun routes(client: HttpClient, json: Json): RouteSource = AdsbdbRouteSource(client, json)

    @Provides @Singleton
    fun photos(client: HttpClient): PhotoSource = PlanespottersPhotoSource(client)

    @Provides @Singleton
    fun weather(client: HttpClient): WeatherRadarSource = RainViewerSource(client)

    @Provides @Singleton
    fun tracks(client: HttpClient): TrackSource = OpenSkyTrackSource(client)

    @Provides @Singleton
    fun logostream(client: HttpClient): LogostreamSource = LogostreamSource(client)

    @Provides @Singleton
    fun news(client: HttpClient): RssNewsSource = RssNewsSource(client)

    @Provides @Singleton
    fun aviationWeather(client: HttpClient): AviationWeatherSource = AviationWeatherSource(client)

    @Provides @Singleton
    fun overpass(client: HttpClient): OverpassSource = OverpassSource(client)

    @Provides @Singleton
    fun aircraftLookup(client: HttpClient): AircraftLookupSource = FailoverLookupSource(
        listOf(ReadsbLookupSource.adsbFi(client), ReadsbLookupSource.adsbLol(client)),
    )

    @Provides @Singleton
    fun adsbdbAircraft(client: HttpClient, json: Json): AdsbdbAircraftSource = AdsbdbAircraftSource(client, json)

    @Provides @Singleton
    fun flightHistory(client: HttpClient, keys: ApiKeys, usage: UsageTracker, clock: Clock): FlightHistorySource = OpenSkyHistorySource(
        client,
        credentials = {
            val id = keys.get(ApiService.OpenSkyClientId)
            val secret = keys.get(ApiService.OpenSkyClientSecret)
            if (id != null && secret != null) OpenSkyCredentials(id, secret) else null
        },
        onCredits = { remaining ->
            val day = java.time.Instant.ofEpochMilli(clock.nowMs()).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            usage.recordOpenSky(remaining, day)
        },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ContrailDatabase =
        Room.databaseBuilder(context, ContrailDatabase::class.java, "contrail.db").build()

    @Provides @Singleton
    fun radarDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.radarDataStore

    @Provides @Singleton
    fun clock(): Clock = Clock.System

    @Provides @Singleton @Named("secure")
    fun secureStore(@ApplicationContext context: Context): DataStore<Preferences> = context.secureDataStore

    @Provides @Singleton @Named("usage")
    fun usageStore(@ApplicationContext context: Context): DataStore<Preferences> = context.usageDataStore

    @Provides @Singleton
    fun cipher(): SecretCipher = KeystoreCipher()

    @Provides @Singleton @Named("trips")
    fun tripsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.tripsDataStore

    @Provides @Singleton @Named("sky")
    fun skyStore(@ApplicationContext context: Context): DataStore<Preferences> = context.skyDataStore

    @Provides
    fun airportsAsset(@ApplicationContext context: Context): AirportsAsset = AirportsAsset { context.assets.open("airports.json") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun liveTraffic(impl: DefaultLiveTrafficRepository): LiveTrafficRepository
    @Binds abstract fun details(impl: DefaultAircraftDetailsRepository): AircraftDetailsRepository
    @Binds abstract fun radarPrefs(impl: DataStoreRadarPreferences): RadarPreferencesRepository
}
