package industries.geesawra.monarch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import sh.christian.ozone.api.Did

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadView(
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState = SettingsState(),
    backButton: () -> Unit,
    coroutineScope: CoroutineScope,
    onProfileTap: ((Did) -> Unit)? = null,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onThreadTap: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )

    val isRefreshing = remember { mutableStateOf(true) }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing.value,
        onRefresh = {},
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    colors = monarchTopAppBarColors(),
                    navigationIcon = {
                        IconButton(onClick = backButton) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back"
                            )
                        }
                    },
                    title = {
                        Text("Post")
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val thread = timelineViewModel.uiState.currentlyShownThread
            val tappedPostUri = remember { thread.post.uri }
            val threadData = remember(thread) { thread.flatten() }
            val focusIdx = remember(threadData) {
                threadData.indexOfFirst { it.isFocused }.coerceAtLeast(0)
            }
            val listState = remember(focusIdx) {
                LazyListState(firstVisibleItemIndex = focusIdx)
            }

            LaunchedEffect(Unit) {
                if (thread.replies.isEmpty()) {
                    timelineViewModel.getThread {
                        isRefreshing.value = false
                    }
                } else {
                    isRefreshing.value = false
                }
            }

            val contentModifier = if (!settingsState.forceCompactLayout) {
                Modifier.widthIn(max = 600.dp)
            } else {
                Modifier
            }

            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(modifier = contentModifier) {
                    ShowSkeets(
                        viewModel = timelineViewModel,
                        isScrollEnabled = true,
                        state = listState,
                        data = threadData,
                        shouldFetchMoreData = false,
                        isShowingThread = true,
                        settingsState = settingsState,
                        onProfileTap = onProfileTap,
                        onReplyTap = onReplyTap,
                        onSeeMoreTap = { tapped ->
                            if (tapped.uri != tappedPostUri) {
                                timelineViewModel.setThread(tapped)
                                onThreadTap()
                            }
                        },
                    )
                }
            }
        }
    }
}