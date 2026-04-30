@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.content.Context
import android.os.Build
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import industries.geesawra.monarch.BuildConfig
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.bsky.actor.GetProfileQueryParams
import app.bsky.bookmark.CreateBookmarkRequest
import app.bsky.bookmark.DeleteBookmarkRequest
import app.bsky.bookmark.GetBookmarksQueryParams
import app.bsky.bookmark.GetBookmarksResponse
import app.bsky.actor.GetProfileResponse
import app.bsky.actor.ContentLabelPref
import app.bsky.actor.ContentLabelPrefVisibility
import app.bsky.actor.MutedWord
import app.bsky.actor.MutedWordsPref
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.Profile
import app.bsky.actor.PutPreferencesRequest
import app.bsky.actor.SavedFeedType
import app.bsky.actor.SavedFeedsPrefV2
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.actor.SearchActorsQueryParams
import app.bsky.actor.SearchActorsResponse
import app.bsky.actor.SearchActorsTypeaheadQueryParams
import app.bsky.actor.SearchActorsTypeaheadResponse
import app.bsky.draft.CreateDraftRequest
import app.bsky.draft.CreateDraftResponse
import app.bsky.draft.DeleteDraftRequest
import app.bsky.draft.Draft
import app.bsky.draft.DraftEmbedExternal
import app.bsky.draft.DraftEmbedImage
import app.bsky.draft.DraftEmbedLocalRef
import app.bsky.draft.DraftEmbedRecord
import app.bsky.draft.DraftEmbedVideo
import app.bsky.draft.DraftPost
import app.bsky.draft.DraftThreadgateAllowUnion
import app.bsky.draft.DraftWithId
import app.bsky.draft.GetDraftsQueryParams
import app.bsky.draft.GetDraftsResponse
import app.bsky.draft.UpdateDraftRequest
import app.bsky.embed.AspectRatio
import app.bsky.embed.External
import app.bsky.embed.ExternalExternal
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.embed.RecordWithMedia
import app.bsky.embed.RecordWithMediaMediaUnion
import app.bsky.embed.Video
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetFeedResponse
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponse
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetLikesQueryParams
import app.bsky.feed.GetLikesResponse
import app.bsky.feed.GetPostsResponse
import app.bsky.feed.GetQuotesQueryParams
import app.bsky.feed.GetQuotesResponse
import app.bsky.feed.GetRepostedByQueryParams
import app.bsky.feed.GetRepostedByResponse
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.PostView
import app.bsky.feed.Repost
import app.bsky.feed.Threadgate
import app.bsky.feed.ThreadgateAllowUnion
import app.bsky.labeler.GetServicesQueryParams
import app.bsky.labeler.GetServicesResponse
import app.bsky.labeler.GetServicesResponseViewUnion
import com.atproto.label.Label
import com.atproto.label.LabelValueDefinition
import com.atproto.label.LabelValueDefinitionBlurs
import com.atproto.label.LabelValueDefinitionDefaultSetting
import com.atproto.label.LabelValueDefinitionSeverity
import app.bsky.notification.ListNotificationsQueryParams
import app.bsky.notification.ListNotificationsResponse
import app.bsky.notification.UpdateSeenRequest
import app.bsky.richtext.Facet
import app.bsky.video.GetJobStatusQueryParams
import app.bsky.video.GetJobStatusResponse
import app.bsky.video.JobStatus
import app.bsky.video.JobStatusState
import app.bsky.video.UploadVideoResponse
import com.atproto.identity.ResolveHandleQueryParams
import com.atproto.identity.ResolveHandleResponse
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.CreateRecordResponse
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.GetRecordQueryParams
import com.atproto.repo.ListRecordsQueryParams
import com.atproto.repo.ListRecordsResponse
import com.atproto.repo.GetRecordResponse
import com.atproto.repo.StrongRef
import com.atproto.repo.UploadBlobResponse
import com.atproto.server.GetServiceAuthQueryParams
import com.atproto.server.GetServiceAuthResponse
import industries.geesawra.monarch.collection
import industries.geesawra.monarch.did
import industries.geesawra.monarch.rkey
import java.util.UUID
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.Tid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.response.AtpErrorDescription
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.api.response.StatusCode
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthClient
import sh.christian.ozone.oauth.OAuthCodeChallengeMethod
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

enum class AuthData {
    OAuthInFlightState,
    OAuthInFlightVerifier,
    OAuthInFlightNonce,
    OAuthInFlightHandle,
    OAuthInFlightPdsURL,
    OAuthInFlightAppviewProxy,
    OAuthInFlightAuthServerURL,
}

/**
 * Like [suspendRunCatching] but rethrows [CancellationException] so that coroutine cancellation
 * is never swallowed and turned into a [Result.failure]. This prevents "job was cancelled"
 * errors from leaking to the user as snackbars/toasts.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

class LoginException(message: String?) : Exception(message)

class HandleNotFoundException(message: String?) : Exception(message)

/**
 * Wraps an [AtpException] from a failed `AtpResponse.Failure` with the surrounding call-site
 * context. Throwing this (instead of a plain `Exception` with a concatenated message) lets the
 * refresh classifier type-check `cause` against `AtpException` and pull the structured
 * `statusCode` / `error` fields out, which is minification-safe (unlike `simpleName`).
 */
class ApiCallFailure(
    val callContext: String,
    val atp: AtpException,
) : Exception("$callContext: ${atp.message}", atp)

/**
 * Kinds of authentication failure the refresh path cares about. Derived from
 * `AtpException.statusCode` (type) plus `AtpException.error?.error` (string), both of which
 * survive R8 obfuscation when the corresponding ozone classes are `-keep`-ed.
 */
private enum class AuthError { DpopNonce, ExpiredToken, InvalidGrant, Other }

private fun unwrapAtp(t: Throwable): AtpException? {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is AtpException) return cur
        cur = cur.cause
    }
    return null
}

private fun classifyAuthError(t: Throwable): AuthError {
    val atp = unwrapAtp(t)
    if (atp != null) {
        val kind = atp.error?.error
        if (atp.statusCode is StatusCode.InvalidRequest && kind == "invalid_grant") {
            return AuthError.InvalidGrant
        }
        if (kind == "use_dpop_nonce") return AuthError.DpopNonce
        if (kind == "ExpiredToken" || kind == "InvalidToken" || kind == "invalid_token") {
            return AuthError.ExpiredToken
        }
        if (atp.statusCode is StatusCode.AuthenticationRequired) return AuthError.ExpiredToken
        return AuthError.Other
    }
    // Fallback: no AtpException in cause chain (e.g. pre-wrap error path).
    val msg = t.message.orEmpty()
    return when {
        msg.contains("invalid_grant", ignoreCase = true) -> AuthError.InvalidGrant
        msg.contains("use_dpop_nonce", ignoreCase = true)
            || msg.contains("DPoP", ignoreCase = true) -> AuthError.DpopNonce
        msg.contains("ExpiredToken", ignoreCase = true)
            || msg.contains("InvalidToken", ignoreCase = true)
            || msg.contains("invalid_token", ignoreCase = true)
            || msg.contains("timestamp check failed", ignoreCase = true)
            || msg.contains("exp claim", ignoreCase = true) -> AuthError.ExpiredToken
        msg.contains("DPoP-Nonce header not found", ignoreCase = true) -> AuthError.DpopNonce
        else -> AuthError.Other
    }
}

private fun summarizeAuthError(t: Throwable): String {
    val atp = unwrapAtp(t)
    return if (atp != null) {
        "${atp.statusCode::class.simpleName}(${atp.error?.error ?: "<no-error>"})"
    } else {
        "${t::class.simpleName}: ${t.message?.take(80) ?: "<no-message>"}"
    }
}

private fun tokenTail(t: String?): String =
    if (t.isNullOrEmpty()) "<empty>" else "…" + t.takeLast(6)

/**
 * Parse the `exp` claim from a JWT access token. Returns `null` if the token is not a valid
 * JWT or the claim is missing.
 */
private fun accessTokenExp(accessToken: String): Long? {
    return runCatching {
        val payload = accessToken.split(".")[1]
        val decoded = java.util.Base64.getUrlDecoder().decode(payload)
        val json = BlueskyJson.decodeFromString(
            kotlinx.serialization.json.JsonObject.serializer(),
            decoded.decodeToString()
        )
        (json["exp"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
    }.getOrNull()
}

/**
 * In-memory "who am I" record for the active session. Credentials live separately in
 * `BlueskyConn.oauthToken` (an `OAuthToken`); this struct only carries identity.
 */
data class SessionData(
    val handle: Handle,
    val did: Did,
    val active: Boolean? = null,
)

data class Timeline(
    val cursor: String? = null,
    val feed: List<FeedViewPost>,
)

data class PostResult(
    val uri: AtUri,
    val cid: Cid,
)

data class ThreadPostData(
    val text: String,
    val facets: List<Facet> = emptyList(),
    val images: List<Uri>? = null,
    val video: Uri? = null,
    val mediaAltTexts: Map<Uri, String> = emptyMap(),
    val linkPreview: LinkPreviewData? = null,
)

@Stable
@Serializable
data class MonarchAccountNote(
    val did: String,
    val note: String,
    val createdAt: String,
)

@Serializable
data class MonarchAccountNotesData(
    val notes: List<MonarchAccountNote> = emptyList(),
)

@Serializable
data class MonarchBlockNotesPref(
    @SerialName("\$type")
    val type: String = "\$monarch.blockNotes",
    val data: MonarchAccountNotesData,
)

@Serializable
data class MonarchAccountNotesPref(
    @SerialName("\$type")
    val type: String = "\$monarch.accountNotes",
    val data: MonarchAccountNotesData,
)

class BlueskyConn(val context: Context) {
    companion object {
        const val MAX_IMAGE_SIZE_BYTES = 1_950_000L
        const val VIDEO_UPLOAD_TIMEOUT_MS = 300_000L
        const val DEFAULT_TIMEOUT_MS = 30_000L

        // Official Bluesky moderation labeler (always subscribed, applies porn/nsfw/nudity/etc.)
        private const val BSKY_MOD_SERVICE = "did:plc:ar7c4by46qjdydhdevvrndac"

        // Transient OAuth-flow state lives here. The active session itself (credentials, handle,
        // PDS, appview) is stored in AccountManager — see datalayer/AccountManager.kt.
        private val Context.dataStore by preferencesDataStore("bluesky")
        private val OAUTH_IN_FLIGHT_STATE = stringPreferencesKey(AuthData.OAuthInFlightState.name)
        private val OAUTH_IN_FLIGHT_VERIFIER = stringPreferencesKey(AuthData.OAuthInFlightVerifier.name)
        private val OAUTH_IN_FLIGHT_NONCE = stringPreferencesKey(AuthData.OAuthInFlightNonce.name)
        private val OAUTH_IN_FLIGHT_HANDLE = stringPreferencesKey(AuthData.OAuthInFlightHandle.name)
        private val OAUTH_IN_FLIGHT_PDS_URL = stringPreferencesKey(AuthData.OAuthInFlightPdsURL.name)
        private val OAUTH_IN_FLIGHT_APPVIEW_PROXY = stringPreferencesKey(AuthData.OAuthInFlightAppviewProxy.name)
        private val OAUTH_IN_FLIGHT_AUTH_SERVER_URL = stringPreferencesKey(AuthData.OAuthInFlightAuthServerURL.name)
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val CACHED_FEED_ORDER = stringPreferencesKey("cached_feed_order")
        private val CACHED_FEED_METADATA = stringPreferencesKey("cached_feed_metadata")

        // OAuth client identity. Must match the JSON document hosted at OAUTH_CLIENT_ID exactly.
        // See oauth/client-metadata.json and oauth/README.md.
        //
        // OAUTH_REDIRECT_URI uses a reverse-DNS custom scheme per RFC 8252 §7.1. Two
        // non-obvious requirements that Bluesky's auth server enforces strictly:
        //   1. Scheme must be reverse-DNS form (rejects generic schemes like "monarch://").
        //   2. Form is `<scheme>:/{path}` — SINGLE slash, no authority/host. RFC 8252 §7.1
        //      shows `com.example.app:/oauth2redirect/example-provider` — there is no `://`.
        //      Bluesky's error: "Private-Use URI Scheme must be in the form <scheme>:/{path}".
        // The scheme matches Monarch's Android application ID so the intent-filter pattern
        // is collision-free by Android's package-name uniqueness guarantee.
        const val OAUTH_CLIENT_ID = "https://monarch.geesawra.industries/client-metadata.json"
        const val OAUTH_REDIRECT_URI = "industries.geesawra.monarch:/oauth/callback"

        private fun createRetryHttpClient(
            baseUrl: String? = null,
            requestTimeout: Long = DEFAULT_TIMEOUT_MS,
            socketTimeout: Long = DEFAULT_TIMEOUT_MS,
            configure: io.ktor.client.HttpClientConfig<io.ktor.client.engine.okhttp.OkHttpConfig>.() -> Unit = {},
        ): HttpClient {
            return HttpClient(OkHttp) {
                if (baseUrl != null) {
                    defaultRequest {
                        url(baseUrl)
                    }
                }
                if (BuildConfig.DEBUG) {
                    engine {
                        addInterceptor { chain ->
                            val req = chain.request()
                            val res = chain.proceed(req)
                            val code = res.code
                            val xrpcLike = code == 404 || code in 100..199 || code in 300..399
                            val path = req.url.encodedPath
                            if (path.contains("/xrpc/")) {
                                if (xrpcLike) {
                                    Log.w("BlueskyXrpc", "XrpcNotSupported $code ${req.method} ${req.url}")
                                } else if (code == 401) {
                                    val body = runCatching { res.peekBody(4096).string() }.getOrElse { "<peek-failed: ${it.message}>" }
                                    Log.w("BlueskyXrpc", "401 ${req.method} ${req.url} body=$body")
                                }
                            }
                            res
                        }
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = requestTimeout
                    connectTimeoutMillis = DEFAULT_TIMEOUT_MS
                    socketTimeoutMillis = socketTimeout
                }
                install(HttpRequestRetry) {
                    maxRetries = 3
                    retryIf { _, response ->
                        response.status.value in 500..599
                    }
                    retryOnExceptionIf { _, cause ->
                        cause is java.io.IOException
                    }
                    exponentialDelay()
                    // Strip auth headers between retries so BlueskyAuthPlugin regenerates a
                    // fresh DPoP proof (new jti) for each attempt. Without this, ktor replays
                    // the original headers including the now-used DPoP proof, and the server
                    // rejects the retry as "DPoP proof replayed". Harmless for unauth'd
                    // clients (no Authorization/DPoP headers to strip in the first place).
                    modifyRequest { request ->
                        request.headers.remove(io.ktor.http.HttpHeaders.Authorization)
                        request.headers.remove("DPoP")
                    }
                }
                configure()
            }
        }

        suspend fun resolveHandleToDid(handle: String): Result<Did> {
            return suspendRunCatching {
                val api = XrpcBlueskyApi()
                val rawId = api.resolveHandle(
                    ResolveHandleQueryParams(handle = Handle(handle))
                )
                when (rawId) {
                    is AtpResponse.Failure<*> -> {
                        if (rawId.error?.error == "HandleNotFound") {
                            throw HandleNotFoundException(rawId.error?.message)
                        }
                        throw Exception("Failed to resolve handle: ${rawId.error?.message}")
                    }
                    is AtpResponse.Success<ResolveHandleResponse> -> Did(rawId.response.did.did)
                }
            }
        }

        suspend fun pdsForHandle(handleOrDid: String): Result<String> {
            return suspendRunCatching {
                val did = if (handleOrDid.startsWith("did:")) {
                    handleOrDid
                } else {
                    resolveHandleToDid(handleOrDid).getOrThrow().did
                }

                val httpClient = createRetryHttpClient()

                val didDocUrl = when {
                    did.startsWith("did:web:") -> {
                        val domain = did.removePrefix("did:web:").replace(":", "/")
                        "https://$domain/.well-known/did.json"
                    }
                    else -> "https://plc.directory/$did"
                }

                val rawDoc = httpClient.get(didDocUrl)
                httpClient.close()

                if (rawDoc.status != HttpStatusCode.OK) {
                    return Result.failure(Exception("DID document lookup failed: HTTP ${rawDoc.status}"))
                }

                val body: String = rawDoc.body()
                val solvedDoc: DIDDoc = BlueskyJson.decodeFromString(DIDDoc.serializer(), body)

                for (ps in solvedDoc.service) {
                    if (ps.id == "#atproto_pds" && ps.type == "AtprotoPersonalDataServer") {
                        return Result.success(ps.serviceEndpoint)
                    }
                }

                return Result.failure(Exception("No PDS service defined in the DID document associated with $handleOrDid"))
            }
        }

        suspend fun authServerForPds(pdsURL: String): Result<String> {
            return suspendRunCatching {
                val httpClient = createRetryHttpClient()
                val endpoint = pdsURL.trimEnd('/') + "/.well-known/oauth-protected-resource"
                val response = httpClient.get(endpoint)
                httpClient.close()

                if (response.status != HttpStatusCode.OK) {
                    return Result.failure(Exception("Failed to fetch OAuth protected resource metadata: HTTP ${response.status}"))
                }

                val body: String = response.body()
                val protectedResource = BlueskyJson.decodeFromString(OAuthProtectedResource.serializer(), body)

                if (protectedResource.authorizationServers.isEmpty()) {
                    return Result.failure(Exception("PDS does not specify any authorization servers"))
                }

                return Result.success(protectedResource.authorizationServers.first())
            }
        }
    }

    @Serializable
    private data class Service(
        val id: String,
        val type: String,
        val serviceEndpoint: String
    )

    @Serializable
    private data class DIDDoc(
        val service: List<Service>
    )

    @Serializable
    private data class OAuthProtectedResource(
        val resource: String,
        @kotlinx.serialization.SerialName("authorization_servers")
        val authorizationServers: List<String>
    )

    var client: BlueskyApi? = null      // appview queries (has atproto-proxy)
    var pdsClient: BlueskyApi? = null   // PDS procedures (no atproto-proxy)
    val publicClient: BlueskyApi by lazy {
        val hc = createRetryHttpClient(baseUrl = "https://public.api.bsky.app")
        XrpcBlueskyApi(hc)
    }
    var session: SessionData? = null
    var oauthToken: OAuthToken? = null
    var createMutex: Mutex = Mutex()
    // Serializes the auth-recovery path. When multiple concurrent XRPC calls hit an expired
    // access token simultaneously, only one coroutine at a time goes through refresh; the
    // others reconverge on the refreshed tokens via [refreshGeneration] and just retry.
    private val refreshMutex: Mutex = Mutex()
    // Monotonic counter bumped by every app-driven token refresh. `retryOnDpopHiccup` snapshots
    // the value before running `block()` and compares it after a failure to detect whether
    // another coroutine has already refreshed in the meantime (in which case we skip refresh
    // and go straight to retry, avoiding a second POST that would burn the single-use token).
    private val refreshGeneration: AtomicLong = AtomicLong(0L)
    var pdsURL: String? = null
    var appviewProxy: String? = null

    // Shared auth state across `pdsClient` and `client`. Both clients' BlueskyAuthPlugin
    // instances reference the SAME MutableStateFlow, so when one client's plugin refreshes
    // the OAuth tokens or rotates the DPoP nonce, the other picks up the update on its next
    // request. This is the fix for the "Invalid refresh token" error that previously
    // appeared when the two clients tried to refresh independently with the same old
    // refresh token.
    private var sharedAuthTokens: MutableStateFlow<BlueskyAuthPlugin.Tokens?>? = null
    private var sharedOAuthApi: OAuthApi? = null
    private var sharedOAuthHttpClient: HttpClient? = null

    // Long-lived scope for the OAuth-token refresh watcher. Survives client rebuilds via
    // SupervisorJob so a single watcher failure doesn't cancel the others.
    private val connScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tokenWatcherJob: Job? = null

    private fun clearInMemorySession() {
        session = null
        oauthToken = null
        client = null
        pdsClient = null
        tokenWatcherJob?.cancel()
        tokenWatcherJob = null
        sharedAuthTokens = null
        sharedOAuthApi = null
        sharedOAuthHttpClient?.close()
        sharedOAuthHttpClient = null
    }

    /**
     * Wrap an XRPC call with single-flight auth recovery.
     *
     * On auth-flavored failures, coordinates retries across concurrent callers via
     * [refreshMutex] + [refreshGeneration]:
     *   - `DpopNonce` → brief settle delay, retry.
     *   - `ExpiredToken` → acquire the mutex, then check if another coroutine already bumped
     *     the generation counter (i.e. already refreshed). If so, just retry with the current
     *     tokens. Otherwise, drive the refresh ourselves through [actuallyRefresh].
     *   - `InvalidGrant` → refresh token permanently rejected; clear the session via
     *     [handleInvalidGrant] and surface as [LoginException] so the UI navigates to login.
     *   - Anything else → surface the original failure unchanged.
     *
     * Only one coroutine at a time can be in the recovery critical section, which prevents
     * two parallel requests from each POSTing the same single-use refresh token and killing
     * the session.
     */
    private suspend fun <T> retryOnDpopHiccup(block: suspend () -> Result<T>): Result<T> {
        val genBefore = refreshGeneration.get()
        val first = block()
        if (first.isSuccess) return first
        val err = first.exceptionOrNull() ?: return first

        return when (classifyAuthError(err)) {
            AuthError.Other -> first
            AuthError.DpopNonce -> {
                delay(150)
                block()
            }
            AuthError.InvalidGrant -> {
                Log.w("BlueskyAuth", "refresh-failed gen=$genBefore cause=${summarizeAuthError(err)} terminal=true")
                handleInvalidGrant(err)
                Result.failure(LoginException("Session expired: ${summarizeAuthError(err)}"))
            }
            AuthError.ExpiredToken -> {
                val recovery: Result<Unit> = refreshMutex.withLock {
                    val genNow = refreshGeneration.get()
                    if (genNow != genBefore) {
                        Log.d("BlueskyAuth", "refresh-skipped gen=$genBefore (current=$genNow) cause=${summarizeAuthError(err)}")
                        Result.success(Unit)
                    } else {
                        Log.d("BlueskyAuth", "refresh-start gen=$genBefore cause=${summarizeAuthError(err)}")
                        val refreshResult = actuallyRefresh()
                        val rErr = refreshResult.exceptionOrNull()
                        when {
                            rErr == null -> Result.success(Unit)
                            classifyAuthError(rErr) == AuthError.InvalidGrant -> {
                                Log.w("BlueskyAuth", "refresh-failed gen=$genBefore cause=${summarizeAuthError(rErr)} terminal=true")
                                handleInvalidGrant(rErr)
                                Result.failure(LoginException("Session expired: ${summarizeAuthError(rErr)}"))
                            }
                            else -> {
                                Log.w("BlueskyAuth", "refresh-failed gen=$genBefore cause=${summarizeAuthError(rErr)} terminal=false")
                                Result.failure(rErr)
                            }
                        }
                    }
                }
                if (recovery.isFailure) {
                    @Suppress("UNCHECKED_CAST")
                    return recovery as Result<T>
                }
                block()
            }
        }
    }

    /**
     * Actively drive a token refresh through the shared `OAuthApi`. Called when the app
     * detects an expired-token failure and holds [refreshMutex]. Persists the rotated
     * credentials to DataStore **before** publishing to [sharedAuthTokens], so a crash
     * between refresh and persist cannot strand the device with a dead refresh token in
     * DataStore but a working one in memory.
     */
    private suspend fun actuallyRefresh(): Result<Unit> = suspendRunCatching {
        val tokens = sharedAuthTokens?.value as? BlueskyAuthPlugin.Tokens.Dpop
            ?: throw LoginException("Auth state missing: no DPoP tokens available")
        val oauthApi = sharedOAuthApi
            ?: throw LoginException("Auth state missing: OAuth API not initialized")
        val refreshed = oauthApi.refreshToken(
            clientId = tokens.clientId,
            nonce = tokens.nonce,
            refreshToken = tokens.refresh,
            keyPair = tokens.keyPair,
        )
        val previous = oauthToken
            ?: throw LoginException("Auth state missing: no in-memory OAuthToken to merge refresh into")
        val updated = previous.copy(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            nonce = refreshed.nonce,
            keyPair = refreshed.keyPair,
        )
        val updatedJson = BlueskyJson.encodeToString(OAuthToken.serializer(), updated)
        context.updateStoredAccountOAuthToken(previous.subject.did, updatedJson)
        this.oauthToken = updated

        val newTokens = BlueskyAuthPlugin.Tokens.Dpop(
            auth = refreshed.accessToken,
            refresh = refreshed.refreshToken,
            pdsUrl = refreshed.pds,
            keyPair = refreshed.keyPair,
            clientId = refreshed.clientId,
            nonce = refreshed.nonce,
        )
        sharedAuthTokens?.value = newTokens
        val bumped = refreshGeneration.incrementAndGet()
        Log.d("BlueskyAuth", "refresh-done gen=$bumped token=${tokenTail(refreshed.accessToken)}")
    }

    /**
     * Handle a terminal refresh failure (`invalid_grant`): the refresh token has been
     * permanently rejected by the auth server. Tear down the in-memory session and remove
     * the now-dead account from DataStore. The subsequent [LoginException] return propagates
     * through `TimelineViewModel.handleError` → `sessionState.loginError`, which the login
     * screen observer routes to.
     */
    private suspend fun handleInvalidGrant(cause: Throwable) {
        Log.w("BlueskyAuth", "invalid_grant: signing out active account, cause=${summarizeAuthError(cause)}")
        val activeDid = session?.did?.did
        clearInMemorySession()
        pdsURL = null
        appviewProxy = null
        if (activeDid != null) {
            suspendRunCatching { context.removeStoredAccount(activeDid) }.onFailure {
                Log.w("BlueskyAuth", "Failed to remove stored account on invalid_grant: ${it.message}")
            }
        }
    }

    fun appviewName(): String {
        return when (appviewProxy) {
            "did:web:api.bsky.app#bsky_appview" -> "Bluesky"
            "did:web:api.blacksky.community#bsky_appview" -> "Blacksky"
            else -> appviewProxy?.substringAfter("did:web:")?.substringBefore("#") ?: "Unknown"
        }
    }

    // Label cache: maps "labelerDid:labelVal" -> display name/description, and labelerDid -> avatar URL
    private var labelDisplayNames: Map<String, String> = emptyMap()
    private var labelDescriptions: Map<String, String> = emptyMap()
    private var labelerAvatars: Map<String, String> = emptyMap()
    // Moderation metadata from label value definitions: "labelerDid:labelVal" -> moderation info
    private var labelSeverities: Map<String, LabelValueDefinitionSeverity> = emptyMap()
    private var labelBlurs: Map<String, LabelValueDefinitionBlurs> = emptyMap()
    private var labelDefaultSettings: Map<String, LabelValueDefinitionDefaultSetting> = emptyMap()
    // User-configured content label preferences (overrides labeler defaults)
    private var contentLabelPrefs: Map<String, ContentLabelPrefVisibility> = emptyMap()
    // Labeler display names: labelerDid -> display name
    private var labelerNames: Map<String, String> = emptyMap()
    private var labelCacheFetchCount: Int = 0
    private val labelCacheRefreshInterval: Int = 12

    fun labelDisplayName(label: Label): String? {
        val key = "${label.src.did}:${label.`val`}"
        return labelDisplayNames[key]
    }

    fun labelDescription(label: Label): String? {
        val key = "${label.src.did}:${label.`val`}"
        return labelDescriptions[key]
    }

    fun labelerAvatar(label: Label): String? {
        return labelerAvatars[label.src.did]
    }

    fun labelSeverity(label: Label): LabelValueDefinitionSeverity? {
        val key = "${label.src.did}:${label.`val`}"
        return labelSeverities[key]
    }

    fun labelBlurs(label: Label): LabelValueDefinitionBlurs? {
        val key = "${label.src.did}:${label.`val`}"
        return labelBlurs[key]
    }

    fun labelDefaultSetting(label: Label): LabelValueDefinitionDefaultSetting? {
        val key = "${label.src.did}:${label.`val`}"
        return labelDefaultSettings[key]
    }

    fun contentLabelPrefVisibility(label: Label): ContentLabelPrefVisibility? {
        val specificKey = "${label.src.did}:${label.`val`}"
        val globalKey = "_:${label.`val`}"
        return contentLabelPrefs[specificKey] ?: contentLabelPrefs[globalKey]
    }

    fun labelerName(label: Label): String {
        val did = label.src.did
        if (did == BSKY_MOD_SERVICE) return "Bluesky Moderation Service"
        return labelerNames[did] ?: did.run { substringAfter("did:web:").substringBefore("#") }
    }

    suspend fun refreshLabelCacheIfNeeded() {
        labelCacheFetchCount++
        if (labelCacheFetchCount < labelCacheRefreshInterval) return
        labelCacheFetchCount = 0
        subscribedLabelers().onSuccess { buildLabelCache(it) }
        getContentLabelPrefs().onSuccess { contentLabelPrefs = it }
    }

    private fun buildLabelCache(
        labelers: Map<Did?, GetServicesResponseViewUnion.LabelerViewDetailed?>
    ) {
        val names = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        val avatars = mutableMapOf<String, String>()
        val severities = mutableMapOf<String, LabelValueDefinitionSeverity>()
        val blurs = mutableMapOf<String, LabelValueDefinitionBlurs>()
        val defaultSettings = mutableMapOf<String, LabelValueDefinitionDefaultSetting>()
        val lblrNames = mutableMapOf<String, String>()
        for ((did, detailed) in labelers) {
            if (did == null || detailed == null) continue
            detailed.value.creator.displayName?.let { lblrNames[did.did] = it }
            detailed.value.creator.avatar?.let { avatars[did.did] = it.uri }
            for (defn in detailed.value.policies.labelValueDefinitions.orEmpty()) {
                val locale = defn.locales.firstOrNull() ?: continue
                val key = "${did.did}:${defn.identifier}"
                names[key] = locale.name
                if (locale.description.isNotEmpty()) descriptions[key] = locale.description
                severities[key] = defn.severity
                blurs[key] = defn.blurs
                defn.defaultSetting?.let { defaultSettings[key] = it }
            }
        }
        labelDisplayNames = names
        labelDescriptions = descriptions
        labelerAvatars = avatars
        labelSeverities = severities
        labelBlurs = blurs
        labelDefaultSettings = defaultSettings
        labelerNames = lblrNames
    }

    /** Reads [ContentLabelPref] entries from the user's account preferences and returns
     *  a lookup map keyed by "labelerDid:labelVal". Labels without a labeler DID use "_"
     *  as the did component for global matching. */
    suspend fun getContentLabelPrefs(): Result<Map<String, ContentLabelPrefVisibility>> {
        return suspendRunCatching {
            val client = pdsClient
            if (client == null) {
                return Result.success(emptyMap())
            }
            val prefs = client.getPreferencesForActor().requireResponse()
            val result = mutableMapOf<String, ContentLabelPrefVisibility>()
            for (pref in prefs.preferences) {
                if (pref !is PreferencesUnion.ContentLabelPref) continue
                val cp = pref.value
                val didPart = cp.labelerDid?.did ?: "_"
                val key = "$didPart:${cp.label}"
                result[key] = cp.visibility
            }
            Log.d("Moderation", "Loaded ${result.size} ContentLabelPrefs: $result")
            result
        }
    }

    suspend fun logout() {
        clearInMemorySession()
        pdsURL = null
        appviewProxy = null
    }

    fun resetClients() {
        clearInMemorySession()
        pdsURL = null
        appviewProxy = null
    }

    /**
     * Wire up an in-memory authenticated session from an already-resolved OAuth token. Used by
     * the OAuth login completion path (where the caller has just exchanged the auth code) and by
     * account switching (where the OAuth token comes from an already-stored account record).
     *
     * Seeds a shared [MutableStateFlow] of auth tokens and a shared [OAuthApi], then builds both
     * the appview client and the PDS-direct client against that shared state. Sets `session` and
     * `oauthToken`, and starts the token-refresh watcher so subsequent SDK-driven refreshes get
     * re-persisted to DataStore.
     */
    suspend fun initializeInMemory(
        pdsURL: String,
        appviewProxy: String,
        oauthToken: OAuthToken,
        handle: Handle,
        authServerURL: String? = null,
    ) {
        resetClients()
        this.pdsURL = pdsURL
        this.appviewProxy = appviewProxy
        this.oauthToken = oauthToken
        this.session = SessionData(
            handle = handle,
            did = oauthToken.subject,
            active = true,
        )

        // The OAuthApi resolves authorization-server metadata via a relative GET to
        // /.well-known/oauth-authorization-server. It MUST be pointed at the auth server,
        // not the PDS, otherwise discovery fails and refreshes throw.
        val resolvedAuthServerURL = if (!authServerURL.isNullOrBlank()) {
            authServerURL
        } else {
            authServerForPds(pdsURL).getOrNull() ?: pdsURL
        }

        // Seed the shared auth state. Both pdsClient and client will install a
        // BlueskyAuthPlugin that reads from this same flow, so a refresh from either side
        // propagates immediately to the other.
        val initialTokens = BlueskyAuthPlugin.Tokens.Dpop(
            auth = oauthToken.accessToken,
            refresh = oauthToken.refreshToken,
            pdsUrl = Url(pdsURL),
            keyPair = oauthToken.keyPair,
            clientId = oauthToken.clientId,
            nonce = oauthToken.nonce,
        )
        this.sharedAuthTokens = MutableStateFlow<BlueskyAuthPlugin.Tokens?>(initialTokens)
        this.sharedOAuthHttpClient = createRetryHttpClient(baseUrl = resolvedAuthServerURL)
        this.sharedOAuthApi = OAuthApi(
            httpClient = this.sharedOAuthHttpClient!!,
            challengeSelector = { OAuthCodeChallengeMethod.S256 },
        )

        this.pdsClient = mkClient(pdsURL)
        this.client = mkClient(pdsURL, appviewProxy = appviewProxy)
        launchTokenWatcher()
    }

    suspend fun hasSession(): Boolean {
        return context.readActiveStoredAccount() != null
    }

    /**
     * Begin an OAuth login flow. Resolves the user's PDS, builds an authorization request via
     * the PDS's auth server, persists the in-flight state to DataStore so the redirect callback
     * can verify it, and returns the URL the caller should open in a Custom Tab.
     */
    suspend fun oauthBeginLogin(
        handle: String,
        appviewProxy: String,
    ): Result<String> {
        return suspendRunCatching {
            val pdsURL = pdsForHandle(handle).getOrElse {
                return Result.failure(it)
            }

            val authServerURL = authServerForPds(pdsURL).getOrElse {
                return Result.failure(it)
            }

            val httpClient = createRetryHttpClient(baseUrl = authServerURL)
            val oauthApi = OAuthApi(
                httpClient = httpClient,
                challengeSelector = { OAuthCodeChallengeMethod.S256 },
            )

            try {
                val request = oauthApi.buildAuthorizationRequest(
                    oauthClient = OAuthClient(
                        clientId = OAUTH_CLIENT_ID,
                        redirectUri = OAUTH_REDIRECT_URI,
                    ),
                    scopes = listOf(OAuthScope.AtProto, OAuthScope.Generic),
                    loginHandleHint = handle,
                )

                context.dataStore.edit { settings ->
                    settings[OAUTH_IN_FLIGHT_STATE] = request.state
                    settings[OAUTH_IN_FLIGHT_VERIFIER] = request.codeVerifier
                    settings[OAUTH_IN_FLIGHT_NONCE] = request.nonce
                    settings[OAUTH_IN_FLIGHT_HANDLE] = handle
                    settings[OAUTH_IN_FLIGHT_PDS_URL] = pdsURL
                    settings[OAUTH_IN_FLIGHT_APPVIEW_PROXY] = appviewProxy
                    settings[OAUTH_IN_FLIGHT_AUTH_SERVER_URL] = authServerURL
                }

                Result.success(request.authorizeRequestUrl)
            } finally {
                httpClient.close()
            }
        }.getOrElse { Result.failure(it) }
    }

    /**
     * Complete an OAuth login. Called by the deep-link handler when the auth server redirects
     * back with `?code=&state=`. Verifies state, exchanges the code for an OAuthToken, builds a
     * one-shot OAuth client to fetch the user's profile, and returns a fully-populated
     * [StoredAccount] for the caller to persist via [AccountManager.addAccount]. The in-flight
     * OAuth state is cleared either way; on failure the caller stays on the login screen.
     */
    suspend fun oauthCompleteLogin(
        code: String,
        state: String,
    ): Result<StoredAccount> {
        return suspendRunCatching {
            val prefs = context.dataStore.data.first()
            val expectedState = prefs[OAUTH_IN_FLIGHT_STATE]
                ?: return Result.failure(Exception("No OAuth login in progress"))
            val verifier = prefs[OAUTH_IN_FLIGHT_VERIFIER]
                ?: return Result.failure(Exception("Missing OAuth code verifier"))
            val nonce = prefs[OAUTH_IN_FLIGHT_NONCE]
                ?: return Result.failure(Exception("Missing OAuth nonce"))
            val pdsURL = prefs[OAUTH_IN_FLIGHT_PDS_URL]
                ?: return Result.failure(Exception("Missing OAuth PDS URL"))
            val appviewProxy = prefs[OAUTH_IN_FLIGHT_APPVIEW_PROXY]
                ?: return Result.failure(Exception("Missing OAuth appview proxy"))
            val authServerURL = prefs[OAUTH_IN_FLIGHT_AUTH_SERVER_URL]
                ?: return Result.failure(Exception("Missing OAuth auth server URL"))

            if (state != expectedState) {
                clearInFlightOAuthState()
                return Result.failure(Exception("OAuth state mismatch (possible CSRF)"))
            }

            val httpClient = createRetryHttpClient(baseUrl = authServerURL)
            val oauthApi = OAuthApi(
                httpClient = httpClient,
                challengeSelector = { OAuthCodeChallengeMethod.S256 },
            )

            // Throwaway one-shot OAuth client for the initial profile lookup. Doesn't share
            // auth state with anything — it's used exactly once to resolve the handle and
            // then closed. Long-lived clients with shared auth state get built later in
            // initializeInMemory.
            val tempHc = createRetryHttpClient {
                defaultRequest {
                    url(pdsURL)
                    headers["atproto-proxy"] = appviewProxy
                }
            }

            try {
                val token = oauthApi.requestToken(
                    oauthClient = OAuthClient(
                        clientId = OAUTH_CLIENT_ID,
                        redirectUri = OAUTH_REDIRECT_URI,
                    ),
                    nonce = nonce,
                    codeVerifier = verifier,
                    code = code,
                )

                val tempClient = AuthenticatedXrpcBlueskyApi(httpClient = tempHc)
                    .also { it.activateOAuth(token) }
                val profileResp = tempClient.getProfile(GetProfileQueryParams(actor = token.subject))
                val profile = when (profileResp) {
                    is AtpResponse.Success<GetProfileResponse> -> profileResp.response
                    is AtpResponse.Failure<*> -> {
                        clearInFlightOAuthState()
                        return Result.failure(LoginException("Failed to resolve OAuth profile: ${profileResp.error?.message}"))
                    }
                }

                clearInFlightOAuthState()

                Log.d("BlueskyAuth", "OAuth login complete for ${profile.handle}")
                Result.success(
                    StoredAccount(
                        did = token.subject.did,
                        handle = profile.handle.handle,
                        displayName = profile.displayName,
                        avatarUrl = profile.avatar?.uri,
                        pdsHost = pdsURL,
                        appviewProxy = appviewProxy,
                        oauthTokenJson = BlueskyJson.encodeToString(OAuthToken.serializer(), token),
                        authServerURL = authServerURL,
                    )
                )
            } finally {
                httpClient.close()
                tempHc.close()
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun clearInFlightOAuthState() {
        context.dataStore.edit { settings ->
            settings.remove(OAUTH_IN_FLIGHT_STATE)
            settings.remove(OAUTH_IN_FLIGHT_VERIFIER)
            settings.remove(OAUTH_IN_FLIGHT_NONCE)
            settings.remove(OAUTH_IN_FLIGHT_HANDLE)
            settings.remove(OAUTH_IN_FLIGHT_PDS_URL)
            settings.remove(OAUTH_IN_FLIGHT_APPVIEW_PROXY)
            settings.remove(OAUTH_IN_FLIGHT_AUTH_SERVER_URL)
        }
    }

    /**
     * Build an OAuth-authenticated `BlueskyApi`. Installs `BlueskyAuthPlugin` manually with the
     * shared `sharedAuthTokens` state flow and `sharedOAuthApi`, so all clients built via this
     * method read and write the SAME auth state. This is critical for correctness: both the
     * pdsClient and the client must see refreshed tokens from each other, otherwise one of
     * them will try to use a rotated (invalidated) refresh token and the server will kill
     * the session.
     *
     * Requires `sharedAuthTokens` and `sharedOAuthApi` to have been set up by
     * [initializeInMemory] prior to this call — asserts non-null.
     */
    private fun mkClient(
        pds: String,
        labelers: List<String> = listOf(),
        appviewProxy: String? = null,
    ): BlueskyApi {
        val tokens = requireNotNull(sharedAuthTokens) {
            "mkClient called without sharedAuthTokens set — initializeInMemory must run first"
        }
        val oauthApi = requireNotNull(sharedOAuthApi) {
            "mkClient called without sharedOAuthApi set — initializeInMemory must run first"
        }
        val hc = createRetryHttpClient {
            defaultRequest {
                url(pds)
                headers["atproto-accept-labelers"] = labelers.joinToString { did ->
                    if (did == BSKY_MOD_SERVICE) "$did;redact" else did
                }
                if (appviewProxy != null) {
                    headers["atproto-proxy"] = appviewProxy
                }
            }
            install(BlueskyAuthPlugin) {
                this.authTokens = tokens
                this.oauthApi = oauthApi
            }
        }
        return XrpcBlueskyApi(hc)
    }

    /**
     * Watch the shared auth-tokens StateFlow. Whenever the SDK refreshes the DPoP-bound
     * access/refresh tokens (which happens transparently inside the auth plugin when the
     * server returns ExpiredToken or use_dpop_nonce), we mirror the change into
     * `this.oauthToken` and, if the access/refresh credentials themselves rotated, re-persist
     * to DataStore so the next process restart picks up the latest credentials.
     *
     * DPoP nonce rotations happen on every response and would flood DataStore with writes if
     * we persisted each one — they're intentionally skipped for persistence but still mirrored
     * in the in-memory `oauthToken` so subsequent client rebuilds see the latest nonce.
     */
    private fun launchTokenWatcher() {
        tokenWatcherJob?.cancel()
        val tokensFlow = sharedAuthTokens ?: return
        tokenWatcherJob = connScope.launch {
            tokensFlow.collect { tokens ->
                if (tokens !is BlueskyAuthPlugin.Tokens.Dpop) return@collect
                val previous = oauthToken ?: return@collect
                val accessChanged = previous.accessToken != tokens.auth
                val refreshChanged = previous.refreshToken != tokens.refresh
                val nonceChanged = previous.nonce != tokens.nonce
                if (!accessChanged && !refreshChanged && !nonceChanged) return@collect

                val updated = previous.copy(
                    accessToken = tokens.auth,
                    refreshToken = tokens.refresh,
                    nonce = tokens.nonce,
                    keyPair = tokens.keyPair,
                )
                this@BlueskyConn.oauthToken = updated

                if (accessChanged || refreshChanged) {
                    suspendRunCatching {
                        val updatedJson = BlueskyJson.encodeToString(OAuthToken.serializer(), updated)
                        context.updateStoredAccountOAuthToken(previous.subject.did, updatedJson)
                    }.onFailure {
                        Log.w("BlueskyAuth", "Failed to persist refreshed OAuth token: ${it.message}")
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> apiCall(
        errorMsg: String,
        crossinline block: suspend () -> AtpResponse<T>
    ): Result<T> = suspendRunCatching {
        create().getOrThrow()
        when (val ret = block()) {
            is AtpResponse.Failure<*> -> throw ApiCallFailure(errorMsg, ret.asException())
            is AtpResponse.Success -> ret.response
        }
    }

    private suspend inline fun <reified T : Any> publicApiCall(
        errorMsg: String,
        crossinline block: suspend () -> AtpResponse<T>
    ): Result<T> = suspendRunCatching {
        when (val ret = block()) {
            is AtpResponse.Failure<*> -> throw ApiCallFailure(errorMsg, ret.asException())
            is AtpResponse.Success -> ret.response
        }
    }

    private suspend fun uploadImageBlob(uri: Uri, label: String): Blob {
        val compressor = Compressor(context)
        val compressed = compressor.compressImage(uri, MAX_IMAGE_SIZE_BYTES)
        val uploaded = pdsClient!!.uploadBlob(compressed.data)
        return when (uploaded) {
            is AtpResponse.Failure<*> -> throw Exception("Failed uploading $label: ${uploaded.error}")
            is AtpResponse.Success<UploadBlobResponse> -> uploaded.response.blob
        }
    }

    suspend fun create(): Result<Unit> {
        createMutex.lock()
        try {
            val currentToken = oauthToken
            if (client != null && pdsClient != null && pdsURL != null && currentToken != null) {
                val exp = accessTokenExp(currentToken.accessToken)
                if (exp != null && Instant.fromEpochSeconds(exp) > Clock.System.now().plus(Duration.parse("60s"))) {
                    Log.d("BlueskyAuth", "create: session already active and token valid, skipping initialization")
                    return Result.success(Unit)
                }
                Log.d("BlueskyAuth", "create: session active but token expired or near expiry, re-initializing")
                clearInMemorySession()
                pdsURL = null
                appviewProxy = null
            }

            Log.d("Bluesky", "create called without session or client")
            val account = context.readActiveStoredAccount()
                ?: return Result.failure(Exception("No active account"))

            val token = suspendRunCatching {
                BlueskyJson.decodeFromString(OAuthToken.serializer(), account.oauthTokenJson)
            }.getOrElse {
                return Result.failure(LoginException("Failed to deserialize OAuth token: ${it.message}"))
            }

            var authServerURL = account.authServerURL
            if (authServerURL.isNullOrBlank()) {
                authServerURL = authServerForPds(account.pdsHost).getOrElse {
                    return Result.failure(LoginException("Failed to resolve auth server: ${it.message}"))
                }
                suspendRunCatching {
                    context.updateStoredAccountAuthServerURL(account.did, authServerURL)
                }.onFailure {
                    Log.w("BlueskyAuth", "Failed to persist auth server URL: ${it.message}")
                }
            }

            val exp = accessTokenExp(token.accessToken)
            val needsRefresh = exp == null || Instant.fromEpochSeconds(exp) <= Clock.System.now().plus(Duration.parse("60s"))
            val freshToken = if (needsRefresh) {
                Log.d("BlueskyAuth", "create: token expired or near expiry, proactively refreshing")
                val tempOAuthClient = createRetryHttpClient(baseUrl = authServerURL)
                val tempOAuthApi = OAuthApi(
                    httpClient = tempOAuthClient,
                    challengeSelector = { OAuthCodeChallengeMethod.S256 },
                )
                try {
                    val refreshed = tempOAuthApi.refreshToken(
                        clientId = token.clientId,
                        nonce = token.nonce,
                        refreshToken = token.refreshToken,
                        keyPair = token.keyPair,
                    )
                    val updated = token.copy(
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        nonce = refreshed.nonce,
                        keyPair = refreshed.keyPair,
                    )
                    val updatedJson = BlueskyJson.encodeToString(OAuthToken.serializer(), updated)
                    context.updateStoredAccountOAuthToken(account.did, updatedJson)
                    Log.d("BlueskyAuth", "create: proactive refresh succeeded token=${tokenTail(updated.accessToken)}")
                    updated
                } catch (e: Throwable) {
                    Log.w("BlueskyAuth", "create: proactive refresh failed: ${e::class.simpleName}: ${e.message}")
                    return Result.failure(LoginException("Session expired: ${e.message}"))
                } finally {
                    tempOAuthClient.close()
                }
            } else token

            initializeInMemory(
                pdsURL = account.pdsHost,
                appviewProxy = account.appviewProxy,
                oauthToken = freshToken,
                handle = Handle(account.handle),
                authServerURL = authServerURL,
            )

            // Create client first so `subscribedLabelers()` can use it for `getServices()`.
            this.client = mkClient(account.pdsHost, listOf(), appviewProxy = account.appviewProxy)

            // Hydrate the labeler cache and rebuild `client` with the labelers header so future
            // appview queries respect the user's subscribed labelers. The rebuilt client still
            // shares `sharedAuthTokens` with `pdsClient` because `mkClient` installs the plugin
            // with the connection-level shared state, so no watcher re-arming is needed.
            val labelerMap = runCatching {
                this.subscribedLabelers().getOrDefault(emptyMap())
            }.getOrElse {
                Log.w("BlueskyAuth", "create: labeler cache hydration failed: ${it.message}")
                emptyMap()
            }
            buildLabelCache(labelerMap)
            getContentLabelPrefs().onSuccess { contentLabelPrefs = it }
            labelCacheFetchCount = 0
            val labelers = labelerMap.keys.mapNotNull { it?.did }.toMutableList()
            if (BSKY_MOD_SERVICE !in labelers) {
                labelers.add(0, BSKY_MOD_SERVICE)
            }
            this.client = mkClient(account.pdsHost, labelers, appviewProxy = account.appviewProxy)

            Log.d("BlueskyAuth", "Clients initialized: pdsClient=${this.pdsClient != null}, client=${this.client != null}, handle=${account.handle}")
            return Result.success(Unit)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.d("BlueskyAuth", "create() failed: ${e::class.simpleName}: ${e.message}")
            return Result.failure(e)
        } finally {
            createMutex.unlock()
        }
    }

    suspend fun fetchFeed(feed: String, cursor: String? = null): Result<Timeline> = retryOnDpopHiccup {
        apiCall("Failed to fetch feed") {
            client!!.getFeed(GetFeedQueryParams(feed = AtUri(feed), limit = 25, cursor = cursor))
        }.map { Timeline(it.cursor, it.feed) }
    }

    suspend fun fetchTimeline(cursor: String? = null): Result<Timeline> = retryOnDpopHiccup {
        apiCall("Failed to fetch timeline") {
            client!!.getTimeline(GetTimelineQueryParams(limit = 25, cursor = cursor))
        }.map { Timeline(it.cursor, it.feed) }
    }

    suspend fun post(
        content: String,
        images: List<Uri>? = null,
        video: Uri? = null,
        mediaAltTexts: Map<Uri, String> = emptyMap(),
        replyRef: PostReplyRef? = null,
        quotePostRef: StrongRef? = null,
        facets: List<Facet> = listOf(),
        linkPreview: LinkPreviewData? = null,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
        onVideoStatus: (VideoUploadStatus, Long?) -> Unit = { _, _ -> },
    ): Result<AtUri> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            var mediaUnion: RecordWithMediaMediaUnion? = null

            if (images != null) {
                val blobs = uploadImages(images).getOrThrow()
                mediaUnion = RecordWithMediaMediaUnion.Images(
                    value = Images(
                        images = blobs.mapIndexed { index, uploaded ->
                            ImagesImage(
                                image = uploaded.blob,
                                alt = mediaAltTexts[images[index]].orEmpty(),
                                aspectRatio = AspectRatio(uploaded.width, uploaded.height)
                            )
                        }
                    )
                )
            }

            if (video != null) {
                val blob = uploadVideo(video, onVideoStatus).getOrThrow()
                mediaUnion = RecordWithMediaMediaUnion.Video(
                    value = Video(
                        video = blob.blob,
                        alt = mediaAltTexts[video].orEmpty(),
                        aspectRatio = AspectRatio(blob.width, blob.height)
                    )
                )
            }

            if (mediaUnion == null && linkPreview != null) {
                var thumbBlob: Blob? = null
                if (linkPreview.imageUrl != null) {
                    try {
                        thumbBlob = uploadBlobFromUrl(linkPreview.imageUrl)
                    } catch (_: Exception) {
                        // Thumbnail upload failed, proceed without it
                    }
                }
                mediaUnion = RecordWithMediaMediaUnion.External(
                    value = External(
                        external = ExternalExternal(
                            uri = sh.christian.ozone.api.Uri(linkPreview.url),
                            title = linkPreview.title ?: "",
                            description = linkPreview.description ?: "",
                            thumb = thumbBlob,
                        )
                    )
                )
            }

            val postEmbed: PostEmbedUnion? = when {
                quotePostRef != null && mediaUnion != null -> PostEmbedUnion.RecordWithMedia(
                    value = RecordWithMedia(
                        record = Record(quotePostRef),
                        media = mediaUnion,
                    )
                )
                quotePostRef != null -> PostEmbedUnion.Record(
                    value = Record(quotePostRef)
                )
                mediaUnion is RecordWithMediaMediaUnion.Images -> PostEmbedUnion.Images(
                    value = mediaUnion.value
                )
                mediaUnion is RecordWithMediaMediaUnion.Video -> PostEmbedUnion.Video(
                    value = mediaUnion.value
                )
                mediaUnion is RecordWithMediaMediaUnion.External -> PostEmbedUnion.External(
                    value = mediaUnion.value
                )
                else -> null
            }

            val r = BlueskyJson.encodeAsJsonContent(
                Post(
                    text = content,
                    createdAt = Clock.System.now(),
                    embed = postEmbed,
                    reply = replyRef,
                    facets = facets,
                )
            )

            val postRes = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle, // Use handle from the session
                    collection = Nsid("app.bsky.feed.post"),
                    record = r,
                )
            )
            return when (postRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not create post: ${postRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> {
                    if (threadgateRules != null) {
                        createThreadgate(postRes.response.uri, threadgateRules)
                    }
                    Result.success(postRes.response.uri)
                }
            }
        }
    }

    private suspend fun createThreadgate(postUri: AtUri, rules: List<ThreadgateAllowUnion>) {
        val record = BlueskyJson.encodeAsJsonContent(
            Threadgate(
                post = postUri,
                allow = rules,
                createdAt = Clock.System.now(),
            )
        )
        pdsClient!!.createRecord(
            CreateRecordRequest(
                repo = session!!.handle,
                collection = Nsid("app.bsky.feed.threadgate"),
                record = record,
                rkey = postUri.rkey(),
            )
        )
    }

    suspend fun postThread(
        posts: List<ThreadPostData>,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onVideoStatus: (VideoUploadStatus, Long?) -> Unit = { _, _ -> },
    ): Result<List<PostResult>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            if (posts.isEmpty()) {
                return Result.failure(Exception("Thread must have at least one post"))
            }

            val results = mutableListOf<PostResult>()
            var rootRef: StrongRef? = null

            for ((index, postData) in posts.withIndex()) {
                onProgress(index + 1, posts.size)

                var mediaUnion: RecordWithMediaMediaUnion? = null

                if (postData.images != null) {
                    val blobs = uploadImages(postData.images).getOrThrow()
                    mediaUnion = RecordWithMediaMediaUnion.Images(
                        value = Images(
                            images = blobs.mapIndexed { i, uploaded ->
                                ImagesImage(
                                    image = uploaded.blob,
                                    alt = postData.mediaAltTexts[postData.images[i]].orEmpty(),
                                    aspectRatio = AspectRatio(uploaded.width, uploaded.height)
                                )
                            }
                        )
                    )
                }

                if (postData.video != null) {
                    val blob = uploadVideo(postData.video, onVideoStatus).getOrThrow()
                    mediaUnion = RecordWithMediaMediaUnion.Video(
                        value = Video(
                            video = blob.blob,
                            alt = postData.mediaAltTexts[postData.video].orEmpty(),
                            aspectRatio = AspectRatio(blob.width, blob.height)
                        )
                    )
                }

                if (mediaUnion == null && postData.linkPreview != null) {
                    var thumbBlob: Blob? = null
                    if (postData.linkPreview.imageUrl != null) {
                        try {
                            thumbBlob = uploadBlobFromUrl(postData.linkPreview.imageUrl)
                        } catch (_: Exception) {
                        }
                    }
                    mediaUnion = RecordWithMediaMediaUnion.External(
                        value = External(
                            external = ExternalExternal(
                                uri = sh.christian.ozone.api.Uri(postData.linkPreview.url),
                                title = postData.linkPreview.title ?: "",
                                description = postData.linkPreview.description ?: "",
                                thumb = thumbBlob,
                            )
                        )
                    )
                }

                val postEmbed: PostEmbedUnion? = when {
                    mediaUnion is RecordWithMediaMediaUnion.Images -> PostEmbedUnion.Images(
                        value = mediaUnion.value
                    )
                    mediaUnion is RecordWithMediaMediaUnion.Video -> PostEmbedUnion.Video(
                        value = mediaUnion.value
                    )
                    mediaUnion is RecordWithMediaMediaUnion.External -> PostEmbedUnion.External(
                        value = mediaUnion.value
                    )
                    else -> null
                }

                val replyRef: PostReplyRef? = if (results.isNotEmpty()) {
                    val previousPost = results.last()
                    PostReplyRef(
                        root = rootRef!!,
                        parent = StrongRef(previousPost.uri, previousPost.cid)
                    )
                } else null

                val r = BlueskyJson.encodeAsJsonContent(
                    Post(
                        text = postData.text,
                        createdAt = Clock.System.now(),
                        embed = postEmbed,
                        reply = replyRef,
                        facets = postData.facets,
                    )
                )

                val postRes = pdsClient!!.createRecord(
                    CreateRecordRequest(
                        repo = session!!.handle,
                        collection = Nsid("app.bsky.feed.post"),
                        record = r,
                    )
                )

                when (postRes) {
                    is AtpResponse.Failure<*> -> {
                        return Result.failure(Exception("Failed to post part ${index + 1}: ${postRes.error?.message}"))
                    }
                    is AtpResponse.Success<CreateRecordResponse> -> {
                        val result = PostResult(postRes.response.uri, postRes.response.cid)
                        results.add(result)
                        if (index == 0) {
                            rootRef = StrongRef(result.uri, result.cid)
                            if (threadgateRules != null) {
                                createThreadgate(result.uri, threadgateRules)
                            }
                        }
                    }
                }
            }

            Result.success(results)
        }.getOrElse { Result.failure(it) }
    }

    suspend fun fetchRecord(uri: AtUri): Result<JsonContent> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = pdsClient!!.getRecord(
                GetRecordQueryParams(
                    repo = uri.did(),
                    collection = uri.collection(),
                    rkey = uri.rkey()
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed fetching record: ${ret.error}"))
                is AtpResponse.Success<GetRecordResponse> -> Result.success(ret.response.value)
            }
        }
    }

    private val pdsCache = mutableMapOf<String, String>()

    private suspend fun listRecordsFromRepo(did: Did, collection: String): Result<List<com.atproto.repo.ListRecordsRecord>> {
        val pds = pdsCache.getOrPut(did.did) {
            pdsForHandle(did.did).getOrElse { return Result.failure(it) }
        }
        val httpClient = createRetryHttpClient(baseUrl = pds)
        val api = XrpcBlueskyApi(httpClient)
        return try {
            val ret = api.listRecords(
                ListRecordsQueryParams(
                    repo = did as AtIdentifier,
                    collection = Nsid(collection),
                    limit = 100,
                )
            )
            when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed listing $collection: ${ret.error}"))
                is AtpResponse.Success<ListRecordsResponse> -> Result.success(ret.response.records)
            }
        } finally {
            httpClient.close()
        }
    }

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String? =
        get(key)?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

    private fun parseContentBlocks(content: kotlinx.serialization.json.JsonElement?, did: Did? = null): List<ContentBlock> {
        if (content == null) return emptyList()
        val blocks = mutableListOf<ContentBlock>()

        fun processBlock(block: kotlinx.serialization.json.JsonObject) {
            val type = block.str("\$type") ?: ""
            val plaintext = block.str("plaintext") ?: ""
            when {
                type.contains("header", true) -> {
                    val level = block["level"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 1
                    if (plaintext.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.HEADING, plaintext, level))
                }
                type.contains("text", true) && !type.contains("list", true) -> {
                    if (plaintext.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.PARAGRAPH, plaintext))
                }
                type.contains("blockquote", true) -> {
                    if (plaintext.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.BLOCKQUOTE, plaintext))
                }
                type.contains("code", true) -> {
                    blocks.add(ContentBlock(ContentBlockType.CODE, plaintext))
                }
                type.contains("image", true) -> {
                    val alt = block.str("alt") ?: ""
                    val imageBlob = block["image"] as? kotlinx.serialization.json.JsonObject
                    val blobRef = imageBlob?.let { img ->
                        val ref = img["ref"] as? kotlinx.serialization.json.JsonObject
                        ref?.str("\$link")
                    }
                    val imageUrl = if (blobRef != null && did != null) {
                        "https://cdn.bsky.app/img/feed_fullsize/plain/${did.did}/$blobRef@jpeg"
                    } else null
                    blocks.add(ContentBlock(ContentBlockType.IMAGE, alt, imageUrl = imageUrl))
                }
                type.contains("unorderedList", true) || type.contains("orderedList", true) -> {
                    val children = block["children"] as? kotlinx.serialization.json.JsonArray ?: return
                    children.forEach { item ->
                        val itemObj = item as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val itemContent = itemObj["content"] as? kotlinx.serialization.json.JsonObject
                        val itemText = itemContent?.str("plaintext") ?: itemObj.str("plaintext") ?: ""
                        if (itemText.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.LIST_ITEM, itemText))
                    }
                }
                type.contains("horizontalRule", true) -> {
                    blocks.add(ContentBlock(ContentBlockType.HORIZONTAL_RULE))
                }
                type.contains("website", true) -> {
                    val src = block.str("src") ?: ""
                    val title = block.str("title") ?: ""
                    val description = block.str("description")
                    blocks.add(ContentBlock(ContentBlockType.WEBSITE, linkUrl = src, linkTitle = title, linkDescription = description))
                }
                type.contains("iframe", true) -> {
                    val url = block.str("url") ?: ""
                    if (url.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.LINK, linkUrl = url, linkTitle = "Open in browser"))
                }
                type.contains("bskyPost", true) -> {
                    val postRef = block["postRef"] as? kotlinx.serialization.json.JsonObject
                    val uri = postRef?.str("uri") ?: ""
                    if (uri.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.LINK, linkUrl = uri, linkTitle = "Bluesky post"))
                }
                else -> {
                    if (plaintext.isNotBlank()) blocks.add(ContentBlock(ContentBlockType.PARAGRAPH, plaintext))
                }
            }
        }

        val contentObj = content as? kotlinx.serialization.json.JsonObject ?: return emptyList()
        val pages = contentObj["pages"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()

        for (page in pages) {
            val pageObj = page as? kotlinx.serialization.json.JsonObject ?: continue
            val pageBlocks = pageObj["blocks"] as? kotlinx.serialization.json.JsonArray ?: continue
            for (entry in pageBlocks) {
                val entryObj = entry as? kotlinx.serialization.json.JsonObject ?: continue
                val block = entryObj["block"] as? kotlinx.serialization.json.JsonObject
                if (block != null) {
                    processBlock(block)
                }
            }
        }

        return blocks
    }

    suspend fun listPublications(did: Did): Result<List<PublicationRecord>> {
        return listRecordsFromRepo(did, "site.standard.publication").map { records ->
            records.mapNotNull { record ->
                suspendRunCatching {
                    val obj = record.value.value as kotlinx.serialization.json.JsonObject
                    PublicationRecord(
                        uri = record.uri,
                        cid = record.cid,
                        publication = StandardPublication(
                            name = obj.str("name") ?: return@mapNotNull null,
                            url = obj.str("url"),
                            description = obj.str("description"),
                        ),
                    )
                }.getOrNull()
            }
        }
    }

    suspend fun listDocuments(did: Did): Result<List<DocumentRecord>> {
        return listRecordsFromRepo(did, "site.standard.document").map { records ->
            records.mapNotNull { record ->
                suspendRunCatching {
                    val obj = record.value.value as kotlinx.serialization.json.JsonObject
                    val tagsArray = obj["tags"] as? kotlinx.serialization.json.JsonArray
                    val contentBlocks = parseContentBlocks(obj["content"], did)
                    val textContent = obj.str("textContent")
                        ?: contentBlocks.joinToString("\n\n") { it.text }.ifBlank { null }
                    DocumentRecord(
                        uri = record.uri,
                        cid = record.cid,
                        authorDid = did,
                        document = StandardDocument(
                            title = obj.str("title") ?: return@mapNotNull null,
                            path = obj.str("path"),
                            description = obj.str("description"),
                            textContent = textContent,
                            contentBlocks = contentBlocks,
                            publishedAt = obj.str("publishedAt"),
                            updatedAt = obj.str("updatedAt"),
                            site = obj.str("site"),
                            tags = tagsArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList(),
                        ),
                    )
                }.getOrNull()
            }
        }
    }

    suspend fun fetchActor(did: Did): Result<ProfileViewDetailed> = retryOnDpopHiccup {
        apiCall("Failed fetching profile") {
            client!!.getProfile(GetProfileQueryParams(actor = did))
        }
    }

    suspend fun fetchSelf(): Result<ProfileViewDetailed> {
        create().onFailure { return Result.failure(it) }
        return fetchActor(session!!.did)
    }


    private suspend fun uploadBlobFromUrl(imageUrl: String): Blob? {
        val httpClient = createRetryHttpClient()
        try {
            val response = httpClient.get(imageUrl)
            if (!response.status.isSuccess()) return null
            val bytes: ByteArray = response.body()
            val uploadResponse = pdsClient!!.uploadBlob(bytes)
            return when (uploadResponse) {
                is AtpResponse.Failure<*> -> null
                is AtpResponse.Success<UploadBlobResponse> -> uploadResponse.response.blob
            }
        } finally {
            httpClient.close()
        }
    }
    private data class MediaBlob(
        val blob: Blob,
        val width: Long,
        val height: Long,
    )

    private suspend fun uploadImages(images: List<Uri>): Result<List<MediaBlob>> {
        val maxImageSize = MAX_IMAGE_SIZE_BYTES

        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val uploadedBlobs = mutableListOf<MediaBlob>()

            val compressor = Compressor(context)

            images.forEach {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val compressedImage = run {
                        inputStream.mark(0)
                        val c = compressor.compressImage(it, maxImageSize)
                        return@run c
                    }

                    val blob = pdsClient!!.uploadBlob(compressedImage.data)
                    when (blob) {
                        is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed uploading image: ${blob.error}"))
                        is AtpResponse.Success<UploadBlobResponse> -> {
                            uploadedBlobs.add(
                                MediaBlob(
                                    blob = blob.response.blob,
                                    width = compressedImage.width,
                                    height = compressedImage.height
                                )
                            )
                        }
                    }
                }
            }

            return Result.success(uploadedBlobs)
        }
    }

    private suspend fun uploadVideo(video: Uri, onStatus: (VideoUploadStatus, Long?) -> Unit): Result<MediaBlob> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            onStatus(VideoUploadStatus.Compressing, null)
            val compressedUri = Compressor(context).compressVideo(video) ?: video

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, compressedUri)
            val width =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
            val rotation =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0

            val dimensions = if (rotation == 90 || rotation == 270) {
                Pair(height, width)
            } else {
                Pair(width, height)
            }
            retriever.release()

            val uploadedBlobs = mutableListOf<Blob>()

            val host = pdsURL?.toUri()?.host
                ?: return Result.failure(Exception("PDS URL or host not available"))
            val did = Did("did:web:$host")

            val uploadVideoTicket = pdsClient!!.getServiceAuth(
                GetServiceAuthQueryParams(
                    aud = did,
                    exp = Clock.System.now().plus(Duration.parse("30m")).epochSeconds,
                    lxm = Nsid("com.atproto.repo.uploadBlob"),
                )
            )

            val serviceAuth = when (uploadVideoTicket) {
                is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed requesting upload ticket: ${uploadVideoTicket.error}"))
                is AtpResponse.Success<GetServiceAuthResponse> -> uploadVideoTicket.response.token
            }

            val httpClient = createRetryHttpClient(
                baseUrl = "https://video.bsky.app",
                requestTimeout = VIDEO_UPLOAD_TIMEOUT_MS,
                socketTimeout = VIDEO_UPLOAD_TIMEOUT_MS,
            ) {
                install(ContentNegotiation) {
                    register(
                        ContentType.Any, KotlinxSerializationConverter(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                            }
                        )
                    )
                }
            }

            val videoBskyAppClient = AuthenticatedXrpcBlueskyApi(
                httpClient,
                BlueskyAuthPlugin.Tokens.Bearer(serviceAuth, serviceAuth)
            )

            onStatus(VideoUploadStatus.Uploading, null)
            val uploadRes = context.contentResolver.openInputStream(compressedUri)?.use { inputStream ->
                val byteArray = inputStream.readBytes()

                val rs = httpClient.post {
                    headers["Authorization"] = "Bearer $serviceAuth"
                    headers["Content-Type"] = "video/mp4"
                    headers["Content-Length"] = byteArray.size.toString()
                    url {
                        protocol = URLProtocol.HTTPS
                        path("/xrpc/app.bsky.video.uploadVideo")
                        parameters.append("did", session!!.did.did)
                        parameters.append(
                            "name",
                            "video_upload_${Clock.System.now().toEpochMilliseconds()}"
                        )
                    }
                    setBody(byteArray)
                }


                when (rs.status) {
                    HttpStatusCode.OK, HttpStatusCode.Conflict -> {
                        val bodyText = rs.bodyAsText()
                        val json = Json { ignoreUnknownKeys = true }
                        try {
                            return@use json.decodeFromString<UploadVideoResponse>(bodyText).jobStatus
                        } catch (_: Exception) {
                            return@use json.decodeFromString<JobStatus>(bodyText)
                        }
                    }

                    else -> {
                        httpClient.close()
                        return Result.failure(Exception("Failed uploading video: status code ${rs.status}"))
                    }
                }
            }

            onStatus(VideoUploadStatus.Processing, null)
            while (true) {
                try {
                    val response =
                        videoBskyAppClient.getJobStatus(GetJobStatusQueryParams(uploadRes!!.jobId))

                    val resp = when (response) {
                        is AtpResponse.Failure<*> -> {
                            httpClient.close()
                            return Result.failure(
                                Exception("Failed video processing job status check: ${response.error}")
                            )
                        }

                        is AtpResponse.Success<GetJobStatusResponse> -> response.response.jobStatus
                    }

                    onStatus(VideoUploadStatus.Processing, resp.progress)

                    if (resp.blob != null) {
                        uploadedBlobs.add(resp.blob!!)
                        break
                    }

                    when (resp.state) {
                        JobStatusState.JOBSTATECOMPLETED -> {}
                        JobStatusState.JOBSTATEFAILED -> {
                            httpClient.close()
                            return Result.failure(Exception("Video processing failed, ${resp.error}: ${resp.message}"))
                        }
                        is JobStatusState.Unknown -> delay(1000)
                    }
                } catch (e: Exception) {
                    httpClient.close()
                    return Result.failure(e)
                }
            }


            httpClient.close()
            return Result.success(
                MediaBlob(
                    blob = uploadedBlobs.first(),
                    width = dimensions.first.toLong(),
                    height = dimensions.second.toLong()
                )
            )
        }
    }

    data class CachedFeed(val uri: String, val name: String, val avatar: String?)

    suspend fun getCachedFeedOrder(): List<String> {
        return context.dataStore.data.first()[CACHED_FEED_ORDER]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: listOf("following")
    }

    suspend fun getCachedFeedMetadata(): List<CachedFeed> {
        val raw = context.dataStore.data.first()[CACHED_FEED_METADATA] ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size >= 2) {
                CachedFeed(parts[0], parts[1], parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            } else null
        }
    }

    private suspend fun saveCachedFeedOrder(uris: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[CACHED_FEED_ORDER] = uris.joinToString(",")
        }
    }

    private suspend fun saveCachedFeedMetadata(feeds: List<GeneratorView>) {
        context.dataStore.edit { prefs ->
            prefs[CACHED_FEED_METADATA] = feeds.joinToString("\n") { feed ->
                "${feed.uri.atUri}\t${feed.displayName}\t${feed.avatar?.uri ?: ""}"
            }
        }
    }

    suspend fun orderedFeedUris(): Result<List<String>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val savedFeeds = prefs.preferences.firstOrNull {
                it is PreferencesUnion.SavedFeedsPrefV2
            } as? PreferencesUnion.SavedFeedsPrefV2

            if (savedFeeds == null) {
                val default = listOf("following")
                saveCachedFeedOrder(default)
                return Result.success(default)
            }

            val orderedUris = savedFeeds.value.items.filter {
                (it.type is SavedFeedType.Feed && it.value.startsWith("at://")) ||
                it.type is SavedFeedType.Timeline
            }.map { it.value }

            if (orderedUris.isEmpty()) {
                val default = listOf("following")
                saveCachedFeedOrder(default)
                return Result.success(default)
            }

            saveCachedFeedOrder(orderedUris)
            return Result.success(orderedUris)
        }
    }

    suspend fun feeds(): Result<List<GeneratorView>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val savedFeeds = prefs.preferences.firstOrNull {
                it is PreferencesUnion.SavedFeedsPrefV2
            } as? PreferencesUnion.SavedFeedsPrefV2

            if (savedFeeds == null) {
                return Result.success(emptyList())
            }

            val feedUris = savedFeeds.value.items.filter {
                it.type is SavedFeedType.Feed && it.value.startsWith("at://")
            }.map { AtUri(it.value) }

            if (feedUris.isEmpty()) {
                return Result.success(emptyList())
            }

            val batch = suspendRunCatching {
                client!!.getFeedGenerators(
                    GetFeedGeneratorsQueryParams(feedUris)
                )
            }.getOrNull()

            if (batch is AtpResponse.Success) {
                val feedMap = batch.response.feeds.associateBy { it.uri.atUri }
                val orderedFeeds = feedUris.mapNotNull { feedMap[it.atUri] }
                saveCachedFeedMetadata(orderedFeeds)
                return Result.success(orderedFeeds)
            }

            val resolved = coroutineScope {
                feedUris.map { uri ->
                    async {
                        suspendRunCatching {
                            client!!.getFeedGenerator(
                                GetFeedGeneratorQueryParams(uri)
                            )
                        }.getOrNull()?.let { resp ->
                            (resp as? AtpResponse.Success)?.response
                        }
                    }
                }.awaitAll()
            }.mapNotNull { it?.takeIf { r -> r.isValid }?.view }

            val resolvedMap = resolved.associateBy { it.uri.atUri }
            val orderedFeeds = feedUris.mapNotNull { resolvedMap[it.atUri] }
            saveCachedFeedMetadata(orderedFeeds)
            return Result.success(orderedFeeds)
        }
    }

    suspend fun reorderFeeds(newOrder: List<String>): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val savedFeeds = prefs.preferences.firstOrNull {
                it is PreferencesUnion.SavedFeedsPrefV2
            } as? PreferencesUnion.SavedFeedsPrefV2

            if (savedFeeds == null) {
                return Result.success(Unit)
            }

            val existingItems = savedFeeds.value.items.toMutableList()
            val orderMap = newOrder.withIndex().associate { it.value to it.index }
            existingItems.sortBy { item ->
                orderMap[item.value] ?: Int.MAX_VALUE
            }

            val updatedPrefs = prefs.preferences.toMutableList()
            val existingIndex = updatedPrefs.indexOfFirst { it is PreferencesUnion.SavedFeedsPrefV2 }
            val newPref = PreferencesUnion.SavedFeedsPrefV2(
                SavedFeedsPrefV2(items = existingItems)
            )
            if (existingIndex >= 0) {
                updatedPrefs[existingIndex] = newPref
            } else {
                updatedPrefs.add(newPref)
            }
            pdsClient!!.putPreferences(PutPreferencesRequest(preferences = updatedPrefs)).requireResponse()
        }
    }

    suspend fun subscribedLabelers(): Result<Map<Did?, GetServicesResponseViewUnion.LabelerViewDetailed?>> {
        return suspendRunCatching {
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val labelersPref = prefs.preferences.firstOrNull {
                it is PreferencesUnion.LabelersPref
            } as? PreferencesUnion.LabelersPref

            val labelers = (labelersPref?.value?.labelers?.map { it.did } ?: emptyList()).toMutableList()
            // Always include the official Bluesky moderation labeler so we get its label
            // definitions (display names, severities, etc.) for the label cache.
            if (Did(BSKY_MOD_SERVICE) !in labelers) {
                labelers.add(0, Did(BSKY_MOD_SERVICE))
            }

            val res = client!!.getServices(
                GetServicesQueryParams(detailed = true, dids = labelers)
            )

            val response = when (res) {
                is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed to fetch subscribed labelers: ${res.error}"))
                is AtpResponse.Success<GetServicesResponse> -> res
            }

            val labelerMap = response.response.views.associate {
                when (it) {
                    is GetServicesResponseViewUnion.LabelerView -> it.value.uri.did() to null
                    is GetServicesResponseViewUnion.LabelerViewDetailed -> it.value.uri.did() to it
                    is GetServicesResponseViewUnion.Unknown -> null to null
                }
            }.filter { it.value != null && it.key != null }

            return Result.success(labelerMap)
        }
    }

    suspend fun getMutedWords(): Result<List<MutedWord>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val mutedWordsPref = prefs.preferences.firstOrNull {
                it is PreferencesUnion.MutedWordsPref
            } as? PreferencesUnion.MutedWordsPref

            return Result.success(mutedWordsPref?.value?.items ?: emptyList())
        }
    }

    suspend fun setMutedWords(words: List<MutedWord>): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val updatedPrefs = prefs.preferences.toMutableList()
            val existingIndex = updatedPrefs.indexOfFirst { it is PreferencesUnion.MutedWordsPref }
            val newPref = PreferencesUnion.MutedWordsPref(MutedWordsPref(items = words))
            if (existingIndex >= 0) {
                updatedPrefs[existingIndex] = newPref
            } else {
                updatedPrefs.add(newPref)
            }
            pdsClient!!.putPreferences(PutPreferencesRequest(preferences = updatedPrefs)).requireResponse()
        }
    }

    private fun prefType(pref: PreferencesUnion): String? {
        return when (pref) {
            is PreferencesUnion.Unknown -> {
                val raw = pref.value.value
                if (raw is kotlinx.serialization.json.JsonObject) {
                    raw["\$type"]?.let { type ->
                        if (type is kotlinx.serialization.json.JsonPrimitive) type.content else null
                    }
                } else null
            }
            else -> null
        }
    }

    private fun isMonarchBlockNotesPref(pref: PreferencesUnion): Boolean {
        return prefType(pref) == "\$monarch.blockNotes"
    }

    private fun isMonarchAccountNotesPref(pref: PreferencesUnion): Boolean {
        return prefType(pref) == "\$monarch.accountNotes"
    }

    private fun extractNotesFromPref(
        prefs: List<PreferencesUnion>,
        matcher: (PreferencesUnion) -> Boolean,
    ): MonarchAccountNotesData? {
        val allNotes = prefs.filter { matcher(it) }.mapNotNull {
            when (it) {
                is PreferencesUnion.Unknown -> {
                    val raw = it.value.value
                    if (raw is kotlinx.serialization.json.JsonObject) {
                        val data = raw["data"]
                        if (data is kotlinx.serialization.json.JsonObject &&
                            data["notes"] is kotlinx.serialization.json.JsonArray
                        ) {
                            BlueskyJson.decodeFromJsonElement(MonarchAccountNotesData.serializer(), data)
                        } else null
                    } else null
                }
                else -> null
            }
        }
        if (allNotes.isEmpty()) return null
        return MonarchAccountNotesData(
            notes = allNotes.flatMap { it.notes }.distinctBy { it.did },
        )
    }

    private fun extractBlockNotesPref(prefs: List<PreferencesUnion>): MonarchAccountNotesData? {
        return extractNotesFromPref(prefs, ::isMonarchBlockNotesPref)
    }

    private fun extractAccountNotesPref(prefs: List<PreferencesUnion>): MonarchAccountNotesData? {
        return extractNotesFromPref(prefs, ::isMonarchAccountNotesPref)
    }

    suspend fun getBlockNotes(): Result<List<MonarchAccountNote>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            return Result.success(extractBlockNotesPref(prefs.preferences)?.notes ?: emptyList())
        }
    }

    suspend fun getAccountNotes(): Result<List<MonarchAccountNote>> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            return Result.success(extractAccountNotesPref(prefs.preferences)?.notes ?: emptyList())
        }
    }

    private suspend inline fun <reified T : Any> putNotes(
        did: Did,
        note: String,
        crossinline matcher: (PreferencesUnion) -> Boolean,
        crossinline prefFactory: (MonarchAccountNotesData) -> T,
    ): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val updatedPrefs = prefs.preferences.toMutableList()

            val mergedNotes = updatedPrefs
                .filter { matcher(it) }
                .flatMap {
                    BlueskyJson.decodeFromJsonElement(
                        MonarchAccountNotesData.serializer(),
                        (it as PreferencesUnion.Unknown).value.value.jsonObject["data"]!!,
                    ).notes
                }
                .associateBy { it.did }
                .toMutableMap()

            updatedPrefs.removeAll { matcher(it) }

            mergedNotes[did.did] = MonarchAccountNote(
                did = did.did,
                note = note,
                createdAt = Clock.System.now().toString(),
            )

            val newPrefJson = BlueskyJson.encodeAsJsonContent(
                prefFactory(MonarchAccountNotesData(notes = mergedNotes.values.toList()))
            )
            updatedPrefs.add(PreferencesUnion.Unknown(newPrefJson))
            pdsClient!!.putPreferences(PutPreferencesRequest(preferences = updatedPrefs)).requireResponse()
        }
    }

    private suspend inline fun <reified T : Any> removeNotes(
        did: Did,
        crossinline matcher: (PreferencesUnion) -> Boolean,
        crossinline prefFactory: (MonarchAccountNotesData) -> T,
    ): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val updatedPrefs = prefs.preferences.toMutableList()

            val mergedNotes = updatedPrefs
                .filter { matcher(it) }
                .flatMap {
                    BlueskyJson.decodeFromJsonElement(
                        MonarchAccountNotesData.serializer(),
                        (it as PreferencesUnion.Unknown).value.value.jsonObject["data"]!!,
                    ).notes
                }
                .associateBy { it.did }
                .toMutableMap()

            updatedPrefs.removeAll { matcher(it) }
            mergedNotes.remove(did.did)

            if (mergedNotes.isNotEmpty()) {
                val newPrefJson = BlueskyJson.encodeAsJsonContent(
                    prefFactory(MonarchAccountNotesData(notes = mergedNotes.values.toList()))
                )
                updatedPrefs.add(PreferencesUnion.Unknown(newPrefJson))
            }
            pdsClient!!.putPreferences(PutPreferencesRequest(preferences = updatedPrefs)).requireResponse()
        }
    }

    suspend fun addBlockNote(did: Did, note: String): Result<Unit> =
        putNotes(did, note, ::isMonarchBlockNotesPref) { data ->
            MonarchBlockNotesPref(data = data)
        }

    suspend fun removeBlockNote(did: Did): Result<Unit> =
        removeNotes(did, ::isMonarchBlockNotesPref) { data ->
            MonarchBlockNotesPref(data = data)
        }

    suspend fun addAccountNote(did: Did, note: String): Result<Unit> =
        putNotes(did, note, ::isMonarchAccountNotesPref) { data ->
            MonarchAccountNotesPref(data = data)
        }

    suspend fun removeAccountNote(did: Did): Result<Unit> =
        removeNotes(did, ::isMonarchAccountNotesPref) { data ->
            MonarchAccountNotesPref(data = data)
        }

    suspend fun notifications(
        cursor: String? = null,
        reasons: List<String>? = null,
    ): Result<ListNotificationsResponse> = retryOnDpopHiccup {
        apiCall("Failed to fetch notifications") {
            client!!.listNotifications(ListNotificationsQueryParams(cursor = cursor, reasons = reasons))
        }
    }

    suspend fun getUnreadCount(): Result<Long> = retryOnDpopHiccup {
        apiCall("Failed to fetch unread count") {
            client!!.getUnreadCount(app.bsky.notification.GetUnreadCountQueryParams())
        }.map { it.count }
    }

    suspend fun updateSeenNotifications(seenAt: Instant = Clock.System.now()): Result<Unit> = retryOnDpopHiccup {
        apiCall("Failed to update seen notifications") {
            client!!.updateSeen(UpdateSeenRequest(seenAt = seenAt))
        }
    }

    suspend fun getLikes(uri: AtUri, cursor: String? = null) = retryOnDpopHiccup {
        apiCall("Failed to fetch likes") {
            client!!.getLikes(GetLikesQueryParams(uri = uri, cursor = cursor))
        }
    }

    suspend fun getRepostedBy(uri: AtUri, cursor: String? = null) = retryOnDpopHiccup {
        apiCall("Failed to fetch reposts") {
            client!!.getRepostedBy(GetRepostedByQueryParams(uri = uri, cursor = cursor))
        }
    }

    suspend fun getQuotes(uri: AtUri, cursor: String? = null) = retryOnDpopHiccup {
        apiCall("Failed to fetch quotes") {
            client!!.getQuotes(GetQuotesQueryParams(uri = uri, cursor = cursor))
        }
    }

    suspend fun getPosts(uri: List<AtUri>): Result<List<PostView>> = retryOnDpopHiccup {
        apiCall("Failed to fetch posts") {
            client!!.getPosts(GetPostsQueryParams(uris = uri))
        }.map { it.posts }
    }

    suspend fun fetchAlsoLiked(uri: AtUri, cursor: String? = null): Result<AlsoLikedResponse> {
        return fetchAlsoLiked(uri.atUri, cursor)
    }

    suspend fun like(uri: AtUri, cid: Cid): Result<RKey> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val like = BlueskyJson.encodeAsJsonContent(
                Like(
                    subject = StrongRef(uri, cid),
                    createdAt = Clock.System.now(),
                )
            )


            val likeRes = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.feed.like"),
                    record = like,
                )
            )

            return when (likeRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not like post: ${likeRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(likeRes.response.uri.rkey())
            }
        }
    }

    suspend fun repost(uri: AtUri, cid: Cid): Result<RKey> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val record = BlueskyJson.encodeAsJsonContent(
                Repost(
                    subject = StrongRef(uri, cid),
                    createdAt = Clock.System.now(),
                )
            )

            val res = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.feed.repost"),
                    record = record,
                )
            )

            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not repost: ${res.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(
                    res.response.uri.rkey()
                )
            }
        }
    }

    suspend fun deleteLike(rKey: RKey): Result<Unit> {
        return deleteRecord(rKey, "app.bsky.feed.like")
    }

    suspend fun deleteRepost(rKey: RKey): Result<Unit> {
        return deleteRecord(rKey, "app.bsky.feed.repost")
    }

    suspend fun deletePost(rKey: RKey): Result<Unit> {
        return deleteRecord(rKey, "app.bsky.feed.post")
    }

    suspend fun getThread(uri: AtUri, parentHeight: Long = 80): Result<GetPostThreadResponse> = retryOnDpopHiccup {
        apiCall("Could not get thread") {
            client!!.getPostThread(GetPostThreadQueryParams(uri = uri, depth = 50, parentHeight = parentHeight))
        }
    }

    suspend fun searchActorsTypeahead(query: String): Result<List<ProfileViewBasic>> =
        publicApiCall("Typeahead search failed") {
            publicClient.searchActorsTypeahead(SearchActorsTypeaheadQueryParams(q = query, limit = 5))
        }.map { it.actors }

    suspend fun getAuthorFeed(
        did: Did,
        cursor: String? = null,
        filter: app.bsky.feed.GetAuthorFeedFilter? = null,
    ): Result<Timeline> = retryOnDpopHiccup {
        apiCall("Failed to fetch author feed") {
            client!!.getAuthorFeed(app.bsky.feed.GetAuthorFeedQueryParams(actor = did, limit = 25, cursor = cursor, filter = filter))
        }.map { Timeline(it.cursor, it.feed) }
    }

    suspend fun follow(did: Did): Result<RKey> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val follow = BlueskyJson.encodeAsJsonContent(
                app.bsky.graph.Follow(
                    subject = did,
                    createdAt = Clock.System.now(),
                )
            )

            val followRes = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.graph.follow"),
                    record = follow,
                )
            )

            return when (followRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not follow: ${followRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(followRes.response.uri.rkey())
            }
        }
    }

    suspend fun unfollow(followUri: AtUri): Result<Unit> {
        return deleteRecord(followUri.rkey(), "app.bsky.graph.follow")
    }

    suspend fun blockActor(did: Did): Result<RKey> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val block = BlueskyJson.encodeAsJsonContent(
                app.bsky.graph.Block(
                    subject = did,
                    createdAt = Clock.System.now(),
                )
            )
            val blockRes = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.graph.block"),
                    record = block,
                )
            )
            return when (blockRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not block: ${blockRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(blockRes.response.uri.rkey())
            }
        }
    }

    suspend fun unblockActor(blockUri: AtUri): Result<Unit> {
        return deleteRecord(blockUri.rkey(), "app.bsky.graph.block")
    }

    suspend fun getFollowers(did: Did, cursor: String? = null) = retryOnDpopHiccup {
        apiCall("Could not get followers") {
            client!!.getFollowers(app.bsky.graph.GetFollowersQueryParams(actor = did, cursor = cursor))
        }
    }

    suspend fun getFollows(did: Did, cursor: String? = null) = retryOnDpopHiccup {
        apiCall("Could not get follows") {
            client!!.getFollows(app.bsky.graph.GetFollowsQueryParams(actor = did, cursor = cursor))
        }
    }

    suspend fun muteActor(did: Did): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not mute") {
            pdsClient!!.muteActor(app.bsky.graph.MuteActorRequest(actor = did))
        }
    }

    suspend fun getProfileRecord(): Result<Profile> = retryOnDpopHiccup {
        apiCall("Failed fetching profile record") {
            pdsClient!!.getRecord(GetRecordQueryParams(repo = session!!.did, collection = Nsid("app.bsky.actor.profile"), rkey = RKey("self")))
        }.map { it.value.decodeAs() }
    }

    suspend fun updateProfile(
        displayName: String?,
        description: String?,
        pronouns: String?,
        avatarUri: Uri? = null,
        bannerUri: Uri? = null,
    ): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            // First get the current profile to preserve fields we don't change
            val currentProfile = getProfileRecord().getOrThrow()

            val avatarBlob = if (avatarUri != null) uploadImageBlob(avatarUri, "avatar") else currentProfile.avatar
            val bannerBlob = if (bannerUri != null) uploadImageBlob(bannerUri, "banner") else currentProfile.banner

            val updatedProfile = Profile(
                displayName = displayName ?: currentProfile.displayName,
                description = description ?: currentProfile.description,
                pronouns = pronouns ?: currentProfile.pronouns,
                avatar = avatarBlob,
                banner = bannerBlob,
                labels = currentProfile.labels,
                pinnedPost = currentProfile.pinnedPost,
                createdAt = currentProfile.createdAt,
            )

            val record = BlueskyJson.encodeAsJsonContent(updatedProfile)

            val ret = pdsClient!!.putRecord(
                com.atproto.repo.PutRecordRequest(
                    repo = session!!.did,
                    collection = Nsid("app.bsky.actor.profile"),
                    rkey = RKey("self"),
                    record = record,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed updating profile: ${ret.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun pinPost(
        uri: AtUri,
        cid: Cid,
    ): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val currentProfile = getProfileRecord().getOrThrow()

            val updatedProfile = Profile(
                displayName = currentProfile.displayName,
                description = currentProfile.description,
                pronouns = currentProfile.pronouns,
                avatar = currentProfile.avatar,
                banner = currentProfile.banner,
                labels = currentProfile.labels,
                pinnedPost = StrongRef(uri = uri, cid = cid),
                createdAt = currentProfile.createdAt,
            )

            val record = BlueskyJson.encodeAsJsonContent(updatedProfile)

            val ret = pdsClient!!.putRecord(
                com.atproto.repo.PutRecordRequest(
                    repo = session!!.did,
                    collection = Nsid("app.bsky.actor.profile"),
                    rkey = RKey("self"),
                    record = record,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed pinning post: ${ret.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun unpinPost(): Result<Unit> {
        return suspendRunCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val currentProfile = getProfileRecord().getOrThrow()

            val updatedProfile = Profile(
                displayName = currentProfile.displayName,
                description = currentProfile.description,
                pronouns = currentProfile.pronouns,
                avatar = currentProfile.avatar,
                banner = currentProfile.banner,
                labels = currentProfile.labels,
                pinnedPost = null,
                createdAt = currentProfile.createdAt,
            )

            val record = BlueskyJson.encodeAsJsonContent(updatedProfile)

            val ret = pdsClient!!.putRecord(
                com.atproto.repo.PutRecordRequest(
                    repo = session!!.did,
                    collection = Nsid("app.bsky.actor.profile"),
                    rkey = RKey("self"),
                    record = record,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed unpinning post: ${ret.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun searchPosts(
        query: String,
        sort: app.bsky.feed.SearchPostsSort? = app.bsky.feed.SearchPostsSort.Latest,
        cursor: String? = null,
        author: Did? = null,
    ): Result<Pair<List<PostView>, String?>> =
        publicApiCall("Search failed") {
            publicClient.searchPosts(app.bsky.feed.SearchPostsQueryParams(q = query, sort = sort, limit = 25, cursor = cursor, author = author))
        }.map { it.posts to it.cursor }

    suspend fun searchActors(
        query: String,
        cursor: String? = null,
    ): Result<Pair<List<app.bsky.actor.ProfileView>, String?>> =
        publicApiCall("Search failed") {
            publicClient.searchActors(SearchActorsQueryParams(q = query, limit = 25, cursor = cursor))
        }.map { it.actors to it.cursor }

    suspend fun unmuteActor(did: Did): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not unmute") {
            pdsClient!!.unmuteActor(app.bsky.graph.UnmuteActorRequest(actor = did))
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = id }
        return id
    }

    fun buildDraft(
        text: String,
        mediaUris: List<Uri> = emptyList(),
        isVideo: Boolean = false,
        replyData: SkeetData? = null,
        isQuotePost: Boolean = false,
        linkPreview: LinkPreviewData? = null,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
        deviceId: String,
    ): Draft {
        val embedImages = if (!isVideo && mediaUris.isNotEmpty()) {
            mediaUris.map { DraftEmbedImage(localRef = DraftEmbedLocalRef(it.toString())) }
        } else null

        val embedVideos = if (isVideo && mediaUris.isNotEmpty()) {
            listOf(DraftEmbedVideo(localRef = DraftEmbedLocalRef(mediaUris.first().toString())))
        } else null

        val embedExternals = if (linkPreview != null && mediaUris.isEmpty()) {
            listOf(DraftEmbedExternal(uri = sh.christian.ozone.api.Uri(linkPreview.url)))
        } else null

        val embedRecords = if (replyData != null && (isQuotePost || replyData.uri.atUri.isNotEmpty())) {
            listOf(DraftEmbedRecord(record = StrongRef(replyData.uri, replyData.cid)))
        } else null

        val draftPost = DraftPost(
            text = text,
            embedImages = embedImages,
            embedVideos = embedVideos,
            embedExternals = embedExternals,
            embedRecords = if (isQuotePost) embedRecords else null,
        )

        val draftThreadgateAllow = threadgateRules?.map { rule ->
            when (rule) {
                is ThreadgateAllowUnion.MentionRule -> DraftThreadgateAllowUnion.MentionRule(rule.value)
                is ThreadgateAllowUnion.FollowerRule -> DraftThreadgateAllowUnion.FollowerRule(rule.value)
                is ThreadgateAllowUnion.FollowingRule -> DraftThreadgateAllowUnion.FollowingRule(rule.value)
                is ThreadgateAllowUnion.ListRule -> DraftThreadgateAllowUnion.ListRule(rule.value)
                is ThreadgateAllowUnion.Unknown -> null
            }
        }?.filterNotNull()

        return Draft(
            deviceId = deviceId,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            posts = listOf(draftPost),
            threadgateAllow = draftThreadgateAllow,
        )
    }

    suspend fun createDraft(draft: Draft): Result<String> = retryOnDpopHiccup {
        apiCall("Could not create draft") {
            pdsClient!!.createDraft(CreateDraftRequest(draft = draft))
        }.map { it.id }
    }

    suspend fun getDrafts(cursor: String? = null, limit: Long = 50): Result<GetDraftsResponse> = retryOnDpopHiccup {
        apiCall("Could not fetch drafts") {
            pdsClient!!.getDrafts(GetDraftsQueryParams(limit = limit, cursor = cursor))
        }
    }

    suspend fun updateDraft(id: Tid, draft: Draft): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not update draft") {
            pdsClient!!.updateDraft(UpdateDraftRequest(draft = DraftWithId(id = id, draft = draft)))
        }
    }

    suspend fun deleteDraft(id: Tid): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not delete draft") {
            pdsClient!!.deleteDraft(DeleteDraftRequest(id = id))
        }
    }

    suspend fun deviceId(): String = getOrCreateDeviceId()

    suspend fun createBookmark(uri: AtUri, cid: Cid): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not create bookmark") {
            client!!.createBookmark(CreateBookmarkRequest(uri = uri, cid = cid))
        }
    }

    suspend fun deleteBookmark(uri: AtUri): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not delete bookmark") {
            client!!.deleteBookmark(DeleteBookmarkRequest(uri = uri))
        }
    }

    suspend fun getBookmarks(limit: Long = 50, cursor: String? = null): Result<GetBookmarksResponse> = retryOnDpopHiccup {
        apiCall("Could not fetch bookmarks") {
            client!!.getBookmarks(GetBookmarksQueryParams(limit = limit, cursor = cursor))
        }
    }

    private suspend fun deleteRecord(rKey: RKey, collection: String): Result<Unit> = retryOnDpopHiccup {
        apiCall("Could not delete record") {
            pdsClient!!.deleteRecord(DeleteRecordRequest(repo = session!!.handle, collection = Nsid(collection), rkey = rKey))
        }.map { }
    }
}