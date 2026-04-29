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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRefParentUnion

import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import sh.christian.ozone.api.Cid
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import kotlin.time.Instant

val LocalActiveVideoKey = compositionLocalOf<MutableState<String?>?> { null }

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

    val filteredData = remember(data, threadContextCids, searchFilter, isShowingThread, settingsState.showOnlyLatestThreadInteraction) {
        val seenRootCids = mutableSetOf<Cid>()
        val useLatestWins = settingsState.showOnlyLatestThreadInteraction

        val latestPerRoot: Set<SkeetData> = if (useLatestWins) {
            data
                .filter {
                    !it.replyToNotFollowing && it.cid !in threadContextCids &&
                    (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.BlockedPost) &&
                    (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.NotFoundPost) &&
                    (isShowingThread || !it.isMuted)
                }
                .filter {
                    val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
                    if (isRepost) return@filter false
                    it.reply != null && it.threadRootCid() != null
                }
                .groupBy { it.threadRootCid() }
                .mapValues { it.value.maxByOrNull { it.createdAt ?: kotlin.time.Instant.fromEpochMilliseconds(0) } }
                .mapNotNull { it.value }
                .toSet()
        } else {
            emptySet()
        }

        data.filter {
            !it.replyToNotFollowing && it.cid !in threadContextCids &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.BlockedPost) &&
            (isShowingThread || it.reply?.parent !is ReplyRefParentUnion.NotFoundPost) &&
            (isShowingThread || !it.isMuted)
        }.filter {
            if (isShowingThread) return@filter true
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            if (useLatestWins) {
                val isTopLevel = it.reply == null
                if (isTopLevel) return@filter true
                latestPerRoot.contains(it)
            } else {
                val rootCid = it.root()?.cid ?: return@filter true
                seenRootCids.add(rootCid)
            }
        }.filter {
            if (searchFilter.isBlank()) return@filter true
            it.content.contains(searchFilter, ignoreCase = true) ||
                it.authorName?.contains(searchFilter, ignoreCase = true) == true ||
                it.authorHandle?.handle?.contains(searchFilter, ignoreCase = true) == true
        }.filter {
            if (isShowingThread) return@filter true
            computeModerationDecision(
                postLabels = it.postLabels,
                authorLabels = it.authorLabels,
                contentLabelPrefVisibility = { l -> viewModel.contentLabelPrefVisibility(l) },
                labelDefaultSetting = { l -> viewModel.labelDefaultSetting(l) },
                labelSeverity = { l -> viewModel.labelSeverity(l) },
                labelBlurs = { l -> viewModel.labelBlurs(l) },
                labelDisplayName = { l -> viewModel.labelDisplayName(l) },
            ) !is ModerationDecision.Hide
        }
    }

    val visibleKeys by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }

    val activeVideoKey = remember { mutableStateOf<String?>(null) }

    val focusedUri = remember(filteredData) { filteredData.firstOrNull { it.isFocused }?.uri }
    var alsoLikedPosts by remember { mutableStateOf<List<SkeetData>>(emptyList()) }

    LaunchedEffect(focusedUri, settingsState.alsoLikedEnabled) {
        if (isShowingThread && settingsState.alsoLikedEnabled && focusedUri != null) {
            val result = viewModel.getAlsoLikedPosts(focusedUri, null).getOrNull()
            if (result != null && result.first.isNotEmpty()) {
                alsoLikedPosts = result.first
            } else {
                alsoLikedPosts = emptyList()
                if (result == null) {
                    android.util.Log.e("ShowSkeets", "Also liked request failed for $focusedUri")
                } else {
                    android.util.Log.e("ShowSkeets", "Also liked returned empty for $focusedUri")
                }
            }
        } else {
            alsoLikedPosts = emptyList()
        }
    }

    val focusIdxLocal = remember(filteredData) { filteredData.indexOfFirst { it.isFocused } }

    val focusedVisibleIdx = remember(filteredData) {
        filteredData.indexOfFirst { it.isFocused }
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
            .then(if (!isShowingThread) Modifier.padding(horizontal = feedHorizontalPadding()) else Modifier),
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
            items = filteredData,
            key = { _, skeet -> skeet.lazyListKey() },
            contentType = { _, skeet -> if (skeet.reason is FeedViewPostReasonUnion.ReasonRepost) 1 else 0 },
        ) { index, skeet ->
            val skeetKey = skeet.lazyListKey()
            val isVisible = visibleKeys.contains(skeetKey)

            if (isShowingThread && index > 0 && filteredData[index - 1].chainBlockId != skeet.chainBlockId) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 80.dp, vertical = 12.dp)
                ) {
                    SquigglyDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }

            val isGroupEnd = isShowingThread && skeet.isInChain &&
                (index == filteredData.lastIndex || filteredData[index + 1].chainBlockId != skeet.chainBlockId)
            val threadCardShape = MaterialTheme.shapes.medium

            Column(
                modifier = Modifier
                    .then(
                        if (isShowingThread)
                            Modifier.padding(horizontal = feedHorizontalPadding())
                        else
                            Modifier
                    )
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
                                showConnectorDown = true,
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
                                showConnectorDown = true,
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
                        inThread = isShowingThread && skeet.isInChain,
                        showConnectorDown = isShowingThread && skeet.isInChain && !isGroupEnd,
                        overrideAvatarSize = null,
                        translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                        targetTranslationLanguage = settingsState.targetTranslationLanguage,
                        carouselImageGallery = settingsState.carouselImageGallery,
                    )
                }
            }

            if (isShowingThread && skeet.hasMoreReplies && onSeeMoreTap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (skeet.chainBlockId > 0)
                                Modifier.padding(horizontal = feedHorizontalPadding())
                            else
                                Modifier
                        ),
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
        }

        if (isShowingThread && settingsState.alsoLikedEnabled && alsoLikedPosts.isNotEmpty()) {
            item(key = "also_liked_divider") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 80.dp, vertical = 12.dp)
                ) {
                    SquigglyDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            item(key = "also_liked_header") {
                AnimatedVisibility(
                    visible = alsoLikedPosts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = "People also liked",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = feedHorizontalPadding(), vertical = 16.dp),
                    )
                }
            }
            items(
                items = alsoLikedPosts,
                key = { "${it.cid.cid}_${it.uri.atUri}" },
            ) { post ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .then(
                                if (isShowingThread)
                                    Modifier.padding(horizontal = feedHorizontalPadding())
                                else
                                    Modifier
                            )
                            .padding(bottom = feedItemSpacing())
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        SkeetView(
                            viewModel = viewModel,
                            skeet = post,
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
                            isVisible = visibleKeys.contains("${post.cid.cid}_${post.uri.atUri}"),
                            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                            targetTranslationLanguage = settingsState.targetTranslationLanguage,
                            carouselImageGallery = settingsState.carouselImageGallery,
                        )
                    }
                }
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

