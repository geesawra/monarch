package industries.geesawra.jerryno.datalayer

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bsky.feed.FeedViewPost
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


data class TimelineUiState(
    val skeets: List<FeedViewPost> = listOf(),
    val isFetchingMoreTimeline: Boolean = false,
    val cursor: String? = null,
    val authenticated: Boolean = false,
    val sessionChecked: Boolean = false,
    val authError: String = ""
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

    fun create() {
        viewModelScope.launch {
            bskyConn.create()
        }
    }

    fun loadSession() {
        viewModelScope.launch {
            if (!bskyConn.hasSession()) {
                uiState = uiState.copy(sessionChecked = true)
                return@launch
            }

            uiState = uiState.copy(authenticated = true, sessionChecked = true)
        }
    }


    fun fetchTimeline() {
        uiState = uiState.copy(isFetchingMoreTimeline = true)
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            bskyConn.fetchTimeline(uiState.cursor).onSuccess {
                Log.d("TimelineViewModel", "New cursor ${it.cursor}")
                uiState = uiState.copy(
                    skeets = uiState.skeets + it.feed,
                    cursor = it.cursor,
                    isFetchingMoreTimeline = false
                )
            }.onFailure {
                uiState = uiState.copy(isFetchingMoreTimeline = true)
                Log.e("TimelineViewModel", "Failed to fetch timeline: ${it.message}")
            }
        }
    }

    fun post(content: String, then: suspend () -> Unit) {
        viewModelScope.launch {
            bskyConn.post(content)
            then()
        }
    }
}