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
import com.contrail.contrail.feature.widgets.Widgets
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
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
