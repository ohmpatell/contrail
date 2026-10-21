package com.contrail.contrail

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import javax.inject.Inject

@HiltAndroidApp
class ContrailApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var okHttp: Lazy<OkHttpClient>
    @Inject lateinit var httpClient: Lazy<HttpClient>

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Map tiles go through the same client so they carry Contrail's User-Agent too.
        HttpRequestUtil.setOkHttpClient(okHttp.get())
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
