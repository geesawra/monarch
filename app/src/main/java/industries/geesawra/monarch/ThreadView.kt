package industries.geesawra.monarch

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.util.fastForEach
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadView(
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel,
    coroutineScope: CoroutineScope
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
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    colors = TopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground, // Ensuring correct contrast
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                        subtitleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Text("Thread")
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LaunchedEffect(Unit) {
                timelineViewModel.getThread {
                    Log.d("ThreadView", "Thread retrieved")
                    isRefreshing.value = false
                }
            }

            Column(
                modifier = Modifier.padding(padding)
            ) {
                timelineViewModel.uiState.currentlyShownThread.fastForEach { threadView ->
                    ShowSkeets(
                        viewModel = timelineViewModel,
                        isScrollEnabled = true,
                        data = threadView,
                        shouldFetchMoreData = false,
                        isShowingThread = true,
                    )

                    HorizontalDivider()
                }
            }
        }
    }
}

