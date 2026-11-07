package com.contrail.contrail.core.network.sync

import com.contrail.contrail.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A signed-in session: short-lived access token plus the refresh token that renews it. */
data class Session(val userId: String, val accessToken: String, val refreshToken: String, val expiresAtMs: Long)

/** One synced thing as stored on the server. */
@Serializable
data class RemoteRecord(
    val kind: String,
    val id: String,
    val data: JsonObject,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val seq: Long = 0,
)

/** Why signing in or up failed, in words for the screen. */
class AccountException(message: String) : Exception(message)

/** Accounts and the records table. Behind an interface so tests and a missing key can use a fake. */
interface SyncBackend {
    val configured: Boolean
    suspend fun signUp(username: String, password: String): Session
    suspend fun signIn(username: String, password: String): Session
    suspend fun refresh(refreshToken: String): Session
    suspend fun push(session: Session, records: List<RemoteRecord>)
    /** Records changed after [afterSeq], oldest first, at most [limit]. */
    suspend fun pull(session: Session, afterSeq: Long, limit: Int = 500): List<RemoteRecord>

    /** The shared copy of an airport's board: when it was fetched and the raw AeroDataBox JSON. */
    suspend fun board(session: Session, iata: String): Pair<Long, String>? = null
    suspend fun putBoard(session: Session, iata: String, fetchedAtMs: Long, body: String) = Unit

    /** The account id for a username, or null when nobody has it. */
    suspend fun userId(session: Session, username: String): String? = null
    suspend fun share(session: Session, share: OutgoingShare) = Unit
    /** Shares sent to you after [afterSeq]. */
    suspend fun incoming(session: Session, afterSeq: Long): List<IncomingShare> = emptyList()
}

/** A flight or trip you send to someone. [ref] is the trip uid; [data] the trip record. */
@Serializable
data class OutgoingShare(
    @SerialName("to_user") val toUser: String,
    @SerialName("from_username") val fromUsername: String,
    val kind: String,
    val ref: String,
    val data: JsonObject,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class IncomingShare(
    @SerialName("from_username") val fromUsername: String,
    val kind: String,
    val ref: String,
    val data: JsonObject,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val seq: Long = 0,
)

/**
 * Supabase over plain REST (GoTrue for accounts, PostgREST for records), on the app's shared
 * Ktor client so every call carries Contrail's User-Agent. Usernames map to placeholder
 * addresses on the reserved `.invalid` domain: nothing is ever emailed.
 */
class SupabaseBackend(
    private val client: HttpClient,
    private val json: Json,
    private val url: String = BuildConfig.SUPABASE_URL,
    private val key: () -> String = { BuildConfig.SUPABASE_KEY },
    private val now: () -> Long = System::currentTimeMillis,
) : SyncBackend {
    override val configured: Boolean get() = key().isNotBlank()

    private fun HttpRequestBuilder.base() {
        header("apikey", key())
        contentType(ContentType.Application.Json)
    }

    override suspend fun signUp(username: String, password: String): Session {
        val r = client.post("$url/auth/v1/signup") {
            base()
            setBody(mapOf("email" to emailFor(username), "password" to password))
        }
        val s = session(r)
        // Claim the username for sharing later. A clash here means someone else has it.
        val p = client.post("$url/rest/v1/profiles") {
            base()
            header("Authorization", "Bearer ${s.accessToken}")
            setBody(mapOf("id" to s.userId, "username" to normalise(username)))
        }
        if (!p.status.isSuccess() && p.status.value != 409) throw AccountException("Couldn't save your username. Try again.")
        return s
    }

    override suspend fun signIn(username: String, password: String): Session {
        val r = client.post("$url/auth/v1/token") {
            base()
            parameter("grant_type", "password")
            setBody(mapOf("email" to emailFor(username), "password" to password))
        }
        return session(r)
    }

    override suspend fun refresh(refreshToken: String): Session {
        val r = client.post("$url/auth/v1/token") {
            base()
            parameter("grant_type", "refresh_token")
            setBody(mapOf("refresh_token" to refreshToken))
        }
        return session(r)
    }

    override suspend fun push(session: Session, records: List<RemoteRecord>) {
        if (records.isEmpty()) return
        records.chunked(200).forEach { batch ->
            val r = client.post("$url/rest/v1/records") {
                base()
                header("Authorization", "Bearer ${session.accessToken}")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "user_id,kind,id")
                setBody(batch.map { PushRow(it.kind, it.id, it.data, it.updatedAt, it.deleted) })
            }
            if (!r.status.isSuccess()) throw AccountException("Sync failed (${r.status.value}).")
        }
    }

    override suspend fun pull(session: Session, afterSeq: Long, limit: Int): List<RemoteRecord> {
        val r = client.get("$url/rest/v1/records") {
            base()
            header("Authorization", "Bearer ${session.accessToken}")
            parameter("select", "kind,id,data,updated_at,deleted,seq")
            parameter("seq", "gt.$afterSeq")
            parameter("order", "seq.asc")
            parameter("limit", limit)
        }
        if (!r.status.isSuccess()) throw AccountException("Sync failed (${r.status.value}).")
        return json.decodeFromString(r.bodyAsText())
    }

    private fun HttpRequestBuilder.authed(session: Session) {
        base()
        header("Authorization", "Bearer ${session.accessToken}")
    }

    override suspend fun board(session: Session, iata: String): Pair<Long, String>? {
        val r = client.get("$url/rest/v1/boards") {
            authed(session)
            parameter("select", "fetched_at,body")
            parameter("airport", "eq.${iata.uppercase()}")
        }
        if (!r.status.isSuccess()) return null
        val row = json.parseToJsonElement(r.bodyAsText()).let { it as? kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject ?: return null
        val at = row["fetched_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val body = row["body"]?.jsonPrimitive?.content ?: return null
        return at to body
    }

    override suspend fun putBoard(session: Session, iata: String, fetchedAtMs: Long, body: String) {
        client.post("$url/rest/v1/boards") {
            authed(session)
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "airport")
            setBody(listOf(BoardRow(iata.uppercase(), fetchedAtMs, body)))
        }
    }

    override suspend fun userId(session: Session, username: String): String? {
        val r = client.get("$url/rest/v1/profiles") {
            authed(session)
            parameter("select", "id")
            parameter("username", "eq.${normalise(username)}")
        }
        if (!r.status.isSuccess()) return null
        return json.parseToJsonElement(r.bodyAsText()).let { it as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    override suspend fun share(session: Session, share: OutgoingShare) {
        val r = client.post("$url/rest/v1/shares") {
            authed(session)
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            parameter("on_conflict", "from_user,to_user,kind,ref")
            setBody(listOf(share))
        }
        if (!r.status.isSuccess()) throw AccountException("Couldn't share (${r.status.value}).")
    }

    override suspend fun incoming(session: Session, afterSeq: Long): List<IncomingShare> {
        val r = client.get("$url/rest/v1/shares") {
            authed(session)
            parameter("select", "from_username,kind,ref,data,updated_at,deleted,seq")
            parameter("to_user", "eq.${session.userId}")
            parameter("seq", "gt.$afterSeq")
            parameter("order", "seq.asc")
        }
        if (!r.status.isSuccess()) throw AccountException("Couldn't check shares (${r.status.value}).")
        return json.decodeFromString(r.bodyAsText())
    }

    @Serializable
    private data class BoardRow(val airport: String, @SerialName("fetched_at") val fetchedAt: Long, val body: String)

    private suspend fun session(r: HttpResponse): Session {
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw AccountException(friendly(r.status.value, text))
        val o = json.parseToJsonElement(text).jsonObject
        val access = o["access_token"]?.jsonPrimitive?.content ?: throw AccountException("Couldn't sign in. Try again.")
        val refresh = o["refresh_token"]?.jsonPrimitive?.content.orEmpty()
        val expiresIn = o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
        val userId = o["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content.orEmpty()
        return Session(userId, access, refresh, now() + expiresIn * 1000)
    }

    @Serializable
    private data class PushRow(
        val kind: String,
        val id: String,
        val data: JsonObject,
        @SerialName("updated_at") val updatedAt: Long,
        val deleted: Boolean,
    )

    companion object {
        private const val DOMAIN = "users.contrail.invalid"

        fun normalise(username: String) = username.trim().lowercase()

        fun emailFor(username: String) = "${normalise(username)}@$DOMAIN"

        /** Letters, digits, dots and underscores, 3 to 24 long. Matches the database check. */
        fun validUsername(username: String) = Regex("^[a-z0-9_.]{3,24}$").matches(normalise(username))

        internal fun friendly(status: Int, body: String): String {
            val b = body.lowercase()
            return when {
                "already registered" in b || "already exists" in b -> "That username is taken."
                "invalid login" in b || "invalid_credentials" in b -> "Wrong username or password."
                "password" in b && ("short" in b || "least" in b || "weak" in b) -> "Use a password of at least 8 characters."
                status == 429 -> "Too many tries. Wait a minute and try again."
                status >= 500 -> "The sync server isn't answering. Try again later."
                else -> "Couldn't sign in ($status)."
            }
        }
    }
}
