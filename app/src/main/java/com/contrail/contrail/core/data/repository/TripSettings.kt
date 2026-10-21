package com.contrail.contrail.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.contrail.contrail.core.data.rotation.TurnThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Trip preferences: the cockpit-visit turnaround thresholds. */
@Singleton
class TripSettings @Inject constructor(@Named("trips") private val store: DataStore<Preferences>) {
    private val relaxed = intPreferencesKey("turn.relaxed")
    private val maybe = intPreferencesKey("turn.maybe")

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
