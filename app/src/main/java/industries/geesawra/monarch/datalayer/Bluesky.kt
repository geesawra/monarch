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
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.AspectRatio
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Record
import app.bsky.embed.Video
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetFeedResponse
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponse
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetPostsResponse
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.PostView
import app.bsky.feed.Repost
import app.bsky.labeler.GetServicesQueryParams
import app.bsky.labeler.GetServicesResponse
import app.bsky.labeler.GetServicesResponseViewUnion
import app.bsky.notification.ListNotificationsQueryParams
import app.bsky.notification.ListNotificationsResponse
import app.bsky.notification.UpdateSeenRequest
import app.bsky.video.GetJobStatusQueryParams
import app.bsky.video.GetJobStatusResponse
import app.bsky.video.JobStatus
import app.bsky.video.State
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
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import com.atproto.server.GetServiceAuthQueryParams
import com.atproto.server.GetServiceAuthResponse
import com.atproto.server.RefreshSessionResponse
import industries.geesawra.monarch.collection
import industries.geesawra.monarch.did
import industries.geesawra.monarch.rkey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.toDeprecatedInstant
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

enum class AuthData {
    PDSHost,
    SessionData,
}

class LoginException(message: String?) : Exception(message)

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
    }
}

data class Timeline(
    val cursor: String? = null,
    val feed: List<FeedViewPost>,
)

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

                val solvedDoc: DIDDoc = BlueskyJson.decodeFromString(DIDDoc.serializer(), body)

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
    private data class Service(
        val id: String,
        val type: String,
        val serviceEndpoint: String
    )

    @Serializable
    private data class DIDDoc(
        val service: List<Service>
    )

    var client: AuthenticatedXrpcBlueskyApi? = null
    var session: SessionData? = null
    var createMutex: Mutex = Mutex()
    var pdsURL: String? = null

    suspend fun storeSessionData(pdsURL: String, session: SessionData) {
        context.dataStore.edit { settings ->
            settings[SESSION] = session.encodeToJson()
            settings[PDSHOST] = pdsURL
        }
    }

    suspend fun cleanSessionData() {
        context.dataStore.edit { settings ->
            settings.remove(SESSION)
            settings.remove(PDSHOST)
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
        createMutex.lock()
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
            is AtpResponse.Failure<*> -> {
                createMutex.unlock()
                return Result.failure(
                    Exception(
                        "Failed to create session: ${
                            s.error?.message?.toLowerCase(
                                Locale.current
                            )
                        }"
                    )
                )
            }

            is AtpResponse.Success<CreateSessionResponse> -> s.response
        }

        storeSessionData(pdsURL, SessionData.fromCreateSessionResponse(sessionResponse))
        session = null
        this.client = null

        createMutex.unlock()
        return Result.success(Unit)
    }

    @Serializable
    private data class atpError(
        val error: String?,
        val message: String?,
    )

    private fun mkClient(
        pds: String,
        sessionData: SessionData,
        labelers: List<String> = listOf()
    ): AuthenticatedXrpcBlueskyApi {
        val hc = HttpClient(OkHttp) {
            defaultRequest {
                url(pds)
                headers["atproto-accept-labelers"] = labelers.joinToString()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }

        return AuthenticatedXrpcBlueskyApi(
            hc,
            BlueskyAuthPlugin.Tokens(sessionData.accessJwt, sessionData.refreshJwt)
        )
    }

    private suspend fun refreshIfNeeded(
        pdsURL: String,
        token: SessionData,
    ): Result<Unit> {
        return runCatching {
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

            val gs = httpClient.get {
                headers["Authorization"] = "Bearer " + token.accessJwt
                url {
                    protocol = URLProtocol.HTTPS
                    path("/xrpc/com.atproto.server.getSession")
                }
            }

            when (gs.status) {
                HttpStatusCode.OK -> run {
                    this.session = token
                    return Result.success(Unit)
                }

                else -> run {
                    val body: String = gs.body()

                    val error: atpError =
                        BlueskyJson.decodeFromString(
                            atpError.serializer(),
                            body
                        )
                    if (error.error == "ExpiredToken") {
                        return@run
                    }
                    cleanSessionData()
                    return Result.failure(Exception("Session checking failed, status code ${gs.status}: ${error.message}"))
                }
            }

            val rs = httpClient.post {
                headers["Authorization"] = "Bearer " + token.refreshJwt
                url {
                    protocol = URLProtocol.HTTPS
                    path("/xrpc/com.atproto.server.refreshSession")
                }
            }

            when (rs.status) {
                HttpStatusCode.OK -> run {
                    val body: String = rs.body()
                    val rs: RefreshSessionResponse =
                        BlueskyJson.decodeFromString(
                            RefreshSessionResponse.serializer(),
                            body
                        )

                    this.session = SessionData.fromRefreshSessionResponse(rs)
                    storeSessionData(pdsURL, this.session!!)
                    return Result.success(Unit)
                }

                else -> run {
                    val body: String = rs.body()

                    val error: atpError =
                        BlueskyJson.decodeFromString(
                            atpError.serializer(),
                            body
                        )
                    cleanSessionData()
                    return Result.failure(Exception("Login refresh failed, status code ${rs.status}: ${error.message}"))
                }
            }

        }
    }

    suspend fun create(): Result<Unit> {
        return runCatching {
            createMutex.lock()
            if (session != null && client != null && pdsURL != null) {
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

            refreshIfNeeded(pdsURL, sessionData).onFailure {
                createMutex.unlock()
                return Result.failure(it)
            }
            this.pdsURL = pdsURL

            this.client = mkClient(
                pdsURL,
                sessionData,
            )

            val labelers = this.subscribedLabelers().getOrThrow().keys.mapNotNull { it?.did }
            this.client = mkClient(
                pdsURL,
                sessionData,
                labelers
            )

            createMutex.unlock()
        }
    }

    suspend fun fetchFeed(feed: String, cursor: String? = null): Result<Timeline> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
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
                return Result.failure(LoginException(it.message))
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
        quotePostRef: StrongRef? = null
    ): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            var postEmbed: PostEmbedUnion? = null

            if (quotePostRef != null) { // TODO: handle image/video plus quote
                postEmbed = PostEmbedUnion.Record(
                    value = Record(quotePostRef)
                )
            } else {

                if (images != null) {
                    val blobs = uploadImages(images).getOrThrow()
                    postEmbed = PostEmbedUnion.Images(
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
                    val blob = uploadVideo(video).getOrThrow()
                    postEmbed = PostEmbedUnion.Video(
                        value = Video(
                            video = blob.blob,
                            alt = "",
                            aspectRatio = AspectRatio(blob.width, blob.height)
                        )
                    )
                }
            }

            val r = BlueskyJson.encodeAsJsonContent(
                Post(
                    text = content,
                    createdAt = Clock.System.now().toDeprecatedInstant(),
                    embed = postEmbed,
                    reply = replyRef
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

    suspend fun fetchRecord(uri: AtUri): Result<JsonContent> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val ret = client!!.getRecord(
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
                return Result.failure(LoginException(it.message))
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

    private data class MediaBlob(
        val blob: Blob,
        val width: Long,
        val height: Long,
    )

    private suspend fun uploadImages(images: List<Uri>): Result<List<MediaBlob>> {
        val maxImageSize = 1000000

        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val uploadedBlobs = mutableListOf<MediaBlob>()

            val compressor = Compressor(context)

            images.forEach {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val compressedImage = run {
                        inputStream.mark(0)
                        val c = compressor.compressImage(it, maxImageSize.toLong())
                        return@run c
                    }

                    val blob = client!!.uploadBlob(compressedImage.data)
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

    private suspend fun uploadVideo(video: Uri): Result<MediaBlob> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, video)
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

            val did = Did("did:web:" + pdsURL!!.toUri().host!!)

            val uploadVideoTicket = client!!.getServiceAuth(
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

            val httpClient = HttpClient(OkHttp) {
                defaultRequest {
                    url("https://video.bsky.app")
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 15000
                }
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
                BlueskyAuthPlugin.Tokens(serviceAuth, serviceAuth)
            )

            val uploadRes = context.contentResolver.openInputStream(video)?.use { inputStream ->
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
                    HttpStatusCode.OK -> {
                        return@use rs.body<UploadVideoResponse>().jobStatus
                    }

                    HttpStatusCode.Conflict -> {
                        // already uploaded once
                        return@use rs.body<JobStatus>()
                    }

                    else -> {
                        return Result.failure(Exception("Failed uploading video: status code ${rs.status}"))
                    }
                }
            }

            while (true) {
                try {
                    val response =
                        videoBskyAppClient.getJobStatus(GetJobStatusQueryParams(uploadRes!!.jobId))

                    val resp = when (response) {
                        is AtpResponse.Failure<*> -> return Result.failure(
                            Exception("Failed video processing job status check: ${response.error}")
                        )

                        is AtpResponse.Success<GetJobStatusResponse> -> response.response.jobStatus
                    }

                    if (resp.blob != null) {
                        uploadedBlobs.add(resp.blob!!)
                        break
                    }

                    when (resp.state) {
                        State.JOBSTATECOMPLETED -> {} // ignore, as we check blobk anyway
                        State.JOBSTATEFAILED -> return Result.failure(Exception("Video processing failed, ${resp.error}: ${resp.message}"))
                        is State.Unknown -> delay(1000)
                    }
                } catch (e: Exception) {
                    // Network or other error. Return the failure and exit the loop.
                    return Result.failure(e)
                }
            }


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
                return Result.failure(LoginException(it.message))
            }
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

    suspend fun subscribedLabelers(): Result<Map<Did?, GetServicesResponseViewUnion.LabelerViewDetailed?>> {
        return runCatching {
            val prefs = client!!.getPreferences().requireResponse()
            val labelers = (prefs.preferences.first {
                when (it) {
                    is PreferencesUnion.LabelersPref -> true
                    else -> false
                }
            } as PreferencesUnion.LabelersPref).value.labelers.map { it.did }.toMutableList()

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

    suspend fun notifications(
        cursor: String? = null,
    ): Result<ListNotificationsResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
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
                return Result.failure(LoginException(it.message))
            }

            val ret = client!!.updateSeen(
                UpdateSeenRequest(
                    seenAt = Clock.System.now().toDeprecatedInstant(),
                )
            )

            return when (ret) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Failed to update seen notifications: ${ret.error}"))
                is AtpResponse.Success<*> -> Result.success(Unit)
            }
        }
    }

    suspend fun getPosts(uri: List<AtUri>): Result<List<PostView>> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
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
                return Result.failure(LoginException(it.message))
            }

            val like = BlueskyJson.encodeAsJsonContent(
                Like(
                    subject = StrongRef(uri, cid),
                    createdAt = Clock.System.now().toDeprecatedInstant(),
                )
            )


            val likeRes = client!!.createRecord(
                CreateRecordRequest(
                    repo = session!!.handle,
                    collection = Nsid("app.bsky.feed.like"),
                    record = like,
                )
            )

            return when (likeRes) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not like post: ${likeRes.error?.message}"))
                is AtpResponse.Success<CreateRecordResponse> -> Result.success(
                    RKey(likeRes.response.uri.atUri.toUri().lastPathSegment!!)
                )
            }
        }
    }

    suspend fun repost(uri: AtUri, cid: Cid): Result<RKey> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val like = BlueskyJson.encodeAsJsonContent(
                Repost(
                    subject = StrongRef(uri, cid),
                    createdAt = Clock.System.now().toDeprecatedInstant(),
                )
            )


            val likeRes = client!!.createRecord(
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

    suspend fun getThread(uri: AtUri): Result<GetPostThreadResponse> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val res = client!!.getPostThread(
                GetPostThreadQueryParams(
                    uri = uri,
                )
            )

            return when (res) {
                is AtpResponse.Failure<*> -> Result.failure(Exception("Could not get thread: ${res.error?.message}"))
                is AtpResponse.Success<GetPostThreadResponse> -> Result.success(res.response)
            }
        }
    }

    private suspend fun deleteRecord(rKey: RKey, collection: String): Result<Unit> {
        return runCatching {
            create().onFailure {
                return Result.failure(LoginException(it.message))
            }

            val delRes = client!!.deleteRecord(
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