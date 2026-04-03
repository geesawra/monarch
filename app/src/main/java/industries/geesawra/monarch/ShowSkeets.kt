package industries.geesawra.monarch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRefParentUnion
import io.github.fornewid.placeholder.foundation.PlaceholderHighlight
import io.github.fornewid.placeholder.material3.fade
import io.github.fornewid.placeholder.material3.placeholder
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import sh.christian.ozone.api.Cid
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did

val LocalActiveVideoKey = compositionLocalOf<MutableState<String?>?> { null }

@Composable
fun ShowSkeets(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    isScrollEnabled: Boolean,
    state: LazyListState = rememberLazyListState(),
    data: List<SkeetData>,
    isShowingThread: Boolean = false,
    shouldFetchMoreData: Boolean = true,
    isLoading: Boolean = false,
    settingsState: SettingsState = SettingsState(),
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onSeeMoreTap: ((SkeetData) -> Unit)? = null,
    onProfileTap: ((Did) -> Unit)? = null,
) {
    val avatarClipShape = if (settingsState.avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(8.dp) else CircleShape
    // Collect CIDs already shown as thread context (root/parent) to avoid duplicates
    val threadContextCids = remember(data) {
        if (isShowingThread) emptySet()
        else {
            val cids = mutableSetOf<Cid>()
            data.forEach { skeet ->
                val isRepost = skeet.reason is FeedViewPostReasonUnion.ReasonRepost
                if (!isRepost) {
                    skeet.root()?.cid?.let { cids.add(it) }
                    skeet.parent().first?.cid?.let { cids.add(it) }
                }
            }
            cids
        }
    }

    val filteredData = remember(data, threadContextCids) {
        data.filter {
            !it.replyToNotFollowing && it.cid !in threadContextCids &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.BlockedPost) &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.NotFoundPost)
        }
    }

    val visibleKeys by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }

    val activeVideoKey = remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalActiveVideoKey provides activeVideoKey) {
    LazyColumn(
        state = state,
        userScrollEnabled = isScrollEnabled,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = feedHorizontalPadding()),
        verticalArrangement = Arrangement.spacedBy(feedItemSpacing()),
    ) {
        if (isLoading && data.isEmpty()) {
            items(8, key = { "skeleton_$it" }) {
                SkeletonPost()
            }
            return@LazyColumn
        }
        itemsIndexed(
            items = filteredData,
            key = { _, skeet -> skeet.rkey },
            contentType = { _, skeet -> if (skeet.reason is FeedViewPostReasonUnion.ReasonRepost) 1 else 0 },
        ) { idx, skeet ->
            val isVisible = visibleKeys.contains(skeet.rkey)
            Card(
                modifier = Modifier.padding(start = (skeet.nestingLevel * nestingIndent()).dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
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
                                postTextSize = settingsState.postTextSize,
                                avatarShape = avatarClipShape,
                                showLabels = settingsState.showLabels,
                                onAvatarTap = onProfileTap,
                                onShowThread = { skeet ->
                                    if (onSeeMoreTap != null) {
                                        viewModel.setThread(skeet)
                                        onSeeMoreTap(skeet)
                                    }
                                },
                                isVisible = isVisible,
                            )
                        }

                        parent?.let {
                            if ((parentsParent?.cid != root?.cid) && root?.cid != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(start = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
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
                                    FilledTonalButton(
                                        modifier = Modifier.padding(start = 12.dp),
                                        onClick = {
                                            if (onSeeMoreTap != null) {
                                                viewModel.setThread(root)
                                                onSeeMoreTap(root)
                                            }
                                        }
                                    ) {
                                        Text("See full thread")
                                    }
                                }
                            }

                            SkeetView(
                                viewModel = viewModel,
                                skeet = it,
                                onReplyTap = onReplyTap,
                                inThread = true,
                                postTextSize = settingsState.postTextSize,
                                avatarShape = avatarClipShape,
                                showLabels = settingsState.showLabels,
                                onAvatarTap = onProfileTap,
                                onShowThread = { skeet ->
                                    if (onSeeMoreTap != null) {
                                        viewModel.setThread(skeet)
                                        onSeeMoreTap(skeet)
                                    }
                                },
                                isVisible = isVisible,
                            )
                        }
                    }
                }

                SkeetView(
                    viewModel = viewModel,
                    skeet = skeet,
                    onReplyTap = onReplyTap,
                    showInReplyTo = parent == null,
                    postTextSize = settingsState.postTextSize,
                    avatarShape = avatarClipShape,
                    showLabels = settingsState.showLabels,
                    onAvatarTap = onProfileTap,
                    onShowThread = { skeet ->
                        if (onSeeMoreTap != null) {
                            viewModel.setThread(skeet)
                            onSeeMoreTap(skeet)
                        }
                    },
                    isVisible = isVisible,
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

@Composable
private fun SkeletonPost() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, start = postHorizontalPadding(), end = postHorizontalPadding(), bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize())
                    .clip(CircleShape)
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade(),
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade(),
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade(),
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade(),
                        )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .placeholder(
                                    visible = true,
                                    highlight = PlaceholderHighlight.fade(),
                                )
                        )
                    }
                }
            }
        }
    }
}