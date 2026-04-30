@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import sh.christian.ozone.api.response.StatusCode
import app.bsky.actor.ContentLabelPrefVisibility
import com.atproto.label.Label
import com.atproto.label.LabelValueDefinitionBlurs
import com.atproto.label.LabelValueDefinitionDefaultSetting
import com.atproto.label.LabelValueDefinitionSeverity

import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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

data class CachedFeedInfo(val uri: String, val name: String, val avatar: String?)

data class TimelineState(
    val selectedFeed: String = "following",
    val feedName: String = "",
    val feedAvatar: String? = null,
    val feeds: ImmutableList<GeneratorView> = persistentListOf(),
    val cachedFeedMetadata: ImmutableList<CachedFeedInfo> = persistentListOf(),
    val orderedFeedUris: ImmutableList<String> = persistentListOf("following"),
    val skeets: ImmutableList<SkeetData> = persistentListOf(),
    val feedSkeets: ImmutableMap<String, ImmutableList<SkeetData>> = persistentMapOf(),
    val feedCursors: ImmutableMap<String, String?> = persistentMapOf(),
    val timelineCursor: String? = null,
    val isFetchingMoreTimeline: Boolean = false,
    val mutedWords: ImmutableList<MutedWord> = persistentListOf(),
)

enum class NotificationTab {
    All, Replies, Likes, Reposts;

    fun reasons(): List<String>? = when (this) {
        All -> null
        Replies -> listOf("reply")
        Likes -> listOf("like")
        Reposts -> listOf("repost", "quote")
    }
}

data class PerTabNotificationsState(
    val notifications: ImmutableList<Notification> = persistentListOf(),
    val cursor: String? = null,
    val isFetching: Boolean = false,
)

data class NotificationsState(
    val tabs: ImmutableMap<NotificationTab, PerTabNotificationsState> = NotificationTab.entries.associateWith {
        PerTabNotificationsState()
    }.toImmutableMap(),
    val unreadNotificationsAmt: Int = 0,
    val seenNotificationsAt: Instant? = null,
)

data class ThreadState(
    val threadStack: ImmutableList<ThreadPost> = persistentListOf(),
    val threadNotFound: Boolean = false,
) {
    val currentlyShownThread: ThreadPost get() = threadStack.lastOrNull() ?: ThreadPost()
}

data class ProfileState(
    val profileUser: ProfileViewDetailed? = null,
    val profilePosts: ImmutableList<SkeetData> = persistentListOf(),
    val profileFeedCursor: String? = null,
    val profileFeedFilter: GetAuthorFeedFilter? = null,
    val isFetchingProfile: Boolean = false,
    val isFetchingProfileFeed: Boolean = false,
    val profileNotFound: Boolean = false,
    val profileKey: String? = null,
    val blockNotes: ImmutableMap<String, MonarchAccountNote> = persistentMapOf(),
    val accountNotes: ImmutableMap<String, MonarchAccountNote> = persistentMapOf(),
)

data class FollowersState(
    val followersListDid: Did? = null,
    val followersListName: String? = null,
    val profileFollowers: ImmutableList<ProfileView> = persistentListOf(),
    val profileFollows: ImmutableList<ProfileView> = persistentListOf(),
    val profileFollowersCursor: String? = null,
    val profileFollowsCursor: String? = null,
    val showFollowersTab: Boolean = true,
)

data class SearchState(
    val searchQuery: String = "",
    val searchPostResults: ImmutableList<SkeetData> = persistentListOf(),
    val searchActorResults: ImmutableList<ProfileView> = persistentListOf(),
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
    val publications: ImmutableList<PublicationRecord> = persistentListOf(),
    val hasPublications: Boolean = false,
    val isTabActive: Boolean = false,
    val isFetchingPublications: Boolean = false,
    val selectedPublication: PublicationRecord? = null,
    val documents: ImmutableList<DocumentRecord> = persistentListOf(),
    val isFetchingDocuments: Boolean = false,
    val selectedDocument: DocumentRecord? = null,
)

data class VideoUploadState(
    val status: VideoUploadStatus? = null,
    val progress: Long? = null,
)

data class DraftsState(
    val drafts: ImmutableList<app.bsky.draft.DraftView> = persistentListOf(),
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val activeDraftId: sh.christian.ozone.api.Tid? = null,
)

data class BookmarksState(
    val bookmarks: ImmutableList<SkeetData> = persistentListOf(),
    val cursor: String? = null,
    val isFetchingBookmarks: Boolean = false,
)

object NotificationBadge {
    val count = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun increment() { count.value++ }
    fun set(value: Int) { count.value = value }
    fun clear() { count.value = 0 }
}

@Stable
@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val bskyConn: BlueskyConn,
    private val accountManager: AccountManager,
    private val pushNotificationManager: PushNotificationManager,
    val postInteractionStore: PostInteractionStore,
    val postTranslationStore: PostTranslationStore,
    val altTextGenerator: AltTextGenerator,
    private val translationService: TranslationService,
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
    var bookmarksState by mutableStateOf(BookmarksState()); private set
    var redraftText by mutableStateOf<String?>(null); private set
    var redraftReplyParent by mutableStateOf<SkeetData?>(null); private set
    var redraftPending by mutableStateOf(false); private set
    var pendingNotificationsTab by mutableStateOf(false)
    var hasNewTimelinePosts by mutableStateOf(false); private set
    var uploadingPost by mutableStateOf(false); internal set
    private var postJob: Job? = null

    // Track recent block/unblock mutations to avoid overwriting local state
    // with stale server responses during propagation delay.
    private val recentBlockMutations = mutableMapOf<String, Pair<Boolean, Instant>>()

    private val _dismissCurrentThread = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismissCurrentThread: SharedFlow<Unit> = _dismissCurrentThread.asSharedFlow()

    init {
        viewModelScope.launch {
            val cachedOrder = bskyConn.getCachedFeedOrder()
            val cachedMetadata = bskyConn.getCachedFeedMetadata().map {
                CachedFeedInfo(it.uri, it.name, it.avatar)
            }
            val firstFeed = cachedOrder.firstOrNull() ?: "following"
            val firstFeedMeta = if (firstFeed == "following") {
                CachedFeedInfo("following", "Following", null)
            } else {
                cachedMetadata.find { it.uri == firstFeed }
                    ?: CachedFeedInfo(firstFeed, "Feed", null)
            }
            updateTimeline {
                it.copy(
                    orderedFeedUris = cachedOrder.toImmutableList(),
                    cachedFeedMetadata = cachedMetadata.toImmutableList(),
                    selectedFeed = firstFeed,
                    feedName = firstFeedMeta.name,
                    feedAvatar = firstFeedMeta.avatar
                )
            }
        }
    }

    fun clearNewTimelinePostsIndicator() {
        hasNewTimelinePosts = false
    }

    fun setRedraft(text: String?) {
        redraftText = text
        if (text == null) {
            redraftReplyParent = null
            redraftPending = false
        }
    }

    fun setRedraftSource(source: SkeetData) {
        val hydratedParent = if (source.reply != null) source.parent().first else null
        val recordReply = source.recordReply
        if (hydratedParent != null) {
            redraftReplyParent = hydratedParent
            redraftText = source.content
            return
        }
        if (recordReply == null) {
            redraftReplyParent = null
            redraftText = source.content
            return
        }
        redraftPending = true
        viewModelScope.launch {
            val fetched = bskyConn.getPosts(listOf(recordReply.parent.uri)).getOrNull()?.firstOrNull()
            redraftReplyParent = if (fetched != null) {
                SkeetData.fromPostView(fetched, fetched.author)
            } else {
                SkeetData(
                    uri = recordReply.parent.uri,
                    cid = recordReply.parent.cid,
                    recordReply = recordReply,
                )
            }
            redraftText = source.content
            redraftPending = false
        }
    }

    // ── Flat read-only forwarding getters (compat with existing call sites) ─
    val user: ProfileViewDetailed? get() = sessionState.user
    val authenticated: Boolean get() = sessionState.authenticated
    val sessionChecked: Boolean get() = sessionState.sessionChecked
    val loginError: String? get() = sessionState.loginError
    val error: String? get() = sessionState.error

    val selectedFeed: String get() = timelineState.selectedFeed
    val feedName: String get() = timelineState.feedName
    val feedAvatar: String? get() = timelineState.feedAvatar
    val feeds: ImmutableList<GeneratorView> get() = timelineState.feeds
    val cachedFeedMetadata: ImmutableList<CachedFeedInfo> get() = timelineState.cachedFeedMetadata
    val orderedFeedUris: ImmutableList<String> get() = timelineState.orderedFeedUris
    val skeets: ImmutableList<SkeetData> get() = timelineState.skeets
    val feedSkeets: ImmutableMap<String, ImmutableList<SkeetData>> get() = timelineState.feedSkeets
    val feedCursors: ImmutableMap<String, String?> get() = timelineState.feedCursors
    val timelineCursor: String? get() = timelineState.timelineCursor
    val isFetchingMoreTimeline: Boolean get() = timelineState.isFetchingMoreTimeline
    val mutedWords: ImmutableList<MutedWord> get() = timelineState.mutedWords

    fun notificationsForTab(tab: NotificationTab): ImmutableList<Notification> =
        notificationsState.tabs.getOrElse(tab) { PerTabNotificationsState() }.notifications

    fun isFetchingNotificationsForTab(tab: NotificationTab): Boolean =
        notificationsState.tabs.getOrElse(tab) { PerTabNotificationsState() }.isFetching

    fun notificationsCursorForTab(tab: NotificationTab): String? =
        notificationsState.tabs.getOrElse(tab) { PerTabNotificationsState() }.cursor

    val notifications: ImmutableList<Notification> get() = notificationsForTab(NotificationTab.All)
    val notificationsCursor: String? get() = notificationsCursorForTab(NotificationTab.All)
    val isFetchingMoreNotifications: Boolean get() = NotificationTab.entries.any { isFetchingNotificationsForTab(it) }
    val unreadNotificationsAmt: Int get() = notificationsState.unreadNotificationsAmt
    private val seenNotificationsAt: Instant? get() = notificationsState.seenNotificationsAt
    var activeNotificationTab by mutableStateOf(NotificationTab.All)

    val threadStack: ImmutableList<ThreadPost> get() = threadState.threadStack
    val currentlyShownThread: ThreadPost get() = threadState.currentlyShownThread
    val threadNotFound: Boolean get() = threadState.threadNotFound

    val profileUser: ProfileViewDetailed? get() = profileState.profileUser
    val profilePosts: ImmutableList<SkeetData> get() = profileState.profilePosts
    val profileFeedCursor: String? get() = profileState.profileFeedCursor
    val profileFeedFilter: GetAuthorFeedFilter? get() = profileState.profileFeedFilter
    val isFetchingProfile: Boolean get() = profileState.isFetchingProfile
    val isFetchingProfileFeed: Boolean get() = profileState.isFetchingProfileFeed
    val blockNotes: ImmutableMap<String, MonarchAccountNote> get() = profileState.blockNotes
    val accountNotes: ImmutableMap<String, MonarchAccountNote> get() = profileState.accountNotes
    val profileNotFound: Boolean get() = profileState.profileNotFound
    val currentProfileKey: String? get() = profileState.profileKey

    val followersListDid: Did? get() = followersState.followersListDid
    val followersListName: String? get() = followersState.followersListName
    val profileFollowers: ImmutableList<ProfileView> get() = followersState.profileFollowers
    val profileFollows: ImmutableList<ProfileView> get() = followersState.profileFollows
    val profileFollowersCursor: String? get() = followersState.profileFollowersCursor
    val profileFollowsCursor: String? get() = followersState.profileFollowsCursor
    val showFollowersTab: Boolean get() = followersState.showFollowersTab

    val searchQuery: String get() = searchState.searchQuery
    val searchPostResults: ImmutableList<SkeetData> get() = searchState.searchPostResults
    val searchActorResults: ImmutableList<ProfileView> get() = searchState.searchActorResults
    val searchPostsCursor: String? get() = searchState.searchPostsCursor
    val searchActorsCursor: String? get() = searchState.searchActorsCursor
    val searchPostsSort: SearchPostsSort get() = searchState.searchPostsSort
    val searchAuthorFilter: String? get() = searchState.searchAuthorFilter
    val isSearching: Boolean get() = searchState.isSearching
    val isSearchingPosts: Boolean get() = searchState.isSearchingPosts
    val isSearchingActors: Boolean get() = searchState.isSearchingActors

    val videoUploadStatus: VideoUploadStatus? get() = videoUploadState.status
    val videoUploadProgress: Long? get() = videoUploadState.progress

    fun setPostJob(job: Job?) {
        postJob = job
    }

    fun cancelPost() {
        postJob?.cancel()
        postJob = null
        uploadingPost = false
        videoUploadState = VideoUploadState()
    }

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
    private inline fun updateBookmarks(block: (BookmarksState) -> BookmarksState) {
        bookmarksState = block(bookmarksState)
    }

    var accounts by mutableStateOf<List<StoredAccount>>(emptyList())
        private set

    var activeDid by mutableStateOf<String?>(null)
        private set

    private var timelineFetchJob: Job? = null
    private var notificationsFetchJob: Job? = null
    private var fetchAllJob: Job? = null

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
        bookmarksState = BookmarksState()
    }

    fun appviewName(): String = bskyConn.appviewName()
    fun appviewProxy(): String? = bskyConn.appviewProxy

    fun changeAppview(newAppviewProxy: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val activeDid = bskyConn.session?.did?.did ?: return@launch
            fetchAllJob?.cancel()
            timelineFetchJob?.cancel()
            notificationsFetchJob?.cancel()
            accountManager.updateAccountAppviewProxy(activeDid, newAppviewProxy)
            bskyConn.resetClients()
            bskyConn.create()
            fetchAllNewData(then = then)
        }
    }

    fun clearError() {
        updateSession { it.copy(error = null) }
    }

    private fun handleError(error: Throwable) {
        if (error is CancellationException) return
        when (error) {
            is LoginException -> updateSession { it.copy(loginError = error.message) }
            else -> updateSession { it.copy(error = error.message) }
        }
    }

    private fun Throwable.isProfileNotFound(): Boolean = when (this) {
        is ApiCallFailure ->
            atp.statusCode is StatusCode.InvalidRequest &&
                atp.error?.message?.contains("Could not find actor", ignoreCase = true) == true
        is HandleNotFoundException -> true
        else -> false
    }

    private fun Throwable.isThreadNotFound(): Boolean = when (this) {
        is ApiCallFailure ->
            atp.statusCode is StatusCode.InvalidRequest &&
                atp.error?.error == "NotFound"
        else -> false
    }

    fun labelDisplayName(label: Label): String? = bskyConn.labelDisplayName(label)
    fun labelDescription(label: Label): String? = bskyConn.labelDescription(label)
    fun labelerAvatar(label: Label): String? = bskyConn.labelerAvatar(label)
    fun labelSeverity(label: Label): LabelValueDefinitionSeverity? = bskyConn.labelSeverity(label)
    fun labelBlurs(label: Label): LabelValueDefinitionBlurs? = bskyConn.labelBlurs(label)
    fun labelDefaultSetting(label: Label): LabelValueDefinitionDefaultSetting? = bskyConn.labelDefaultSetting(label)
    fun contentLabelPrefVisibility(label: Label): ContentLabelPrefVisibility? = bskyConn.contentLabelPrefVisibility(label)
    fun labelerName(label: Label): String = bskyConn.labelerName(label)

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
            val existing = accountManager.getAccount(session.did.did)

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
                    authServerURL = existing?.authServerURL,
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
                authServerURL = account.authServerURL,
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
                authServerURL = target.authServerURL,
            )
            postInteractionStore.clear()
            resetAllState()
            updateSession { it.copy(authenticated = true, sessionChecked = true) }
            refreshAccounts()

            if (target.notificationsEnabled) {
                pushNotificationManager.getAndRegisterToken(did)
            }

            fetchAllNewData(then = then)
        }
    }

    fun setAccountNotificationsEnabled(did: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            accountManager.updateAccountNotificationSetting(did, enabled)
            refreshAccounts()
            if (enabled) {
                pushNotificationManager.getAndRegisterToken(did).onSuccess {
                    onResult(Result.success(Unit))
                }.onFailure {
                    accountManager.updateAccountNotificationSetting(did, false)
                    refreshAccounts()
                    onResult(Result.failure(it))
                }
            } else {
                val account = accountManager.getAccount(did)
                if (account != null && accountManager.getActiveDid() == did) {
                    pushNotificationManager.unregisterToken()
                }
                onResult(Result.success(Unit))
            }
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

            // Eagerly initialize the in-memory auth state and HTTP clients before marking
            // the session as authenticated. Without this, any concurrent API call that
            // triggers token refresh will find sharedAuthTokens == null and crash with
            // "No DPoP tokens to refresh".
            bskyConn.create().onFailure {
                // If create() fails (e.g. corrupted token), surface it as a login error so
                // the UI routes to the login screen instead of leaving the user in a broken
                // authenticated state.
                if (it is CancellationException) throw it
                handleError(it)
                updateSession { it.copy(sessionChecked = true) }
                return@launch
            }

            updateSession { it.copy(authenticated = true, sessionChecked = true) }
            refreshAccounts()
        }
    }

    fun fetchAllNewData(refreshNotifications: Boolean = true, then: () -> Unit = {}) {
        fetchAllJob?.cancel()
        updateTimeline { it.copy(isFetchingMoreTimeline = true) }
        fetchAllJob = viewModelScope.launch {
            bskyConn.fetchSelf().onFailure {
                handleError(it)
            }.onSuccess { fetched ->
                updateSession { it.copy(user = fetched) }
                saveCurrentAccount()
            }

            bskyConn.orderedFeedUris().onSuccess { uris ->
                val firstFeed = uris.firstOrNull() ?: "following"
                updateTimeline {
                    it.copy(
                        orderedFeedUris = uris.toImmutableList(),
                        selectedFeed = firstFeed
                    )
                }
            }

            fetchTimeline(fresh = true)
            val nJob = if (refreshNotifications) {
                fetchNotifications(activeNotificationTab, fresh = true)
            } else null
            fetchMutedWords()
            loadAccountNotes()
            val fJob = feeds()

            val jobs = mutableListOf(timelineFetchJob!!, fJob)
            if (nJob != null) {
                jobs += nJob
            }
            joinAll(*jobs.toTypedArray())
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
        suspendRunCatching {
            timelineFetchJob?.cancel()
        }

        timelineFetchJob = viewModelScope.launch {
            val feedKey = selectedFeed
            val cursor = if (fresh) null else timelineCursor

            bskyConn.refreshLabelCacheIfNeeded()

            val response = when (feedKey) {
                "following" -> bskyConn.fetchTimeline(cursor)
                else -> bskyConn.fetchFeed(feed = feedKey, cursor = cursor)
            }.getOrNull()

            if (response == null) {
                then()
                updateTimeline { it.copy(isFetchingMoreTimeline = false) }
                return@launch
            }

            val existingFeedSkeets: List<SkeetData> = feedSkeets[feedKey] ?: persistentListOf()
            val currentMutedWords = mutedWords
            val previousFirstCid = skeets.firstOrNull()?.cid
            val responseSkeets = response.feed.map { SkeetData.fromFeedViewPost(it, bskyConn.session?.did, replyFilterMode) }
                .distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }

            val newFeedSkeets = if (fresh) {
                responseSkeets
            } else {
                (existingFeedSkeets + responseSkeets).distinctBy { if (it.reason is FeedViewPostReasonUnion.ReasonRepost) "repost-${it.cid}" else it.cid.cid }
            }.withMuteFlags(currentMutedWords)

            updateTimeline { t ->
                val isStillOnThisFeed = selectedFeed == feedKey
                t.copy(
                    skeets = if (isStillOnThisFeed) newFeedSkeets else t.skeets,
                    feedSkeets = (t.feedSkeets + (feedKey to newFeedSkeets)).toImmutableMap(),
                    feedCursors = (t.feedCursors + (feedKey to response.cursor)).toImmutableMap(),
                    timelineCursor = if (isStillOnThisFeed) response.cursor else t.timelineCursor,
                    isFetchingMoreTimeline = false,
                )
            }
            if (fresh && previousFirstCid != null && newFeedSkeets.firstOrNull()?.cid != previousFirstCid) {
                hasNewTimelinePosts = true
            }
            newFeedSkeets.forEach { postInteractionStore.seed(it) }
            then()
        }
    }

    fun fetchNotifications(tab: NotificationTab = NotificationTab.All, fresh: Boolean = false, then: () -> Unit = {}): Job {
        val currentTabState = notificationsState.tabs.getOrElse(tab) { PerTabNotificationsState() }
        if (currentTabState.isFetching) { then(); return Job().apply { complete() } }

        updateNotifications { state ->
            state.copy(tabs = state.tabs.toMutableMap().apply {
                this[tab] = currentTabState.copy(isFetching = true)
            }.toImmutableMap())
        }

        val job = viewModelScope.launch {
            val rawNotifs = bskyConn.notifications(
                cursor = if (fresh) null else currentTabState.cursor,
                reasons = tab.reasons(),
            ).onFailure { err ->
                if (err is CancellationException) {
                    return@onFailure
                }

                then()

                updateNotifications { state ->
                    state.copy(tabs = state.tabs.toMutableMap().apply {
                        this[tab] = (state.tabs[tab] ?: PerTabNotificationsState()).copy(isFetching = false)
                    }.toImmutableMap())
                }
                updateSession { it.copy(error = "Failed to fetch notifications: ${err.message}") }
            }.getOrNull()

            if (rawNotifs == null) {
                return@launch
            }

            val oldNotifications = notificationsForTab(tab)
            val existing = if (fresh) persistentListOf() else oldNotifications

            val posts = parseRawNotifications(rawNotifs)
            val (notifs, repeatable) = buildNotificationList(rawNotifs, posts)
            val grouped = groupRepeatableNotifications(repeatable)
            val processed = processGroupedNotifications(notifs, grouped)
            val merged = (existing + processed).distinctBy { it.uniqueKey() }.toPersistentList()

            if (tab == NotificationTab.All && fresh) {
                val isSameData = oldNotifications.isNotEmpty() && processed.isNotEmpty() &&
                    processed.size <= oldNotifications.size &&
                    processed.zip(oldNotifications).all { (a, b) -> a.uniqueKey() == b.uniqueKey() }
                if (isSameData) {
                    updateSeenNotifications()
                }
            }

            updateNotifications { state ->
                state.copy(
                    tabs = state.tabs.toMutableMap().apply {
                        this[tab] = PerTabNotificationsState(
                            notifications = merged,
                            cursor = rawNotifs.cursor,
                            isFetching = false,
                        )
                    }.toImmutableMap()
                )
            }

            if (fresh) {
                bskyConn.getUnreadCount().onSuccess { count ->
                    updateNotifications { it.copy(unreadNotificationsAmt = count.toInt()) }
                    NotificationBadge.set(count.toInt())
                }
            }

            then()
        }
        notificationsFetchJob = job
        return job
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

    fun updateSeenNotifications(seenAt: Instant = Clock.System.now()): Job {
        updateNotifications { it.copy(seenNotificationsAt = seenAt) }
        return viewModelScope.launch {
            bskyConn.updateSeenNotifications(seenAt).onFailure {
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
        mediaAltTexts: Map<Uri, String> = emptyMap(),
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
            mediaAltTexts = mediaAltTexts,
            replyRef = replyRef,
            quotePostRef = quotePostRef,
            facets = facets,
            linkPreview = linkPreview,
            threadgateRules = threadgateRules,
            onVideoStatus = { status, progress ->
                updateVideoUpload { VideoUploadState(status = status, progress = progress) }
            },
        )
        updateVideoUpload { VideoUploadState() }
        result.onSuccess { uri ->
            val shouldAppendToTimeline = selectedFeed == "following"
            val shouldAppendToThread = replyRef != null && threadStack.isNotEmpty()
            if (shouldAppendToTimeline || shouldAppendToThread) {
                kotlinx.coroutines.delay(500)
                bskyConn.getPosts(listOf(uri)).onSuccess { posts ->
                    val newSkeet = posts.firstOrNull()?.let { SkeetData.fromPostView(it, it.author) }
                    if (newSkeet != null) {
                        if (shouldAppendToTimeline) {
                            updateTimeline { it.copy(skeets = it.skeets.toPersistentList().add(0, newSkeet)) }
                        }
                        if (shouldAppendToThread) {
                            val parentUri = replyRef.parent.uri
                            if (threadStack.any { threadContainsPost(it, parentUri) }) {
                                appendReplyToThread(newSkeet, parentUri)
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    suspend fun postThread(
        posts: List<ThreadPostData>,
        threadgateRules: List<ThreadgateAllowUnion>? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<List<PostResult>> {
        val result = bskyConn.postThread(
            posts = posts,
            threadgateRules = threadgateRules,
            onProgress = onProgress,
            onVideoStatus = { status, progress ->
                updateVideoUpload { VideoUploadState(status = status, progress = progress) }
            },
        )
        updateVideoUpload { VideoUploadState() }
        result.onSuccess { postResults ->
            if (selectedFeed == "following" && postResults.isNotEmpty()) {
                kotlinx.coroutines.delay(500)
                bskyConn.getPosts(postResults.map { it.uri }).onSuccess { posts ->
                    val newSkeets = posts.mapNotNull { SkeetData.fromPostView(it, it.author) }
                    newSkeets.forEach { postInteractionStore.seed(it) }
                    updateTimeline { it.copy(skeets = (newSkeets + it.skeets).toImmutableList()) }
                }
            }
        }
        return result
    }

    fun feeds(): Job {
        return viewModelScope.launch {
            bskyConn.orderedFeedUris().onSuccess { uris ->
                updateTimeline { it.copy(orderedFeedUris = uris.toImmutableList()) }
            }
            bskyConn.feeds().onFailure {
                handleError(it)
            }.onSuccess { fetched ->
                updateTimeline { it.copy(feeds = fetched.toPersistentList()) }
            }
        }
    }

    fun applyFeedState(uri: String, displayName: String, avatar: String?) {
        updateTimeline {
            it.copy(
                selectedFeed = uri,
                feedName = displayName,
                feedAvatar = avatar,
                skeets = it.feedSkeets[uri] ?: persistentListOf(),
                timelineCursor = it.feedCursors[uri],
            )
        }
    }

    fun selectFeed(uri: String, displayName: String, avatar: String?, then: () -> Unit = {}) {
        applyFeedState(uri, displayName, avatar)
        fetchTimeline(fresh = true) { then() }
    }

    fun reorderFeeds(newOrder: List<String>, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = bskyConn.reorderFeeds(newOrder)
            result.onSuccess {
                bskyConn.orderedFeedUris().onSuccess { uris ->
                    updateTimeline { it.copy(orderedFeedUris = uris.toImmutableList()) }
                }
                bskyConn.feeds().onSuccess { updatedFeeds ->
                    updateTimeline { it.copy(feeds = updatedFeeds.toImmutableList()) }
                }
            }
            onComplete(result)
        }
    }

    fun like(uri: AtUri, cid: Cid) {
        if (postInteractionStore.peek(cid)?.didLike == true) return
        postInteractionStore.update(cid) {
            it.copy(didLike = true, likes = it.likes + 1)
        }
        viewModelScope.launch {
            bskyConn.like(uri, cid).onFailure {
                postInteractionStore.update(cid) {
                    it.copy(didLike = false, likes = (it.likes - 1).coerceAtLeast(0), likeRkey = null)
                }
                handleError(it)
            }.onSuccess { rkey ->
                postInteractionStore.update(cid) { it.copy(likeRkey = rkey) }
            }
        }
    }

    fun repost(uri: AtUri, cid: Cid) {
        if (postInteractionStore.peek(cid)?.didRepost == true) return
        postInteractionStore.update(cid) {
            it.copy(didRepost = true, reposts = it.reposts + 1)
        }
        viewModelScope.launch {
            bskyConn.repost(uri, cid).onFailure {
                postInteractionStore.update(cid) {
                    it.copy(didRepost = false, reposts = (it.reposts - 1).coerceAtLeast(0), repostRkey = null)
                }
                handleError(it)
            }.onSuccess { rkey ->
                postInteractionStore.update(cid) { it.copy(repostRkey = rkey) }
            }
        }
    }

    suspend fun getLikes(uri: AtUri, cursor: String? = null) = bskyConn.getLikes(uri, cursor)
    suspend fun getRepostedBy(uri: AtUri, cursor: String? = null) = bskyConn.getRepostedBy(uri, cursor)
    suspend fun getQuotes(uri: AtUri, cursor: String? = null) = bskyConn.getQuotes(uri, cursor)
    suspend fun getAlsoLikedPosts(uri: AtUri, cursor: String? = null): Result<Pair<List<SkeetData>, String?>> {
        return try {
            val response = bskyConn.fetchAlsoLiked(uri, cursor).getOrThrow()
            android.util.Log.d("AlsoLiked", "Got ${response.feed.size} URIs from foryou.club")
            if (response.feed.isEmpty()) {
                return Result.success(Pair(emptyList<SkeetData>(), response.cursor))
            }
            val allUris = response.feed.map { AtUri(it.post) }
            val allPosts = mutableListOf<SkeetData>()
            for (chunk in allUris.chunked(25)) {
                android.util.Log.d("AlsoLiked", "Resolving chunk of ${chunk.size} URIs...")
                val posts = bskyConn.getPosts(chunk).getOrThrow()
                allPosts.addAll(posts.map { SkeetData.fromPostView(it, it.author) })
            }
            android.util.Log.d("AlsoLiked", "Resolved ${allPosts.size} posts total")
            Result.success(Pair(allPosts, response.cursor))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("AlsoLiked", "Failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun removePostsLocally(deleted: Set<AtUri>) {
        if (deleted.isEmpty()) return
        updateTimeline { t ->
            t.copy(
                skeets = t.skeets.filter { it.uri !in deleted }.toPersistentList(),
                feedSkeets = t.feedSkeets.mapValues { (_, list) ->
                    list.filter { it.uri !in deleted }.toPersistentList()
                }.toImmutableMap(),
            )
        }
        updateProfile { p ->
            p.copy(profilePosts = p.profilePosts.filter { it.uri !in deleted }.toPersistentList())
        }
    }

    private fun pruneCurrentThread(deleted: Set<AtUri>) {
        if (deleted.isEmpty()) return
        val stack = threadState.threadStack
        val top = stack.lastOrNull() ?: return
        if (top.post.uri in deleted) {
            if (redraftText == null && !redraftPending) {
                _dismissCurrentThread.tryEmit(Unit)
            }
            return
        }
        fun strip(node: ThreadPost): ThreadPost = node.copy(
            replies = node.replies.filter { it.post.uri !in deleted }.map(::strip),
        )
        val pruned = strip(top)
        updateThread { it.copy(threadStack = (stack.dropLast(1) + pruned).toPersistentList()) }
    }

    fun deletePost(uri: AtUri, then: () -> Unit) {
        removePostsLocally(setOf(uri))
        pruneCurrentThread(setOf(uri))
        viewModelScope.launch {
            bskyConn.deletePost(uri.rkey()).onFailure {
                handleError(it)
            }.onSuccess {
                then()
            }
        }
    }

    fun deleteThreadPosts(uris: List<AtUri>, then: () -> Unit) {
        if (uris.isEmpty()) { then(); return }
        val uriSet = uris.toSet()
        removePostsLocally(uriSet)
        pruneCurrentThread(uriSet)
        viewModelScope.launch {
            for (uri in uris) {
                val result = bskyConn.deletePost(uri.rkey())
                if (result.isFailure) {
                    handleError(result.exceptionOrNull() ?: Exception("delete failed"))
                    return@launch
                }
            }
            then()
        }
    }

    suspend fun findSelfAuthoredThreadUris(skeet: SkeetData): List<AtUri> {
        val myDid = bskyConn.session?.did ?: return listOf(skeet.uri)
        if (skeet.did != myDid) return listOf(skeet.uri)
        val response = bskyConn.getThread(skeet.uri, parentHeight = 80).getOrNull()
            ?: return listOf(skeet.uri)
        val tree = readThread(response.thread)
        val path = mutableListOf<ThreadPost>()
        if (!findThreadPath(tree, skeet.uri, path)) return listOf(skeet.uri)
        var topIdx = path.lastIndex
        while (topIdx > 0 && path[topIdx - 1].post.did == myDid) topIdx--
        val out = mutableListOf<AtUri>()
        collectSelfSubtree(path[topIdx], myDid, out)
        return if (out.isEmpty()) listOf(skeet.uri) else out
    }

    private fun findThreadPath(
        node: ThreadPost,
        target: AtUri,
        acc: MutableList<ThreadPost>,
    ): Boolean {
        acc.add(node)
        if (node.post.uri == target) return true
        for (r in node.replies) {
            if (findThreadPath(r, target, acc)) return true
        }
        acc.removeAt(acc.lastIndex)
        return false
    }

    private fun collectSelfSubtree(
        node: ThreadPost,
        selfDid: Did,
        out: MutableList<AtUri>,
    ) {
        if (node.post.did != selfDid) return
        out.add(node.post.uri)
        for (r in node.replies) {
            collectSelfSubtree(r, selfDid, out)
        }
    }

    fun isOwnPost(skeet: SkeetData): Boolean {
        return skeet.did == bskyConn.session?.did
    }

    fun translatePost(skeet: SkeetData, targetLanguage: String, sourceOverride: String? = null) {
        if (skeet.content.isEmpty()) return
        viewModelScope.launch {
            postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.DetectingLanguage)
            val progressListener = object : TranslationProgressListener {
                override fun onDetectingLanguage() {
                    postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.DetectingLanguage)
                }
                override fun onDownloadingModel() {
                    postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.DownloadingModel)
                }
                override fun onTranslating() {
                    postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.Translating)
                }
            }
            translationService.translate(skeet.content, targetLanguage, sourceOverride, progressListener)
                .onSuccess { result ->
                    postTranslationStore.setTranslated(skeet.cid, result)
                }
                .onFailure { error ->
                    postTranslationStore.setError(skeet.cid, error.message ?: "Translation failed")
                }
        }
    }

    fun toggleTranslationOriginal(cid: sh.christian.ozone.api.Cid) {
        postTranslationStore.toggleShowOriginal(cid)
    }

    fun retranslatePost(skeet: SkeetData, targetLanguage: String, sourceLanguage: String) {
        if (skeet.content.isEmpty()) return
        viewModelScope.launch {
            postTranslationStore.retranslate(skeet.cid)
            val progressListener = object : TranslationProgressListener {
                override fun onDetectingLanguage() {}
                override fun onDownloadingModel() {
                    postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.DownloadingModel)
                }
                override fun onTranslating() {
                    postTranslationStore.setPhase(skeet.cid, skeet.content, TranslationPhase.Translating)
                }
            }
            translationService.translate(skeet.content, targetLanguage, sourceLanguage, progressListener)
                .onSuccess { result ->
                    postTranslationStore.setTranslated(skeet.cid, result)
                }
                .onFailure { error ->
                    postTranslationStore.setError(skeet.cid, error.message ?: "Translation failed")
                }
        }
    }

    fun fetchDrafts(fresh: Boolean = true) {
        viewModelScope.launch {
            if (draftsState.isLoading) return@launch
            updateDrafts { it.copy(isLoading = true) }
            val cursor = if (fresh) null else draftsState.cursor
            bskyConn.getDrafts(cursor = cursor).onSuccess { response ->
                updateDrafts {
                    it.copy(
                        drafts = if (fresh) response.drafts.toPersistentList() else (it.drafts + response.drafts).toPersistentList(),
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
                updateDrafts { it.copy(drafts = it.drafts.filter { d -> d.id != id }.toPersistentList()) }
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
        val snapshot = postInteractionStore.peek(cid) ?: return
        val rkey = snapshot.likeRkey ?: return
        postInteractionStore.update(cid) {
            it.copy(didLike = false, likes = (it.likes - 1).coerceAtLeast(0), likeRkey = null)
        }
        viewModelScope.launch {
            bskyConn.deleteLike(rkey).onFailure {
                postInteractionStore.update(cid) {
                    it.copy(didLike = true, likes = it.likes + 1, likeRkey = rkey)
                }
                handleError(it)
            }
        }
    }

    fun deleteRepost(cid: Cid) {
        val snapshot = postInteractionStore.peek(cid) ?: return
        val rkey = snapshot.repostRkey ?: return
        postInteractionStore.update(cid) {
            it.copy(didRepost = false, reposts = (it.reposts - 1).coerceAtLeast(0), repostRkey = null)
        }
        viewModelScope.launch {
            bskyConn.deleteRepost(rkey).onFailure {
                postInteractionStore.update(cid) {
                    it.copy(didRepost = true, reposts = it.reposts + 1, repostRkey = rkey)
                }
                handleError(it)
            }
        }
    }

    fun bookmark(uri: AtUri, cid: Cid) {
        if (postInteractionStore.peek(cid)?.didBookmark == true) return
        postInteractionStore.update(cid) { it.copy(didBookmark = true) }
        viewModelScope.launch {
            bskyConn.createBookmark(uri, cid).onFailure {
                postInteractionStore.update(cid) { it.copy(didBookmark = false) }
                handleError(it)
            }
        }
    }

    fun deleteBookmark(uri: AtUri, cid: Cid) {
        if (postInteractionStore.peek(cid)?.didBookmark != true) return
        postInteractionStore.update(cid) { it.copy(didBookmark = false) }
        updateBookmarks { state ->
            state.copy(bookmarks = state.bookmarks.filter { it.cid != cid }.toPersistentList())
        }
        viewModelScope.launch {
            bskyConn.deleteBookmark(uri).onFailure {
                postInteractionStore.update(cid) { it.copy(didBookmark = true) }
                handleError(it)
            }
        }
    }

    fun fetchBookmarks(refresh: Boolean = false) {
        if (bookmarksState.isFetchingBookmarks) return
        val cursor = if (refresh) null else bookmarksState.cursor
        updateBookmarks { it.copy(isFetchingBookmarks = true) }
        viewModelScope.launch {
            bskyConn.getBookmarks(cursor = cursor).onFailure {
                updateBookmarks { it.copy(isFetchingBookmarks = false) }
                handleError(it)
            }.onSuccess { response ->
                val newBookmarks = response.bookmarks.mapNotNull { bookmark ->
                    when (val item = bookmark.item) {
                        is app.bsky.bookmark.BookmarkViewItemUnion.PostView ->
                            SkeetData.fromPostView(item.value, item.value.author)
                        else -> null
                    }
                }
                newBookmarks.forEach { postInteractionStore.seed(it) }
                updateBookmarks { state ->
                    val combined = if (refresh) {
                        newBookmarks.toPersistentList()
                    } else {
                        (state.bookmarks + newBookmarks).toPersistentList()
                    }
                    state.copy(
                        bookmarks = combined,
                        cursor = response.cursor,
                        isFetchingBookmarks = false,
                    )
                }
            }
        }
    }

    fun startThread(tappedElement: SkeetData) {
        updateThread { ThreadState(threadStack = persistentListOf(ThreadPost(post = tappedElement))) }
    }

    fun setThread(tappedElement: SkeetData) {
        updateThread { it.copy(threadStack = it.threadStack.toPersistentList().add(ThreadPost(post = tappedElement))) }
    }

    fun popThread() {
        if (threadStack.size > 1) {
            updateThread { it.copy(threadStack = it.threadStack.dropLast(1).toPersistentList()) }
        }
    }

    fun appendReplyToThread(newSkeet: SkeetData, parentUri: AtUri) {
        postInteractionStore.seed(newSkeet)
        updateThread { state ->
            state.copy(
                threadStack = state.threadStack.map { thread ->
                    thread.appendReply(parentUri, newSkeet)
                }.toPersistentList()
            )
        }
    }

    private fun threadContainsPost(thread: ThreadPost, uri: AtUri): Boolean {
        return thread.post.uri == uri || thread.replies.any { threadContainsPost(it, uri) }
    }

    private fun reapplyMuteFlags(words: List<MutedWord>) {
        updateTimeline { t ->
            t.copy(
                mutedWords = words.toPersistentList(),
                skeets = t.skeets.withMuteFlags(words),
                feedSkeets = t.feedSkeets.mapValues { (_, list) -> list.withMuteFlags(words) }.toImmutableMap(),
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
                val updated = if (fresh) res.followers.toPersistentList() else (profileFollowers + res.followers).toPersistentList()
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
                val updated = if (fresh) res.follows.toPersistentList() else (profileFollows + res.follows).toPersistentList()
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
                    post = SkeetData(hidden = true),
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
                if (!it.isThreadNotFound()) handleError(it)
                updateThread { it.copy(threadNotFound = true) }
            }.onSuccess {
                val asd = readThread(it.thread)
                val rootNotFound = asd.post.notFound
                updateThread { t ->
                    t.copy(
                        threadStack = (t.threadStack.dropLast(1) + asd).toPersistentList(),
                        threadNotFound = rootNotFound,
                    )
                }
                if (!rootNotFound) {
                    asd.allPosts().forEach { postInteractionStore.seed(it) }
                    then()
                }
            }
        }
    }

    fun openProfile(did: Did, key: String? = null) {
        val cachedProfile = if (did == bskyConn.session?.did) user else null
        updateProfile {
            it.copy(
                profileUser = cachedProfile,
                isFetchingProfile = true,
                isFetchingProfileFeed = true,
                profileKey = key ?: did.did,
            )
        }

        viewModelScope.launch {
            bskyConn.fetchActor(did).onFailure {
                if (!it.isProfileNotFound()) handleError(it)
                updateProfile { it.copy(isFetchingProfile = false, profileNotFound = true) }
            }.onSuccess { fetched ->
                val localMutation = recentBlockMutations[fetched.did.did]
                val isRecent = localMutation?.second?.let {
                    Clock.System.now() - it < 10.seconds
                } == true

                val effectiveProfile = when {
                    // We recently blocked but server hasn't caught up yet
                    isRecent && localMutation!!.first && fetched.viewer?.blocking == null -> {
                        fetched.copy(
                            viewer = (fetched.viewer ?: ViewerState()).copy(
                                blocking = AtUri("at://${bskyConn.session?.did?.did}/app.bsky.graph.block/local"),
                            ),
                        )
                    }
                    // We recently unblocked but server hasn't caught up yet
                    isRecent && !localMutation!!.first && fetched.viewer?.blocking != null -> {
                        fetched.copy(viewer = fetched.viewer!!.copy(blocking = null))
                    }
                    else -> fetched
                }

                updateProfile {
                    it.copy(profileUser = effectiveProfile, isFetchingProfile = false)
                }
                if (effectiveProfile.viewer?.blocking == null) {
                    fetchProfileFeed(did, fresh = true)
                } else {
                    updateProfile { it.copy(isFetchingProfileFeed = false) }
                }
            }
        }
    }

    fun openProfileByHandle(handle: String) {
        updateProfile {
            it.copy(
                isFetchingProfile = true,
                isFetchingProfileFeed = true,
                profileKey = handle,
            )
        }

        viewModelScope.launch {
            BlueskyConn.resolveHandleToDid(handle).onFailure { err ->
                if (!err.isProfileNotFound()) handleError(err)
                updateProfile { it.copy(isFetchingProfile = false, profileNotFound = true) }
            }.onSuccess { did ->
                openProfile(did, key = handle)
            }
        }
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
            val pubs = allPubs.filter { it.uri.atUri in docSites }.toPersistentList()
            updatePublications { it.copy(publications = pubs, isFetchingPublications = false) }
        }
    }

    fun fetchDocuments(publication: PublicationRecord) {
        updatePublications { it.copy(selectedPublication = publication, isFetchingDocuments = true, documents = persistentListOf(), selectedDocument = null) }
        viewModelScope.launch {
            val did = publicationsState.publicationsDid ?: return@launch
            bskyConn.listDocuments(did).onFailure {
                updatePublications { it.copy(isFetchingDocuments = false) }
            }.onSuccess { docs ->
                val filtered = docs.filter { it.document.site == publication.uri.atUri }
                    .sortedByDescending { it.document.publishedAt }
                    .toPersistentList()
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
        updatePublications { it.copy(selectedPublication = null, documents = persistentListOf(), selectedDocument = null) }
    }

    fun fetchProfileFeed(did: Did? = null, fresh: Boolean = false) {
        val profileDid = did ?: profileUser?.did ?: return
        if (profileUser?.viewer?.blocking != null) return
        updateProfile { it.copy(isFetchingProfileFeed = true) }

        viewModelScope.launch {
            bskyConn.getAuthorFeed(
                did = profileDid,
                cursor = if (fresh) null else profileFeedCursor,
                filter = profileFeedFilter,
            ).onFailure { err ->
                if (err is CancellationException) return@onFailure
                if (!profileNotFound && !err.isProfileNotFound()) handleError(err)
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
                profilePosts = persistentListOf(),
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

    fun loadAccountNotes() {
        viewModelScope.launch {
            bskyConn.getBlockNotes().onSuccess { notes ->
                updateProfile {
                    it.copy(blockNotes = notes.associateBy { n -> n.did }.toImmutableMap())
                }
            }
            bskyConn.getAccountNotes().onSuccess { notes ->
                updateProfile {
                    it.copy(accountNotes = notes.associateBy { n -> n.did }.toImmutableMap())
                }
            }
        }
    }

    fun blockProfile(note: String = "") {
        val profile = profileUser ?: return
        viewModelScope.launch {
            bskyConn.blockActor(profile.did).onFailure {
                handleError(it)
            }.onSuccess { rkey ->
                recentBlockMutations[profile.did.did] = true to Clock.System.now()
                val updatedViewer = (profile.viewer ?: ViewerState()).copy(
                    blocking = AtUri("at://${bskyConn.session?.did?.did}/app.bsky.graph.block/${rkey.rkey}"),
                )
                updateProfile {
                    it.copy(
                        profileUser = profile.copy(viewer = updatedViewer),
                        profilePosts = persistentListOf(),
                        profileFeedCursor = null,
                    )
                }
                if (note.isNotBlank()) {
                    bskyConn.addBlockNote(profile.did, note).onSuccess {
                        updateProfile { p ->
                            p.copy(
                                blockNotes = p.blockNotes.toMutableMap().apply {
                                    put(
                                        profile.did.did,
                                        MonarchAccountNote(
                                            did = profile.did.did,
                                            note = note,
                                            createdAt = Clock.System.now().toString(),
                                        ),
                                    )
                                }.toImmutableMap(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun unblockProfile() {
        val profile = profileUser ?: return
        val blockUri = profile.viewer?.blocking ?: return
        viewModelScope.launch {
            bskyConn.unblockActor(blockUri).onFailure {
                handleError(it)
            }.onSuccess {
                recentBlockMutations[profile.did.did] = false to Clock.System.now()
                val updatedViewer = profile.viewer!!.copy(blocking = null)
                updateProfile {
                    it.copy(
                        profileUser = profile.copy(viewer = updatedViewer),
                    )
                }
                fetchProfileFeed(fresh = true)
                bskyConn.removeBlockNote(profile.did).onFailure { err ->
                    handleError(err)
                }
            }
        }
    }

    fun getBlockNote(did: Did): MonarchAccountNote? = blockNotes[did.did]
    fun getAccountNote(did: Did): MonarchAccountNote? = accountNotes[did.did]

    fun saveBlockNote(did: Did, note: String) {
        viewModelScope.launch {
            bskyConn.addBlockNote(did, note).onSuccess {
                updateProfile { p ->
                    p.copy(
                        blockNotes = p.blockNotes.toMutableMap().apply {
                            put(
                                did.did,
                                MonarchAccountNote(
                                    did = did.did,
                                    note = note,
                                    createdAt = Clock.System.now().toString(),
                                ),
                            )
                        }.toImmutableMap(),
                    )
                }
            }.onFailure { handleError(it) }
        }
    }

    fun deleteBlockNote(did: Did) {
        viewModelScope.launch {
            bskyConn.removeBlockNote(did).onSuccess {
                updateProfile { p ->
                    p.copy(
                        blockNotes = p.blockNotes.toMutableMap().apply {
                            remove(did.did)
                        }.toImmutableMap(),
                    )
                }
            }.onFailure { handleError(it) }
        }
    }

    fun saveAccountNote(did: Did, note: String) {
        viewModelScope.launch {
            bskyConn.addAccountNote(did, note).onSuccess {
                updateProfile { p ->
                    p.copy(
                        accountNotes = p.accountNotes.toMutableMap().apply {
                            put(
                                did.did,
                                MonarchAccountNote(
                                    did = did.did,
                                    note = note,
                                    createdAt = Clock.System.now().toString(),
                                ),
                            )
                        }.toImmutableMap(),
                    )
                }
            }.onFailure { handleError(it) }
        }
    }

    fun deleteAccountNote(did: Did) {
        viewModelScope.launch {
            bskyConn.removeAccountNote(did).onSuccess {
                updateProfile { p ->
                    p.copy(
                        accountNotes = p.accountNotes.toMutableMap().apply {
                            remove(did.did)
                        }.toImmutableMap(),
                    )
                }
            }.onFailure { handleError(it) }
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
                        searchActorResults = if (fresh) actors.toPersistentList()
                            else (s.searchActorResults + actors).distinctBy { it.did }.toPersistentList(),
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

    fun isPinnedPost(skeet: SkeetData): Boolean {
        val pinned = profileUser?.pinnedPost ?: user?.pinnedPost
        return pinned != null && pinned.uri == skeet.uri && pinned.cid == skeet.cid
    }

    fun pinPost(uri: AtUri, cid: Cid) {
        viewModelScope.launch {
            bskyConn.pinPost(uri, cid).onFailure {
                handleError(it)
            }.onSuccess {
                val did = profileUser?.did ?: bskyConn.session?.did ?: return@onSuccess
                bskyConn.fetchActor(did).onSuccess { fetched ->
                    updateProfile { it.copy(profileUser = fetched) }
                }
                fetchSelf()
            }
        }
    }

    fun unpinPost() {
        viewModelScope.launch {
            bskyConn.unpinPost().onFailure {
                handleError(it)
            }.onSuccess {
                val did = profileUser?.did ?: bskyConn.session?.did ?: return@onSuccess
                bskyConn.fetchActor(did).onSuccess { fetched ->
                    updateProfile { it.copy(profileUser = fetched) }
                }
                fetchSelf()
            }
        }
    }
}