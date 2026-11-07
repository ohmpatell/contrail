package com.contrail.contrail.core.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contrail.contrail.core.common.Clock
import com.contrail.contrail.core.data.settings.SecretCipher
import com.contrail.contrail.core.database.ContrailDatabase
import com.contrail.contrail.core.network.sync.AccountException
import com.contrail.contrail.core.network.sync.Session
import com.contrail.contrail.core.network.sync.SupabaseBackend
import com.contrail.contrail.core.network.sync.SyncBackend
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Who's signed in and how the last sync went. */
data class AccountState(
    val available: Boolean = false,
    val username: String? = null,
    val lastSyncMs: Long? = null,
    val syncing: Boolean = false,
    val error: String? = null,
) {
    val signedIn: Boolean get() = username != null
}

/**
 * Optional sign-in. Everything works signed out; signing in syncs your trips, lounges,
 * watchlist and favourite airports between your devices. The session lives encrypted on the
 * phone (Android Keystore), like the API keys.
 */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("secure") private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val backend: SyncBackend,
    private val db: ContrailDatabase,
    private val json: Json,
    private val clock: Clock,
) {
    private val userKey = stringPreferencesKey("account.user")
    private val userIdKey = stringPreferencesKey("account.userId")
    private val refreshKey = stringPreferencesKey("account.refresh")
    private val accessKey = stringPreferencesKey("account.access")
    private val expiresKey = longPreferencesKey("account.expires")
    private val cursorKey = longPreferencesKey("account.cursor")
    private val lastSyncKey = longPreferencesKey("account.lastSync")

    private val activity = MutableStateFlow<Pair<Boolean, String?>>(false to null)
    private val lock = Mutex()

    val state: Flow<AccountState> = kotlinx.coroutines.flow.combine(store.data, activity.asStateFlow()) { p, (busy, err) ->
        AccountState(backend.configured, p[userKey], p[lastSyncKey], busy, err)
    }

    val signedIn: Flow<Boolean> = store.data.map { it[userKey] != null }

    suspend fun signUp(username: String, password: String) = enter(username) { backend.signUp(username, password) }

    suspend fun signIn(username: String, password: String) = enter(username) { backend.signIn(username, password) }

    private suspend fun enter(username: String, block: suspend () -> Session) {
        if (!backend.configured) throw AccountException("Sync isn't set up in this build.")
        if (!SupabaseBackend.validUsername(username)) throw AccountException("Usernames are 3 to 24 letters, numbers, dots or underscores.")
        val s = block()
        // A new account on this phone starts from a clean slate: everything here gets sent.
        db.syncDao().clear()
        store.edit {
            it[userKey] = SupabaseBackend.normalise(username)
            it[cursorKey] = 0
            it.remove(lastSyncKey)
        }
        save(s)
        SyncScheduler.start(context)
    }

    /** Signs out on this phone. Your data stays here and on the server. */
    suspend fun signOut() {
        SyncScheduler.stop(context)
        store.edit { p -> listOf(userKey, userIdKey, refreshKey, accessKey, expiresKey, cursorKey, lastSyncKey).forEach { p.remove(it) } }
        db.syncDao().clear()
        activity.value = false to null
    }

    /** One full sync. Safe to call often: concurrent calls wait for the one running. */
    suspend fun syncNow(): Result<SyncResult> = lock.withLock {
        val session = runCatching { session() }.getOrElse { return Result.failure(it) } ?: return Result.failure(AccountException("Not signed in."))
        activity.value = true to null
        val cursor = store.data.first()[cursorKey] ?: 0
        val engine = SyncEngine(db, backend, SyncCodec(json), clock::nowMs)
        val result = runCatching { engine.sync(session, cursor) }
        result.onSuccess { r ->
            store.edit { it[cursorKey] = r.cursor; it[lastSyncKey] = clock.nowMs() }
            activity.value = false to null
        }.onFailure { activity.value = false to (it.message ?: "Sync failed.") }
        result
    }

    /** The signed-in session for other features (shared boards, shares), or null. */
    suspend fun currentSession(): Session? = runCatching { session() }.getOrNull()

    /** A usable session, refreshed when it's about to expire. */
    private suspend fun session(): Session? {
        val p = store.data.first()
        if (p[userKey] == null) return null
        val refresh = p[refreshKey]?.let(cipher::decrypt) ?: return null
        val access = p[accessKey]?.let(cipher::decrypt)
        val expires = p[expiresKey] ?: 0
        if (access != null && expires - clock.nowMs() > 120_000) return Session(p[userIdKey].orEmpty(), access, refresh, expires)
        return backend.refresh(refresh).also { save(it) }
    }

    private suspend fun save(s: Session) = store.edit {
        it[userIdKey] = s.userId
        it[refreshKey] = cipher.encrypt(s.refreshToken)
        it[accessKey] = cipher.encrypt(s.accessToken)
        it[expiresKey] = s.expiresAtMs
    }
}

/** Hourly in the background while signed in, plus soon after any local change. */
object SyncScheduler {
    private const val PERIODIC = "sync-periodic"
    private const val SOON = "sync-soon"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(online).build(),
        )
        soon(context, delaySeconds = 0)
    }

    fun soon(context: Context, delaySeconds: Long = 5) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SOON,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(SOON)
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val account: AccountRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!account.signedIn.first()) return Result.success()
        return if (account.syncNow().isSuccess) Result.success() else Result.retry()
    }
}
