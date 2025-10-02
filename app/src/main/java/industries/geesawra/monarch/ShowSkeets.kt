package industries.geesawra.monarch

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel

@Composable
fun ShowSkeets(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    state: LazyListState = rememberLazyListState(),
    onReplyTap: (SkeetData) -> Unit = {},
    doneFirstRefresh: () -> Unit = {}
) {
    LaunchedEffect(key1 = viewModel.uiState.skeets.isEmpty()) {
        if (viewModel.uiState.skeets.isEmpty()) {
            viewModel.fetchTimeline {
                doneFirstRefresh()
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        viewModel.uiState.skeets.forEach { skeet ->
            item(key = skeet.key()) {
                skeet.root()?.let {
                    SkeetView(
                        viewModel = viewModel,
                        skeet = it,
                        onReplyTap = onReplyTap,
                        inThread = true
                    )
                }
                skeet.parent()?.let {
                    SkeetView(
                        viewModel = viewModel,
                        skeet = it,
                        onReplyTap = onReplyTap,
                        inThread = true
                    )
                }
                SkeetView(viewModel = viewModel, skeet = skeet, onReplyTap = onReplyTap)
            }
        }

        if (viewModel.uiState.isFetchingMoreTimeline && viewModel.uiState.skeets.isNotEmpty()) {
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