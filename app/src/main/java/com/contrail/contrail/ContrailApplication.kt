package com.contrail.contrail

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.reference.TypeDesignators
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.repository.TripSettings
import com.contrail.contrail.core.data.settings.ThemeResolver
import com.contrail.contrail.feature.trips.live.LiveFlightUpdates
import com.contrail.contrail.feature.widgets.Widgets
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import javax.inject.Inject

@HiltAndroidApp
class ContrailApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject lateinit var okHttp: Lazy<OkHttpClient>
    @Inject lateinit var httpClient: Lazy<HttpClient>
    @Inject lateinit var trips: Lazy<TripRepository>
    @Inject lateinit var tripSettings: Lazy<TripSettings>
    @Inject lateinit var liveFlights: Lazy<LiveFlightUpdates>
    @Inject lateinit var themes: Lazy<ThemeResolver>
    @Inject lateinit var account: Lazy<com.contrail.contrail.core.data.sync.AccountRepository>
    @Inject lateinit var database: Lazy<com.contrail.contrail.core.database.ContrailDatabase>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TypeDesignators.install { assets.open("aircraft_types.json") }
        Airlines.install { assets.open("airlines.json") }
        MapLibre.getInstance(this)
        // Map tiles go through the same client so they carry Contrail's User-Agent too.
        HttpRequestUtil.setOkHttpClient(okHttp.get())
        // Home-screen widgets follow your trips: a new or edited flight refreshes them.
        appScope.launch {
            @OptIn(FlowPreview::class)
            trips.get().trips.drop(1).debounce(1_500).collect { Widgets.updateTrips(this@ContrailApplication) }
        }
        // Signed in: sync shortly after anything that syncs changes here, and whenever the app opens.
        appScope.launch {
            if (account.get().signedIn.first()) com.contrail.contrail.core.data.sync.SyncScheduler.start(this@ContrailApplication)
            database.get().invalidationTracker.createFlow("trip", "segment", "connection_pref", "lounge_visit", "lounge", "watch_item", "airport_user", emitInitialState = false)
                .debounce(3_000)
                .collect { if (account.get().signedIn.first()) com.contrail.contrail.core.data.sync.SyncScheduler.soon(this@ContrailApplication) }
        }
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                appScope.launch { if (account.get().signedIn.first()) com.contrail.contrail.core.data.sync.SyncScheduler.soon(this@ContrailApplication, delaySeconds = 0) }
            }
        })
        // Widgets repaint in the new colours when the theme changes.
        appScope.launch {
            themes.get().theme.map { it.colors }.distinctUntilChanged().drop(1).collect { Widgets.updateAll(this@ContrailApplication) }
        }
        // The lock-screen Live Update follows your next flight, and the switch in Settings.
        appScope.launch {
            @OptIn(FlowPreview::class)
            combine(trips.get().allTrips, tripSettings.get().liveUpdates) { _, _ -> }.debounce(1_500).collect {
                runCatching { liveFlights.get().reschedule() }
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient.get() })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
}
