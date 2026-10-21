package com.contrail.contrail.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object ContrailHttp {
    const val CONTACT_URL = "https://github.com/ohmpatell/contrail"

    /** Every request identifies the app, as Planespotters and the ADS-B community APIs ask. */
    fun userAgent(versionName: String) = "Contrail/$versionName (+$CONTACT_URL; personal flight tracker)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun okHttp(userAgent: String, cacheDir: File?): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .apply { if (cacheDir != null) cache(Cache(File(cacheDir, "http"), 64L * 1024 * 1024)) }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun ktor(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = false
    }
}

/**
 * Enforces a minimum gap between requests to one host (adsb.lol and airplanes.live ask
 * for about one request per second at most).
 */
class RequestThrottle(
    private val minIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val mutex = Mutex()
    private var lastRequestAt = Long.MIN_VALUE / 2

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = lastRequestAt + minIntervalMs - clock()
        if (wait > 0) sleep(wait)
        lastRequestAt = clock()
        block()
    }
}

/**
 * One throttle per host, shared by every caller (the radar, flight lookups, airport boards),
 * so the per-host request rate holds no matter how many features poll it.
 */
object HostThrottles {
    private val throttles = java.util.concurrent.ConcurrentHashMap<String, RequestThrottle>()

    fun of(host: String, minIntervalMs: Long = 1_100): RequestThrottle =
        throttles.getOrPut(host) { RequestThrottle(minIntervalMs) }
}
