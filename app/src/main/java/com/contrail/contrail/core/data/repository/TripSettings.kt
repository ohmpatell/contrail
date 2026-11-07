package com.contrail.contrail.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.contrail.contrail.core.data.rotation.TurnThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Trip preferences: the cockpit-visit turnaround thresholds and the lock-screen Live Update. */
@Singleton
class TripSettings @Inject constructor(@Named("trips") private val store: DataStore<Preferences>) {
    private val relaxed = intPreferencesKey("turn.relaxed")
    private val maybe = intPreferencesKey("turn.maybe")
    private val live = booleanPreferencesKey("live.updates")

    /** Your flight on the lock screen while it's under way. On unless you turn it off. */
    val liveUpdates: Flow<Boolean> = store.data.map { it[live] ?: true }

    suspend fun setLiveUpdates(on: Boolean) {
        store.edit { it[live] = on }
    }

    val thresholds: Flow<TurnThresholds> = store.data.map { p ->
        TurnThresholds(p[relaxed] ?: TurnThresholds().relaxedMinutes, p[maybe] ?: TurnThresholds().maybeMinutes)
    }

    suspend fun setThresholds(t: TurnThresholds) {
        store.edit { p ->
            p[relaxed] = t.relaxedMinutes.coerceIn(30, 600)
            p[maybe] = t.maybeMinutes.coerceIn(15, t.relaxedMinutes)
        }
    }
}
