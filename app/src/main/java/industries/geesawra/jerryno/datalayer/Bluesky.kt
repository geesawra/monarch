package industries.geesawra.jerryno.datalayer

import android.content.Context
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.Post
import com.atproto.repo.CreateRecordRequest
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import com.atproto.server.GetSessionResponse
import com.atproto.server.RefreshSessionResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.response.AtpResponse

data class Client(
    var client: AuthenticatedXrpcBlueskyApi,
    var session: SessionData
)

enum class AuthData {
    PDSHost,
    SessionData,
}

@Serializable // Added annotation
data class SessionData(
    val accessJwt: String,
    val refreshJwt: String,
    val handle: Handle,
    val did: Did,
    val active: Boolean? = null,
) {
    fun encodeToJson(): String {
        return BlueskyJson.encodeToString(serializer(), this)
    }

    companion object {
        fun decodeFromJson(jsonString: String): SessionData {
            return BlueskyJson.decodeFromString(serializer(), jsonString)
        }

        fun fromCreateSessionResponse(session: CreateSessionResponse): SessionData {
            return SessionData(
                accessJwt = session.accessJwt,
                refreshJwt = session.refreshJwt,
                handle = session.handle,
                did = session.did,
                active = session.active,
            )
        }

        fun fromRefreshSessionResponse(session: RefreshSessionResponse): SessionData {
            return SessionData(
                accessJwt = session.accessJwt,
                refreshJwt = session.refreshJwt,
                handle = session.handle,
                did = session.did,
                active = session.active,
            )
        }

        fun fromGetSessionResponse(session: GetSessionResponse): SessionData {
            return SessionData(
                handle = session.handle,
                did = session.did,
                active = session.active,
                accessJwt = "",
                refreshJwt = ""
            )
        }
    }
}


class BlueskyConn(val context: Context) {
    companion object {
        private val Context.dataStore by preferencesDataStore("bluesky")
        private val SESSION = stringPreferencesKey(AuthData.SessionData.name)
        private val PDSHOST = stringPreferencesKey(AuthData.PDSHost.name)
    }

    var client: AuthenticatedXrpcBlueskyApi? = null
    var session: SessionData? = null

    suspend fun storeSessionData(pdsURL: String, session: SessionData) {
        context.dataStore.edit { settings ->
            settings[SESSION] = session.encodeToJson()
            settings[PDSHOST] = pdsURL
        }
    }

    suspend fun hasSession(): Boolean {
        val pdsURLFlow: Flow<String> = context.dataStore.data.map { settings ->
            settings[PDSHOST] ?: ""
        }
        val sessionDataStringFlow: Flow<String> = context.dataStore.data.map { settings ->
            settings[SESSION] ?: ""
        }

        val pdsURL = pdsURLFlow.first()
        val sessionDataString = sessionDataStringFlow.first()

        return !(pdsURL.isEmpty() || sessionDataString.isEmpty())
    }

    suspend fun login(pdsURL: String, handle: String, password: String): Result<Unit> {
        val httpClient = HttpClient(OkHttp) {
            defaultRequest {
                url(pdsURL)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }

        val client = XrpcBlueskyApi(httpClient)

        val s = client.createSession(CreateSessionRequest(handle, password))
        val sessionResponse: CreateSessionResponse = when (s) {
            is AtpResponse.Failure<*> -> return Result.failure(
                Exception(
                    "Failed to create session: ${
                        s.error?.message?.toLowerCase(
                            Locale.current
                        )
                    }"
                )
            )

            is AtpResponse.Success<CreateSessionResponse> -> s.response
        }

        storeSessionData(pdsURL, SessionData.fromCreateSessionResponse(sessionResponse))

        return Result.success(Unit)
    }

    suspend fun create(): Result<Unit> {
        return runCatching {
            if (session != null && client != null) {
                return Result.success(Unit)
            }

            val pdsURLFlow: Flow<String> = context.dataStore.data.map { settings ->
                settings[PDSHOST] ?: ""
            }
            val sessionDataStringFlow: Flow<String> = context.dataStore.data.map { settings ->
                settings[SESSION] ?: ""
            }

            val pdsURL = pdsURLFlow.first()
            val sessionDataString = sessionDataStringFlow.first()

            if (pdsURL.isEmpty() || sessionDataString.isEmpty()) {
                return Result.failure(Exception("No session data found"))
            }

            val sessionData = SessionData.decodeFromJson(sessionDataString)


            val httpClient = HttpClient(OkHttp) {
                defaultRequest {
                    url(pdsURL)
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 15000
                }
            }

            val tokens =
                BlueskyAuthPlugin.Tokens(sessionData.accessJwt, sessionData.refreshJwt)
            val authClient = AuthenticatedXrpcBlueskyApi(httpClient, tokens)

            val gs = authClient.getSession().maybeResponse()
            gs?.let {
                this.client = authClient
                this.session = SessionData.fromGetSessionResponse(it)

                return Result.success(Unit)
            }

            // No session, try to refresh
            val rs = authClient.refreshSession().maybeResponse()
            rs?.let {
                this.client = authClient
                this.session = SessionData.fromRefreshSessionResponse(it)

                return Result.success(Unit)
            }

            return Result.failure(Exception("Could not refresh session, maybe login again?"))
        }
    }

    suspend fun fetchTimeline(cursor: String? = null): Result<GetTimelineResponse> {
        return runCatching {
            create().getOrThrow()
            val timeline = client!!.getTimeline(
                GetTimelineQueryParams(
                    limit = 25,
                    cursor = cursor
                )
            );
            val feed = when (timeline) {
                is AtpResponse.Failure<*> -> {
                    return Result.failure(Exception("Failed to fetch timeline: ${timeline.error}"))
                }

                is AtpResponse.Success<GetTimelineResponse> -> timeline.response
            };

            return Result.success(feed)
        }
    }

    suspend fun post(content: String) {
        create().getOrThrow()

        val r = BlueskyJson.encodeAsJsonContent(
            Post(
                text = content,
                createdAt = Clock.System.now()
            )
        )
        client!!.createRecord(
            CreateRecordRequest(
                repo = session!!.handle, // Use handle from the session
                collection = Nsid("app.bsky.feed.post"),
                record = r,
            )
        )


    }
}