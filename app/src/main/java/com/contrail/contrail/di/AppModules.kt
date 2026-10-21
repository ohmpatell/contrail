package com.contrail.contrail.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.contrail.contrail.BuildConfig
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
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
import com.contrail.contrail.core.network.track.OpenSkyTrackSource
import com.contrail.contrail.core.network.track.TrackSource
import com.contrail.contrail.core.network.weather.RainViewerSource
import com.contrail.contrail.core.network.weather.WeatherRadarSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.InputStream
import javax.inject.Named
import javax.inject.Singleton

private val Context.radarDataStore: DataStore<Preferences> by preferencesDataStore(name = "radar")

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

    @Provides @Named("airportsAsset")
    fun airportsAsset(@ApplicationContext context: Context): () -> InputStream = { context.assets.open("airports.json") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun liveTraffic(impl: DefaultLiveTrafficRepository): LiveTrafficRepository
    @Binds abstract fun details(impl: DefaultAircraftDetailsRepository): AircraftDetailsRepository
    @Binds abstract fun radarPrefs(impl: DataStoreRadarPreferences): RadarPreferencesRepository
}
