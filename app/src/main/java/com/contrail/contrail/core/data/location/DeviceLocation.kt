package com.contrail.contrail.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.contrail.contrail.core.common.LatLon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Your position from the platform location service. No Play services needed. */
@Singleton
class DeviceLocation @Inject constructor(@ApplicationContext private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fix no older than [maxAgeMs], or a fresh one (waiting up to ten seconds). */
    @SuppressLint("MissingPermission")
    suspend fun current(maxAgeMs: Long = 120_000): LatLon? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val recent = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < maxAgeMs) return LatLon(recent.latitude, recent.longitude)
        val provider = when {
            lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return recent?.let { LatLon(it.latitude, it.longitude) }
        }
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc?.let { LatLon(it.latitude, it.longitude) })
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: recent?.let { LatLon(it.latitude, it.longitude) }
    }
}
