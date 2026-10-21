package com.contrail.contrail.core.data.repository

import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.model.WatchItem
import com.contrail.contrail.core.data.model.WatchKind
import com.contrail.contrail.core.data.model.Watchlist
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.database.WatchItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Tails, types and airlines you're watching for. Lives on the phone only. */
@Singleton
class WatchlistRepository @Inject constructor(db: ContrailDatabase, private val clock: Clock) {
    private val dao = db.watchDao()

    fun observe(): Flow<List<WatchItem>> = dao.observe().map { rows ->
        rows.mapNotNull { r ->
            val kind = runCatching { WatchKind.valueOf(r.kind) }.getOrNull() ?: return@mapNotNull null
            WatchItem(kind, r.value, r.label)
        }
    }

    suspend fun add(item: WatchItem) {
        val value = if (item.kind == WatchKind.Tail) Watchlist.normalizeTail(item.value) else item.value.uppercase()
        dao.upsert(WatchItemEntity(item.kind.name, value, item.label, clock.nowMs()))
    }

    suspend fun remove(kind: WatchKind, value: String) {
        val normalized = if (kind == WatchKind.Tail) Watchlist.normalizeTail(value) else value.uppercase()
        dao.delete(kind.name, normalized)
    }
}
