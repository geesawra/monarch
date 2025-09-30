package industries.geesawra.jerryno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import industries.geesawra.jerryno.datalayer.TimelineViewModel

@Composable
fun ShowSkeets(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    state: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(key1 = viewModel.uiState.skeets.isEmpty()) {
        if (viewModel.uiState.skeets.isEmpty()) {
            viewModel.fetchTimeline()
        }
    }

    val isRefreshing = remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing.value,
        onRefresh = {
            viewModel.reset()
            viewModel.fetchTimeline()
        },
    ) {

        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            if (viewModel.uiState.skeets.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                viewModel.uiState.skeets.distinctBy { it.post.cid }.forEach { skeet ->
                    item(key = skeet.post.cid.cid) {
                        SkeetRowView(viewModel, skeet)
                    }
                }

                if (viewModel.uiState.isFetchingMoreTimeline) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(64.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val endOfListReached by remember {
            derivedStateOf {
                val layoutInfo = state.layoutInfo
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (layoutInfo.totalItemsCount == 0) {
                    false
                } else {
                    val lastVisibleItem = visibleItemsInfo.lastOrNull()
                    lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                }
            }
        }

        LaunchedEffect(endOfListReached) {
            if (endOfListReached && viewModel.uiState.skeets.isNotEmpty()) {
                viewModel.fetchTimeline()
            }
        }
    }
}