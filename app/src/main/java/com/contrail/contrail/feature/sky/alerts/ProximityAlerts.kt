package com.contrail.contrail.feature.sky.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contrail.contrail.MainActivity
import com.contrail.contrail.R
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.data.repository.LiveTrafficRepository
import com.contrail.contrail.core.data.repository.WatchlistRepository
import com.contrail.contrail.core.data.sky.SkyGeometry
import com.contrail.contrail.core.data.sky.SkySettingsRepository
import com.contrail.contrail.feature.widgets.OverheadSnapshotStore
import com.contrail.contrail.feature.widgets.Widgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** Turns the 15-minute background check on and off. */
@Singleton
class ProximityAlerts @Inject constructor(@ApplicationContext private val context: Context) {
    /** Runs while alerts are on or an overhead widget is on the home screen. */
    fun schedule(needed: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (needed) {
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ProximityWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val WORK = "proximity"
        const val CHANNEL = "overhead"
    }
}

/**
 * Every 15 minutes: fetch traffic around the place you last opened Sky, notify about anything
 * worth looking up (once per aircraft every few hours), and refresh the overhead widget.
 */
@HiltWorker
class ProximityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val traffic: LiveTrafficRepository,
    private val settings: SkySettingsRepository,
    private val watchlist: WatchlistRepository,
    private val snapshots: OverheadSnapshotStore,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        // Nothing left that needs this check: stop scheduling it.
        if (!s.alerts.enabled && !Widgets.hasOverhead(applicationContext)) {
            ProximityAlerts(applicationContext).schedule(false)
            return Result.success()
        }
        val spot = s.spot ?: return Result.success()
        runCatching { traffic.refresh(spot, ceil(s.radiusKm / 1.852 + 5).toInt()) }.onFailure { return Result.retry() }
        val now = clock.nowMs()
        val contacts = SkyGeometry.contacts(spot, traffic.observeTraffic().first(), s.radiusKm.toDouble(), now)
        snapshots.save(contacts.firstOrNull(), contacts.size, s.spotName, now)
        Widgets.updateOverhead(applicationContext)

        if (!s.alerts.enabled) return Result.success()
        val watch = Watchlist(watchlist.observe().first())
        val already = settings.recentlyNotified(now)
        val fresh = contacts.mapNotNull { c -> s.alerts.reason(c, watch)?.let { c to it } }.filter { it.first.aircraft.hex !in already }
        if (fresh.isEmpty()) return Result.success()
        notify(fresh.take(3).map { (c, why) ->
            val a = c.aircraft
            val alt = a.altitudeFt?.let { String.format(Locale.US, "%,d ft", it) } ?: "low"
            "${a.displayCallsign} · ${a.typeName ?: a.typeCode ?: "aircraft"}" to "$why · ${String.format(Locale.US, "%.0f", c.distanceKm)} km away at $alt"
        })
        settings.markNotified(fresh.map { it.first.aircraft.hex }.toSet(), now)
        return Result.success()
    }

    private fun notify(lines: List<Pair<String, String>>) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ProximityAlerts.CHANNEL, ctx.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "Sky"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        lines.forEachIndexed { i, (title, body) ->
            val n = NotificationCompat.Builder(ctx, ProximityAlerts.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_plane)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setColor(0xFF8FD8FF.toInt())
                .build()
            nm.notify(NOTIFICATION_BASE + i, n)
        }
    }

    companion object {
        private const val NOTIFICATION_BASE = 4_100
    }
}
