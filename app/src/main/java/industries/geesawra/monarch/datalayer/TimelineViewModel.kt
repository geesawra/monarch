@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.GeneratorView
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostReplyRef
import app.bsky.feed.Repost
import app.bsky.graph.Follow
import app.bsky.notification.ListNotificationsReason
import com.atproto.repo.StrongRef
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.toStdlibInstant
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime


data class TimelineUiState(
    val selectedFeed: String = "following",
    val feedName: String = "Following",
    val feedAvatar: String? = null,
    val feeds: List<GeneratorView> = listOf(),
    val skeets: List<SkeetData> = listOf(),
    val notifications: Notifications = Notifications(list = listOf()),
    val isFetchingMoreTimeline: Boolean = false,
    val isFetchingMoreNotifications: Boolean = false,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,

    val timelineCursor: String? = null,
    val notificationsCursor: String? = null,

    val cidInteractedWith: Map<Cid, RKey> = mapOf(),

    val loginError: String? = null,
    val error: String? = null,
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

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                uiState = uiState.copy(sessionChecked = true)
                return@launch
            }

            uiState = uiState.copy(authenticated = true, sessionChecked = true)
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

    suspend fun fetchActor(did: Did): Result<ProfileViewDetailed> {
        val ret = bskyConn.fetchActor(did).onFailure {
            uiState = when (it) {
                is LoginException -> uiState.copy(loginError = it.message)
                else -> uiState.copy(error = it.message)
            }
        }.getOrThrow()

        return Result.success(ret)
    }

    fun fetchNewData(then: () -> Unit = {}) {
        fetchTimeline(fresh = true)
        fetchNotifications(fresh = true)
        viewModelScope.launch {
            timelineFetchJob?.join()
            notificationsFetchJob?.join()
            then()
        }
    }

    fun fetchTimeline(fresh: Boolean = false, then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreTimeline = true)
        runCatching {
            timelineFetchJob?.cancel()
        }

        timelineFetchJob = viewModelScope.launch {

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
                    response.feed.map { SkeetData.fromFeedViewPost(it) }.distinctBy { it.cid }
                } else {
                    (uiState.skeets + response.feed.map { SkeetData.fromFeedViewPost(it) }).distinctBy { it.cid }
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
                }.getOrThrow()

            val repeatable = mutableListOf<Notification>()

            // we could bulk request posts here and avoid much of the network IO
            var notifs = Notifications(list = rawNotifs.notifications.mapNotNull {
                when (it.reason) {
                    ListNotificationsReason.Follow -> {
                        val l: Follow = it.record.decodeAs()
                        Notification.Follow(it.author, l.createdAt.toStdlibInstant())
                    }

                    ListNotificationsReason.Like -> {
                        val l: Like = it.record.decodeAs()
                        val lp = fetchRecord(l.subject.uri).getOrThrow()

                        repeatable += Notification.RawLike(
                            lp.decodeAs(),
                            it.author,
                            l.createdAt.toStdlibInstant()
                        )

                        null // repeatable, will be processed later
                    }

                    ListNotificationsReason.Mention -> {
                        val p: Post = it.record.decodeAs()
                        Notification.Mention(
                            Pair(it.cid, it.uri),
                            p,
                            it.author,
                            p.createdAt.toStdlibInstant()
                        )
                    }

                    ListNotificationsReason.Quote -> {
                        val p: Post = it.record.decodeAs()
                        Notification.Quote(
                            Pair(it.cid, it.uri),
                            p,
                            it.author,
                            p.createdAt.toStdlibInstant()
                        )
                    }

                    ListNotificationsReason.Reply -> {
                        val p: Post = it.record.decodeAs()
                        Notification.Reply(
                            Pair(it.cid, it.uri),
                            p,
                            it.author,
                            p.createdAt.toStdlibInstant()
                        )
                    }

                    ListNotificationsReason.Repost -> {
                        val p: Repost = it.record.decodeAs()
                        val pp = fetchRecord(p.subject.uri).getOrThrow()
                        Notification.Repost(pp.decodeAs(), it.author, p.createdAt.toStdlibInstant())
                    }

                    else -> {
                        null
                    }
                }
            })

            if (fresh) {
                uiState = uiState.copy(notifications = Notifications(list = listOf()))
            }

            val processedRepeatable =
                mutableMapOf<RepeatableNotification, MutableMap<Post, RepeatedNotification>>()

            repeatable.fastForEach {
                when (it) {
                    is Notification.RawLike -> {
                        val list = processedRepeatable.getOrPut(RepeatableNotification.Like) {
                            mutableMapOf()
                        }

                        if (list.contains(it.post)) {
                            val l = list[it.post]!!
                            l.authors += RepeatedAuthor(it.author, it.createdAt)
                            if (it.createdAt > l.timestamp) {
                                l.timestamp = it.createdAt
                            }
                            list[it.post] = l
                        } else {
                            list[it.post] = RepeatedNotification(
                                kind = RepeatableNotification.Like,
                                authors = listOf(
                                    RepeatedAuthor(
                                        it.author,
                                        it.createdAt
                                    )
                                ),
                                post = it.post,
                                timestamp = it.createdAt
                            )
                        }
                    }

                    else -> null
                }
            }

            processedRepeatable.forEach { a, n ->
                when (a) {
                    RepeatableNotification.Like -> {
                        n.forEach { _, r ->
                            notifs.list += Notification.Like(
                                data = r.copy(
                                    r.kind,
                                    r.post,
                                    r.authors.sortedByDescending { it.timestamp },
                                    r.timestamp
                                )
                            )
                        }
                    }

                    RepeatableNotification.Repost -> {}
                }
            }

            notifs.list = notifs.list.sortedByDescending { it.createdAt() }

            uiState = uiState.copy(
                notifications = Notifications(list = uiState.notifications.list + notifs.list),
                notificationsCursor = rawNotifs.cursor,
                isFetchingMoreNotifications = false,
            )

            then()
        }
    }

    suspend fun post(
        content: String,
        images: List<Uri>? = null,
        video: Uri? = null,
        replyRef: PostReplyRef? = null,
        quotePostRef: StrongRef? = null,
    ): Result<Unit> {
        return bskyConn.post(
            content,
            images,
            video,
            replyRef,
            quotePostRef
        ) // TODO: maybe refactor this to use uistate.Error?
    }

    fun feeds() {
        viewModelScope.launch {
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
}
