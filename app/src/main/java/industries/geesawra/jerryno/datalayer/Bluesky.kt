package industries.geesawra.jerryno.datalayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.bsky.actor.PreferencesUnion
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Video
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import com.atproto.identity.ResolveHandleQueryParams
import com.atproto.identity.ResolveHandleResponse
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.UploadBlobResponse
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import com.atproto.server.GetSessionResponse
import com.atproto.server.RefreshSessionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.response.AtpResponse

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

        suspend fun pdsForHandle(handle: String): Result<String> {
            return runCatching {
                val api = XrpcBlueskyApi()

                val rawId = api.resolveHandle(
                    ResolveHandleQueryParams(
                        handle = Handle(handle)
                    )
                )

                val did = when (rawId) {
                    is AtpResponse.Failure<*> -> {
                        return Result.failure(Exception("Failed to resolve handle: ${rawId.error?.message}"))
                    }

                    is AtpResponse.Success<ResolveHandleResponse> -> {
                        rawId.response.did.did
                    }
                }

                val httpClient = HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15000
                        connectTimeoutMillis = 15000
                        socketTimeoutMillis = 15000
                    }
                }

                val rawDoc = httpClient.get {
                    url {
                        protocol = URLProtocol.HTTPS
                        host = "plc.directory"
                        path(did)
                    }
                }

                if (rawDoc.status != HttpStatusCode.OK) {
                    return Result.failure(Exception("PLC lookup HTTP status code ${rawDoc.status}"))
                }

                val body: String = rawDoc.body()

                val solvedDoc: didDoc = BlueskyJson.decodeFromString(didDoc.serializer(), body)

                for (ps in solvedDoc.service) {
                    if (ps.id == "#atproto_pds" && ps.type == "AtprotoPersonalDataServer") {
                        return Result.success(ps.serviceEndpoint)
                    }
                }

                return Result.failure(Exception("No PDS service defined in the DID document associated with ${handle}"))
            }
        }
    }

    @Serializable
    private data class service(
        val id: String,
        val type: String,
        val serviceEndpoint: String
    )

    @Serializable
    private data class didDoc(
        val service: List<service>
    )

    var client: AuthenticatedXrpcBlueskyApi? = null
    var session: SessionData? = null
    var createMutex: Mutex = Mutex()

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
            createMutex.lock()
            if (session != null && client != null) {
                createMutex.unlock()
                return Result.success(Unit)
            }

            Log.d("Bluesky", "create called without session or client")
            val pdsURLFlow: Flow<String> = context.dataStore.data.map { settings ->
                settings[PDSHOST] ?: ""
            }
            val sessionDataStringFlow: Flow<String> = context.dataStore.data.map { settings ->
                settings[SESSION] ?: ""
            }

            val pdsURL = pdsURLFlow.first()
            val sessionDataString = sessionDataStringFlow.first()

            if (pdsURL.isEmpty() || sessionDataString.isEmpty()) {
                createMutex.unlock()
                return Result.failure(Exception("No session data found"))
            }

            val sessionData = SessionData.decodeFromJson(sessionDataString)

            val httpClient = HttpClient(OkHttp) {
                defaultRequest {
                    url(pdsURL)
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 30000
                    socketTimeoutMillis = 30000
                }
            }

            val tokens =
                BlueskyAuthPlugin.Tokens(sessionData.accessJwt, sessionData.refreshJwt)
            val authClient = AuthenticatedXrpcBlueskyApi(httpClient, tokens)

            val gs = authClient.getSession()
            when (gs) {
                is AtpResponse.Failure<*> -> run {
                    return@run
                }

                is AtpResponse.Success<GetSessionResponse> -> {
                    this.client = authClient
                    this.session = SessionData.fromGetSessionResponse(gs.response)
                    createMutex.unlock()
                    return Result.success(Unit)
                }
            }

            // No session, try to refresh
            val rs = authClient.refreshSession()
            Log.d("ASD", rs.toString())
            when (rs) {
                is AtpResponse.Failure<*> -> return Result.failure(Exception("Could not refresh session, maybe login again?"))
                is AtpResponse.Success<RefreshSessionResponse> -> {
                    this.client = authClient
                    this.session = SessionData.fromRefreshSessionResponse(rs.response)
                    storeSessionData(pdsURL, this.session!!)
                    createMutex.unlock()
                    return Result.success(Unit)
                }
            }
        }
    }

    suspend fun fetchTimeline(
        algorithm: String,
        cursor: String? = null
    ): Result<GetTimelineResponse> {
        return runCatching {
            create().getOrThrow()
            val timeline = client!!.getTimeline(
                GetTimelineQueryParams(
                    algorithm = algorithm,
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

            return Result.success(feed)
        }
    }

    suspend fun post(content: String, images: List<Uri>? = null, video: Uri? = null): Result<Unit> {
        return runCatching {
            create().getOrThrow()

            var postEmbed: PostEmbedUnion? = null

            if (images != null) {
                val blobs = uploadImages(images).getOrThrow()
                postEmbed = PostEmbedUnion.Images(
                    value = Images(
                        images = blobs.map {
                            ImagesImage(
                                image = it,
                                alt = "",
                            )
                        }
                    )
                )
            }

            if (video != null) {
                val blob = uploadVideo(video).getOrThrow()
                postEmbed = PostEmbedUnion.Video(
                    value = Video(
                        video = blob,
                        alt = "",
                    )
                )
            }

            val r = BlueskyJson.encodeAsJsonContent(
                Post(
                    text = content,
                    createdAt = Clock.System.now(),
                    embed = postEmbed
                )
            )

            val postRes = client!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle, // Use handle from the session
                    collection = Nsid("app.bsky.feed.post"),
                    record = r,
                )
            )
            return when (postRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not create post: ${postRes.error?.message}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun uploadImages(images: List<Uri>): Result<List<Blob>> {
        return runCatching {
            create().getOrThrow()

            val uploadedBlobs = mutableListOf<Blob>()

            images.forEach {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val byteArray = inputStream.readBytes()
                    val blob = client!!.uploadBlob(byteArray)
                    when (blob) {
                        is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed uploading image: ${blob.error}"))
                        is AtpResponse.Success<UploadBlobResponse> -> {
                            uploadedBlobs.add(blob.response.blob)
                        }
                    }
                }
            }

            return Result.success(uploadedBlobs)
        }
    }

    suspend fun uploadVideo(video: Uri): Result<Blob> {
        return runCatching {
            create().getOrThrow()

            val uploadedBlobs = mutableListOf<Blob>()

            context.contentResolver.openInputStream(video)?.use { inputStream ->
                val byteArray = inputStream.readBytes()
                val blob = client!!.uploadBlob(byteArray)
                when (blob) {
                    is AtpResponse.Failure<*> -> return Result.failure(Exception("Failed uploading video: ${blob.error}"))
                    is AtpResponse.Success<UploadBlobResponse> -> {
                        uploadedBlobs.add(blob.response.blob)
                    }
                }
            }


            return Result.success(uploadedBlobs.first())
        }
    }

    suspend fun feeds(): Result<List<GeneratorView>> {
        return runCatching {
            create().getOrThrow()

            val prefs = client!!.getPreferences().requireResponse()
            val feedUris = (prefs.preferences.first {
                when (it) {
                    is PreferencesUnion.SavedFeedsPrefV2 -> true
                    else -> false
                }
            } as PreferencesUnion.SavedFeedsPrefV2).value.items.filter {
                it.type.value != "timeline"
            }.map { AtUri(it.value) }

            val resp = client!!.getFeedGenerators(
                GetFeedGeneratorsQueryParams(
                    feedUris
                )
            ).requireResponse()

            return Result.success(resp.feeds)
        }
    }
}