@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.GetAuthorFeedFilter
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.Repost
import app.bsky.feed.ThreadViewPostReplieUnion
import app.bsky.graph.Follow
import app.bsky.notification.ListNotificationsReason
import app.bsky.richtext.Facet
import com.atproto.repo.StrongRef
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.RKey
import com.atproto.label.Label
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


data class TimelineUiState(
    val user: ProfileViewDetailed? = null,
    val selectedFeed: String = "following",
    val feedName: String = "Following",
    val feedAvatar: String? = null,
    val feeds: List<GeneratorView> = listOf(),
    val skeets: List<SkeetData> = listOf(),
    val notifications: List<Notification> = listOf(),
    val isFetchingMoreTimeline: Boolean = false,
    val isFetchingMoreNotifications: Boolean = false,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,

    val timelineCursor: String? = null,
    val notificationsCursor: String? = null,
    val unreadNotificationsAmt: Int = 0,

    val cidInteractedWith: Map<Cid, RKey> = mapOf(),

    val currentlyShownThread: ThreadPost = ThreadPost(),

    val loginError: String? = null,
    val error: String? = null,

    // Profile viewer state
    val profileUser: ProfileViewDetailed? = null,
    val profilePosts: List<SkeetData> = listOf(),
    val profileFeedCursor: String? = null,
    val profileFeedFilter: GetAuthorFeedFilter? = null,
    val isFetchingProfile: Boolean = false,
    val isFetchingProfileFeed: Boolean = false,
)

@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val bskyConn: BlueskyConn
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(bskyConn: BlueskyConn): TimelineViewModel
    }

    var uiState by mutableStateOf(TimelineUiState())
        private set

    private var timelineFetchJob: Job? = null
    private var notificationsFetchJob: Job? = null

    fun labelDisplayName(label: Label): String = bskyConn.labelDisplayName(label)
    fun labelDescription(label: Label): String? = bskyConn.labelDescription(label)
    fun labelerAvatar(label: Label): String? = bskyConn.labelerAvatar(label)

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                uiState = uiState.copy(sessionChecked = true)
                return@launch
            }

            uiState = uiState.copy(authenticated = true, sessionChecked = true)
        }
    }

    fun fetchAllNewData(then: () -> Unit = {}) {
        fetchTimeline(fresh = true)
        fetchNotifications(fresh = true)
        val fsJob = fetchSelf()
        val fJob = feeds()

        viewModelScope.launch {
            joinAll(timelineFetchJob!!, notificationsFetchJob!!, fsJob, fJob)
            then()
        }
    }

    suspend fun fetchRecord(uri: AtUri): Result<JsonContent> {
        val ret = bskyConn.fetchRecord(uri).onFailure {
            uiState = when (it) {
                is LoginException -> uiState.copy(loginError = it.message)
                else -> uiState.copy(error = it.message)
            }
        }.getOrThrow()

        return Result.success(ret)
    }

    private suspend fun fetchActor(did: Did): Result<ProfileViewDetailed> {
        val ret = bskyConn.fetchActor(did).onFailure {
            uiState = when (it) {
                is LoginException -> uiState.copy(loginError = it.message)
                else -> uiState.copy(error = it.message)
            }
        }.getOrThrow()

        return Result.success(ret)
    }

    fun fetchSelf(): Job {
        return viewModelScope.launch {
            val ret = bskyConn.fetchSelf().onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(user = it)
            }
        }
    }

    fun fetchTimeline(fresh: Boolean = false, then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreTimeline = true)
        runCatching {
            timelineFetchJob?.cancel()
        }

        timelineFetchJob = viewModelScope.launch {
            bskyConn.refreshLabelCacheIfNeeded()

            when (uiState.selectedFeed) {
                "following" -> bskyConn.fetchTimeline(
                    if (fresh) {
                        null
                    } else {
                        uiState.timelineCursor
                    }
                )

                else -> bskyConn.fetchFeed(
                    feed = uiState.selectedFeed,
                    cursor = if (fresh) {
                        null
                    } else {
                        uiState.timelineCursor
                    }
                )
            }.onSuccess { response ->
                val newSkeets = if (fresh) {
                    response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did) }.distinctBy { it.cid }
                } else {
                    (uiState.skeets + response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did) }).distinctBy { it.cid }
                }

                uiState = uiState.copy(
                    skeets = newSkeets,
                    timelineCursor = response.cursor,
                    isFetchingMoreTimeline = false
                )
                then()
            }.onFailure {
                if (it is CancellationException) {
                    return@onFailure
                }

                then()

                uiState = uiState.copy(
                    isFetchingMoreTimeline = false,
                    error = "Failed to fetch timeline: ${it.message}"
                )
            }
        }
    }

    fun fetchNotifications(fresh: Boolean = false, then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreNotifications = true)
        runCatching {
            notificationsFetchJob?.cancel()
        }

        notificationsFetchJob = viewModelScope.launch {
            val rawNotifs = bskyConn.notifications(
                if (fresh) {
                    null
                } else {
                    uiState.notificationsCursor
                }
            )
                .onFailure {
                    if (it is CancellationException) {
                        return@onFailure
                    }

                    then()

                    uiState = uiState.copy(
                        isFetchingMoreNotifications = false,
                        error = "Failed to fetch notifications: ${it.message}"
                    )
                }.getOrNull()

            if (rawNotifs == null) {
                return@launch
            }

            uiState = uiState.copy(
                unreadNotificationsAmt = rawNotifs.notifications.fold(0) { acc, notification ->
                    when (notification.isRead) {
                        false -> acc + 1
                        true -> acc
                    }
                }
            )

            val repeatable = mutableListOf<Notification>()
            val postsToFetch = rawNotifs.notifications.mapNotNull {
                when (it.reason) {
                    ListNotificationsReason.Like -> {
                        val l: Like = it.record.decodeAs()
                        l.subject.uri
                    }

                    ListNotificationsReason.Repost -> {
                        val l: Repost = it.record.decodeAs()
                        l.subject.uri
                    }

                    ListNotificationsReason.Quote -> {
                        val l: Post = it.record.decodeAs()
                        val e = l.embed
                        when (e) {
                            is PostEmbedUnion.Record -> {
                                e.value.record.uri
                            }

                            is PostEmbedUnion.RecordWithMedia -> {
                                e.value.record.record.uri
                            }

                            else -> null
                        }
                    }

                    else -> null
                }
            }

            val posts =
                postsToFetch.chunked(25).fold(mapOf<AtUri, Pair<SkeetData, Post>>()) { acc, chunk ->
                    acc + bskyConn.getPosts(chunk).getOrThrow()
                        .associate {
                            val record = it.record.decodeAs<Post>()
                            it.uri to (SkeetData.fromPost(
                                (it.cid to it.uri),
                                record,
                                it.author
                            ) to record)
                        }
                }

            // we could bulk request posts here and avoid much of the network IO
            var notifs = rawNotifs.notifications.mapNotNull {
                when (it.reason) {
                    ListNotificationsReason.Follow -> {
                        val l: Follow = it.record.decodeAs()
                        Notification.Follow(it.author, l.createdAt.toStdlibInstant(), !it.isRead)
                    }

                    ListNotificationsReason.Like -> {
                        val l: Like = it.record.decodeAs()
                        val lp = posts[l.subject.uri]!!

                        repeatable += Notification.RawLike(
                            l.subject,
                            lp.first,
                            it.author,
                            l.createdAt.toStdlibInstant(),
                            !it.isRead
                        )

                        null // repeatable, will be processed later
                    }

                    ListNotificationsReason.Mention -> {
                        val p: Post = it.record.decodeAs()
                        Notification.Mention(
                            Pair(it.cid, it.uri),
                            p,
                            it.author,
                            p.createdAt.toStdlibInstant(),
                            !it.isRead
                        )
                    }

                    ListNotificationsReason.Quote -> {
                        val p: Post = it.record.decodeAs()
                        val quotedUrl = when (p.embed) {
                            is PostEmbedUnion.Record -> {
                                (p.embed as PostEmbedUnion.Record).value.record.uri
                            }

                            is PostEmbedUnion.RecordWithMedia -> {
                                (p.embed as PostEmbedUnion.RecordWithMedia).value.record.record.uri
                            }

                            else -> null

                        }

                        if (quotedUrl == null) {
                            throw Exception("quote notification without a record or record media!")
                        }
                        val lp = posts[quotedUrl]!!
                        val skeetData = lp.first
                        val post = lp.second
                        Notification.Quote(
                            Pair(it.cid, it.uri),
                            p,
                            PostViewEmbedUnion.RecordView(
                                value = RecordView(
                                    record = RecordViewRecordUnion.ViewRecord(
                                        value = RecordViewRecord(
                                            uri = skeetData.uri,
                                            cid = skeetData.cid,
                                            author = ProfileViewBasic(
                                                did = skeetData.did!!,
                                                handle = skeetData.authorHandle!!,
                                                displayName = skeetData.authorName,
                                                avatar = skeetData.authorAvatarURL?.let { uri ->
                                                    sh.christian.ozone.api.Uri(
                                                        uri
                                                    )
                                                },
                                                associated = it.author.associated,
                                                viewer = it.author.viewer,
                                                labels = it.author.labels,
                                                createdAt = it.author.createdAt,
                                                verification = it.author.verification,
                                            ),
                                            value = Json.encodeAsJsonContent(post),
                                            indexedAt = post.createdAt
                                        )
                                    )
                                )
                            ), // TODO: handle recordwithmedia
                            it.author,
                            p.createdAt.toStdlibInstant(),
                            !it.isRead
                        )
                    }

                    ListNotificationsReason.Reply -> {
                        val p: Post = it.record.decodeAs()
                        Notification.Reply(
                            Pair(it.cid, it.uri),
                            p,
                            it.author,
                            p.createdAt.toStdlibInstant(),
                            !it.isRead
                        )
                    }

                    ListNotificationsReason.Repost -> {
                        val p: Repost = it.record.decodeAs()
                        val rpp = posts[p.subject.uri]!!
                        repeatable += Notification.RawRepost(
                            p.subject,
                            rpp.first,
                            it.author,
                            p.createdAt.toStdlibInstant(),
                            !it.isRead
                        )

                        null
                    }

                    else -> {
                        null
                    }
                }
            }.toMutableList()

            if (fresh) {
                uiState = uiState.copy(notifications = listOf())
            }

            val processedRepeatable =
                mutableMapOf<RepeatableNotification, MutableMap<SkeetData, RepeatedNotification>>()

            val processRepeatable =
                { kind: RepeatableNotification, list: MutableMap<SkeetData, RepeatedNotification>, ref: StrongRef, post: SkeetData, author: ProfileView, createdAt: Instant, new: Boolean ->
                    if (list.contains(post)) {
                        val l = list[post]!!
                        l.authors += RepeatedAuthor(author, createdAt)
                        if (createdAt > l.timestamp) {
                            l.timestamp = createdAt
                        }
                        list[post] = l
                    } else {
                        list[post] = RepeatedNotification(
                            kind = kind,
                            authors = listOf(
                                RepeatedAuthor(
                                    author,
                                    createdAt
                                )
                            ),
                            post = post,
                            timestamp = createdAt,
                            new = new,
                        )
                    }
                }

            repeatable.fastForEach {
                when (it) {
                    is Notification.RawLike -> {
                        val list = processedRepeatable.getOrPut(RepeatableNotification.Like) {
                            mutableMapOf()
                        }

                        processRepeatable(
                            RepeatableNotification.Like,
                            list,
                            it.subject,
                            it.post,
                            it.author,
                            it.createdAt,
                            it.new,
                        )
                    }

                    is Notification.RawRepost -> {
                        val list = processedRepeatable.getOrPut(RepeatableNotification.Repost) {
                            mutableMapOf()
                        }

                        processRepeatable(
                            RepeatableNotification.Repost,
                            list,
                            it.subject,
                            it.post,
                            it.author,
                            it.createdAt,
                            it.new
                        )
                    }

                    else -> null
                }
            }

            processedRepeatable.forEach { a, n ->
                when (a) {
                    RepeatableNotification.Like -> {
                        n.forEach { _, r ->
                            notifs += Notification.Like(
                                data = r.copy(
                                    r.kind,
                                    r.post,
                                    r.authors.sortedByDescending { it.timestamp },
                                    r.timestamp,
                                ),
                                new = r.new
                            )
                        }
                    }

                    RepeatableNotification.Repost -> {
                        n.forEach { _, r ->
                            notifs += Notification.Like(
                                data = r.copy(
                                    r.kind,
                                    r.post,
                                    r.authors.sortedByDescending { it.timestamp },
                                    r.timestamp
                                ),
                                new = r.new
                            )
                        }
                    }
                }
            }

            notifs = notifs.sortedByDescending { it.createdAt() }.toMutableList()

            uiState = uiState.copy(
                notifications = uiState.notifications + notifs,
                notificationsCursor = rawNotifs.cursor,
                isFetchingMoreNotifications = false,
            )

            then()
        }
    }

    fun updateSeenNotifications() {
        viewModelScope.launch {
            bskyConn.updateSeenNotifications().onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(unreadNotificationsAmt = 0)
            }
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
    ): Result<Unit> {
        return bskyConn.post(
            content,
            images,
            video,
            replyRef,
            quotePostRef,
            facets,
            linkPreview = linkPreview
        ) // TODO: maybe refactor this to use uistate.Error?
    }

    fun feeds(): Job {
        return viewModelScope.launch {
            bskyConn.feeds().onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(feeds = it)
            }
        }
    }

    fun selectFeed(uri: String, displayName: String, avatar: String?, then: () -> Unit = {}) {
        uiState = uiState.copy(
            selectedFeed = uri,
            feedName = displayName,
            feedAvatar = avatar,
        )

        fetchTimeline(fresh = true) { then() }
    }

    fun like(uri: AtUri, cid: Cid, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.like(uri, cid).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(
                    cidInteractedWith = uiState.cidInteractedWith.plus(cid to it)
                )
                then()
            }
        }
    }

    fun repost(uri: AtUri, cid: Cid, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.repost(uri, cid).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(
                    cidInteractedWith = uiState.cidInteractedWith.plus(cid to it)
                )
                then()
            }
        }
    }

    fun deleteLike(cid: Cid, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.deleteLike(uiState.cidInteractedWith[cid]!!).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(
                    cidInteractedWith = uiState.cidInteractedWith.minus(cid)
                )
                then()
            }
        }
    }

    fun deleteRepost(cid: Cid, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.deleteRepost(uiState.cidInteractedWith[cid]!!).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                uiState = uiState.copy(
                    cidInteractedWith = uiState.cidInteractedWith.minus(cid)
                )
                then()
            }
        }
    }

    fun setThread(tappedElement: SkeetData) {
        uiState = uiState.copy(currentlyShownThread = ThreadPost(post = tappedElement))
    }

    private fun readThread(
        threadUnion: GetPostThreadResponseThreadUnion,
        level: Int = 0
    ): ThreadPost {
        if (threadUnion !is GetPostThreadResponseThreadUnion.ThreadViewPost) {
            return when (threadUnion) {
                is GetPostThreadResponseThreadUnion.BlockedPost -> ThreadPost(
                    post = SkeetData(blocked = true),
                    level = level
                )

                is GetPostThreadResponseThreadUnion.NotFoundPost -> ThreadPost(
                    post = SkeetData(notFound = true),
                    level = level
                )

                else -> ThreadPost(level = level) // Default for unknown
            }
        }

        val currentPostSkeetData =
            SkeetData.fromPostView(threadUnion.value.post, threadUnion.value.post.author)

        val replies = threadUnion.value.replies.map { replyUnion ->
            readThread(
                threadUnion = when (replyUnion) {
                    is ThreadViewPostReplieUnion.BlockedPost -> GetPostThreadResponseThreadUnion.BlockedPost(
                        replyUnion.value
                    )

                    is ThreadViewPostReplieUnion.NotFoundPost -> GetPostThreadResponseThreadUnion.NotFoundPost(
                        replyUnion.value
                    )

                    is ThreadViewPostReplieUnion.ThreadViewPost -> GetPostThreadResponseThreadUnion.ThreadViewPost(
                        replyUnion.value
                    )

                    is ThreadViewPostReplieUnion.Unknown -> GetPostThreadResponseThreadUnion.Unknown(
                        replyUnion.value
                    )
                },
                level = level + 1
            )
        }

        return ThreadPost(
            post = currentPostSkeetData,
            level = level,
            replies = replies
        )
    }

    suspend fun searchActorsTypeahead(query: String): Result<List<ProfileViewBasic>> {
        return bskyConn.searchActorsTypeahead(query)
    }

    fun getThread(then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.getThread(uiState.currentlyShownThread.post.uri).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                val asd = readThread(it.thread)
                uiState = uiState.copy(
                    currentlyShownThread = asd
                )
                then()
            }
        }
    }

    fun openProfile(did: Did) {
        uiState = uiState.copy(
            profileUser = null,
            profilePosts = listOf(),
            profileFeedCursor = null,
            profileFeedFilter = null,
            isFetchingProfile = true,
            isFetchingProfileFeed = true,
        )

        viewModelScope.launch {
            bskyConn.fetchActor(did).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message, isFetchingProfile = false)
                    else -> uiState.copy(error = it.message, isFetchingProfile = false)
                }
            }.onSuccess {
                uiState = uiState.copy(profileUser = it, isFetchingProfile = false)
            }
        }

        fetchProfileFeed(did, fresh = true)
    }

    fun fetchProfileFeed(did: Did? = null, fresh: Boolean = false) {
        val profileDid = did ?: uiState.profileUser?.did ?: return
        uiState = uiState.copy(isFetchingProfileFeed = true)

        viewModelScope.launch {
            bskyConn.getAuthorFeed(
                did = profileDid,
                cursor = if (fresh) null else uiState.profileFeedCursor,
                filter = uiState.profileFeedFilter,
            ).onFailure {
                if (it is CancellationException) return@onFailure
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message, isFetchingProfileFeed = false)
                    else -> uiState.copy(error = it.message, isFetchingProfileFeed = false)
                }
            }.onSuccess { timeline ->
                val newPosts = timeline.feed.map {
                    SkeetData.fromFeedViewPost(it, bskyConn.session?.did)
                }.distinctBy { it.cid }

                uiState = uiState.copy(
                    profilePosts = if (fresh) newPosts else (uiState.profilePosts + newPosts).distinctBy { it.cid },
                    profileFeedCursor = timeline.cursor,
                    isFetchingProfileFeed = false,
                )
            }
        }
    }

    fun setProfileFeedFilter(filter: GetAuthorFeedFilter?) {
        uiState = uiState.copy(
            profileFeedFilter = filter,
            profilePosts = listOf(),
            profileFeedCursor = null,
        )
        fetchProfileFeed(fresh = true)
    }

    fun followProfile() {
        val profile = uiState.profileUser ?: return
        viewModelScope.launch {
            bskyConn.follow(profile.did).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                // Re-fetch profile to get updated viewer state
                bskyConn.fetchActor(profile.did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
            }
        }
    }

    fun unfollowProfile() {
        val profile = uiState.profileUser ?: return
        val followUri = profile.viewer?.following ?: return
        viewModelScope.launch {
            bskyConn.unfollow(followUri).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
            }
        }
    }

    fun muteProfile() {
        val profile = uiState.profileUser ?: return
        viewModelScope.launch {
            bskyConn.muteActor(profile.did).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
            }
        }
    }

    fun unmuteProfile() {
        val profile = uiState.profileUser ?: return
        viewModelScope.launch {
            bskyConn.unmuteActor(profile.did).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
            }
        }
    }

    fun isOwnProfile(): Boolean {
        return uiState.profileUser?.did == bskyConn.session?.did
    }

    fun updateProfile(
        displayName: String?,
        description: String?,
        avatarUri: android.net.Uri? = null,
        bannerUri: android.net.Uri? = null,
        then: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            bskyConn.updateProfile(displayName, description, avatarUri, bannerUri).onFailure {
                uiState = when (it) {
                    is LoginException -> uiState.copy(loginError = it.message)
                    else -> uiState.copy(error = it.message)
                }
                then(false)
            }.onSuccess {
                // Refresh both the profile view and the self user data
                val did = uiState.profileUser?.did ?: bskyConn.session?.did ?: return@onSuccess
                bskyConn.fetchActor(did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
                fetchSelf()
                then(true)
            }
        }
    }
}