package industries.geesawra.jerryno.datalayer

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.feed.GeneratorView
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.RKey
import kotlin.coroutines.cancellation.CancellationException


data class TimelineUiState(
    val selectedFeed: String = "Following",
    val feedName: String = "Following",
    val feedAvatar: String? = null,
    val feeds: List<GeneratorView> = listOf(),
    val skeets: List<SkeetData> = listOf(),
    val isFetchingMoreTimeline: Boolean = false,
    val cursor: String? = null,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,

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

    private var fetchJob: Job? = null

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                uiState = uiState.copy(sessionChecked = true)
                return@launch
            }

            uiState = uiState.copy(authenticated = true, sessionChecked = true)
        }
    }


    fun fetchTimeline(then: () -> Unit = {}) {
        uiState = uiState.copy(isFetchingMoreTimeline = true)
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            bskyConn.fetchTimeline({
                if (uiState.selectedFeed == "Following") {
                    ""
                } else {
                    uiState.selectedFeed
                }
            }(), uiState.cursor).onSuccess { it ->
                uiState = uiState.copy(
                    skeets = (uiState.skeets + it.feed.map { SkeetData.fromFeedViewPost(it) }).distinctBy { it.cid },
                    cursor = it.cursor,
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

    fun reset() {
        uiState = uiState.copy(
            skeets = listOf(), isFetchingMoreTimeline = false, cursor = null,
        )
    }

    suspend fun post(content: String, images: List<Uri>? = null, video: Uri? = null): Result<Unit> {
        return bskyConn.post(
            content,
            images,
            video
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