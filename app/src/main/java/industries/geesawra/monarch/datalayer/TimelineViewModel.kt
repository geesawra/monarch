@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.GeneratorView
import app.bsky.feed.Like
import app.bsky.feed.PostReplyRef
import app.bsky.notification.ListNotificationsReason
import com.atproto.repo.StrongRef
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime


data class TimelineUiState(
    val selectedFeed: String = "Following",
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

    fun fetchTimeline(then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreTimeline = true)
        runCatching {
            timelineFetchJob?.cancel()
        }

        timelineFetchJob = viewModelScope.launch {
            bskyConn.fetchTimeline({
                if (uiState.selectedFeed == "Following") {
                    ""
                } else {
                    uiState.selectedFeed
                }
            }(), uiState.timelineCursor).onSuccess { it ->
                val newData =
                    (uiState.skeets + it.feed.map { SkeetData.fromFeedViewPost(it) }).distinctBy { it.cid }

                uiState = uiState.copy(
                    skeets = newData,
                    timelineCursor = it.cursor,
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

    fun fetchNotifications(then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreNotifications = true)
        runCatching {
            notificationsFetchJob?.cancel()
        }

        notificationsFetchJob = viewModelScope.launch {
            val rawNotifs = bskyConn.notifications(uiState.notificationsCursor)
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

            val notifs: List<Notification> = rawNotifs.notifications.mapNotNull {
                when (it.reason) {
                    ListNotificationsReason.Follow -> {
                        Notification.Follow(it.author)
                    }

                    ListNotificationsReason.Like -> {
                        val l: Like = it.record.decodeAs()
                        val lp = fetchRecord(l.subject.uri).getOrThrow()
                        Notification.Like(lp.decodeAs(), it.author)
                    }

                    ListNotificationsReason.Mention -> {
                        val p: app.bsky.feed.Post = it.record.decodeAs()
                        Notification.Mention(p, it.author)
                    }

                    ListNotificationsReason.Quote -> {
                        val p: app.bsky.feed.Post = it.record.decodeAs()
                        Notification.Quote(Pair(it.cid, it.uri), p, it.author)
                    }

                    ListNotificationsReason.Reply -> {
                        val p: app.bsky.feed.Post = it.record.decodeAs()
                        Notification.Reply(Pair(it.cid, it.uri), p, it.author)
                    }

                    ListNotificationsReason.Repost -> {
                        val p: app.bsky.feed.Repost = it.record.decodeAs()
                        val pp = fetchRecord(p.subject.uri).getOrThrow()
                        Notification.Repost(pp.decodeAs(), it.author)
                    }

                    else -> {
                        null
                    }
                }
            }

            uiState = uiState.copy(
                notifications = uiState.notifications + notifs,
                notificationsCursor = rawNotifs.cursor,
                isFetchingMoreNotifications = false,
            )
            then()
        }
    }

    fun reset() {
        uiState = uiState.copy(
            skeets = listOf(),
            isFetchingMoreTimeline = false,
            timelineCursor = null,
            notificationsCursor = null,
            notifications = listOf()
        )
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
        reset()
        fetchTimeline { then() }
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