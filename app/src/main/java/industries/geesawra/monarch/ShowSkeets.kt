@file:Suppress("DEPRECATION")

package industries.geesawra.monarch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRefParentUnion

import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.ThreadConnectorType
import sh.christian.ozone.api.Cid
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

val LocalActiveVideoKey = compositionLocalOf<MutableState<String?>?> { null }

private const val DEEP_BRANCH_THRESHOLD = 3

private sealed interface ThreadItem {
    data class Post(val skeet: SkeetData, val originalIndex: Int) : ThreadItem
    data class Collapsed(val key: String, val count: Int, val level: Int) : ThreadItem
}

@Composable
fun ShowSkeets(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    isScrollEnabled: Boolean,
    state: LazyListState = rememberLazyListState(),
    data: ImmutableList<SkeetData>,
    isShowingThread: Boolean = false,
    shouldFetchMoreData: Boolean = true,
    isLoading: Boolean = false,
    settingsState: SettingsState = SettingsState(),
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onSeeMoreTap: ((SkeetData) -> Unit)? = null,
    onProfileTap: ((Did) -> Unit)? = null,
    searchFilter: String = "",
    onShowLikes: ((AtUri) -> Unit)? = null,
    onShowReposts: ((AtUri) -> Unit)? = null,
    onShowQuotes: ((AtUri) -> Unit)? = null,
) {
    val avatarClipShape = settingsState.avatarClipShape
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

    val filteredData = remember(data, threadContextCids, searchFilter, isShowingThread) {
        val seenRootCids = mutableSetOf<Cid>()
        data.filter {
            !it.replyToNotFollowing && it.cid !in threadContextCids &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.BlockedPost) &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.NotFoundPost) &&
            (isShowingThread || !it.isMuted)
        }.filter {
            if (isShowingThread) return@filter true
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val rootCid = it.root()?.cid ?: return@filter true
            seenRootCids.add(rootCid)
        }.filter {
            if (searchFilter.isBlank()) return@filter true
            it.content.contains(searchFilter, ignoreCase = true) ||
                it.authorName?.contains(searchFilter, ignoreCase = true) == true ||
                it.authorHandle?.handle?.contains(searchFilter, ignoreCase = true) == true
        }
    }

    val visibleKeys by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }

    val activeVideoKey = remember { mutableStateOf<String?>(null) }

    val expandedBranches = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }

    val focusIdxLocal = remember(filteredData) { filteredData.indexOfFirst { it.isFocused } }

    val isAncestorOrFocusAt: (Int) -> Boolean = lambda@{ idx ->
        if (!isShowingThread) return@lambda false
        if (idx < 0 || idx >= filteredData.size) return@lambda false
        val s = filteredData[idx]
        if (s.threadConnectors.isNotEmpty()) return@lambda false
        s.isFocused || (focusIdxLocal >= 0 && idx < focusIdxLocal)
    }

    val visibleItems = remember(filteredData, expandedBranches.size, isShowingThread) {
        if (!isShowingThread) {
            filteredData.mapIndexed { i, s -> ThreadItem.Post(s, i) as ThreadItem }
        } else {
            buildList<ThreadItem> {
                var i = 0
                while (i < filteredData.size) {
                    val skeet = filteredData[i]
                    if (skeet.nestingLevel < DEEP_BRANCH_THRESHOLD) {
                        add(ThreadItem.Post(skeet, i))
                        i++
                        continue
                    }
                    var end = i
                    while (end < filteredData.size && filteredData[end].nestingLevel >= DEEP_BRANCH_THRESHOLD) end++
                    val branchKey = filteredData[i].uri.atUri
                    val containsFocus = (i until end).any { filteredData[it].isFocused }
                    if (expandedBranches.contains(branchKey) || containsFocus) {
                        for (j in i until end) add(ThreadItem.Post(filteredData[j], j))
                    } else {
                        add(ThreadItem.Collapsed(branchKey, end - i, filteredData[i].nestingLevel))
                    }
                    i = end
                }
            }
        }
    }

    val focusedVisibleIdx = remember(visibleItems) {
        visibleItems.indexOfFirst { it is ThreadItem.Post && it.skeet.isFocused }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val sumFromFocusPx by remember(focusedVisibleIdx) {
        derivedStateOf {
            if (focusedVisibleIdx < 0) return@derivedStateOf 0
            state.layoutInfo.visibleItemsInfo
                .filter { it.index >= focusedVisibleIdx }
                .sumOf { it.size }
        }
    }
    val viewportHeightPx by remember {
        derivedStateOf { state.layoutInfo.viewportSize.height }
    }

    val fallbackBottomPadding = LocalConfiguration.current.screenHeightDp.dp
    val bottomScrollPadding = if (!isShowingThread) {
        16.dp
    } else if (viewportHeightPx <= 0 || sumFromFocusPx <= 0) {
        fallbackBottomPadding
    } else {
        with(density) { (viewportHeightPx - sumFromFocusPx).coerceAtLeast(0).toDp() }
    }

    CompositionLocalProvider(LocalActiveVideoKey provides activeVideoKey) {
    LazyColumn(
        state = state,
        userScrollEnabled = isScrollEnabled,
        modifier = modifier
            .testTag("feed_list")
            .fillMaxSize()
            .padding(horizontal = feedHorizontalPadding()),
        contentPadding = PaddingValues(bottom = bottomScrollPadding),
        verticalArrangement = if (isShowingThread) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(feedItemSpacing()),
    ) {
        if (isLoading && (data.isEmpty() || (isShowingThread && data.size <= 1))) {
            items(8, key = { "skeleton_$it" }) {
                val skeletonModifier = if (isShowingThread) {
                    Modifier.padding(bottom = feedItemSpacing())
                } else {
                    Modifier
                }
                SkeletonPost(skeletonModifier)
            }
            return@LazyColumn
        }
        itemsIndexed(
            items = visibleItems,
            key = { _, item ->
                when (item) {
                    is ThreadItem.Post -> item.skeet.lazyListKey()
                    is ThreadItem.Collapsed -> "collapsed_${item.key}"
                }
            },
            contentType = { _, item ->
                when (item) {
                    is ThreadItem.Post -> if (item.skeet.reason is FeedViewPostReasonUnion.ReasonRepost) 1 else 0
                    is ThreadItem.Collapsed -> 2
                }
            },
        ) { _, item ->
            if (item is ThreadItem.Collapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width((item.level * nestingIndent()).dp))
                    FilledTonalButton(onClick = { expandedBranches.add(item.key) }) {
                        Text("Show ${item.count} more replies")
                    }
                }
                return@itemsIndexed
            }
            item as ThreadItem.Post
            val skeet = item.skeet
            val originalIndex = item.originalIndex
            val skeetKey = skeet.lazyListKey()
            val isVisible = visibleKeys.contains(skeetKey)

            val connectors = skeet.threadConnectors
            val hasConnectors = isShowingThread && connectors.isNotEmpty()

            val inAncestorGroup = isAncestorOrFocusAt(originalIndex)

            val isGroupStart = if (isShowingThread && !hasConnectors) {
                if (inAncestorGroup) {
                    !isAncestorOrFocusAt(originalIndex - 1)
                } else {
                    val prev = filteredData.getOrNull(originalIndex - 1)
                    prev == null || prev.threadConnectors.isNotEmpty() ||
                        (prev.nestingLevel == 0 && !skeet.isSameAuthorContinuation && originalIndex > 0) ||
                        skeet.isFocused || prev.isFocused
                }
            } else false

            val isGroupEnd = if (isShowingThread && !hasConnectors) {
                if (inAncestorGroup) {
                    !isAncestorOrFocusAt(originalIndex + 1)
                } else {
                    val next = filteredData.getOrNull(originalIndex + 1)
                    next == null || next.threadConnectors.isNotEmpty() ||
                        (next.nestingLevel == 0 && !next.isSameAuthorContinuation) ||
                        skeet.isFocused || next.isFocused
                }
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        if (skeet.isFocused) {
                            FocusedSkeetView(
                                viewModel = viewModel,
                                skeet = skeet,
                                onReplyTap = onReplyTap,
                                postTextSize = settingsState.postTextSize,
                                avatarShape = avatarClipShape,
                                showLabels = settingsState.showLabels,
                                showPronouns = settingsState.showPronounsInPosts,
                                onAvatarTap = onProfileTap,
                                onShowThread = {},
                                isVisible = isVisible,
                                translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                                targetTranslationLanguage = settingsState.targetTranslationLanguage,
                                carouselImageGallery = settingsState.carouselImageGallery,
                                onShowLikes = { onShowLikes?.invoke(skeet.uri) },
                                onShowReposts = { onShowReposts?.invoke(skeet.uri) },
                                onShowQuotes = { onShowQuotes?.invoke(skeet.uri) },
                            )
                        } else {
                            SkeetView(
                                viewModel = viewModel,
                                skeet = skeet,
                                onReplyTap = onReplyTap,
                                postTextSize = settingsState.postTextSize,
                                avatarShape = avatarClipShape,
                                showLabels = settingsState.showLabels,
                                showPronouns = settingsState.showPronounsInPosts,
                                onAvatarTap = onProfileTap,
                                onShowThread = { tapped ->
                                    onSeeMoreTap?.invoke(tapped)
                                },
                                isVisible = isVisible,
                                overrideAvatarSize = replyAvatarSize(),
                                translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                                targetTranslationLanguage = settingsState.targetTranslationLanguage,
                                carouselImageGallery = settingsState.carouselImageGallery,
                            )
                        }
                    }
                }
            } else {
                if (isShowingThread && !isGroupStart && !inAncestorGroup) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Column(
                    modifier = Modifier
                        .clip(threadCardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                            showPronouns = settingsState.showPronounsInPosts,
                                    onAvatarTap = onProfileTap,
                                    onShowThread = { skeet ->
                                        if (onSeeMoreTap != null) {
                                            viewModel.startThread(skeet)
                                            onSeeMoreTap(skeet)
                                        }
                                    },
                                    isVisible = isVisible,
                                    translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                                    targetTranslationLanguage = settingsState.targetTranslationLanguage,
                                    carouselImageGallery = settingsState.carouselImageGallery,
                                )
                            }

                            parent?.let {
                                if ((parentsParent?.cid != root?.cid) && root?.cid != null) {
                                    val threadLineWidth = avatarSize()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(start = postHorizontalPadding())
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(threadLineWidth)
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
                                                    viewModel.startThread(root)
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
                            showPronouns = settingsState.showPronounsInPosts,
                                    onAvatarTap = onProfileTap,
                                    onShowThread = { skeet ->
                                        if (onSeeMoreTap != null) {
                                            viewModel.startThread(skeet)
                                            onSeeMoreTap(skeet)
                                        }
                                    },
                                    isVisible = isVisible,
                                    translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                                    targetTranslationLanguage = settingsState.targetTranslationLanguage,
                                    carouselImageGallery = settingsState.carouselImageGallery,
                                )
                            }
                        }
                    }

                    if (isShowingThread && skeet.isFocused) {
                        FocusedSkeetView(
                            viewModel = viewModel,
                            skeet = skeet,
                            onReplyTap = onReplyTap,
                            postTextSize = settingsState.postTextSize,
                            avatarShape = avatarClipShape,
                            showLabels = settingsState.showLabels,
                            showPronouns = settingsState.showPronounsInPosts,
                            onAvatarTap = onProfileTap,
                            onShowThread = {},
                            isVisible = isVisible,
                            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                            targetTranslationLanguage = settingsState.targetTranslationLanguage,
                            carouselImageGallery = settingsState.carouselImageGallery,
                            onShowLikes = { onShowLikes?.invoke(skeet.uri) },
                            onShowReposts = { onShowReposts?.invoke(skeet.uri) },
                            onShowQuotes = { onShowQuotes?.invoke(skeet.uri) },
                        )
                    } else {
                        SkeetView(
                            viewModel = viewModel,
                            skeet = skeet,
                            onReplyTap = onReplyTap,
                            showInReplyTo = if (isShowingThread) false else (skeet.root() == null && skeet.parent().first == null),
                            postTextSize = settingsState.postTextSize,
                            avatarShape = avatarClipShape,
                            showLabels = settingsState.showLabels,
                            showPronouns = settingsState.showPronounsInPosts,
                            onAvatarTap = onProfileTap,
                            onShowThread = { tapped ->
                                if (onSeeMoreTap != null) {
                                    if (!isShowingThread) viewModel.startThread(tapped)
                                    onSeeMoreTap(tapped)
                                }
                            },
                            isVisible = isVisible,
                            inThread = isShowingThread && focusIdxLocal >= 0 && originalIndex < focusIdxLocal,
                            overrideAvatarSize = if (isShowingThread && !skeet.isFocused) replyAvatarSize() else null,
                            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                            targetTranslationLanguage = settingsState.targetTranslationLanguage,
                            carouselImageGallery = settingsState.carouselImageGallery,
                        )
                    }
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

            if (isShowingThread && isGroupEnd) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
    }

    OnEndOfListReached(
        listState = state,
        items = viewModel.skeets,
        onEndReached = {
            if (shouldFetchMoreData) {
                viewModel.fetchTimeline()
            }
        },
    )

    if (settingsState.autoLikeOnScroll && !isShowingThread) {
        val autoLikedCids = remember { mutableSetOf<Cid>() }
        LaunchedEffect(state) {
            snapshotFlow {
                val layoutInfo = state.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }?.key as? String
            }.distinctUntilChanged().collectLatest { key ->
                if (key == null) return@collectLatest
                val skeet = filteredData.find { it.lazyListKey() == key } ?: return@collectLatest
                if (skeet.didLike) return@collectLatest
                if (skeet.cid in autoLikedCids) return@collectLatest
                if (skeet.root() != null || skeet.parent().first != null) return@collectLatest
                if (skeet.did == viewModel.user?.did) return@collectLatest
                autoLikedCids.add(skeet.cid)
                viewModel.like(skeet.uri, skeet.cid)
            }
        }
    }
}

@Composable
private fun SkeletonPost(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, start = postHorizontalPadding(), end = postHorizontalPadding(), bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize())
                    .clip(CircleShape)
                    .themedPlaceholder()
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
                        .themedPlaceholder()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
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
                                .themedPlaceholder()
                        )
                    }
                }
            }
        }
    }
}

