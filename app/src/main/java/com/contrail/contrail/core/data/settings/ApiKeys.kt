package com.contrail.contrail.core.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.contrail.contrail.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** The services that need a key. */
enum class ApiService(val title: String, val signUpUrl: String) {
    Logostream("logostream", "https://airline.logostream.dev"),
    OpenSkyClientId("OpenSky client ID", "https://opensky-network.org/my-opensky/account"),
    OpenSkyClientSecret("OpenSky client secret", "https://opensky-network.org/my-opensky/account"),
}

/** Encrypts small strings with an AES-GCM key that never leaves the Android Keystore. */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(sealed: String): String?
}

class KeystoreCipher : SecretCipher {
    private val alias = "contrail.api-keys"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}

enum class KeySource { Settings, LocalProperties, Missing }

data class KeyStatus(val service: ApiService, val source: KeySource, val masked: String?)

/**
 * API keys. Release builds read only what you saved in Settings (encrypted on the phone);
 * debug builds fall back to `local.properties` through BuildConfig.
 */
@Singleton
class ApiKeys @Inject constructor(
    @Named("secure") private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private fun pref(service: ApiService) = stringPreferencesKey("key.${service.name}")

    private fun buildConfigKey(service: ApiService): String = when (service) {
        ApiService.Logostream -> BuildConfig.LOGOSTREAM_API_KEY
        ApiService.OpenSkyClientId -> BuildConfig.OPENSKY_CLIENT_ID
        ApiService.OpenSkyClientSecret -> BuildConfig.OPENSKY_CLIENT_SECRET
    }

    fun status(service: ApiService): Flow<KeyStatus> = store.data.map { p ->
        val saved = p[pref(service)]?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }
        val fallback = buildConfigKey(service).takeIf { it.isNotBlank() }
        when {
            saved != null -> KeyStatus(service, KeySource.Settings, mask(saved))
            fallback != null -> KeyStatus(service, KeySource.LocalProperties, mask(fallback))
            else -> KeyStatus(service, KeySource.Missing, null)
        }
    }

    suspend fun get(service: ApiService): String? {
        val saved = store.data.first()[pref(service)]?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }
        return saved ?: buildConfigKey(service).takeIf { it.isNotBlank() }
    }

    suspend fun save(service: ApiService, key: String?) {
        store.edit { p ->
            val clean = key?.trim().orEmpty()
            if (clean.isEmpty()) p.remove(pref(service)) else p[pref(service)] = cipher.encrypt(clean)
        }
    }

    companion object {
        fun mask(key: String): String = if (key.length <= 6) "••••" else "${key.take(4)}••••${key.takeLast(2)}"
    }
}

/** logostream's monthly quota, read from the `X-RateLimit-*` headers of every response. */
data class LogostreamUsage(
    val limit: Int?,
    val remaining: Int?,
    val resetsOn: String?,
    /** Requests this app made this month, counted locally. */
    val made: Long,
    val updatedAtMs: Long,
) {
    val usedFraction: Float?
        get() = if (limit != null && remaining != null && limit > 0) (limit - remaining).toFloat() / limit else null

    /** Stop calling logostream when this few requests are left, so the month never runs dry. */
    val exhausted: Boolean get() = remaining != null && remaining < RESERVE

    companion object {
        const val RESERVE = 500
    }
}

@Singleton
class UsageTracker @Inject constructor(@Named("usage") private val store: DataStore<Preferences>) {
    private val limit = stringPreferencesKey("logostream.limit")
    private val remaining = stringPreferencesKey("logostream.remaining")
    private val reset = stringPreferencesKey("logostream.reset")
    private val made = longPreferencesKey("logostream.made")
    private val month = stringPreferencesKey("logostream.month")
    private val updated = longPreferencesKey("logostream.updated")

    val logostream: Flow<LogostreamUsage> = store.data.map { p ->
        LogostreamUsage(
            limit = p[limit]?.toIntOrNull(),
            remaining = p[remaining]?.toIntOrNull(),
            resetsOn = p[reset],
            made = p[made] ?: 0,
            updatedAtMs = p[updated] ?: 0,
        )
    }

    suspend fun current(): LogostreamUsage = logostream.first()

    private val openSkyRemaining = stringPreferencesKey("opensky.remaining")
    private val openSkyDay = stringPreferencesKey("opensky.day")
    private val openSkyMade = longPreferencesKey("opensky.made")

    /** OpenSky's credits left today (from `X-Rate-Limit-Remaining`) and requests Contrail made today. */
    val openSky: Flow<OpenSkyUsage> = store.data.map { p ->
        OpenSkyUsage(p[openSkyRemaining]?.toIntOrNull(), p[openSkyMade] ?: 0, p[openSkyDay])
    }

    suspend fun recordOpenSky(remaining: Int?, dayKey: String) {
        store.edit { p ->
            if (p[openSkyDay] != dayKey) {
                p[openSkyDay] = dayKey
                p[openSkyMade] = 0
                p.remove(openSkyRemaining)
            }
            p[openSkyMade] = (p[openSkyMade] ?: 0) + 1
            remaining?.let { p[openSkyRemaining] = it.toString() }
        }
    }

    /** Records one logostream response. [monthKey] is e.g. "2026-10" so the local count resets monthly. */
    suspend fun recordLogostream(limitHeader: String?, remainingHeader: String?, resetHeader: String?, monthKey: String, nowMs: Long) {
        store.edit { p ->
            if (p[month] != monthKey) {
                p[month] = monthKey
                p[made] = 0
            }
            p[made] = (p[made] ?: 0) + 1
            limitHeader?.substringBefore(',')?.trim()?.toIntOrNull()?.let { p[limit] = it.toString() }
            remainingHeader?.substringBefore(',')?.trim()?.toIntOrNull()?.let { p[remaining] = it.toString() }
            resetHeader?.let { p[reset] = it }
            p[updated] = nowMs
        }
    }
}

data class OpenSkyUsage(val remaining: Int?, val madeToday: Long, val day: String?) {
    /** Keep a reserve so the radar's anonymous track lookups and tomorrow's refresh still work. */
    val exhausted: Boolean get() = remaining != null && remaining < RESERVE

    companion object {
        const val RESERVE = 200
    }
}
