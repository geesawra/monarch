@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.actor.MutedWordActorTarget
import app.bsky.actor.MutedWord
import app.bsky.actor.MutedWordTarget
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.actor.ViewerState
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GetAuthorFeedFilter
import app.bsky.feed.SearchPostsSort
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
import app.bsky.feed.ThreadgateAllowUnion
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import app.bsky.graph.Follow
import app.bsky.notification.ListNotificationsNotificationReason
import app.bsky.richtext.Facet
import com.atproto.repo.StrongRef
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import industries.geesawra.monarch.rkey
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.RKey
import com.atproto.label.Label
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


enum class VideoUploadStatus(val label: String) {
    Compressing("Compressing..."),
    Uploading("Uploading..."),
    Processing("Processing..."),
}

data class TimelineUiState(
    val user: ProfileViewDetailed? = null,
    val selectedFeed: String = "following",
    val feedName: String = "",
    val feedAvatar: String? = null,
    val feeds: List<GeneratorView> = listOf(),
    val skeets: List<SkeetData> = listOf(),
    val feedSkeets: Map<String, List<SkeetData>> = mapOf(),
    val feedCursors: Map<String, String?> = mapOf(),
    val notifications: List<Notification> = listOf(),
    val isFetchingMoreTimeline: Boolean = false,
    val isFetchingMoreNotifications: Boolean = false,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,

    val timelineCursor: String? = null,
    val notificationsCursor: String? = null,
    val unreadNotificationsAmt: Int = 0,
    val seenNotificationsAt: Instant? = null,

    val threadStack: List<ThreadPost> = listOf(),

    val mutedWords: List<MutedWord> = listOf(),

    val followersListDid: Did? = null,
    val followersListName: String? = null,
    val profileFollowers: List<ProfileView> = listOf(),
    val profileFollows: List<ProfileView> = listOf(),
    val profileFollowersCursor: String? = null,
    val profileFollowsCursor: String? = null,
    val showFollowersTab: Boolean = true,

    val loginError: String? = null,
    val error: String? = null,

    // Profile viewer state
    val profileUser: ProfileViewDetailed? = null,
    val profilePosts: List<SkeetData> = listOf(),
    val profileFeedCursor: String? = null,
    val profileFeedFilter: GetAuthorFeedFilter? = null,
    val isFetchingProfile: Boolean = false,
    val isFetchingProfileFeed: Boolean = false,
    val profileNotFound: Boolean = false,

    // Search state
    val searchQuery: String = "",
    val searchPostResults: List<SkeetData> = listOf(),
    val searchActorResults: List<ProfileView> = listOf(),
    val searchPostsCursor: String? = null,
    val searchActorsCursor: String? = null,
    val searchPostsSort: SearchPostsSort = SearchPostsSort.Latest,
    val searchAuthorFilter: String? = null,
    val isSearching: Boolean = false,

    val videoUploadStatus: VideoUploadStatus? = null,
    val videoUploadProgress: Long? = null,
) {
    val currentlyShownThread: ThreadPost get() = threadStack.lastOrNull() ?: ThreadPost()
}

object NotificationBadge {
    val count = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun increment() { count.value++ }
    fun set(value: Int) { count.value = value }
    fun clear() { count.value = 0 }
}

@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val bskyConn: BlueskyConn,
    private val accountManager: AccountManager,
    private val pushNotificationManager: PushNotificationManager,
    val postInteractionStore: PostInteractionStore,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(bskyConn: BlueskyConn): TimelineViewModel
    }

    var uiState by mutableStateOf(TimelineUiState())
        private set

    var accounts by mutableStateOf<List<StoredAccount>>(emptyList())
        private set

    var activeDid by mutableStateOf<String?>(null)
        private set

    private var timelineFetchJob: Job? = null
    private var notificationsFetchJob: Job? = null
    private val seenNotificationsAt: Instant? get() = uiState.seenNotificationsAt

    fun appviewName(): String = bskyConn.appviewName()
    fun appviewProxy(): String? = bskyConn.appviewProxy

    fun changeAppview(newAppviewProxy: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            bskyConn.changeAppview(newAppviewProxy)
            fetchAllNewData(then)
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    private fun handleError(error: Throwable) {
        when (error) {
            is LoginException -> uiState = uiState.copy(loginError = error.message)
            else -> uiState = uiState.copy(error = error.message)
        }
    }

    fun labelDisplayName(label: Label): String = bskyConn.labelDisplayName(label)
    fun labelDescription(label: Label): String? = bskyConn.labelDescription(label)
    fun labelerAvatar(label: Label): String? = bskyConn.labelerAvatar(label)

    fun refreshAccounts() {
        viewModelScope.launch {
            accounts = accountManager.getAccounts()
            activeDid = accountManager.getActiveDid()
        }
    }

    fun saveCurrentAccount() {
        viewModelScope.launch {
            val session = bskyConn.session ?: return@launch
            val pds = bskyConn.pdsURL ?: return@launch
            val appview = bskyConn.appviewProxy ?: return@launch
            val user = uiState.user

            accountManager.addAccount(
                StoredAccount(
                    did = session.did.did,
                    handle = session.handle.handle,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatar?.uri,
                    pdsHost = pds,
                    appviewProxy = appview,
                    sessionJson = session.encodeToJson(),
                )
            )
            refreshAccounts()
        }
    }

    fun switchAccount(did: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val currentSession = bskyConn.session
            if (currentSession != null) {
                accountManager.updateAccountSession(currentSession.did.did, currentSession.encodeToJson())
            }

            val target = accountManager.getAccount(did) ?: return@launch
            accountManager.setActiveDid(did)

            bskyConn.storeSessionData(
                target.pdsHost,
                target.appviewProxy,
                SessionData.decodeFromJson(target.sessionJson)
            )
            bskyConn.resetClients()
            postInteractionStore.clear()
            uiState = TimelineUiState()
            uiState = uiState.copy(authenticated = true, sessionChecked = true)
            refreshAccounts()
            fetchAllNewData(then)
        }
    }

    fun logout(then: () -> Unit = {}) {
        viewModelScope.launch {
            pushNotificationManager.unregisterToken()
            val currentDid = bskyConn.session?.did?.did
            if (currentDid != null) {
                accountManager.removeAccount(currentDid)
            }
            bskyConn.logout()

            val remaining = accountManager.getAccounts()
            if (remaining.isNotEmpty()) {
                val next = remaining.first()
                switchAccount(next.did, then)
            } else {
                uiState = TimelineUiState(sessionChecked = true)
                refreshAccounts()
                then()
            }
        }
    }

    fun onNewLogin() {
        bskyConn.resetClients()
        uiState = TimelineUiState(authenticated = true, sessionChecked = true)
    }

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                uiState = uiState.copy(sessionChecked = true)
                refreshAccounts()
                return@launch
            }

            uiState = uiState.copy(authenticated = true, sessionChecked = true)
            refreshAccounts()
        }
    }

    fun fetchAllNewData(then: () -> Unit = {}) {
        fetchTimeline(fresh = true)
        fetchNotifications(fresh = true)
        fetchMutedWords()
        val fsJob = fetchSelf()
        val fJob = feeds()

        viewModelScope.launch {
            joinAll(timelineFetchJob!!, notificationsFetchJob!!, fsJob, fJob)
            then()
        }
    }

    fun fetchSelf(): Job {
        return viewModelScope.launch {
            bskyConn.fetchSelf().onFailure {
                handleError(it)
            }.onSuccess {
                uiState = uiState.copy(user = it)
                saveCurrentAccount()
            }
        }
    }

    fun fetchTimeline(fresh: Boolean = false, replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads, then: () -> Unit = {}) {
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
                val feedKey = uiState.selectedFeed
                val existingFeedSkeets = uiState.feedSkeets[feedKey] ?: listOf()
                val newSkeets = if (fresh) {
                    response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }.distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                } else {
                    (uiState.skeets + response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                }
                val newFeedSkeets = if (fresh) {
                    newSkeets
                } else {
                    (existingFeedSkeets + response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                }

                uiState = uiState.copy(
                    skeets = newSkeets,
                    feedSkeets = uiState.feedSkeets + (feedKey to newFeedSkeets),
                    feedCursors = uiState.feedCursors + (feedKey to response.cursor),
                    timelineCursor = response.cursor,
                    isFetchingMoreTimeline = false
                )
                newSkeets.forEach { postInteractionStore.seed(it) }
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

            val unreadCount = rawNotifs.notifications.fold(0) { acc, notification ->
                when (notification.isRead) {
                    false -> acc + 1
                    true -> acc
                }
            }
            uiState = uiState.copy(unreadNotificationsAmt = unreadCount)
            NotificationBadge.set(unreadCount)

            val posts = parseRawNotifications(rawNotifs)
            val (notifs, repeatable) = buildNotificationList(rawNotifs, posts)
            val grouped = groupRepeatableNotifications(repeatable)
            val processed = processGroupedNotifications(notifs, grouped)

            if (fresh) {
                uiState = uiState.copy(notifications = listOf())
            }

            val merged = (uiState.notifications + processed).distinctBy { it.uniqueKey() }

            uiState = uiState.copy(
                notifications = merged,
                notificationsCursor = rawNotifs.cursor,
                isFetchingMoreNotifications = false,
            )

            then()
        }
    }

    private suspend fun parseRawNotifications(
        rawNotifs: app.bsky.notification.ListNotificationsResponse,
    ): Map<AtUri, Pair<SkeetData, Post>> {
        val postsToFetch = rawNotifs.notifications.flatMap {
            when (it.reason) {
                ListNotificationsNotificationReason.Like -> {
                    val l: Like = it.record.decodeAs()
                    listOf(l.subject.uri)
                }

                ListNotificationsNotificationReason.Repost -> {
                    val l: Repost = it.record.decodeAs()
                    listOf(l.subject.uri)
                }

                ListNotificationsNotificationReason.Quote -> {
                    val l: Post = it.record.decodeAs()
                    val e = l.embed
                    val quotedUri = when (e) {
                        is PostEmbedUnion.Record -> e.value.record.uri
                        is PostEmbedUnion.RecordWithMedia -> e.value.record.record.uri
                        else -> null
                    }
                    listOfNotNull(it.uri, quotedUri)
                }

                ListNotificationsNotificationReason.Mention -> listOf(it.uri)
                ListNotificationsNotificationReason.Reply -> listOf(it.uri)

                else -> emptyList()
            }
        }.distinct()

        return postsToFetch.chunked(25).fold(mapOf()) { acc, chunk ->
            val fetched = bskyConn.getPosts(chunk).getOrNull() ?: return@fold acc
            acc + fetched.associate {
                val record = it.record.decodeAs<Post>()
                it.uri to (SkeetData.fromPostView(it, it.author) to record)
            }
        }
    }

    private fun buildNotificationList(
        rawNotifs: app.bsky.notification.ListNotificationsResponse,
        posts: Map<AtUri, Pair<SkeetData, Post>>,
    ): Pair<List<Notification>, List<Notification>> {
        val repeatable = mutableListOf<Notification>()
        val notifs = rawNotifs.notifications.mapNotNull {
            when (it.reason) {
                ListNotificationsNotificationReason.Follow -> {
                    val l: Follow = it.record.decodeAs()
                    Notification.Follow(it.author, l.createdAt, !it.isRead)
                }

                ListNotificationsNotificationReason.Like -> {
                    val l: Like = it.record.decodeAs()
                    val lp = posts[l.subject.uri] ?: return@mapNotNull null

                    repeatable += Notification.RawLike(
                        l.subject,
                        lp.first,
                        it.author,
                        l.createdAt,
                        !it.isRead
                    )

                    null
                }

                ListNotificationsNotificationReason.Mention -> {
                    val p: Post = it.record.decodeAs()
                    val hydrated = posts[it.uri]?.first
                    Notification.Mention(
                        Pair(it.cid, it.uri),
                        p,
                        it.author,
                        p.createdAt,
                        !it.isRead,
                        hydrated,
                    )
                }

                ListNotificationsNotificationReason.Quote -> {
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
                        return@mapNotNull null
                    }
                    val lp = posts[quotedUrl] ?: return@mapNotNull null
                    val skeetData = lp.first
                    val post = lp.second
                    if (skeetData.did == null || skeetData.authorHandle == null) return@mapNotNull null
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
                                            did = skeetData.did,
                                            handle = skeetData.authorHandle,
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
                        ),
                        it.author,
                        p.createdAt,
                        !it.isRead,
                        posts[it.uri]?.first,
                    )
                }

                ListNotificationsNotificationReason.Reply -> {
                    val p: Post = it.record.decodeAs()
                    val hydrated = posts[it.uri]?.first
                    Notification.Reply(
                        Pair(it.cid, it.uri),
                        p,
                        it.author,
                        p.createdAt,
                        !it.isRead,
                        hydrated,
                    )
                }

                ListNotificationsNotificationReason.Repost -> {
                    val p: Repost = it.record.decodeAs()
                    val rpp = posts[p.subject.uri] ?: return@mapNotNull null
                    repeatable += Notification.RawRepost(
                        p.subject,
                        rpp.first,
                        it.author,
                        p.createdAt,
                        !it.isRead
                    )

                    null
                }

                else -> {
                    null
                }
            }
        }
        return Pair(notifs, repeatable)
    }

    private fun groupRepeatableNotifications(
        repeatable: List<Notification>,
    ): Map<RepeatableNotification, Map<Cid, RepeatedNotification>> {
        val processedRepeatable =
            mutableMapOf<RepeatableNotification, MutableMap<Cid, RepeatedNotification>>()

        val processRepeatable =
            { kind: RepeatableNotification, list: MutableMap<Cid, RepeatedNotification>, post: SkeetData, author: ProfileView, createdAt: Instant, new: Boolean ->
                if (list.contains(post.cid)) {
                    val l = list[post.cid]!!
                    l.authors += RepeatedAuthor(author, createdAt)
                    if (createdAt > l.timestamp) {
                        l.timestamp = createdAt
                    }
                    list[post.cid] = l
                } else {
                    list[post.cid] = RepeatedNotification(
                        kind = kind,
                        authors = listOf(RepeatedAuthor(author, createdAt)),
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
                        RepeatableNotification.Like, list, it.post, it.author, it.createdAt, it.new,
                    )
                }

                is Notification.RawRepost -> {
                    val list = processedRepeatable.getOrPut(RepeatableNotification.Repost) {
                        mutableMapOf()
                    }
                    processRepeatable(
                        RepeatableNotification.Repost, list, it.post, it.author, it.createdAt, it.new,
                    )
                }

                else -> null
            }
        }

        return processedRepeatable
    }

    private fun processGroupedNotifications(
        notifs: List<Notification>,
        grouped: Map<RepeatableNotification, Map<Cid, RepeatedNotification>>,
    ): List<Notification> {
        val result = notifs.toMutableList()

        grouped.forEach { a, n ->
            when (a) {
                RepeatableNotification.Like -> {
                    n.forEach { _, r ->
                        result += Notification.Like(
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
                        result += Notification.Repost(
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

        return result.sortedByDescending { it.createdAt() }
    }

    fun isNotificationNew(notif: Notification): Boolean {
        return notif.new() && (seenNotificationsAt == null || notif.createdAt() > seenNotificationsAt!!)
    }

    fun updateSeenNotifications() {
        uiState = uiState.copy(seenNotificationsAt = Clock.System.now())
        viewModelScope.launch {
            bskyConn.updateSeenNotifications().onFailure {
                handleError(it)
            }.onSuccess {
                uiState = uiState.copy(unreadNotificationsAmt = 0)
                NotificationBadge.clear()
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
        threadgateRules: List<ThreadgateAllowUnion>? = null,
    ): Result<AtUri> {
        val result = bskyConn.post(
            content,
            images,
            video,
            replyRef,
            quotePostRef,
            facets,
            linkPreview = linkPreview,
            threadgateRules = threadgateRules,
            onVideoStatus = { status, progress ->
                uiState = uiState.copy(videoUploadStatus = status, videoUploadProgress = progress)
            },
        )
        uiState = uiState.copy(videoUploadStatus = null, videoUploadProgress = null)
        result.onSuccess { uri ->
            if (uiState.selectedFeed == "following") {
                kotlinx.coroutines.delay(500)
                bskyConn.getPosts(listOf(uri)).onSuccess { posts ->
                    val newSkeet = posts.firstOrNull()?.let { SkeetData.fromPostView(it, it.author) }
                    if (newSkeet != null) {
                        postInteractionStore.seed(newSkeet)
                        uiState = uiState.copy(skeets = listOf(newSkeet) + uiState.skeets)
                    }
                }
            }
        }
        return result
    }

    fun feeds(): Job {
        return viewModelScope.launch {
            bskyConn.feeds().onFailure {
                handleError(it)
            }.onSuccess {
                uiState = uiState.copy(feeds = it)
            }
        }
    }

    fun applyFeedState(uri: String, displayName: String, avatar: String?) {
        uiState = uiState.copy(
            selectedFeed = uri,
            feedName = displayName,
            feedAvatar = avatar,
            skeets = uiState.feedSkeets[uri] ?: listOf(),
            timelineCursor = uiState.feedCursors[uri],
        )
    }

    fun selectFeed(uri: String, displayName: String, avatar: String?, then: () -> Unit = {}) {
        applyFeedState(uri, displayName, avatar)
        fetchTimeline(fresh = true) { then() }
    }

    fun like(uri: AtUri, cid: Cid) {
        viewModelScope.launch {
            bskyConn.like(uri, cid).onFailure {
                handleError(it)
            }.onSuccess { rkey ->
                postInteractionStore.update(cid) {
                    it.copy(didLike = true, likes = it.likes + 1, likeRkey = rkey)
                }
            }
        }
    }

    fun repost(uri: AtUri, cid: Cid) {
        viewModelScope.launch {
            bskyConn.repost(uri, cid).onFailure {
                handleError(it)
            }.onSuccess { rkey ->
                postInteractionStore.update(cid) {
                    it.copy(didRepost = true, reposts = it.reposts + 1, repostRkey = rkey)
                }
            }
        }
    }

    suspend fun getLikes(uri: AtUri, cursor: String? = null) = bskyConn.getLikes(uri, cursor)
    suspend fun getRepostedBy(uri: AtUri, cursor: String? = null) = bskyConn.getRepostedBy(uri, cursor)
    suspend fun getQuotes(uri: AtUri, cursor: String? = null) = bskyConn.getQuotes(uri, cursor)

    fun deletePost(uri: AtUri, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.deletePost(uri.rkey()).onFailure {
                handleError(it)
            }.onSuccess {
                uiState = uiState.copy(
                    skeets = uiState.skeets.filter { it.uri != uri },
                    profilePosts = uiState.profilePosts.filter { it.uri != uri },
                )
                then()
            }
        }
    }

    fun isOwnPost(skeet: SkeetData): Boolean {
        return skeet.did == bskyConn.session?.did
    }

    fun deleteLike(cid: Cid) {
        val rkey = postInteractionStore.getState(cid, PostInteraction(0, 0, 0, false, false)).value.likeRkey ?: return
        viewModelScope.launch {
            bskyConn.deleteLike(rkey).onFailure {
                handleError(it)
            }.onSuccess {
                postInteractionStore.update(cid) {
                    it.copy(didLike = false, likes = it.likes - 1, likeRkey = null)
                }
            }
        }
    }

    fun deleteRepost(cid: Cid) {
        val rkey = postInteractionStore.getState(cid, PostInteraction(0, 0, 0, false, false)).value.repostRkey ?: return
        viewModelScope.launch {
            bskyConn.deleteRepost(rkey).onFailure {
                handleError(it)
            }.onSuccess {
                postInteractionStore.update(cid) {
                    it.copy(didRepost = false, reposts = it.reposts - 1, repostRkey = null)
                }
            }
        }
    }

    fun startThread(tappedElement: SkeetData) {
        uiState = uiState.copy(threadStack = listOf(ThreadPost(post = tappedElement)))
    }

    fun setThread(tappedElement: SkeetData) {
        uiState = uiState.copy(threadStack = uiState.threadStack + ThreadPost(post = tappedElement))
    }

    fun popThread() {
        if (uiState.threadStack.size > 1) {
            uiState = uiState.copy(threadStack = uiState.threadStack.dropLast(1))
        }
    }

    fun fetchMutedWords() {
        viewModelScope.launch {
            bskyConn.getMutedWords().onSuccess {
                uiState = uiState.copy(mutedWords = it)
            }
        }
    }

    fun addMutedWord(value: String, targets: List<MutedWordTarget>, actorTarget: MutedWordActorTarget) {
        viewModelScope.launch {
            val newWord = MutedWord(
                value = value,
                targets = targets,
                actorTarget = actorTarget,
            )
            val updated = uiState.mutedWords + newWord
            bskyConn.setMutedWords(updated).onSuccess {
                uiState = uiState.copy(mutedWords = updated)
            }.onFailure {
                uiState = uiState.copy(error = it.message)
            }
        }
    }

    fun removeMutedWord(word: MutedWord) {
        viewModelScope.launch {
            val updated = uiState.mutedWords.filter { it.value != word.value || it.targets != word.targets }
            bskyConn.setMutedWords(updated).onSuccess {
                uiState = uiState.copy(mutedWords = updated)
            }.onFailure {
                uiState = uiState.copy(error = it.message)
            }
        }
    }

    fun openFollowersList(did: Did, showFollowers: Boolean, name: String? = null) {
        val displayName = name ?: uiState.profileUser?.let { if (it.did == did) it.displayName ?: it.handle.handle else null } ?: did.did
        uiState = uiState.copy(
            followersListDid = did,
            followersListName = displayName,
            profileFollowers = listOf(),
            profileFollows = listOf(),
            profileFollowersCursor = null,
            profileFollowsCursor = null,
            showFollowersTab = showFollowers,
        )
    }

    fun fetchFollowers(did: Did, fresh: Boolean = false) {
        viewModelScope.launch {
            val cursor = if (fresh) null else uiState.profileFollowersCursor
            bskyConn.getFollowers(did, cursor).onSuccess { res ->
                val updated = if (fresh) res.followers else uiState.profileFollowers + res.followers
                val name = res.subject.displayName ?: res.subject.handle.handle
                uiState = uiState.copy(
                    profileFollowers = updated,
                    profileFollowersCursor = res.cursor,
                    followersListName = name,
                )
            }.onFailure {
                handleError(it)
            }
        }
    }

    fun fetchFollows(did: Did, fresh: Boolean = false) {
        viewModelScope.launch {
            val cursor = if (fresh) null else uiState.profileFollowsCursor
            bskyConn.getFollows(did, cursor).onSuccess { res ->
                val updated = if (fresh) res.follows else uiState.profileFollows + res.follows
                uiState = uiState.copy(
                    profileFollows = updated,
                    profileFollowsCursor = res.cursor,
                )
            }.onFailure {
                handleError(it)
            }
        }
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

                else -> ThreadPost(level = level)
            }
        }

        val parents = mutableListOf<SkeetData>()
        if (level == 0) {
            var current = threadUnion.value.parent
            while (current is ThreadViewPostParentUnion.ThreadViewPost) {
                parents.add(SkeetData.fromPostView(current.value.post, current.value.post.author))
                current = current.value.parent
            }
            parents.reverse()
        }

        val currentPostSkeetData =
            SkeetData.fromPostView(threadUnion.value.post, threadUnion.value.post.author)

        val hasMoreReplies = threadUnion.value.replies.orEmpty().isEmpty() &&
            (currentPostSkeetData.replies ?: 0) > 0

        val replies = threadUnion.value.replies.orEmpty().map { replyUnion ->
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

        if (parents.isNotEmpty()) {
            var result = ThreadPost(post = currentPostSkeetData, level = parents.size, replies = replies, hasMoreReplies = hasMoreReplies)
            for (i in parents.indices.reversed()) {
                result = ThreadPost(post = parents[i], level = i, replies = listOf(result))
            }
            return result
        }

        return ThreadPost(
            post = currentPostSkeetData,
            level = level,
            replies = replies,
            hasMoreReplies = hasMoreReplies,
        )
    }

    suspend fun searchActorsTypeahead(query: String): Result<List<ProfileViewBasic>> {
        return bskyConn.searchActorsTypeahead(query)
    }

    fun getThread(parentHeight: Long = 80, then: () -> Unit) {
        viewModelScope.launch {
            bskyConn.getThread(uiState.currentlyShownThread.post.uri, parentHeight).onFailure {
                handleError(it)
            }.onSuccess {
                val asd = readThread(it.thread)
                uiState = uiState.copy(
                    threadStack = uiState.threadStack.dropLast(1) + asd,
                )
                asd.flatten().forEach { postInteractionStore.seed(it) }
                then()
            }
        }
    }

    fun openProfile(did: Did) {
        val cachedProfile = if (did == bskyConn.session?.did) uiState.user else null
        uiState = uiState.copy(
            profileUser = cachedProfile,
            profilePosts = listOf(),
            profileFeedCursor = null,
            profileFeedFilter = null,
            isFetchingProfile = true,
            isFetchingProfileFeed = true,
            profileNotFound = false,
        )

        viewModelScope.launch {
            bskyConn.fetchActor(did).onFailure {
                handleError(it)
                uiState = uiState.copy(isFetchingProfile = false, profileNotFound = true)
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
                handleError(it)
                uiState = uiState.copy(isFetchingProfileFeed = false)
            }.onSuccess { timeline ->
                val newPosts = timeline.feed.map {
                    SkeetData.fromFeedViewPost(it, bskyConn.session?.did)
                }.distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }

                uiState = uiState.copy(
                    profilePosts = if (fresh) newPosts else (uiState.profilePosts + newPosts).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid },
                    profileFeedCursor = timeline.cursor,
                    isFetchingProfileFeed = false,
                )
                newPosts.forEach { postInteractionStore.seed(it) }
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
                handleError(it)
            }.onSuccess { rkey ->
                val followUri = AtUri("at://${bskyConn.session?.did?.did}/app.bsky.graph.follow/${rkey.rkey}")
                val updatedViewer = (profile.viewer ?: ViewerState()).copy(following = followUri)
                uiState = uiState.copy(
                    profileUser = profile.copy(
                        viewer = updatedViewer,
                        followersCount = (profile.followersCount ?: 0) + 1,
                    )
                )
            }
        }
    }

    fun unfollowProfile() {
        val profile = uiState.profileUser ?: return
        val followUri = profile.viewer?.following ?: return
        viewModelScope.launch {
            bskyConn.unfollow(followUri).onFailure {
                handleError(it)
            }.onSuccess {
                val updatedViewer = profile.viewer!!.copy(following = null)
                uiState = uiState.copy(
                    profileUser = profile.copy(
                        viewer = updatedViewer,
                        followersCount = ((profile.followersCount ?: 0) - 1).coerceAtLeast(0),
                    )
                )
            }
        }
    }

    fun muteProfile() {
        val profile = uiState.profileUser ?: return
        viewModelScope.launch {
            bskyConn.muteActor(profile.did).onFailure {
                handleError(it)
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
                handleError(it)
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess {
                    uiState = uiState.copy(profileUser = it)
                }
            }
        }
    }

    fun search(query: String, fresh: Boolean = true) {
        if (query.isBlank()) {
            uiState = uiState.copy(
                searchQuery = "",
                searchPostResults = listOf(),
                searchActorResults = listOf(),
                searchPostsCursor = null,
                searchActorsCursor = null,
                isSearching = false,
            )
            return
        }

        if (fresh) {
            uiState = uiState.copy(
                searchQuery = query,
                searchPostResults = listOf(),
                searchActorResults = listOf(),
                searchPostsCursor = null,
                searchActorsCursor = null,
                isSearching = true,
            )
        } else {
            uiState = uiState.copy(isSearching = true)
        }

        searchPosts(query, fresh)
        searchActors(query, fresh)
    }

    private fun searchPosts(query: String, fresh: Boolean) {
        val effectiveQuery = if (uiState.searchAuthorFilter != null) {
            "from:${uiState.searchAuthorFilter} $query"
        } else {
            query
        }

        viewModelScope.launch {
            bskyConn.searchPosts(
                query = effectiveQuery,
                sort = uiState.searchPostsSort,
                cursor = if (fresh) null else uiState.searchPostsCursor,
            ).onFailure {
                if (it is CancellationException) return@onFailure
                uiState = uiState.copy(isSearching = false, error = it.message)
            }.onSuccess { (posts, cursor) ->
                val newSkeets = posts.map {
                    SkeetData.fromPostView(it, it.author)
                }
                uiState = uiState.copy(
                    searchPostResults = if (fresh) newSkeets
                    else (uiState.searchPostResults + newSkeets).distinctBy { it.cid },
                    searchPostsCursor = cursor,
                    isSearching = false,
                )
                newSkeets.forEach { postInteractionStore.seed(it) }
            }
        }
    }

    private fun searchActors(query: String, fresh: Boolean) {
        viewModelScope.launch {
            bskyConn.searchActors(
                query = query,
                cursor = if (fresh) null else uiState.searchActorsCursor,
            ).onFailure {
                if (it is CancellationException) return@onFailure
                uiState = uiState.copy(isSearching = false)
            }.onSuccess { (actors, cursor) ->
                uiState = uiState.copy(
                    searchActorResults = if (fresh) actors
                    else (uiState.searchActorResults + actors).distinctBy { it.did },
                    searchActorsCursor = cursor,
                    isSearching = false,
                )
            }
        }
    }

    fun fetchMoreSearchPosts() {
        if (uiState.searchPostsCursor == null || uiState.searchQuery.isBlank()) return
        searchPosts(uiState.searchQuery, fresh = false)
    }

    fun fetchMoreSearchActors() {
        if (uiState.searchActorsCursor == null || uiState.searchQuery.isBlank()) return
        searchActors(uiState.searchQuery, fresh = false)
    }

    fun setSearchSort(sort: SearchPostsSort) {
        uiState = uiState.copy(searchPostsSort = sort)
        if (uiState.searchQuery.isNotBlank()) {
            search(uiState.searchQuery, fresh = true)
        }
    }

    fun setSearchAuthorFilter(handle: String?) {
        uiState = uiState.copy(searchAuthorFilter = handle)
        if (uiState.searchQuery.isNotBlank()) {
            search(uiState.searchQuery, fresh = true)
        }
    }

    fun isOwnProfile(): Boolean {
        return uiState.profileUser?.did == bskyConn.session?.did
    }

    fun updateProfile(
        displayName: String?,
        description: String?,
        pronouns: String?,
        avatarUri: android.net.Uri? = null,
        bannerUri: android.net.Uri? = null,
        then: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            bskyConn.updateProfile(displayName, description, pronouns, avatarUri, bannerUri).onFailure {
                handleError(it)
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