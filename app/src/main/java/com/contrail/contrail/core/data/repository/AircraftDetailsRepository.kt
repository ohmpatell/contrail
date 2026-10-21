package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.AircraftPhoto
import com.contrail.contrail.core.data.model.FlightRoute
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.PhotoEntity
import com.contrail.contrail.core.database.RouteEntity
import com.contrail.contrail.core.network.photo.PhotoSource
import com.contrail.contrail.core.network.route.RouteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Route and photo lookups for a radar contact, cached aggressively in Room. */
interface AircraftDetailsRepository {
    fun observeRoute(callsign: String): Flow<FlightRoute?>
    fun observePhoto(hex: String): Flow<AircraftPhoto?>

    /** Fetches the route unless a cached answer (hit or miss) is still fresh. */
    suspend fun refreshRoute(callsign: String)

    /** Fetches the photo unless a cached answer (hit or miss) is still fresh. */
    suspend fun refreshPhoto(hex: String, registration: String?)
}

@Singleton
class DefaultAircraftDetailsRepository @Inject constructor(
    db: ContrailDatabase,
    private val routes: RouteSource,
    private val photos: PhotoSource,
    private val clock: Clock,
) : AircraftDetailsRepository {
    private val routeDao = db.routeDao()
    private val photoDao = db.photoDao()

    override fun observeRoute(callsign: String): Flow<FlightRoute?> =
        routeDao.observe(callsign.uppercase()).map { it?.toModel() }

    override fun observePhoto(hex: String): Flow<AircraftPhoto?> =
        photoDao.observe(hex.lowercase()).map { it?.toModel() }

    override suspend fun refreshRoute(callsign: String) {
        val key = callsign.trim().uppercase()
        val now = clock.nowMs()
        val cached = routeDao.get(key)
        if (cached != null && now - cached.fetchedAtMs < if (cached.found) ROUTE_HIT_TTL else ROUTE_MISS_TTL) return
        val route = routes.routeForCallsign(key)
        routeDao.upsert(if (route != null) RouteEntity.from(route.copy(callsign = key), now) else RouteEntity.miss(key, now))
    }

    override suspend fun refreshPhoto(hex: String, registration: String?) {
        val key = hex.lowercase()
        val now = clock.nowMs()
        val cached = photoDao.get(key)
        if (cached != null && now - cached.fetchedAtMs < if (cached.found) PHOTO_HIT_TTL else PHOTO_MISS_TTL) return
        val photo = photos.photoFor(key, registration)
        photoDao.upsert(
            if (photo != null) {
                PhotoEntity(key, true, now, photo.thumbnailUrl, photo.largeUrl, photo.pageUrl, photo.photographer)
            } else {
                PhotoEntity(key, false, now)
            },
        )
    }

    companion object {
        const val ROUTE_HIT_TTL = 12 * 3_600_000L
        const val ROUTE_MISS_TTL = 6 * 3_600_000L
        const val PHOTO_HIT_TTL = 30 * 86_400_000L
        const val PHOTO_MISS_TTL = 3 * 86_400_000L
    }
}
