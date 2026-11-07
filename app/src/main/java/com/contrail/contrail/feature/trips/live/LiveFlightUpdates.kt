package com.contrail.contrail.feature.trips.live

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contrail.contrail.MainActivity
import com.contrail.contrail.R
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.common.LatLon
import com.contrail.contrail.core.data.reference.Airlines
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.repository.LiveFlightRepository
import com.contrail.contrail.core.data.repository.TripRepository
import com.contrail.contrail.core.data.repository.TripSettings
import com.contrail.contrail.core.data.ticket.AirportDirectory
import com.contrail.contrail.core.data.trip.FlightPhase
import com.contrail.contrail.core.data.trip.FlightProgress
import com.contrail.contrail.core.data.trip.FlightProgressCalc
import com.contrail.contrail.core.data.trip.FlightSegment
import com.contrail.contrail.feature.widgets.Widgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Live Update: your flight on the lock screen and as a status-bar chip, from three hours
 * before departure until half an hour after landing. Android 16 promotes it; older versions show a
 * normal ongoing notification with the same progress bar.
 */
@Singleton
class LiveFlightUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val settings: TripSettings,
    private val clock: Clock,
) {
    /** Plans the next refresh around your next flight. Called on launch and whenever trips change. */
    suspend fun reschedule() {
        val wm = WorkManager.getInstance(context)
        val now = Instant.ofEpochMilli(clock.nowMs())
        val next = if (settings.liveUpdates.first()) upcoming(trips.trips.first().flatMap { it.segments }, now) else null
        if (next == null) {
            wm.cancelUniqueWork(WORK)
            cancel(context)
            return
        }
        val delay = Duration.between(now, next.departure - FlightProgressCalc.SHOW_BEFORE).toMillis().coerceAtLeast(0)
        wm.enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LiveFlightWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val WORK = "live-flight"
        const val CHANNEL = "flight-live"
        const val NOTIFICATION_ID = 4_200

        fun upcoming(segments: List<FlightSegment>, now: Instant) = FlightProgressCalc.upcoming(segments, now)

        fun current(segments: List<FlightSegment>, now: Instant) = FlightProgressCalc.current(segments, now)

        fun cancel(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}

@HiltWorker
class LiveFlightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trips: TripRepository,
    private val airports: AirportRepository,
    private val liveFlights: LiveFlightRepository,
    private val settings: TripSettings,
    private val updates: LiveFlightUpdates,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = Instant.ofEpochMilli(clock.nowMs())
        val segments = trips.trips.first().flatMap { it.segments }
        val seg = if (settings.liveUpdates.first()) LiveFlightUpdates.current(segments, now) else null
        if (seg == null) {
            // Window closed (or turned off): clear it and plan around the next flight.
            LiveFlightUpdates.cancel(applicationContext)
            updates.reschedule()
            return Result.success()
        }
        val dir = airports.directory()
        val o = dir.find(seg.origin)
        val d = dir.find(seg.destination)
        val progress = FlightProgressCalc.compute(seg, now, o?.let { LatLon(it.lat, it.lon) }, d?.let { LatLon(it.lat, it.lon) }, live(seg, now))
        LiveFlightNotification.post(applicationContext, seg, progress, dir, now)
        Widgets.updateTrips(applicationContext)

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            LiveFlightUpdates.WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<LiveFlightWorker>().setInitialDelay(nextRun(seg, progress, now).toMillis(), TimeUnit.MILLISECONDS).build(),
        )
        return Result.success()
    }

    /** ADS-B is only asked once boarding could have started, through the radar's polite throttle. */
    private suspend fun live(seg: FlightSegment, now: Instant) =
        if (now.isBefore(seg.departure - Duration.ofMinutes(45))) {
            null
        } else {
            withTimeoutOrNull(20_000) { runCatching { liveFlights.find(seg) }.getOrNull() }
        }

    private fun nextRun(seg: FlightSegment, p: FlightProgress, now: Instant): Duration = when (p.phase) {
        FlightPhase.Upcoming -> minOf(Duration.ofMinutes(10), Duration.between(now, seg.departure - Duration.ofMinutes(45)).coerceAtLeast(Duration.ofMinutes(1)))
        FlightPhase.Boarding, FlightPhase.Departing, FlightPhase.Landing -> Duration.ofMinutes(2)
        FlightPhase.Airborne -> Duration.ofMinutes(if (p.live) 3 else 5)
        FlightPhase.Landed -> Duration.ofMinutes(10)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}

/** Builds the notification itself. Kept apart from the worker so the app can post one instantly. */
object LiveFlightNotification {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    fun post(context: Context, seg: FlightSegment, p: FlightProgress, dir: AirportDirectory, now: Instant) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(LiveFlightUpdates.CHANNEL, context.getString(R.string.live_flight_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val accent = (Airlines.byIata(seg.airlineIata)?.accentArgb ?: 0xFF8FD8FF).toInt()
        val destCity = dir.find(seg.destination)?.city ?: seg.destination
        val target = p.target(seg)
        val open = PendingIntent.getActivity(
            context,
            seg.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FLIGHT, seg.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val plane = IconCompat.createWithResource(context, R.drawable.ic_stat_plane)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(1000).setColor(accent)))
            .setProgress((p.fraction * 1000).toInt())
            .setProgressTrackerIcon(plane)

        val n = NotificationCompat.Builder(context, LiveFlightUpdates.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plane)
            .setContentTitle("${seg.designator} · ${seg.origin} → ${seg.destination}")
            .setContentText(text(seg, p, destCity))
            .setSubText(if (p.live) "Live · estimated" else if (seg.timesEstimated) "Estimated times" else null)
            .setShortCriticalText(chip(p, target, now))
            .setStyle(style)
            .setColor(accent)
            .setOngoing(p.phase != FlightPhase.Landed)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(p.phase != FlightPhase.Landed)
            .setContentIntent(open)
            .apply {
                if (p.phase != FlightPhase.Landed) {
                    // The system ticks the countdown itself between refreshes.
                    setWhen(target.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                } else {
                    setShowWhen(false).setTimeoutAfter(FlightProgressCalc.SHOW_AFTER.toMillis())
                }
            }
            .build()
        nm.notify(LiveFlightUpdates.NOTIFICATION_ID, n)
    }

    internal fun text(seg: FlightSegment, p: FlightProgress, destCity: String): String {
        val at = listOfNotNull(seg.departureTerminal?.let { "Terminal $it" }, seg.gate?.let { "Gate $it" }).joinToString(" · ")
        val dep = TIME.format(seg.departureLocal)
        val eta = TIME.format(p.eta.atZone(seg.arrivalZone))
        return when (p.phase) {
            FlightPhase.Upcoming -> listOf("Departs $dep", at, seg.seat?.let { "Seat $it" }).filter { !it.isNullOrBlank() }.joinToString(" · ")
            FlightPhase.Boarding -> listOf("Boarding · departs $dep", at).filter { it.isNotBlank() }.joinToString(" · ")
            FlightPhase.Departing -> if ((p.groundSpeedKt ?: 0) > 4) "Taxiing out" else "Departing · lands $destCity $eta"
            FlightPhase.Airborne -> "Lands in $destCity at $eta" +
                (p.remainingKm?.let { String.format(Locale.US, " · %,d km to go", it.toLong()) } ?: "")
            FlightPhase.Landing -> "Landing in $destCity · $eta"
            FlightPhase.Landed -> "Landed in $destCity" + (seg.arrivalTerminal?.let { " · Terminal $it" } ?: "")
        }
    }

    /** The status-bar chip: about seven characters at most. */
    internal fun chip(p: FlightProgress, target: Instant, now: Instant): String =
        if (p.phase == FlightPhase.Landed) "Landed" else FlightProgressCalc.shortDuration(Duration.between(now, target))
}
