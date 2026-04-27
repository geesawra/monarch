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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import sh.christian.ozone.api.AtUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import sh.christian.ozone.api.Did

enum class EngagementType { Likes, Reposts, Quotes, AlsoLiked }

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
    var engagementUri by remember { mutableStateOf<AtUri?>(null) }
    var engagementType by remember { mutableStateOf<EngagementType?>(null) }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing.value,
        onRefresh = {
            isRefreshing.value = true
            timelineViewModel.getThread { isRefreshing.value = false }
        },
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
            val thread = timelineViewModel.currentlyShownThread
            val tappedPostUri = remember { thread.post.uri }
            val threadData = remember(thread) { thread.flatten(focusedUri = tappedPostUri, selection = settingsState.threadChainSelection) }
            val focusIdx = remember(threadData) {
                threadData.indexOfFirst { it.isFocused }.coerceAtLeast(0)
            }
            val threadKey = tappedPostUri.atUri

            val listState = rememberSaveable(
                threadKey, focusIdx,
                saver = LazyListState.Saver,
            ) {
                LazyListState(firstVisibleItemIndex = focusIdx.coerceAtLeast(0))
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

            LaunchedEffect(Unit) {
                timelineViewModel.dismissCurrentThread.collect { backButton() }
            }

            val contentModifier = if (!settingsState.forceCompactLayout) {
                Modifier.widthIn(max = 600.dp)
            } else {
                Modifier
            }

            if (timelineViewModel.threadNotFound) {
                PostNotFound(
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
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
                            isLoading = isRefreshing.value,
                            settingsState = settingsState,
                            onProfileTap = onProfileTap,
                            onReplyTap = onReplyTap,
                            onSeeMoreTap = { tapped ->
                                timelineViewModel.setThread(tapped)
                                onThreadTap()
                            },
                            onShowLikes = { uri ->
                                engagementUri = uri
                                engagementType = EngagementType.Likes
                            },
                            onShowReposts = { uri ->
                                engagementUri = uri
                                engagementType = EngagementType.Reposts
                            },
                    onShowQuotes = { uri ->
                        engagementUri = uri
                        engagementType = EngagementType.Quotes
                    },
                    onShowAlsoLiked = { uri ->
                        engagementUri = uri
                        engagementType = EngagementType.AlsoLiked
                    },
                        )
                    }
                }
            }
        }
    }

    engagementUri?.let { uri ->
        when (engagementType) {
            EngagementType.Likes -> {
                UserListSheet(
                    title = "Likes",
                    onDismiss = { engagementUri = null; engagementType = null },
                    fetchUsers = { cursor ->
                        timelineViewModel.getLikes(uri, cursor).getOrNull()?.let {
                            Pair(it.likes.map { like -> like.actor }, it.cursor)
                        }
                    },
                    onProfileTap = { did ->
                        engagementUri = null
                        engagementType = null
                        onProfileTap?.invoke(did)
                    },
                )
            }
            EngagementType.Reposts -> {
                UserListSheet(
                    title = "Reposts",
                    onDismiss = { engagementUri = null; engagementType = null },
                    fetchUsers = { cursor ->
                        timelineViewModel.getRepostedBy(uri, cursor).getOrNull()?.let {
                            Pair(it.repostedBy, it.cursor)
                        }
                    },
                    onProfileTap = { did ->
                        engagementUri = null
                        engagementType = null
                        onProfileTap?.invoke(did)
                    },
                )
            }
            EngagementType.Quotes -> {
                QuotesSheet(
                    onDismiss = { engagementUri = null; engagementType = null },
                    fetchQuotes = { cursor ->
                        timelineViewModel.getQuotes(uri, cursor).getOrNull()?.let {
                            Pair(it.posts.map { post -> SkeetData.fromPostView(post, post.author) }, it.cursor)
                        }
                    },
                    timelineViewModel = timelineViewModel,
                    onShowThread = { skeet ->
                        engagementUri = null
                        engagementType = null
                        timelineViewModel.setThread(skeet)
                        onThreadTap()
                    },
                    onProfileTap = { did ->
                        engagementUri = null
                        engagementType = null
                        onProfileTap?.invoke(did)
                    },
                )
            }
            EngagementType.AlsoLiked -> {
                AlsoLikedSheet(
                    onDismiss = { engagementUri = null; engagementType = null },
                    fetchAlsoLiked = { cursor ->
                        timelineViewModel.getAlsoLikedPosts(uri, cursor).getOrNull()
                    },
                    timelineViewModel = timelineViewModel,
                    settingsState = settingsState,
                    onShowThread = { skeet ->
                        engagementUri = null
                        engagementType = null
                        timelineViewModel.setThread(skeet)
                        onThreadTap()
                    },
                    onProfileTap = { did ->
                        engagementUri = null
                        engagementType = null
                        onProfileTap?.invoke(did)
                    },
                )
            }
            null -> {}
        }
    }
}