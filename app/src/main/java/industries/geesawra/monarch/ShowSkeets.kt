package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPostReasonUnion
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did

@Composable
fun ShowSkeets(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    isScrollEnabled: Boolean,
    state: LazyListState = rememberLazyListState(),
    data: List<SkeetData>,
    isShowingThread: Boolean = false,
    shouldFetchMoreData: Boolean = true,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onSeeMoreTap: ((SkeetData) -> Unit)? = null,
    onProfileTap: ((Did) -> Unit)? = null,
) {
    LazyColumn(
        state = state,
        userScrollEnabled = isScrollEnabled,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            items = data.filter { !it.replyToNotFollowing },
            key = { _, skeet -> skeet.key() }
        ) { idx, skeet ->
            Card(
                modifier = Modifier.padding(start = (skeet.nestingLevel * 16).dp)
            ) {
                val isRepost = when (skeet.reason) {
                    is FeedViewPostReasonUnion.ReasonRepost -> true
                    else -> false
                }

                val root = skeet.root()
                val (parent, parentsParent) = skeet.parent()

                if (!isShowingThread) {
                    if (!isRepost) {
                        root?.let {
                            SkeetView(
                                viewModel = viewModel,
                                skeet = it,
                                onReplyTap = onReplyTap,
                                inThread = true,
                                onAvatarTap = onProfileTap,
                                onShowThread = { skeet ->
                                    if (onSeeMoreTap != null) {
                                        viewModel.setThread(skeet)
                                        onSeeMoreTap(skeet)
                                    }
                                }
                            )
                        }

                        parent?.let {
                            if ((parentsParent?.cid != root?.cid) && root?.cid != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(start = 16.dp)
                                        .offset(y = (-14).dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VerticalDivider(
                                            thickness = 3.dp,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(12.dp)),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    ConditionalCard(
                                        text = "See more",
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp),
                                        onTap = {
                                            if (onSeeMoreTap != null) {
                                                viewModel.setThread(root)
                                                onSeeMoreTap(root)
                                            }
                                        }
                                    )
                                }
                            }

                            SkeetView(
                                viewModel = viewModel,
                                skeet = it,
                                onReplyTap = onReplyTap,
                                inThread = true,
                                onAvatarTap = onProfileTap,
                                onShowThread = { skeet ->
                                    if (onSeeMoreTap != null) {
                                        viewModel.setThread(skeet)
                                        onSeeMoreTap(skeet)
                                    }
                                }
                            )
                        }
                    }
                }

                SkeetView(
                    viewModel = viewModel,
                    skeet = skeet,
                    onReplyTap = onReplyTap,
                    showInReplyTo = parent == null,
                    onAvatarTap = onProfileTap,
                    onShowThread = { skeet ->
                        if (onSeeMoreTap != null) {
                            viewModel.setThread(skeet)
                            onSeeMoreTap(skeet)
                        }
                    }
                )
            }

            if (isShowingThread) {
                if (idx + 1 >= data.lastIndex) {
                    return@itemsIndexed
                }
                if (data[idx + 1].isReplyToRoot) {
                    HorizontalDivider(
                        thickness = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
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
        if (endOfListReached && viewModel.uiState.skeets.isNotEmpty() && shouldFetchMoreData) {
            viewModel.fetchTimeline()
        }
    }
}