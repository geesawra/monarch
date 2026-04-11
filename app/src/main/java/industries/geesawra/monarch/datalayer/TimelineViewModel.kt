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

data class SessionState(
    val user: ProfileViewDetailed? = null,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,
    val loginError: String? = null,
    val error: String? = null,
)

data class TimelineState(
    val selectedFeed: String = "following",
    val feedName: String = "",
    val feedAvatar: String? = null,
    val feeds: List<GeneratorView> = listOf(),
    val skeets: List<SkeetData> = listOf(),
    val feedSkeets: Map<String, List<SkeetData>> = mapOf(),
    val feedCursors: Map<String, String?> = mapOf(),
    val timelineCursor: String? = null,
    val isFetchingMoreTimeline: Boolean = false,
    val mutedWords: List<MutedWord> = listOf(),
)

data class NotificationsState(
    val notifications: List<Notification> = listOf(),
    val notificationsCursor: String? = null,
    val isFetchingMoreNotifications: Boolean = false,
    val unreadNotificationsAmt: Int = 0,
    val seenNotificationsAt: Instant? = null,
)

data class ThreadState(
    val threadStack: List<ThreadPost> = listOf(),
) {
    val currentlyShownThread: ThreadPost get() = threadStack.lastOrNull() ?: ThreadPost()
}

data class ProfileState(
    val profileUser: ProfileViewDetailed? = null,
    val profilePosts: List<SkeetData> = listOf(),
    val profileFeedCursor: String? = null,
    val profileFeedFilter: GetAuthorFeedFilter? = null,
    val isFetchingProfile: Boolean = false,
    val isFetchingProfileFeed: Boolean = false,
    val profileNotFound: Boolean = false,
)

data class FollowersState(
    val followersListDid: Did? = null,
    val followersListName: String? = null,
    val profileFollowers: List<ProfileView> = listOf(),
    val profileFollows: List<ProfileView> = listOf(),
    val profileFollowersCursor: String? = null,
    val profileFollowsCursor: String? = null,
    val showFollowersTab: Boolean = true,
)

data class SearchState(
    val searchQuery: String = "",
    val searchPostResults: List<SkeetData> = listOf(),
    val searchActorResults: List<ProfileView> = listOf(),
    val searchPostsCursor: String? = null,
    val searchActorsCursor: String? = null,
    val searchPostsSort: SearchPostsSort = SearchPostsSort.Latest,
    val searchAuthorFilter: String? = null,
    val isSearchingPosts: Boolean = false,
    val isSearchingActors: Boolean = false,
) {
    val isSearching: Boolean get() = isSearchingPosts || isSearchingActors
}

data class PublicationsState(
    val publicationsDid: Did? = null,
    val publications: List<PublicationRecord> = emptyList(),
    val hasPublications: Boolean = false,
    val isTabActive: Boolean = false,
    val isFetchingPublications: Boolean = false,
    val selectedPublication: PublicationRecord? = null,
    val documents: List<DocumentRecord> = emptyList(),
    val isFetchingDocuments: Boolean = false,
    val selectedDocument: DocumentRecord? = null,
)

data class VideoUploadState(
    val status: VideoUploadStatus? = null,
    val progress: Long? = null,
)

data class DraftsState(
    val drafts: List<app.bsky.draft.DraftView> = emptyList(),
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val activeDraftId: sh.christian.ozone.api.Tid? = null,
)


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

    // ── Sliced UI state ─────────────────────────────────────────────────────
    // Each group is its own mutableStateOf so composables that read from one
    // group don't recompose when an unrelated group changes. The flat getters
    // below preserve the old `viewModel.field` call-site API.
    var sessionState by mutableStateOf(SessionState()); private set
    var timelineState by mutableStateOf(TimelineState()); private set
    var notificationsState by mutableStateOf(NotificationsState()); private set
    var threadState by mutableStateOf(ThreadState()); private set
    var profileState by mutableStateOf(ProfileState()); private set
    var followersState by mutableStateOf(FollowersState()); private set
    var searchState by mutableStateOf(SearchState()); private set
    var videoUploadState by mutableStateOf(VideoUploadState()); private set
    var publicationsState by mutableStateOf(PublicationsState()); private set
    var draftsState by mutableStateOf(DraftsState()); private set
    var redraftText by mutableStateOf<String?>(null); private set
    var pendingNotificationsTab by mutableStateOf(false)

    fun setRedraft(text: String?) { redraftText = text }

    // ── Flat read-only forwarding getters (compat with existing call sites) ─
    val user: ProfileViewDetailed? get() = sessionState.user
    val authenticated: Boolean get() = sessionState.authenticated
    val sessionChecked: Boolean get() = sessionState.sessionChecked
    val loginError: String? get() = sessionState.loginError
    val error: String? get() = sessionState.error

    val selectedFeed: String get() = timelineState.selectedFeed
    val feedName: String get() = timelineState.feedName
    val feedAvatar: String? get() = timelineState.feedAvatar
    val feeds: List<GeneratorView> get() = timelineState.feeds
    val skeets: List<SkeetData> get() = timelineState.skeets
    val feedSkeets: Map<String, List<SkeetData>> get() = timelineState.feedSkeets
    val feedCursors: Map<String, String?> get() = timelineState.feedCursors
    val timelineCursor: String? get() = timelineState.timelineCursor
    val isFetchingMoreTimeline: Boolean get() = timelineState.isFetchingMoreTimeline
    val mutedWords: List<MutedWord> get() = timelineState.mutedWords

    val notifications: List<Notification> get() = notificationsState.notifications
    val notificationsCursor: String? get() = notificationsState.notificationsCursor
    val isFetchingMoreNotifications: Boolean get() = notificationsState.isFetchingMoreNotifications
    val unreadNotificationsAmt: Int get() = notificationsState.unreadNotificationsAmt
    private val seenNotificationsAt: Instant? get() = notificationsState.seenNotificationsAt

    val threadStack: List<ThreadPost> get() = threadState.threadStack
    val currentlyShownThread: ThreadPost get() = threadState.currentlyShownThread

    val profileUser: ProfileViewDetailed? get() = profileState.profileUser
    val profilePosts: List<SkeetData> get() = profileState.profilePosts
    val profileFeedCursor: String? get() = profileState.profileFeedCursor
    val profileFeedFilter: GetAuthorFeedFilter? get() = profileState.profileFeedFilter
    val isFetchingProfile: Boolean get() = profileState.isFetchingProfile
    val isFetchingProfileFeed: Boolean get() = profileState.isFetchingProfileFeed
    val profileNotFound: Boolean get() = profileState.profileNotFound

    val followersListDid: Did? get() = followersState.followersListDid
    val followersListName: String? get() = followersState.followersListName
    val profileFollowers: List<ProfileView> get() = followersState.profileFollowers
    val profileFollows: List<ProfileView> get() = followersState.profileFollows
    val profileFollowersCursor: String? get() = followersState.profileFollowersCursor
    val profileFollowsCursor: String? get() = followersState.profileFollowsCursor
    val showFollowersTab: Boolean get() = followersState.showFollowersTab

    val searchQuery: String get() = searchState.searchQuery
    val searchPostResults: List<SkeetData> get() = searchState.searchPostResults
    val searchActorResults: List<ProfileView> get() = searchState.searchActorResults
    val searchPostsCursor: String? get() = searchState.searchPostsCursor
    val searchActorsCursor: String? get() = searchState.searchActorsCursor
    val searchPostsSort: SearchPostsSort get() = searchState.searchPostsSort
    val searchAuthorFilter: String? get() = searchState.searchAuthorFilter
    val isSearching: Boolean get() = searchState.isSearching
    val isSearchingPosts: Boolean get() = searchState.isSearchingPosts
    val isSearchingActors: Boolean get() = searchState.isSearchingActors

    val videoUploadStatus: VideoUploadStatus? get() = videoUploadState.status
    val videoUploadProgress: Long? get() = videoUploadState.progress

    // ── Update helpers ──────────────────────────────────────────────────────
    private inline fun updateSession(block: (SessionState) -> SessionState) {
        sessionState = block(sessionState)
    }
    private inline fun updateTimeline(block: (TimelineState) -> TimelineState) {
        timelineState = block(timelineState)
    }
    private inline fun updateNotifications(block: (NotificationsState) -> NotificationsState) {
        notificationsState = block(notificationsState)
    }
    private inline fun updateThread(block: (ThreadState) -> ThreadState) {
        threadState = block(threadState)
    }
    private inline fun updateProfile(block: (ProfileState) -> ProfileState) {
        profileState = block(profileState)
    }
    private inline fun updateFollowers(block: (FollowersState) -> FollowersState) {
        followersState = block(followersState)
    }
    private inline fun updateSearch(block: (SearchState) -> SearchState) {
        searchState = block(searchState)
    }
    private inline fun updateVideoUpload(block: (VideoUploadState) -> VideoUploadState) {
        videoUploadState = block(videoUploadState)
    }
    private inline fun updatePublications(block: (PublicationsState) -> PublicationsState) {
        publicationsState = block(publicationsState)
    }
    private inline fun updateDrafts(block: (DraftsState) -> DraftsState) {
        draftsState = block(draftsState)
    }

    var accounts by mutableStateOf<List<StoredAccount>>(emptyList())
        private set

    var activeDid by mutableStateOf<String?>(null)
        private set

    private var timelineFetchJob: Job? = null
    private var notificationsFetchJob: Job? = null

    private fun resetAllState() {
        sessionState = SessionState()
        timelineState = TimelineState()
        notificationsState = NotificationsState()
        threadState = ThreadState()
        profileState = ProfileState()
        followersState = FollowersState()
        searchState = SearchState()
        videoUploadState = VideoUploadState()
        publicationsState = PublicationsState()
        draftsState = DraftsState()
    }

    fun appviewName(): String = bskyConn.appviewName()
    fun appviewProxy(): String? = bskyConn.appviewProxy

    fun changeAppview(newAppviewProxy: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val activeDid = bskyConn.session?.did?.did ?: return@launch
            accountManager.updateAccountAppviewProxy(activeDid, newAppviewProxy)
            bskyConn.resetClients()
            bskyConn.create()
            fetchAllNewData(then)
        }
    }

    fun clearError() {
        updateSession { it.copy(error = null) }
    }

    private fun handleError(error: Throwable) {
        when (error) {
            is LoginException -> updateSession { it.copy(loginError = error.message) }
            else -> updateSession { it.copy(error = error.message) }
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
            val token = bskyConn.oauthToken ?: return@launch
            val pds = bskyConn.pdsURL ?: return@launch
            val appview = bskyConn.appviewProxy ?: return@launch
            val user = this@TimelineViewModel.user

            accountManager.addAccount(
                StoredAccount(
                    did = session.did.did,
                    handle = session.handle.handle,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatar?.uri,
                    pdsHost = pds,
                    appviewProxy = appview,
                    oauthTokenJson = sh.christian.ozone.BlueskyJson.encodeToString(
                        sh.christian.ozone.oauth.OAuthToken.serializer(),
                        token,
                    ),
                )
            )
            refreshAccounts()
        }
    }

    /**
     * Post-OAuth orchestration: persist the freshly-resolved account into AccountManager,
     * wire BlueskyConn's in-memory state from the OAuth token + handle, mark the session
     * authenticated. Called by the deep-link handler in MainActivity right after
     * BlueskyConn.oauthCompleteLogin returns successfully.
     */
    fun completeOAuthLogin(account: StoredAccount, then: () -> Unit = {}) {
        viewModelScope.launch {
            accountManager.addAccount(account)
            val token = sh.christian.ozone.BlueskyJson.decodeFromString(
                sh.christian.ozone.oauth.OAuthToken.serializer(),
                account.oauthTokenJson,
            )
            bskyConn.initializeInMemory(
                pdsURL = account.pdsHost,
                appviewProxy = account.appviewProxy,
                oauthToken = token,
                handle = sh.christian.ozone.api.Handle(account.handle),
            )
            postInteractionStore.clear()
            resetAllState()
            updateSession { it.copy(authenticated = true, sessionChecked = true) }
            refreshAccounts()
            then()
        }
    }

    fun switchAccount(did: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val currentToken = bskyConn.oauthToken
            val currentSession = bskyConn.session
            if (currentToken != null && currentSession != null) {
                accountManager.updateAccountOAuthToken(
                    currentSession.did.did,
                    sh.christian.ozone.BlueskyJson.encodeToString(
                        sh.christian.ozone.oauth.OAuthToken.serializer(),
                        currentToken,
                    ),
                )
            }

            val target = accountManager.getAccount(did) ?: return@launch
            accountManager.setActiveDid(did)

            val targetToken = sh.christian.ozone.BlueskyJson.decodeFromString(
                sh.christian.ozone.oauth.OAuthToken.serializer(),
                target.oauthTokenJson,
            )
            bskyConn.initializeInMemory(
                pdsURL = target.pdsHost,
                appviewProxy = target.appviewProxy,
                oauthToken = targetToken,
                handle = sh.christian.ozone.api.Handle(target.handle),
            )
            postInteractionStore.clear()
            resetAllState()
            updateSession { it.copy(authenticated = true, sessionChecked = true) }
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
                resetAllState()
                updateSession { it.copy(sessionChecked = true) }
                refreshAccounts()
                then()
            }
        }
    }

    fun onNewLogin() {
        bskyConn.resetClients()
        resetAllState()
        updateSession { it.copy(authenticated = true, sessionChecked = true) }
    }

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                updateSession { it.copy(sessionChecked = true) }
                refreshAccounts()
                return@launch
            }

            updateSession { it.copy(authenticated = true, sessionChecked = true) }
            refreshAccounts()
        }
    }

    fun fetchAllNewData(then: () -> Unit = {}) {
        updateTimeline { it.copy(isFetchingMoreTimeline = true) }
        viewModelScope.launch {
            bskyConn.fetchSelf().onFailure {
                handleError(it)
            }.onSuccess { fetched ->
                updateSession { it.copy(user = fetched) }
                saveCurrentAccount()
            }

            fetchTimeline(fresh = true)
            fetchNotifications(fresh = true)
            fetchMutedWords()
            val fJob = feeds()

            joinAll(timelineFetchJob!!, notificationsFetchJob!!, fJob)
            then()
        }
    }

    fun fetchSelf(): Job {
        return viewModelScope.launch {
            bskyConn.fetchSelf().onFailure {
                handleError(it)
            }.onSuccess { fetched ->
                updateSession { it.copy(user = fetched) }
                saveCurrentAccount()
            }
        }
    }

    fun fetchTimeline(fresh: Boolean = false, replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads, then: () -> Unit = {}) {
        updateTimeline { it.copy(isFetchingMoreTimeline = true) }
        runCatching {
            timelineFetchJob?.cancel()
        }

        timelineFetchJob = viewModelScope.launch {
            bskyConn.refreshLabelCacheIfNeeded()

            when (selectedFeed) {
                "following" -> bskyConn.fetchTimeline(
                    if (fresh) null else timelineCursor
                )

                else -> bskyConn.fetchFeed(
                    feed = selectedFeed,
                    cursor = if (fresh) null else timelineCursor
                )
            }.onSuccess { response ->
                val feedKey = selectedFeed
                val existingFeedSkeets = feedSkeets[feedKey] ?: listOf()
                val currentMutedWords = mutedWords
                val newSkeets = if (fresh) {
                    response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }.distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                } else {
                    (skeets + response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                }.withMuteFlags(currentMutedWords)
                val newFeedSkeets = if (fresh) {
                    newSkeets
                } else {
                    (existingFeedSkeets + response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                }.withMuteFlags(currentMutedWords)

                updateTimeline { t ->
                    t.copy(
                        skeets = newSkeets,
                        feedSkeets = t.feedSkeets + (feedKey to newFeedSkeets),
                        feedCursors = t.feedCursors + (feedKey to response.cursor),
                        timelineCursor = response.cursor,
                        isFetchingMoreTimeline = false,
                    )
                }
                newSkeets.forEach { postInteractionStore.seed(it) }
                then()
            }.onFailure { err ->
                if (err is CancellationException) {
                    return@onFailure
                }

                then()

                updateTimeline { it.copy(isFetchingMoreTimeline = false) }
                updateSession { it.copy(error = "Failed to fetch timeline: ${err.message}") }
            }
        }
    }

    fun fetchNotifications(fresh: Boolean = false, then: () -> Unit = {}) {
        updateNotifications { it.copy(isFetchingMoreNotifications = true) }
        runCatching {
            notificationsFetchJob?.cancel()
        }

        notificationsFetchJob = viewModelScope.launch {
            val rawNotifs = bskyConn.notifications(
                if (fresh) null else notificationsCursor
            )
                .onFailure { err ->
                    if (err is CancellationException) {
                        return@onFailure
                    }

                    then()

                    updateNotifications { it.copy(isFetchingMoreNotifications = false) }
                    updateSession { it.copy(error = "Failed to fetch notifications: ${err.message}") }
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
            updateNotifications { it.copy(unreadNotificationsAmt = unreadCount) }
            NotificationBadge.set(unreadCount)

            val posts = parseRawNotifications(rawNotifs)
            val (notifs, repeatable) = buildNotificationList(rawNotifs, posts)
            val grouped = groupRepeatableNotifications(repeatable)
            val processed = processGroupedNotifications(notifs, grouped)

            if (fresh) {
                updateNotifications { it.copy(notifications = listOf()) }
            }

            val merged = (notifications + processed).distinctBy { it.uniqueKey() }

            updateNotifications {
                it.copy(
                    notifications = merged,
                    notificationsCursor = rawNotifs.cursor,
                    isFetchingMoreNotifications = false,
                )
            }

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
                val existing = list[post.cid]
                if (existing != null) {
                    list[post.cid] = existing.copy(
                        authors = existing.authors + RepeatedAuthor(author, createdAt),
                        timestamp = if (createdAt > existing.timestamp) createdAt else existing.timestamp,
                    )
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
        updateNotifications { it.copy(seenNotificationsAt = Clock.System.now()) }
        viewModelScope.launch {
            bskyConn.updateSeenNotifications().onFailure {
                handleError(it)
            }.onSuccess {
                updateNotifications { it.copy(unreadNotificationsAmt = 0) }
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
                updateVideoUpload { VideoUploadState(status = status, progress = progress) }
            },
        )
        updateVideoUpload { VideoUploadState() }
        result.onSuccess { uri ->
            if (selectedFeed == "following") {
                kotlinx.coroutines.delay(500)
                bskyConn.getPosts(listOf(uri)).onSuccess { posts ->
                    val newSkeet = posts.firstOrNull()?.let { SkeetData.fromPostView(it, it.author) }
                    if (newSkeet != null) {
                        postInteractionStore.seed(newSkeet)
                        updateTimeline { it.copy(skeets = listOf(newSkeet) + it.skeets) }
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
            }.onSuccess { fetched ->
                updateTimeline { it.copy(feeds = fetched) }
            }
        }
    }

    fun applyFeedState(uri: String, displayName: String, avatar: String?) {
        updateTimeline {
            it.copy(
                selectedFeed = uri,
                feedName = displayName,
                feedAvatar = avatar,
                skeets = it.feedSkeets[uri] ?: listOf(),
                timelineCursor = it.feedCursors[uri],
            )
        }
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
                updateTimeline { t -> t.copy(skeets = t.skeets.filter { it.uri != uri }) }
                updateProfile { p -> p.copy(profilePosts = p.profilePosts.filter { it.uri != uri }) }
                then()
            }
        }
    }

    fun isOwnPost(skeet: SkeetData): Boolean {
        return skeet.did == bskyConn.session?.did
    }

    fun fetchDrafts(fresh: Boolean = true) {
        viewModelScope.launch {
            if (draftsState.isLoading) return@launch
            updateDrafts { it.copy(isLoading = true) }
            val cursor = if (fresh) null else draftsState.cursor
            bskyConn.getDrafts(cursor = cursor).onSuccess { response ->
                updateDrafts {
                    it.copy(
                        drafts = if (fresh) response.drafts else it.drafts + response.drafts,
                        cursor = response.cursor,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                updateDrafts { it.copy(isLoading = false) }
                handleError(error)
            }
        }
    }

    fun saveDraft(
        text: String,
        mediaUris: List<Uri> = emptyList(),
        isVideo: Boolean = false,
        replyData: SkeetData? = null,
        isQuotePost: Boolean = false,
        linkPreview: LinkPreviewData? = null,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
    ) {
        viewModelScope.launch {
            val deviceId = bskyConn.deviceId()
            val draft = bskyConn.buildDraft(
                text = text,
                mediaUris = mediaUris,
                isVideo = isVideo,
                replyData = replyData,
                isQuotePost = isQuotePost,
                linkPreview = linkPreview,
                threadgateRules = threadgateRules,
                deviceId = deviceId,
            )
            bskyConn.createDraft(draft).onSuccess {
                fetchDrafts()
            }.onFailure { error ->
                val msg = error.message ?: ""
                if (msg.contains("DraftLimitReached", ignoreCase = true)) {
                    updateSession { it.copy(error = "Draft limit reached. Delete an existing draft to save a new one.") }
                } else {
                    handleError(error)
                }
            }
        }
    }

    fun deleteDraft(id: sh.christian.ozone.api.Tid, then: () -> Unit = {}) {
        viewModelScope.launch {
            bskyConn.deleteDraft(id).onSuccess {
                updateDrafts { it.copy(drafts = it.drafts.filter { d -> d.id != id }) }
                if (draftsState.activeDraftId == id) {
                    updateDrafts { it.copy(activeDraftId = null) }
                }
                then()
            }.onFailure {
                handleError(it)
            }
        }
    }

    fun setActiveDraftId(id: sh.christian.ozone.api.Tid?) {
        updateDrafts { it.copy(activeDraftId = id) }
    }

    fun clearActiveDraft() {
        updateDrafts { it.copy(activeDraftId = null) }
    }

    suspend fun deviceId(): String = bskyConn.deviceId()

    suspend fun fetchPostViews(uris: List<AtUri>): List<app.bsky.feed.PostView>? {
        return bskyConn.getPosts(uris).getOrNull()
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
        updateThread { ThreadState(threadStack = listOf(ThreadPost(post = tappedElement))) }
    }

    fun setThread(tappedElement: SkeetData) {
        updateThread { it.copy(threadStack = it.threadStack + ThreadPost(post = tappedElement)) }
    }

    fun popThread() {
        if (threadStack.size > 1) {
            updateThread { it.copy(threadStack = it.threadStack.dropLast(1)) }
        }
    }

    private fun reapplyMuteFlags(words: List<MutedWord>) {
        updateTimeline { t ->
            t.copy(
                mutedWords = words,
                skeets = t.skeets.withMuteFlags(words),
                feedSkeets = t.feedSkeets.mapValues { (_, list) -> list.withMuteFlags(words) },
            )
        }
        updateProfile { p -> p.copy(profilePosts = p.profilePosts.withMuteFlags(words)) }
        updateSearch { s -> s.copy(searchPostResults = s.searchPostResults.withMuteFlags(words)) }
    }

    fun fetchMutedWords() {
        viewModelScope.launch {
            bskyConn.getMutedWords().onSuccess { fetched ->
                reapplyMuteFlags(fetched)
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
            val updated = mutedWords + newWord
            bskyConn.setMutedWords(updated).onSuccess {
                reapplyMuteFlags(updated)
            }.onFailure { err ->
                updateSession { it.copy(error = err.message) }
            }
        }
    }

    fun removeMutedWord(word: MutedWord) {
        viewModelScope.launch {
            val updated = mutedWords.filter { it.value != word.value || it.targets != word.targets }
            bskyConn.setMutedWords(updated).onSuccess {
                reapplyMuteFlags(updated)
            }.onFailure { err ->
                updateSession { it.copy(error = err.message) }
            }
        }
    }

    fun openFollowersList(did: Did, showFollowers: Boolean, name: String? = null) {
        val displayName = name ?: profileUser?.let { if (it.did == did) it.displayName ?: it.handle.handle else null } ?: did.did
        updateFollowers {
            FollowersState(
                followersListDid = did,
                followersListName = displayName,
                showFollowersTab = showFollowers,
            )
        }
    }

    fun fetchFollowers(did: Did, fresh: Boolean = false) {
        viewModelScope.launch {
            val cursor = if (fresh) null else profileFollowersCursor
            bskyConn.getFollowers(did, cursor).onSuccess { res ->
                val updated = if (fresh) res.followers else profileFollowers + res.followers
                val name = res.subject.displayName ?: res.subject.handle.handle
                updateFollowers {
                    it.copy(
                        profileFollowers = updated,
                        profileFollowersCursor = res.cursor,
                        followersListName = name,
                    )
                }
            }.onFailure {
                handleError(it)
            }
        }
    }

    fun fetchFollows(did: Did, fresh: Boolean = false) {
        viewModelScope.launch {
            val cursor = if (fresh) null else profileFollowsCursor
            bskyConn.getFollows(did, cursor).onSuccess { res ->
                val updated = if (fresh) res.follows else profileFollows + res.follows
                updateFollowers {
                    it.copy(
                        profileFollows = updated,
                        profileFollowsCursor = res.cursor,
                    )
                }
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
            bskyConn.getThread(currentlyShownThread.post.uri, parentHeight).onFailure {
                handleError(it)
            }.onSuccess {
                val asd = readThread(it.thread)
                updateThread { t -> t.copy(threadStack = t.threadStack.dropLast(1) + asd) }
                asd.flatten().forEach { postInteractionStore.seed(it) }
                then()
            }
        }
    }

    fun openProfile(did: Did) {
        val cachedProfile = if (did == bskyConn.session?.did) user else null
        updateProfile {
            ProfileState(
                profileUser = cachedProfile,
                isFetchingProfile = true,
                isFetchingProfileFeed = true,
            )
        }

        viewModelScope.launch {
            bskyConn.fetchActor(did).onFailure {
                handleError(it)
                updateProfile { it.copy(isFetchingProfile = false, profileNotFound = true) }
            }.onSuccess { fetched ->
                updateProfile { it.copy(profileUser = fetched, isFetchingProfile = false) }
            }
        }

        fetchProfileFeed(did, fresh = true)
    }

    fun setPublicationsTabActive(active: Boolean) {
        updatePublications { it.copy(isTabActive = active) }
    }

    fun fetchPublications(did: Did) {
        updatePublications { PublicationsState(publicationsDid = did, isTabActive = true, isFetchingPublications = true) }
        viewModelScope.launch {
            val allPubs = bskyConn.listPublications(did).getOrElse {
                updatePublications { it.copy(isFetchingPublications = false) }
                return@launch
            }
            val allDocs = bskyConn.listDocuments(did).getOrDefault(emptyList())
            val docSites = allDocs.mapNotNull { it.document.site }.toSet()
            val pubs = allPubs.filter { it.uri.atUri in docSites }
            updatePublications { it.copy(publications = pubs, isFetchingPublications = false) }
        }
    }

    fun fetchDocuments(publication: PublicationRecord) {
        updatePublications { it.copy(selectedPublication = publication, isFetchingDocuments = true, documents = emptyList(), selectedDocument = null) }
        viewModelScope.launch {
            val did = publicationsState.publicationsDid ?: return@launch
            bskyConn.listDocuments(did).onFailure {
                updatePublications { it.copy(isFetchingDocuments = false) }
            }.onSuccess { docs ->
                val filtered = docs.filter { it.document.site == publication.uri.atUri }
                    .sortedByDescending { it.document.publishedAt }
                updatePublications { it.copy(documents = filtered, isFetchingDocuments = false) }
            }
        }
    }

    fun selectDocument(document: DocumentRecord) {
        updatePublications { it.copy(selectedDocument = document) }
        val postUris = document.document.contentBlocks
            .filter { it.linkTitle == "Bluesky post" && it.linkUrl?.startsWith("at://") == true }
            .mapNotNull { it.linkUrl?.let { uri -> AtUri(uri) } }
        if (postUris.isEmpty()) return
        viewModelScope.launch {
            bskyConn.getPosts(postUris).onSuccess { postViews ->
                val postMap = postViews.associateBy { it.uri.atUri }
                val updatedBlocks = document.document.contentBlocks.map { block ->
                    if (block.linkTitle == "Bluesky post" && block.linkUrl != null) {
                        val postView = postMap[block.linkUrl]
                        if (postView != null) {
                            block.copy(embeddedPost = SkeetData.fromPostView(postView, postView.author))
                        } else block
                    } else block
                }
                val updatedDoc = document.copy(document = document.document.copy(contentBlocks = updatedBlocks))
                updatePublications { it.copy(selectedDocument = updatedDoc) }
            }
        }
    }

    fun clearSelectedPublication() {
        updatePublications { it.copy(selectedPublication = null, documents = emptyList(), selectedDocument = null) }
    }

    fun fetchProfileFeed(did: Did? = null, fresh: Boolean = false) {
        val profileDid = did ?: profileUser?.did ?: return
        updateProfile { it.copy(isFetchingProfileFeed = true) }

        viewModelScope.launch {
            bskyConn.getAuthorFeed(
                did = profileDid,
                cursor = if (fresh) null else profileFeedCursor,
                filter = profileFeedFilter,
            ).onFailure { err ->
                if (err is CancellationException) return@onFailure
                handleError(err)
                updateProfile { it.copy(isFetchingProfileFeed = false) }
            }.onSuccess { timeline ->
                val currentMutedWords = mutedWords
                val newPosts = timeline.feed.map {
                    SkeetData.fromFeedViewPost(it, bskyConn.session?.did)
                }.distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }

                updateProfile { p ->
                    val merged = if (fresh) newPosts
                        else (p.profilePosts + newPosts).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
                    p.copy(
                        profilePosts = merged.withMuteFlags(currentMutedWords),
                        profileFeedCursor = timeline.cursor,
                        isFetchingProfileFeed = false,
                    )
                }
                newPosts.forEach { postInteractionStore.seed(it) }
            }
        }
    }

    fun changeProfileFeedFilter(filter: GetAuthorFeedFilter?) {
        updateProfile {
            it.copy(
                profileFeedFilter = filter,
                profilePosts = listOf(),
                profileFeedCursor = null,
            )
        }
        fetchProfileFeed(fresh = true)
    }

    fun followProfile() {
        val profile = profileUser ?: return
        viewModelScope.launch {
            bskyConn.follow(profile.did).onFailure {
                handleError(it)
            }.onSuccess { rkey ->
                val followUri = AtUri("at://${bskyConn.session?.did?.did}/app.bsky.graph.follow/${rkey.rkey}")
                val updatedViewer = (profile.viewer ?: ViewerState()).copy(following = followUri)
                updateProfile {
                    it.copy(
                        profileUser = profile.copy(
                            viewer = updatedViewer,
                            followersCount = (profile.followersCount ?: 0) + 1,
                        )
                    )
                }
            }
        }
    }

    fun unfollowProfile() {
        val profile = profileUser ?: return
        val followUri = profile.viewer?.following ?: return
        viewModelScope.launch {
            bskyConn.unfollow(followUri).onFailure {
                handleError(it)
            }.onSuccess {
                val updatedViewer = profile.viewer!!.copy(following = null)
                updateProfile {
                    it.copy(
                        profileUser = profile.copy(
                            viewer = updatedViewer,
                            followersCount = ((profile.followersCount ?: 0) - 1).coerceAtLeast(0),
                        )
                    )
                }
            }
        }
    }

    fun muteProfile() {
        val profile = profileUser ?: return
        viewModelScope.launch {
            bskyConn.muteActor(profile.did).onFailure {
                handleError(it)
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess { fetched ->
                    updateProfile { it.copy(profileUser = fetched) }
                }
            }
        }
    }

    fun unmuteProfile() {
        val profile = profileUser ?: return
        viewModelScope.launch {
            bskyConn.unmuteActor(profile.did).onFailure {
                handleError(it)
            }.onSuccess {
                bskyConn.fetchActor(profile.did).onSuccess { fetched ->
                    updateProfile { it.copy(profileUser = fetched) }
                }
            }
        }
    }

    fun search(query: String, fresh: Boolean = true) {
        if (query.isBlank()) {
            updateSearch { SearchState(searchAuthorFilter = it.searchAuthorFilter, searchPostsSort = it.searchPostsSort) }
            return
        }

        if (fresh) {
            updateSearch {
                SearchState(
                    searchQuery = query,
                    isSearchingPosts = true,
                    isSearchingActors = true,
                    searchAuthorFilter = it.searchAuthorFilter,
                    searchPostsSort = it.searchPostsSort,
                )
            }
        } else {
            updateSearch { it.copy(isSearchingPosts = true, isSearchingActors = true) }
        }

        searchPosts(query, fresh)
        searchActors(query, fresh)
    }

    private fun searchPosts(query: String, fresh: Boolean) {
        val effectiveQuery = if (searchAuthorFilter != null) {
            "from:$searchAuthorFilter $query"
        } else {
            query
        }

        viewModelScope.launch {
            bskyConn.searchPosts(
                query = effectiveQuery,
                sort = searchPostsSort,
                cursor = if (fresh) null else searchPostsCursor,
            ).onFailure { err ->
                if (err is CancellationException) return@onFailure
                updateSearch { it.copy(isSearchingPosts = false) }
                updateSession { it.copy(error = err.message) }
            }.onSuccess { (posts, cursor) ->
                val currentMutedWords = mutedWords
                val newSkeets = posts.map {
                    SkeetData.fromPostView(it, it.author)
                }
                updateSearch { s ->
                    val merged = if (fresh) newSkeets
                        else (s.searchPostResults + newSkeets).distinctBy { it.cid }
                    s.copy(
                        searchPostResults = merged.withMuteFlags(currentMutedWords),
                        searchPostsCursor = cursor,
                        isSearchingPosts = false,
                    )
                }
                newSkeets.forEach { postInteractionStore.seed(it) }
            }
        }
    }

    private fun searchActors(query: String, fresh: Boolean) {
        viewModelScope.launch {
            bskyConn.searchActors(
                query = query,
                cursor = if (fresh) null else searchActorsCursor,
            ).onFailure { err ->
                if (err is CancellationException) return@onFailure
                updateSearch { it.copy(isSearchingActors = false) }
            }.onSuccess { (actors, cursor) ->
                updateSearch { s ->
                    s.copy(
                        searchActorResults = if (fresh) actors
                            else (s.searchActorResults + actors).distinctBy { it.did },
                        searchActorsCursor = cursor,
                        isSearchingActors = false,
                    )
                }
            }
        }
    }

    fun fetchMoreSearchPosts() {
        if (searchPostsCursor == null || searchQuery.isBlank() || searchState.isSearchingPosts) return
        updateSearch { it.copy(isSearchingPosts = true) }
        searchPosts(searchQuery, fresh = false)
    }

    fun fetchMoreSearchActors() {
        if (searchActorsCursor == null || searchQuery.isBlank() || searchState.isSearchingActors) return
        updateSearch { it.copy(isSearchingActors = true) }
        searchActors(searchQuery, fresh = false)
    }

    fun setSearchSort(sort: SearchPostsSort) {
        updateSearch { it.copy(searchPostsSort = sort) }
        if (searchQuery.isNotBlank()) {
            search(searchQuery, fresh = true)
        }
    }

    fun changeSearchAuthorFilter(handle: String?) {
        updateSearch { it.copy(searchAuthorFilter = handle) }
        if (searchQuery.isNotBlank()) {
            search(searchQuery, fresh = true)
        }
    }

    fun isOwnProfile(): Boolean {
        return profileUser?.did == bskyConn.session?.did
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
                val did = profileUser?.did ?: bskyConn.session?.did ?: return@onSuccess
                bskyConn.fetchActor(did).onSuccess { fetched ->
                    updateProfile { it.copy(profileUser = fetched) }
                }
                fetchSelf()
                then(true)
            }
        }
    }
}