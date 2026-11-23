package com.contrail.contrail.feature.cabin

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.cabin.FlightPackRepository
import com.contrail.contrail.core.data.trip.FlightSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Map tiles along a flight's route, stored by MapLibre so Cabin mode's map works with no
 * internet. Tiles touching the route at zoom 2 to 8 (about 150 km either side at the closest
 * zoom): a few hundred tiles, even for a long-haul.
 */
object OfflineMaps {
    const val STYLE = "asset://map/contrail_dark.json"
    private const val MIN_ZOOM = 2.0
    private const val MAX_ZOOM = 8.0

    /** Downloads the corridor; [progress] gets 0..100. True when every tile arrived. */
    suspend fun download(context: Context, segmentId: Long, route: List<LatLon>, progress: (Int) -> Unit): Boolean = withContext(Dispatchers.Main) {
        delete(context, segmentId)
        val manager = OfflineManager.getInstance(context)
        val definition = OfflineGeometryRegionDefinition(STYLE, corridor(route), MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density, false)
        val region = suspendCancellableCoroutine<OfflineRegion?> { cont ->
            manager.createOfflineRegion(definition, metadata(segmentId), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                override fun onError(error: String) = cont.resume(null)
            })
        } ?: return@withContext false
        suspendCancellableCoroutine { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    if (status.requiredResourceCount > 0) progress((status.completedResourceCount * 100 / status.requiredResourceCount).toInt())
                    if (status.isComplete) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.setObserver(null)
                        if (cont.isActive) cont.resume(true)
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    // Individual tiles fail and are retried; only give up on a hard limit.
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    if (cont.isActive) cont.resume(false)
                }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            cont.invokeOnCancellation { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
        }
    }

    /** Removes a flight's tiles once you've landed (the track and moments stay). */
    suspend fun delete(context: Context, segmentId: Long) = withContext(Dispatchers.Main) {
        val manager = OfflineManager.getInstance(context)
        val regions = suspendCancellableCoroutine<List<OfflineRegion>> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions?.toList().orEmpty())
                override fun onError(error: String) = cont.resume(emptyList())
            })
        }
        regions.filter { String(it.metadata) == "pack:$segmentId" }.forEach { r ->
            suspendCancellableCoroutine { cont ->
                r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = cont.resume(Unit)
                    override fun onError(error: String) = cont.resume(Unit)
                })
            }
        }
    }

    private fun metadata(segmentId: Long) = "pack:$segmentId".toByteArray()

    /** The route as lines, split where it crosses the antimeridian so no tile range wraps the world. */
    fun corridor(route: List<LatLon>): MultiLineString {
        val parts = mutableListOf<MutableList<Point>>(mutableListOf())
        route.forEachIndexed { i, p ->
            val lon = ((p.lon + 540) % 360) - 180
            if (i > 0) {
                val prev = ((route[i - 1].lon + 540) % 360) - 180
                if (kotlin.math.abs(lon - prev) > 180) parts += mutableListOf<Point>()
            }
            parts.last() += Point.fromLngLat(lon, p.lat)
        }
        return MultiLineString.fromLineStrings(parts.filter { it.size >= 2 }.map { LineString.fromLngLats(it) })
    }
}

/**
 * Gets a flight ready for no internet: builds the flight pack, then downloads the map along the
 * route. Runs once on flight day (about 20 hours before departure) and whenever you tap Get ready.
 */
@HiltWorker
class FlightPackWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val packs: FlightPackRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_SEGMENT, 0L).takeIf { it != 0L } ?: return Result.failure()
        setProgress(workDataOf(KEY_STEP to "Starting…", KEY_PERCENT to 0))
        val pack = runCatching { packs.build(id) { step -> setProgressAsync(workDataOf(KEY_STEP to step, KEY_PERCENT to 0)) } }.getOrNull()
            ?: return if (runAttemptCount < 3) Result.retry() else Result.failure()
        setProgress(workDataOf(KEY_STEP to "Saving the map along the route…", KEY_PERCENT to 0))
        val ok = OfflineMaps.download(applicationContext, id, pack.route) { pct -> setProgressAsync(workDataOf(KEY_STEP to "Saving the map along the route…", KEY_PERCENT to pct)) }
        if (ok) packs.markMapReady(id)
        return Result.success()
    }

    companion object {
        const val KEY_SEGMENT = "segment"
        const val KEY_STEP = "step"
        const val KEY_PERCENT = "percent"

        fun name(segmentId: Long) = "pack-$segmentId"

        /** Now, from the Get ready button. */
        fun runNow(context: Context, segmentId: Long) = enqueue(context, segmentId, 0, ExistingWorkPolicy.REPLACE)

        /** For each flight in the next few days: on flight day, about 20 hours before it leaves. */
        fun schedule(context: Context, segments: List<FlightSegment>, now: Instant) {
            segments.filter { it.departure.isAfter(now) && it.departure.isBefore(now.plusSeconds(4 * 86_400L)) }.forEach { s ->
                val at = s.departure.toEpochMilli() - 20 * 3_600_000L
                enqueue(context, s.id, (at - now.toEpochMilli()).coerceAtLeast(0), ExistingWorkPolicy.KEEP)
            }
        }

        private fun enqueue(context: Context, segmentId: Long, delayMs: Long, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                name(segmentId),
                policy,
                OneTimeWorkRequestBuilder<FlightPackWorker>()
                    .setInputData(workDataOf(KEY_SEGMENT to segmentId))
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
