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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRefParentUnion
import io.github.fornewid.placeholder.foundation.PlaceholderHighlight
import io.github.fornewid.placeholder.material3.fade
import io.github.fornewid.placeholder.material3.placeholder
import app.bsky.actor.ActorTarget
import app.bsky.actor.MutedWord
import app.bsky.actor.MutedWordTarget
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.ThreadConnector
import industries.geesawra.monarch.datalayer.ThreadConnectorType
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
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

    val mutedWords = viewModel.uiState.mutedWords
    val filteredData = remember(data, threadContextCids, mutedWords) {
        val seenRootCids = mutableSetOf<Cid>()
        val now = Clock.System.now()
        data.filter {
            !it.replyToNotFollowing && it.cid !in threadContextCids &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.BlockedPost) &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.NotFoundPost)
        }.filter {
            if (isShowingThread) return@filter true
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val rootCid = it.root()?.cid ?: return@filter true
            seenRootCids.add(rootCid)
        }.filter {
            if (isShowingThread || mutedWords.isEmpty()) return@filter true
            !isMutedByWord(it, mutedWords, now)
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
        verticalArrangement = if (isShowingThread) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(feedItemSpacing()),
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

            val connectors = skeet.threadConnectors
            val hasConnectors = isShowingThread && connectors.isNotEmpty()

            val isGroupStart = if (isShowingThread && !hasConnectors) {
                val prev = filteredData.getOrNull(idx - 1)
                prev == null || prev.threadConnectors.isNotEmpty() ||
                    (prev.nestingLevel == 0 && !skeet.isSameAuthorContinuation && idx > 0)
            } else false

            val isGroupEnd = if (isShowingThread && !hasConnectors) {
                val next = filteredData.getOrNull(idx + 1)
                next == null || next.threadConnectors.isNotEmpty() ||
                    (next.nestingLevel == 0 && !next.isSameAuthorContinuation)
            } else false

            val threadCardShape = if (isShowingThread && !hasConnectors) {
                val topRadius = if (isGroupStart) 12.dp else 0.dp
                val bottomRadius = if (isGroupEnd) 12.dp else 0.dp
                RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = bottomRadius, bottomEnd = bottomRadius)
            } else MaterialTheme.shapes.medium

            val connectorColors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            )
            val connectorWidth = nestingIndent()

            if (hasConnectors) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width((skeet.nestingLevel * connectorWidth).dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val slotWidth = connectorWidth.dp.toPx()
                                val lineWidth = 2.dp.toPx()
                                val branchLength = slotWidth * 0.5f

                                connectors.forEach { connector ->
                                    val color = connectorColors[connector.level % connectorColors.size]
                                    val x = connector.level * slotWidth + slotWidth / 2f

                                    when (connector.type) {
                                        ThreadConnectorType.PASS_THROUGH -> {
                                            drawLine(
                                                color = color,
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height),
                                                strokeWidth = lineWidth,
                                                cap = StrokeCap.Round,
                                            )
                                        }

                                        ThreadConnectorType.BRANCH -> {
                                            drawLine(
                                                color = color,
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height),
                                                strokeWidth = lineWidth,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = color,
                                                start = Offset(x, size.height / 2f),
                                                end = Offset(x + branchLength, size.height / 2f),
                                                strokeWidth = lineWidth,
                                                cap = StrokeCap.Round,
                                            )
                                        }

                                        ThreadConnectorType.LAST_BRANCH -> {
                                            drawLine(
                                                color = color,
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height / 2f),
                                                strokeWidth = lineWidth,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = color,
                                                start = Offset(x, size.height / 2f),
                                                end = Offset(x + branchLength, size.height / 2f),
                                                strokeWidth = lineWidth,
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    }
                                }
                            }
                    )

                    Card(
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        SkeetView(
                            viewModel = viewModel,
                            skeet = skeet,
                            onReplyTap = onReplyTap,
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
            } else {
                if (isShowingThread && !isGroupStart && !hasConnectors) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Card(
                    shape = threadCardShape,
                    elevation = if (isShowingThread) CardDefaults.cardElevation(defaultElevation = 0.dp) else CardDefaults.cardElevation(),
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
                        showInReplyTo = if (isShowingThread) false else (skeet.root() == null && skeet.parent().first == null),
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

            if (isShowingThread && skeet.hasMoreReplies && onSeeMoreTap != null) {
                val continueConnectorWidth = nestingIndent()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (skeet.nestingLevel * continueConnectorWidth).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        onClick = { onSeeMoreTap(skeet) }
                    ) {
                        Text("Continue thread")
                    }
                }
            }

            if (isShowingThread && isGroupEnd && !hasConnectors) {
                Spacer(modifier = Modifier.height(4.dp))
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

    if (settingsState.autoLikeOnScroll && !isShowingThread) {
        val autoLikedCids = remember { mutableSetOf<Cid>() }
        LaunchedEffect(state) {
            snapshotFlow {
                val layoutInfo = state.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }?.key as? String
            }.distinctUntilChanged().collectLatest { rkey ->
                if (rkey == null) return@collectLatest
                val skeet = filteredData.find { it.rkey == rkey } ?: return@collectLatest
                if (skeet.didLike) return@collectLatest
                if (skeet.cid in autoLikedCids) return@collectLatest
                if (skeet.root() != null || skeet.parent().first != null) return@collectLatest
                autoLikedCids.add(skeet.cid)
                viewModel.like(skeet.uri, skeet.cid)
            }
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

private fun isMutedByWord(skeet: SkeetData, mutedWords: List<MutedWord>, now: Instant): Boolean {
    val contentLower = skeet.content.lowercase()
    val tags = skeet.facets.flatMap { facet ->
        facet.features.mapNotNull { feature ->
            (feature as? app.bsky.richtext.FacetFeatureUnion.Tag)?.value?.tag?.lowercase()
        }
    }
    return mutedWords.any { word ->
        val expiresAt = word.expiresAt
        if (expiresAt != null && expiresAt < now) return@any false
        if (word.actorTarget is ActorTarget.ExcludeFollowing && skeet.following) return@any false
        val valueLower = word.value.lowercase()
        val matchesContent = word.targets.contains(MutedWordTarget.Content) &&
            contentLower.containsWordBoundary(valueLower)
        val matchesTag = word.targets.contains(MutedWordTarget.Tag) &&
            tags.any { it == valueLower }
        matchesContent || matchesTag
    }
}

private fun String.containsWordBoundary(word: String): Boolean {
    var idx = 0
    while (true) {
        idx = indexOf(word, idx)
        if (idx < 0) return false
        val before = if (idx > 0) this[idx - 1] else ' '
        val after = if (idx + word.length < length) this[idx + word.length] else ' '
        if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
        idx += 1
    }
}