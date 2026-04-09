@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.GetProfileResponse
import app.bsky.actor.MutedWord
import app.bsky.actor.MutedWordsPref
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.Profile
import app.bsky.actor.PutPreferencesRequest
import app.bsky.actor.SavedFeedType
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.actor.SearchActorsQueryParams
import app.bsky.actor.SearchActorsResponse
import app.bsky.actor.SearchActorsTypeaheadQueryParams
import app.bsky.actor.SearchActorsTypeaheadResponse
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
import com.atproto.repo.GetRecordResponse
import com.atproto.repo.StrongRef
import com.atproto.repo.UploadBlobResponse
import com.atproto.server.GetServiceAuthQueryParams
import com.atproto.server.GetServiceAuthResponse
import industries.geesawra.monarch.collection
import industries.geesawra.monarch.did
import industries.geesawra.monarch.rkey
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthClient
import sh.christian.ozone.oauth.OAuthCodeChallengeMethod
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

enum class AuthData {
    OAuthInFlightState,
    OAuthInFlightVerifier,
    OAuthInFlightNonce,
    OAuthInFlightHandle,
    OAuthInFlightPdsURL,
    OAuthInFlightAppviewProxy,
}

class LoginException(message: String?) : Exception(message)

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

class BlueskyConn(val context: Context) {
    companion object {
        const val MAX_IMAGE_SIZE_BYTES = 950_000L
        const val VIDEO_UPLOAD_TIMEOUT_MS = 300_000L
        const val DEFAULT_TIMEOUT_MS = 30_000L

        // Transient OAuth-flow state lives here. The active session itself (credentials, handle,
        // PDS, appview) is stored in AccountManager — see datalayer/AccountManager.kt.
        private val Context.dataStore by preferencesDataStore("bluesky")
        private val OAUTH_IN_FLIGHT_STATE = stringPreferencesKey(AuthData.OAuthInFlightState.name)
        private val OAUTH_IN_FLIGHT_VERIFIER = stringPreferencesKey(AuthData.OAuthInFlightVerifier.name)
        private val OAUTH_IN_FLIGHT_NONCE = stringPreferencesKey(AuthData.OAuthInFlightNonce.name)
        private val OAUTH_IN_FLIGHT_HANDLE = stringPreferencesKey(AuthData.OAuthInFlightHandle.name)
        private val OAUTH_IN_FLIGHT_PDS_URL = stringPreferencesKey(AuthData.OAuthInFlightPdsURL.name)
        private val OAUTH_IN_FLIGHT_APPVIEW_PROXY = stringPreferencesKey(AuthData.OAuthInFlightAppviewProxy.name)

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
                }
                configure()
            }
        }

        suspend fun pdsForHandle(handleOrDid: String): Result<String> {
            return runCatching {
                val did = if (handleOrDid.startsWith("did:")) {
                    handleOrDid
                } else {
                    val api = XrpcBlueskyApi()
                    val rawId = api.resolveHandle(
                        ResolveHandleQueryParams(handle = Handle(handleOrDid))
                    )
                    when (rawId) {
                        is AtpResponse.Failure<*> -> {
                            return Result.failure(Exception("Failed to resolve handle: ${rawId.error?.message}"))
                        }
                        is AtpResponse.Success<ResolveHandleResponse> -> rawId.response.did.did
                    }
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

    var client: AuthenticatedXrpcBlueskyApi? = null      // appview queries (has atproto-proxy)
    var pdsClient: AuthenticatedXrpcBlueskyApi? = null   // PDS procedures (no atproto-proxy)
    var session: SessionData? = null
    var oauthToken: OAuthToken? = null
    var createMutex: Mutex = Mutex()
    var pdsURL: String? = null
    var appviewProxy: String? = null

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
    private var labelCacheFetchCount: Int = 0
    private val labelCacheRefreshInterval: Int = 12

    fun labelDisplayName(label: Label): String {
        val key = "${label.src.did}:${label.`val`}"
        return labelDisplayNames[key] ?: label.`val`
    }

    fun labelDescription(label: Label): String? {
        val key = "${label.src.did}:${label.`val`}"
        return labelDescriptions[key]
    }

    fun labelerAvatar(label: Label): String? {
        return labelerAvatars[label.src.did]
    }

    suspend fun refreshLabelCacheIfNeeded() {
        labelCacheFetchCount++
        if (labelCacheFetchCount < labelCacheRefreshInterval) return
        labelCacheFetchCount = 0
        subscribedLabelers().onSuccess { buildLabelCache(it) }
    }

    private fun buildLabelCache(
        labelers: Map<Did?, GetServicesResponseViewUnion.LabelerViewDetailed?>
    ) {
        val names = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        val avatars = mutableMapOf<String, String>()
        for ((did, detailed) in labelers) {
            if (did == null || detailed == null) continue
            detailed.value.creator.avatar?.let { avatars[did.did] = it.uri }
            for (defn in detailed.value.policies.labelValueDefinitions.orEmpty()) {
                val locale = defn.locales.firstOrNull() ?: continue
                val key = "${did.did}:${defn.identifier}"
                names[key] = locale.name
                if (locale.description.isNotEmpty()) descriptions[key] = locale.description
            }
        }
        labelDisplayNames = names
        labelDescriptions = descriptions
        labelerAvatars = avatars
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
     * Builds both the appview client and the PDS-direct client, sets `session` and `oauthToken`,
     * and starts the token-refresh watcher so subsequent SDK-driven refreshes get re-persisted.
     */
    suspend fun initializeInMemory(
        pdsURL: String,
        appviewProxy: String,
        oauthToken: OAuthToken,
        handle: Handle,
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
        this.pdsClient = mkClient(pdsURL, oauthToken)
        this.client = mkClient(pdsURL, oauthToken, appviewProxy = appviewProxy)
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
        return runCatching {
            val pdsURL = pdsForHandle(handle).getOrElse {
                return Result.failure(it)
            }

            val httpClient = createRetryHttpClient(baseUrl = pdsURL)
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
        return runCatching {
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

            if (state != expectedState) {
                clearInFlightOAuthState()
                return Result.failure(Exception("OAuth state mismatch (possible CSRF)"))
            }

            val httpClient = createRetryHttpClient(baseUrl = pdsURL)
            val oauthApi = OAuthApi(
                httpClient = httpClient,
                challengeSelector = { OAuthCodeChallengeMethod.S256 },
            )

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

                // Resolve the user's profile to get the handle/displayName/avatar that we need
                // for StoredAccount. We build a temporary OAuth client just for this — the
                // long-lived clients get built later in initializeInMemory().
                val tempClient = mkClient(pdsURL, token, appviewProxy = appviewProxy)
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
                    )
                )
            } finally {
                httpClient.close()
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
        }
    }

    /**
     * Build an OAuth-authenticated `AuthenticatedXrpcBlueskyApi`. The SDK's `BlueskyAuthPlugin`
     * handles DPoP signing, nonce rotation, and token refresh transparently once `activateOAuth`
     * is called. Caller is responsible for registering a token-refresh watcher if it wants the
     * refreshed credentials persisted.
     */
    private fun mkClient(
        pds: String,
        token: OAuthToken,
        labelers: List<String> = listOf(),
        appviewProxy: String? = null,
    ): AuthenticatedXrpcBlueskyApi {
        val hc = createRetryHttpClient {
            defaultRequest {
                url(pds)
                headers["atproto-accept-labelers"] = labelers.joinToString()
                if (appviewProxy != null) {
                    headers["atproto-proxy"] = appviewProxy
                }
            }
        }

        val api = AuthenticatedXrpcBlueskyApi(httpClient = hc)
        api.activateOAuth(token)
        return api
    }

    /**
     * Watch the active client's `authTokens` StateFlow. Whenever the SDK refreshes the
     * DPoP-bound access/refresh tokens (which happens transparently inside the auth plugin
     * when the server returns ExpiredToken or use_dpop_nonce), we copy the change back into
     * `this.oauthToken` and re-persist it via [Context.updateStoredAccountOAuthToken] so the
     * next process restart picks up the latest credentials instead of trying the stale ones.
     */
    private fun launchTokenWatcher() {
        tokenWatcherJob?.cancel()
        val activeClient = client ?: return
        tokenWatcherJob = connScope.launch {
            activeClient.authTokens.collect { tokens ->
                if (tokens !is BlueskyAuthPlugin.Tokens.Dpop) return@collect
                val previous = oauthToken ?: return@collect
                if (previous.accessToken == tokens.auth &&
                    previous.refreshToken == tokens.refresh &&
                    previous.nonce == tokens.nonce
                ) {
                    return@collect
                }
                val updated = previous.copy(
                    accessToken = tokens.auth,
                    refreshToken = tokens.refresh,
                    nonce = tokens.nonce,
                    keyPair = tokens.keyPair,
                )
                this@BlueskyConn.oauthToken = updated
                runCatching {
                    val updatedJson = BlueskyJson.encodeToString(OAuthToken.serializer(), updated)
                    context.updateStoredAccountOAuthToken(previous.subject.did, updatedJson)
                }.onFailure {
                    Log.w("BlueskyAuth", "Failed to persist refreshed OAuth token: ${it.message}")
                }
            }
        }
    }

    suspend fun create(): Result<Unit> {
        createMutex.lock()
        try {
            if (client != null && pdsClient != null && pdsURL != null) {
                Log.d("BlueskyAuth", "create: session already active, skipping initialization")
                return Result.success(Unit)
            }

            Log.d("Bluesky", "create called without session or client")
            val account = context.readActiveStoredAccount()
                ?: return Result.failure(Exception("No active account"))

            val token = runCatching {
                BlueskyJson.decodeFromString(OAuthToken.serializer(), account.oauthTokenJson)
            }.getOrElse {
                return Result.failure(LoginException("Failed to deserialize OAuth token: ${it.message}"))
            }

            initializeInMemory(
                pdsURL = account.pdsHost,
                appviewProxy = account.appviewProxy,
                oauthToken = token,
                handle = Handle(account.handle),
            )

            // Hydrate the labeler cache and rebuild `client` with the labelers header so future
            // appview queries respect the user's subscribed labelers.
            val labelerMap = this.subscribedLabelers().getOrDefault(emptyMap())
            buildLabelCache(labelerMap)
            labelCacheFetchCount = 0
            val labelers = labelerMap.keys.mapNotNull { it?.did }
            this.client = mkClient(account.pdsHost, token, labelers, appviewProxy = account.appviewProxy)
            // Re-arm the watcher on the new client instance.
            launchTokenWatcher()

            Log.d("BlueskyAuth", "Clients initialized: pdsClient=${this.pdsClient != null}, client=${this.client != null}, handle=${account.handle}")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.d("BlueskyAuth", "create() failed: ${e::class.simpleName}: ${e.message}")
            return Result.failure(e)
        } finally {
            createMutex.unlock()
        }
    }

    suspend fun fetchFeed(feed: String, cursor: String? = null): Result<Timeline> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val timeline = client!!.getFeed(
                GetFeedQueryParams(
                    feed = AtUri(feed),
                    limit = 25,
                    cursor = cursor
                )
            )
            val feed = when (timeline) {
                is AtpResponse.Failure<*> -> {
                    return Result.failure(Exception("Failed to fetch timeline: ${timeline.error}"))
                }

                is AtpResponse.Success<GetFeedResponse> -> timeline.response
            }

            return Result.success(Timeline(feed.cursor, feed.feed))
        }
    }

    suspend fun fetchTimeline(
        cursor: String? = null
    ): Result<Timeline> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val timeline = client!!.getTimeline(
                GetTimelineQueryParams(
                    limit = 25,
                    cursor = cursor
                )
            )
            val feed = when (timeline) {
                is AtpResponse.Failure<*> -> {
                    return Result.failure(Exception("Failed to fetch timeline: ${timeline.error}"))
                }

                is AtpResponse.Success<GetTimelineResponse> -> timeline.response
            }

            return Result.success(Timeline(feed.cursor, feed.feed))
        }
    }

    suspend fun post(
        content: String,
        images: List<Uri>? = null,
        video: Uri? = null,
        replyRef: PostReplyRef? = null,
        quotePostRef: StrongRef? = null,
        facets: List<Facet> = listOf(),
        linkPreview: LinkPreviewData? = null,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
        onVideoStatus: (VideoUploadStatus, Long?) -> Unit = { _, _ -> },
    ): Result<AtUri> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            var mediaUnion: RecordWithMediaMediaUnion? = null

            if (images != null) {
                val blobs = uploadImages(images).getOrThrow()
                mediaUnion = RecordWithMediaMediaUnion.Images(
                    value = Images(
                        images = blobs.map {
                            ImagesImage(
                                image = it.blob,
                                alt = "",
                                aspectRatio = AspectRatio(it.width, it.height)
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
                        alt = "",
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

    suspend fun fetchRecord(uri: AtUri): Result<JsonContent> {
        return runCatching {
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

    suspend fun fetchActor(did: Did): Result<ProfileViewDetailed> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.getProfile(
                GetProfileQueryParams(
                    actor = did
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed fetching record: ${ret.error}"))
                is AtpResponse.Success<GetProfileResponse> -> Result.success(ret.response)
            }
        }
    }

    suspend fun fetchSelf(): Result<ProfileViewDetailed> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            return fetchActor(session!!.did)
        }
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

        return runCatching {
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
        return runCatching {
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

    suspend fun feeds(): Result<List<GeneratorView>> {
        return runCatching {
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

            val batch = runCatching {
                client!!.getFeedGenerators(
                    GetFeedGeneratorsQueryParams(feedUris)
                )
            }.getOrNull()

            if (batch is AtpResponse.Success) {
                return Result.success(batch.response.feeds)
            }

            val resolved = coroutineScope {
                feedUris.map { uri ->
                    async {
                        runCatching {
                            client!!.getFeedGenerator(
                                GetFeedGeneratorQueryParams(uri)
                            )
                        }.getOrNull()?.let { resp ->
                            (resp as? AtpResponse.Success)?.response
                        }
                    }
                }.awaitAll()
            }.mapNotNull { it?.takeIf { r -> r.isValid }?.view }

            return Result.success(resolved)
        }
    }

    suspend fun subscribedLabelers(): Result<Map<Did?, GetServicesResponseViewUnion.LabelerViewDetailed?>> {
        return runCatching {
            val prefs = pdsClient!!.getPreferencesForActor().requireResponse()
            val labelersPref = prefs.preferences.firstOrNull {
                it is PreferencesUnion.LabelersPref
            } as? PreferencesUnion.LabelersPref

            if (labelersPref == null) {
                return Result.success(emptyMap())
            }

            val labelers = labelersPref.value.labelers.map { it.did }.toMutableList()

            val res = client!!.getServices(
                GetServicesQueryParams(
                    detailed = true,
                    dids = labelers
                )
            )

            val asd = when (res) {
                is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed to fetch subscribed labelers: ${res.error}"))
                is AtpResponse.Success<GetServicesResponse> -> {
                    res
                }
            }

            val kek = asd.response.views.associate {
                when (it) {
                    is GetServicesResponseViewUnion.LabelerView -> it.value.uri.did() to null
                    is GetServicesResponseViewUnion.LabelerViewDetailed -> it.value.uri.did() to it
                    is GetServicesResponseViewUnion.Unknown -> null to null
                }
            }.filter { it.value != null && it.key != null }

            return Result.success(kek)
        }
    }

    suspend fun getMutedWords(): Result<List<MutedWord>> {
        return runCatching {
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
        return runCatching {
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

    suspend fun notifications(
        cursor: String? = null,
    ): Result<ListNotificationsResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.listNotifications(
                ListNotificationsQueryParams(
                    cursor = cursor,
                )
            )
            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch notifications: ${ret.error}"))
                is AtpResponse.Success<ListNotificationsResponse> -> Result.success(ret.response)
            }
        }
    }

    suspend fun updateSeenNotifications(): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = pdsClient!!.updateSeen(
                UpdateSeenRequest(
                    seenAt = Clock.System.now(),
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to update seen notifications: ${ret.error}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun getLikes(uri: AtUri, cursor: String? = null): Result<GetLikesResponse> {
        return runCatching {
            create().onFailure { return Result.failure(it) }
            val ret = client!!.getLikes(GetLikesQueryParams(uri = uri, cursor = cursor))
            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch likes: ${ret.error}"))
                is AtpResponse.Success<GetLikesResponse> -> Result.success(ret.response)
            }
        }
    }

    suspend fun getRepostedBy(uri: AtUri, cursor: String? = null): Result<GetRepostedByResponse> {
        return runCatching {
            create().onFailure { return Result.failure(it) }
            val ret = client!!.getRepostedBy(GetRepostedByQueryParams(uri = uri, cursor = cursor))
            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch reposts: ${ret.error}"))
                is AtpResponse.Success<GetRepostedByResponse> -> Result.success(ret.response)
            }
        }
    }

    suspend fun getQuotes(uri: AtUri, cursor: String? = null): Result<GetQuotesResponse> {
        return runCatching {
            create().onFailure { return Result.failure(it) }
            val ret = client!!.getQuotes(GetQuotesQueryParams(uri = uri, cursor = cursor))
            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch quotes: ${ret.error}"))
                is AtpResponse.Success<GetQuotesResponse> -> Result.success(ret.response)
            }
        }
    }

    suspend fun getPosts(uri: List<AtUri>): Result<List<PostView>> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.getPosts(
                GetPostsQueryParams(
                    uris = uri,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch posts: ${ret.error}"))
                is AtpResponse.Success<GetPostsResponse> -> Result.success(ret.response.posts)
            }
        }
    }

    suspend fun like(uri: AtUri, cid: Cid): Result<RKey> {
        return runCatching {
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
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(
                    RKey(likeRes.response.uri.atUri.toUri().lastPathSegment
                        ?: return Result.failure(Exception("Missing path segment in like response URI")))
                )
            }
        }
    }

    suspend fun repost(uri: AtUri, cid: Cid): Result<RKey> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val like = BlueskyJson.encodeAsJsonContent(
                Repost(
                    subject = StrongRef(uri, cid),
                    createdAt = Clock.System.now(),
                )
            )


            val likeRes = pdsClient!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.feed.repost"),
                    record = like,
                )
            )

            return when (likeRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not repost: ${likeRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(
                    likeRes.response.uri.rkey()
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

    suspend fun getThread(uri: AtUri, parentHeight: Long = 80): Result<GetPostThreadResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val res = client!!.getPostThread(
                GetPostThreadQueryParams(
                    uri = uri,
                    depth = 50,
                    parentHeight = parentHeight,
                )
            )

            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not get thread: ${res.error?.message}"))
                is AtpResponse.Success<GetPostThreadResponse> -> Result.success(res.response)
            }
        }
    }

    suspend fun searchActorsTypeahead(query: String): Result<List<ProfileViewBasic>> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val res = client!!.searchActorsTypeahead(
                SearchActorsTypeaheadQueryParams(
                    q = query,
                    limit = 5,
                )
            )

            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Typeahead search failed: ${res.error?.message}"))
                is AtpResponse.Success<SearchActorsTypeaheadResponse> -> Result.success(res.response.actors)
            }
        }
    }

    suspend fun getAuthorFeed(
        did: Did,
        cursor: String? = null,
        filter: app.bsky.feed.GetAuthorFeedFilter? = null,
    ): Result<Timeline> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.getAuthorFeed(
                app.bsky.feed.GetAuthorFeedQueryParams(
                    actor = did,
                    limit = 25,
                    cursor = cursor,
                    filter = filter,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to fetch author feed: ${ret.error}"))
                is AtpResponse.Success<app.bsky.feed.GetAuthorFeedResponse> -> Result.success(
                    Timeline(ret.response.cursor, ret.response.feed)
                )
            }
        }
    }

    suspend fun follow(did: Did): Result<RKey> {
        return runCatching {
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
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(
                    RKey(followRes.response.uri.atUri.toUri().lastPathSegment
                        ?: return Result.failure(Exception("Missing path segment in follow response URI")))
                )
            }
        }
    }

    suspend fun unfollow(followUri: AtUri): Result<Unit> {
        return deleteRecord(followUri.rkey(), "app.bsky.graph.follow")
    }

    suspend fun getFollowers(did: Did, cursor: String? = null): Result<app.bsky.graph.GetFollowersResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val res = client!!.getFollowers(
                app.bsky.graph.GetFollowersQueryParams(
                    actor = did,
                    cursor = cursor,
                )
            )
            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not get followers: ${res.error?.message}"))
                is AtpResponse.Success -> Result.success(res.response)
            }
        }
    }

    suspend fun getFollows(did: Did, cursor: String? = null): Result<app.bsky.graph.GetFollowsResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }
            val res = client!!.getFollows(
                app.bsky.graph.GetFollowsQueryParams(
                    actor = did,
                    cursor = cursor,
                )
            )
            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not get follows: ${res.error?.message}"))
                is AtpResponse.Success -> Result.success(res.response)
            }
        }
    }

    suspend fun muteActor(did: Did): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = pdsClient!!.muteActor(
                app.bsky.graph.MuteActorRequest(actor = did)
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not mute: ${ret.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun getProfileRecord(): Result<Profile> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = pdsClient!!.getRecord(
                GetRecordQueryParams(
                    repo = session!!.did,
                    collection = Nsid("app.bsky.actor.profile"),
                    rkey = RKey("self"),
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed fetching profile record: ${ret.error}"))
                is AtpResponse.Success<GetRecordResponse> -> Result.success(
                    ret.response.value.decodeAs()
                )
            }
        }
    }

    suspend fun updateProfile(
        displayName: String?,
        description: String?,
        pronouns: String?,
        avatarUri: Uri? = null,
        bannerUri: Uri? = null,
    ): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            // First get the current profile to preserve fields we don't change
            val currentProfile = getProfileRecord().getOrThrow()

            var avatarBlob = currentProfile.avatar
            var bannerBlob = currentProfile.banner

            if (avatarUri != null) {
                val compressor = Compressor(context)
                val compressed = compressor.compressImage(avatarUri, MAX_IMAGE_SIZE_BYTES)
                val uploaded = pdsClient!!.uploadBlob(compressed.data)
                avatarBlob = when (uploaded) {
                    is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed uploading avatar: ${uploaded.error}"))
                    is AtpResponse.Success<UploadBlobResponse> -> uploaded.response.blob
                }
            }

            if (bannerUri != null) {
                val compressor = Compressor(context)
                val compressed = compressor.compressImage(bannerUri, MAX_IMAGE_SIZE_BYTES)
                val uploaded = pdsClient!!.uploadBlob(compressed.data)
                bannerBlob = when (uploaded) {
                    is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed uploading banner: ${uploaded.error}"))
                    is AtpResponse.Success<UploadBlobResponse> -> uploaded.response.blob
                }
            }

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

    suspend fun searchPosts(
        query: String,
        sort: app.bsky.feed.SearchPostsSort? = app.bsky.feed.SearchPostsSort.Latest,
        cursor: String? = null,
        author: Did? = null,
    ): Result<Pair<List<PostView>, String?>> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.searchPosts(
                app.bsky.feed.SearchPostsQueryParams(
                    q = query,
                    sort = sort,
                    limit = 25,
                    cursor = cursor,
                    author = author,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Search failed: ${ret.error}"))
                is AtpResponse.Success<app.bsky.feed.SearchPostsResponse> -> Result.success(
                    ret.response.posts to ret.response.cursor
                )
            }
        }
    }

    suspend fun searchActors(
        query: String,
        cursor: String? = null,
    ): Result<Pair<List<app.bsky.actor.ProfileView>, String?>> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = client!!.searchActors(
                SearchActorsQueryParams(
                    q = query,
                    limit = 25,
                    cursor = cursor,
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Search failed: ${ret.error}"))
                is AtpResponse.Success<SearchActorsResponse> -> Result.success(
                    ret.response.actors to ret.response.cursor
                )
            }
        }
    }

    suspend fun unmuteActor(did: Did): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val ret = pdsClient!!.unmuteActor(
                app.bsky.graph.UnmuteActorRequest(actor = did)
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not unmute: ${ret.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    private suspend fun deleteRecord(rKey: RKey, collection: String): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(it)
            }

            val delRes = pdsClient!!.deleteRecord(
                DeleteRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid(collection),
                    rkey = rKey,
                )
            )

            return when (delRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not delete record: ${delRes.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }

        }
    }
}