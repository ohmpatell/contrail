package com.contrail.contrail.feature.hangar

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contrail.contrail.MainActivity
import com.contrail.contrail.R
import com.contrail.contrail.core.data.hangar.HangarEvent
import com.contrail.contrail.core.data.hangar.HangarRepository
import com.contrail.contrail.core.data.repository.AircraftDetailsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Runs the Hangar check while anything is pinned. */
object HangarAlerts {
    private const val WORK = "hangar"
    const val CHANNEL = "hangar"

    fun schedule(context: Context, needed: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (needed) {
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HangarWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }
}

/**
 * Every 15 minutes: looks your pinned planes up and tells you when one takes off or lands. Off the
 * ground and on it again are compared with the last look, so a missed check never invents a flight.
 */
@HiltWorker
class HangarWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hangar: HangarRepository,
    private val details: AircraftDetailsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val events = runCatching { hangar.refreshLive() }.getOrElse { return Result.retry() }
        events.filter { it.plane.alerts }.forEach { notify(it) }
        return Result.success()
    }

    private suspend fun notify(e: HangarEvent) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val callsign = e.aircraft.callsign?.trim()?.ifEmpty { null }
        val route = callsign?.let { cs ->
            runCatching { details.refreshRoute(cs) }
            details.observeRoute(cs).first()?.let { "${it.origin.code} → ${it.destination.code}" }
        }
        val title = when (e.kind) {
            HangarEvent.Kind.TookOff -> "${e.plane.registration} is in the air"
            HangarEvent.Kind.Landed -> "${e.plane.registration} has landed"
        }
        val body = listOfNotNull(callsign, route?.let { "$it (route from callsign)" }).joinToString(" · ").ifEmpty { "Tap to see where it is" }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(HangarAlerts.CHANNEL, ctx.getString(R.string.hangar_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx,
            e.plane.registration.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_HANGAR, e.plane.registration),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, HangarAlerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plane)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFF5C451.toInt())
            .build()
        nm.notify(NOTIFICATION_BASE + (e.plane.registration.hashCode() and 0xFFF), n)
    }

    private companion object {
        const val NOTIFICATION_BASE = 5_000
    }
}
