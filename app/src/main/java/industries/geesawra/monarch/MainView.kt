@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.widget.Toast
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import app.bsky.feed.ThreadgateAllowUnion
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.LinkPreviewData
import industries.geesawra.monarch.datalayer.NotificationBadge
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did
import kotlin.time.ExperimentalTime

private sealed class DetailPaneContent {
    data object Empty : DetailPaneContent()
    data object Thread : DetailPaneContent()
    data class Profile(val did: Did) : DetailPaneContent()
}

private data class FeedItem(val uri: String, val displayName: String, val avatar: String?)

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int,
    val badgeValue: MutableIntState? = null,
    val badgeDescFmt: (Int) -> String = { "" },
) {
    TIMELINE(R.string.timeline, Icons.Filled.Home, R.string.timeline),
    SEARCH(R.string.search, Icons.Filled.Search, R.string.search),
    NOTIFICATIONS(
        R.string.notifications,
        Icons.Filled.Notifications,
        R.string.notifications,
        mutableIntStateOf(0),
        badgeDescFmt = { notifAmt ->
            when (notifAmt) {
                0 -> "No new notifications"
                1 -> "1 new notification"
                else -> "$notifAmt new notifications"
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    coroutineScope: CoroutineScope,
    onLoginError: () -> Unit,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
    onSettingsTap: () -> Unit,
    onAddAccount: () -> Unit = {},
    onFirstLoad: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val wasEdited = remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showDraftsSheet by remember { mutableStateOf(false) }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden && wasEdited.value) {
                    showSaveDraftDialog = true
                    false
                } else {
                    true
                }
            }
        )
    )
    val inReplyTo = remember { mutableStateOf<SkeetData?>(null) }
    val isQuotePost = remember { mutableStateOf(false) }
    val composeTextFieldState = rememberTextFieldState()
    val composeMediaSelected = remember { mutableStateOf(listOf<Uri>()) }
    val composeMediaSelectedIsVideo = remember { mutableStateOf(false) }
    val composeThreadgateRules = remember { mutableStateOf<List<ThreadgateAllowUnion>?>(null) }
    val composeLinkPreview = remember { mutableStateOf<LinkPreviewData?>(null) }

    LaunchedEffect(timelineViewModel.redraftText) {
        if (timelineViewModel.redraftText != null) {
            inReplyTo.value = null
            isQuotePost.value = false
            scaffoldState.bottomSheetState.expand()
        }
    }

    LaunchedEffect(settingsState.loaded) {
        if (settingsState.loaded) {
            onFirstLoad()
        }
    }

    val focusManager = LocalFocusManager.current
    BackHandler(enabled = scaffoldState.bottomSheetState.isVisible) {
        if (wasEdited.value) {
            showSaveDraftDialog = true
        } else {
            focusManager.clearFocus()
            coroutineScope.launch {
                scaffoldState.bottomSheetState.hide()
            }
        }
    }

    if (showSaveDraftDialog) {
        SaveDraftDialog(
            onSaveDraft = {
                showSaveDraftDialog = false
                timelineViewModel.saveDraft(
                    text = composeTextFieldState.text.toString(),
                    mediaUris = composeMediaSelected.value,
                    isVideo = composeMediaSelectedIsVideo.value,
                    replyData = if (isQuotePost.value) inReplyTo.value else null,
                    isQuotePost = isQuotePost.value,
                    linkPreview = composeLinkPreview.value,
                    threadgateRules = composeThreadgateRules.value,
                )
                wasEdited.value = false
                focusManager.clearFocus()
                coroutineScope.launch {
                    scaffoldState.bottomSheetState.hide()
                }
            },
            onDiscard = {
                showSaveDraftDialog = false
                wasEdited.value = false
                timelineViewModel.clearActiveDraft()
                focusManager.clearFocus()
                coroutineScope.launch {
                    scaffoldState.bottomSheetState.hide()
                }
            },
            onKeepEditing = { showSaveDraftDialog = false },
        )
    }

    var localDeviceId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        localDeviceId = timelineViewModel.deviceId()
    }

    if (showDraftsSheet) {
        DraftsListSheet(
            timelineViewModel = timelineViewModel,
            localDeviceId = localDeviceId,
            onDismiss = { showDraftsSheet = false },
            onLoadDraft = { draftView ->
                showDraftsSheet = false
                val post = draftView.draft.posts.firstOrNull() ?: return@DraftsListSheet
                composeTextFieldState.edit {
                    replace(0, length, post.text)
                    selection = androidx.compose.ui.text.TextRange(post.text.length)
                }
                val isFromThisDevice = draftView.draft.deviceId == null || draftView.draft.deviceId == localDeviceId
                if (isFromThisDevice) {
                    val imageUris = post.embedImages?.map { Uri.parse(it.localRef.path) } ?: emptyList()
                    val videoUris = post.embedVideos?.map { Uri.parse(it.localRef.path) } ?: emptyList()
                    if (videoUris.isNotEmpty()) {
                        composeMediaSelected.value = videoUris
                        composeMediaSelectedIsVideo.value = true
                    } else {
                        composeMediaSelected.value = imageUris
                        composeMediaSelectedIsVideo.value = false
                    }
                } else {
                    composeMediaSelected.value = emptyList()
                    composeMediaSelectedIsVideo.value = false
                }
                val embedRecord = post.embedRecords?.firstOrNull()
                if (embedRecord != null) {
                    isQuotePost.value = true
                    coroutineScope.launch {
                        val postViews = timelineViewModel.fetchPostViews(listOf(embedRecord.record.uri))
                        val quotePost = postViews?.firstOrNull()
                        if (quotePost != null) {
                            inReplyTo.value = SkeetData.fromPostView(quotePost, quotePost.author)
                        }
                    }
                } else {
                    isQuotePost.value = false
                    inReplyTo.value = null
                }
                timelineViewModel.setActiveDraftId(draftView.id)
                wasEdited.value = true
                coroutineScope.launch {
                    scaffoldState.bottomSheetState.expand()
                }
            },
        )
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (scaffoldState.bottomSheetState.isVisible) 0.32f else 0f,
        label = "scrimAlpha"
    )

    BottomSheetScaffold(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetSwipeEnabled = true,
        sheetShadowElevation = 16.dp,
        sheetContent = {
            val redraftText = timelineViewModel.redraftText ?: ""
            if (redraftText.isNotEmpty()) timelineViewModel.setRedraft(null)
            ComposeView(
                context = LocalContext.current,
                coroutineScope = coroutineScope,
                timelineViewModel = timelineViewModel,
                settingsState = settingsState,
                scaffoldState = scaffoldState,
                scrollState = scrollState,
                inReplyTo = inReplyTo,
                isQuotePost = isQuotePost,
                wasEdited = wasEdited,
                initialText = redraftText,
                textfieldState = composeTextFieldState,
                mediaSelected = composeMediaSelected,
                mediaSelectedIsVideo = composeMediaSelectedIsVideo,
                threadgateRules = composeThreadgateRules,
                linkPreview = composeLinkPreview,
                onDraftsClick = { showDraftsSheet = true },
            )
        },
        snackbarHost = {
            SnackbarHost(it) { sd ->
                Snackbar(snackbarData = sd, actionOnNewLine = true)
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                InnerTimelineView(
                    modifier = Modifier.padding(paddingValues),
                    coroutineScope = coroutineScope,
                    timelineViewModel = timelineViewModel,
                    settingsState = settingsState,
                    onProfileTap = onProfileTap,
                    onSettingsTap = onSettingsTap,
                    onAddAccount = onAddAccount,
                    fobOnClick = {
                        coroutineScope.launch {
                            scaffoldState.bottomSheetState.expand()
                        }
                    },
                    onReplyTap = { skeetData, quotePost ->
                        inReplyTo.value = skeetData
                        isQuotePost.value = quotePost
                        coroutineScope.launch {
                            scaffoldState.bottomSheetState.expand()
                        }
                    },
                    loginError = onLoginError,
                    onError = {
                        coroutineScope.launch {
                            scaffoldState.snackbarHostState.showSnackbar(
                                message = "Error: $it",
                                withDismissAction = true,
                            )
                        }
                    },
                    onSeeMoreTap = {
                        onThreadTap(it)
                    }
                )

                if (scaffoldState.bottomSheetState.isVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                coroutineScope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }
                            }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun InnerTimelineView(
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onProfileTap: (Did) -> Unit = {},
    onSettingsTap: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    fobOnClick: () -> Unit,
    loginError: () -> Unit,
    onError: (String) -> Unit,
    onSeeMoreTap: (SkeetData) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(TabBarDestinations.TIMELINE) }
    var timelineSearchQuery by rememberSaveable { mutableStateOf("") }
    var isTimelineSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(timelineViewModel.pendingNotificationsTab) {
        if (timelineViewModel.pendingNotificationsTab) {
            currentDestination = TabBarDestinations.NOTIFICATIONS
            timelineViewModel.pendingNotificationsTab = false
        }
    }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpandedScreen = !settingsState.forceCompactLayout &&
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val scrollBehavior = if (isExpandedScreen) {
        TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    }
    val timelineState = rememberLazyListState()
    val notificationsState = rememberLazyListState()
    val searchPostsState = rememberLazyListState()
    val searchPeopleState = rememberLazyListState()
    val drawerState = rememberWideNavigationRailState(
        initialValue = WideNavigationRailValue.Collapsed
    )
    val feedItems = remember(timelineViewModel.feeds, timelineViewModel.user?.avatar) {
        listOf(FeedItem("following", "Following", timelineViewModel.user?.avatar?.uri)) +
        timelineViewModel.feeds.map { FeedItem(it.uri.atUri, it.displayName, it.avatar?.uri) }
    }
    val pagerState = rememberPagerState(pageCount = { feedItems.size })
    val pagerListStates = remember { mutableMapOf<Int, LazyListState>() }

    if (settingsState.swipeableFeeds) {
        LaunchedEffect(pagerState.settledPage) {
            val feed = feedItems.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            if (feed.uri != timelineViewModel.selectedFeed) {
                timelineViewModel.selectFeed(feed.uri, feed.displayName, feed.avatar)
            }
        }
    }

    var mediaFeedPosts by remember { mutableStateOf<ImmutableList<SkeetData>?>(null) }

    if (mediaFeedPosts != null) {
        Dialog(
            onDismissRequest = { mediaFeedPosts = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            MediaFeedView(
                posts = mediaFeedPosts!!,
                isLoading = timelineViewModel.isFetchingMoreTimeline,
                onLoadMore = { timelineViewModel.fetchTimeline() },
                onProfileTap = { did ->
                    mediaFeedPosts = null
                    onProfileTap(did)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val isRefreshing =
        timelineViewModel.isFetchingMoreTimeline || timelineViewModel.isFetchingMoreNotifications
    val isScrollEnabled = true
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current


    LaunchedEffect(timelineViewModel.loginError) {
        timelineViewModel.loginError?.let {
            Toast.makeText(ctx, "Authentication error: $it", Toast.LENGTH_LONG)
                .show()
            loginError()
        }
    }

    LaunchedEffect(timelineViewModel.error) {
        timelineViewModel.error?.let {
            onError(it)
            timelineViewModel.clearError()
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        indicator = {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
            )
        },
        onRefresh = {
            timelineViewModel.fetchAllNewData() {
                coroutineScope.launch {
                    launch {
                        timelineState.scrollToItem(0)
                    }
                    launch {
                        notificationsState.scrollToItem(0)
                    }
                }
            }
        },
    ) {
        if (!settingsState.swipeableFeeds) {
            Row(modifier = Modifier.fillMaxSize()) {
                ModalWideNavigationRail(
                    header = {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "Feeds",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    hideOnCollapse = true,
                    state = drawerState,
                    modifier = modifier,
                    content = {
                        FeedsDrawer(
                            state = drawerState.targetValue,
                            selectFeed = { uri: String, displayName: String, avatar: String? ->
                                coroutineScope.launch {
                                    drawerState.collapse()
                                }
                                timelineViewModel.selectFeed(
                                    uri,
                                    displayName,
                                    avatar
                                ) {
                                    coroutineScope.launch {
                                        launch {
                                            timelineState.scrollToItem(0)
                                        }
                                    }
                                }
                            },
                            timelineViewModel = timelineViewModel,
                        )
                    }
                )
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val notifBadgeCount by NotificationBadge.count.collectAsState()
            LaunchedEffect(notifBadgeCount) {
                TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue = notifBadgeCount
            }

            val navLayoutType = if (settingsState.forceCompactLayout) {
                NavigationSuiteType.NavigationBar
            } else {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
            }

            var detailPaneContent by remember { mutableStateOf<DetailPaneContent>(DetailPaneContent.Empty) }
            val detailRefreshing = remember { mutableStateOf(false) }

            val expandedOnSeeMoreTap: (SkeetData) -> Unit = { skeet ->
                detailPaneContent = DetailPaneContent.Thread
                detailRefreshing.value = true
                timelineViewModel.startThread(skeet)
                timelineViewModel.getThread {
                    detailRefreshing.value = false
                }
            }

            val expandedOnProfileTap: (Did) -> Unit = { did ->
                detailPaneContent = DetailPaneContent.Profile(did)
                timelineViewModel.openProfile(did)
            }

            val narrowScreen = isNarrowScreen()

            val navItemIcon: @Composable (TabBarDestinations) -> Unit = { dest ->
                if (dest.badgeValue != null) {
                    val badgeValue = remember { dest.badgeValue }
                    BadgedBox(
                        badge = {
                            if (badgeValue.intValue == 0) {
                                return@BadgedBox
                            }

                            Badge {
                                Text(
                                    badgeValue.intValue.toString(),
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription =
                                                dest.badgeDescFmt(dest.badgeValue.intValue)
                                        },
                                )
                            }
                        }
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = stringResource(dest.contentDescription)
                        )
                    }
                } else {
                    Icon(
                        dest.icon,
                        contentDescription = stringResource(dest.contentDescription)
                    )
                }
            }

            val navItemOnClick: (TabBarDestinations) -> Unit = { dest ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (dest == currentDestination) {
                    val state = when (dest) {
                        TabBarDestinations.TIMELINE -> if (settingsState.swipeableFeeds) {
                            pagerListStates[pagerState.settledPage] ?: timelineState
                        } else timelineState
                        TabBarDestinations.SEARCH -> searchPostsState
                        TabBarDestinations.NOTIFICATIONS -> notificationsState
                    }
                    coroutineScope.launch {
                        launch {
                            if (state.firstVisibleItemIndex > 8) {
                                state.scrollToItem(0)
                            } else {
                                state.animateScrollToItem(0)
                            }
                        }
                        launch {
                            animate(
                                initialValue = scrollBehavior.state.heightOffset,
                                targetValue = 0f
                            ) { value, _ ->
                                scrollBehavior.state.heightOffset = value
                            }
                        }
                    }
                } else {
                    currentDestination = dest
                }
            }

            val compactBottomBar: @Composable () -> Unit = {
                val barHeight = 64.dp
                val density = LocalDensity.current
                val collapseFraction = with(scrollBehavior.state) {
                    if (heightOffsetLimit != 0f) (heightOffset / heightOffsetLimit).coerceIn(0f, 1f) else 0f
                }
                val visibleHeight = barHeight * (1f - collapseFraction)
                Box(modifier = Modifier.height(visibleHeight).clipToBounds()) {
                    NavigationBar(modifier = Modifier.height(barHeight)) {
                        TabBarDestinations.entries.forEach { dest ->
                            NavigationBarItem(
                                icon = { navItemIcon(dest) },
                                label = null,
                                selected = dest == currentDestination,
                                onClick = { navItemOnClick(dest) },
                            )
                        }
                    }
                }
            }

            NavigationSuiteScaffold(
                layoutType = if (narrowScreen) NavigationSuiteType.None else navLayoutType,
                navigationSuiteItems = {
                    TabBarDestinations.entries.forEach { dest ->
                        item(
                            icon = { navItemIcon(dest) },
                            label = if (navLayoutType == NavigationSuiteType.NavigationBar) {
                                { Text(stringResource(dest.label)) }
                            } else null,
                            selected = dest == currentDestination,
                            onClick = { navItemOnClick(dest) },
                        )
                    }
                },
            ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                bottomBar = if (narrowScreen) compactBottomBar else { {} },
                topBar = {
                    TopAppBar(
                        expandedHeight = if (isNarrowScreen()) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
                        colors = monarchTopAppBarColors(),
                        title = {
                            when (currentDestination) {
                                TabBarDestinations.TIMELINE -> if (isTimelineSearchActive) {
                                    val focusRequester = remember { FocusRequester() }
                                    BasicTextField(
                                        value = timelineSearchQuery,
                                        onValueChange = { timelineSearchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            if (timelineSearchQuery.isEmpty()) {
                                                Text(
                                                    "Filter posts...",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            innerTextField()
                                        },
                                    )
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                } else if (settingsState.swipeableFeeds) {
                                    Text(text = timelineViewModel.user?.displayName
                                        ?: timelineViewModel.user?.handle?.handle
                                        ?: "")
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (timelineViewModel.feedAvatar != null && timelineViewModel.selectedFeed != "following") {
                                            AsyncImage(
                                                model = timelineViewModel.feedAvatar,
                                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier
                                                    .size(topBarAvatarSize())
                                                    .clip(CircleShape),
                                                contentDescription = "Feed avatar",
                                            )
                                        }

                                        Text(text = timelineViewModel.feedName)
                                    }
                                }

                                TabBarDestinations.SEARCH -> {
                                    val authorFilter = timelineViewModel.searchAuthorFilter
                                    if (authorFilter != null) {
                                        Text(text = "from:$authorFilter")
                                    } else {
                                        Text(text = "Search")
                                    }
                                }

                                TabBarDestinations.NOTIFICATIONS -> Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Notifications")
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            when (currentDestination) {
                                TabBarDestinations.TIMELINE -> {
                                    if (isTimelineSearchActive) {
                                        IconButton(onClick = {
                                            isTimelineSearchActive = false
                                            timelineSearchQuery = ""
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                                        }
                                    } else if (!settingsState.swipeableFeeds) {
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                drawerState.expand()
                                            }
                                        }) {
                                            Icon(Icons.Default.Tag, "Feeds")
                                        }
                                    }
                                }
                                TabBarDestinations.SEARCH -> {}
                                TabBarDestinations.NOTIFICATIONS -> {}
                            }
                        },
                        actions = {
                            when (currentDestination) {
                                TabBarDestinations.TIMELINE -> if (isTimelineSearchActive) {
                                    if (timelineSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { timelineSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, "Clear search")
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { isTimelineSearchActive = true }) {
                                        Icon(Icons.Default.Search, "Search timeline")
                                    }

                                    val user = timelineViewModel.user
                                    var showAccountSwitcher by remember { mutableStateOf(false) }
                                    val avatarClipShape = settingsState.avatarClipShape

                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(user?.avatar?.uri)
                                            .crossfade(true)
                                            .build(),
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        contentDescription = "Profile avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(40.dp)
                                            .clip(avatarClipShape)
                                            .combinedClickable(
                                                onClick = { user?.let { (if (isExpandedScreen) expandedOnProfileTap else onProfileTap)(it.did) } },
                                                onLongClick = { showAccountSwitcher = true }
                                            )
                                    )

                                    if (showAccountSwitcher) {
                                        AccountSwitcherSheet(
                                            accounts = timelineViewModel.accounts,
                                            activeDid = timelineViewModel.activeDid,
                                            onSwitchAccount = { did ->
                                                timelineViewModel.switchAccount(did)
                                            },
                                            onAddAccount = onAddAccount,
                                            onRemoveAccount = { did ->
                                                timelineViewModel.logout {
                                                    if (timelineViewModel.accounts.isEmpty()) {
                                                        loginError()
                                                    }
                                                }
                                            },
                                            onDismiss = { showAccountSwitcher = false }
                                        )
                                    }
                                }

                                TabBarDestinations.SEARCH -> {
                                    val authorFilter = timelineViewModel.searchAuthorFilter
                                    if (authorFilter != null) {
                                        IconButton(onClick = {
                                            timelineViewModel.changeSearchAuthorFilter(null)
                                        }) {
                                            Icon(Icons.Default.PersonSearch, "Remove author filter")
                                        }
                                    } else {
                                        var showFromDialog by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showFromDialog = true }) {
                                            Icon(Icons.Default.PersonSearch, "Filter by author")
                                        }
                                        if (showFromDialog) {
                                            SearchFromAuthorDialog(
                                                onDismiss = { showFromDialog = false },
                                                onConfirm = { handle ->
                                                    timelineViewModel.changeSearchAuthorFilter(handle)
                                                    showFromDialog = false
                                                },
                                            )
                                        }
                                    }
                                }
                                TabBarDestinations.NOTIFICATIONS -> {}
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (LocalBaselineProfileMode.current || isExpandedScreen) {} else when (currentDestination) {
                        TabBarDestinations.TIMELINE -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val feedUri = feedItems.getOrNull(pagerState.settledPage)?.uri ?: "following"
                                        mediaFeedPosts = timelineViewModel.feedSkeets[feedUri] ?: timelineViewModel.skeets
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Icon(Icons.Default.ViewStream, "Media scroll")
                                }

                                if (narrowScreen) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            fobOnClick()
                                        }
                                    ) {
                                        Icon(Icons.Filled.Create, "Post")
                                    }
                                } else {
                                    FloatingActionButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            fobOnClick()
                                        }
                                    ) {
                                        Icon(Icons.Filled.Create, "Post")
                                    }
                                }
                            }
                        }

                        TabBarDestinations.SEARCH -> {}
                        TabBarDestinations.NOTIFICATIONS -> {}
                    }
                },
            ) { values ->
                val contentModifier = if (!settingsState.forceCompactLayout && !isExpandedScreen) {
                    Modifier.widthIn(max = 600.dp)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier.fillMaxSize().padding(values),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = contentModifier.fillMaxSize()) {
                        when (currentDestination) {
                            TabBarDestinations.TIMELINE -> {
                                if (settingsState.swipeableFeeds) {
                                    if (isExpandedScreen) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    SecondaryScrollableTabRow(
                                                        selectedTabIndex = pagerState.currentPage.coerceIn(0, (feedItems.size - 1).coerceAtLeast(0)),
                                                        edgePadding = 8.dp,
                                                        divider = {},
                                                    ) {
                                                        feedItems.forEachIndexed { index, feed ->
                                                            Tab(
                                                                selected = pagerState.currentPage == index,
                                                                onClick = {
                                                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                                                },
                                                                text = {
                                                                    Row(
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        if (feed.avatar != null) {
                                                                            AsyncImage(
                                                                                model = feed.avatar,
                                                                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                                                modifier = Modifier
                                                                                    .size(18.dp)
                                                                                    .clip(CircleShape),
                                                                                contentDescription = null,
                                                                            )
                                                                        }
                                                                        Text(feed.displayName)
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    HorizontalPager(
                                                        state = pagerState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        beyondViewportPageCount = 0,
                                                        contentPadding = PaddingValues(horizontal = 0.dp),
                                                    ) { page ->
                                                        val feedUri = feedItems.getOrNull(page)?.uri ?: return@HorizontalPager
                                                        val pageData = timelineViewModel.feedSkeets[feedUri] ?: persistentListOf()
                                                        val notYetLoaded = feedUri !in timelineViewModel.feedSkeets
                                                        val pageListState = rememberLazyListState()
                                                        pagerListStates[page] = pageListState
                                                        ShowSkeets(
                                                            viewModel = timelineViewModel,
                                                            settingsState = settingsState,
                                                            state = pageListState,
                                                            onReplyTap = onReplyTap,
                                                            data = pageData,
                                                            isLoading = notYetLoaded || (timelineViewModel.isFetchingMoreTimeline && page == pagerState.settledPage),
                                                            isScrollEnabled = isScrollEnabled,
                                                            onSeeMoreTap = expandedOnSeeMoreTap,
                                                            onProfileTap = expandedOnProfileTap,
                                                            shouldFetchMoreData = page == pagerState.settledPage,
                                                            searchFilter = timelineSearchQuery,
                                                        )
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    SmallFloatingActionButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val feedUri = feedItems.getOrNull(pagerState.settledPage)?.uri ?: "following"
                                                            mediaFeedPosts = timelineViewModel.feedSkeets[feedUri] ?: timelineViewModel.skeets
                                                        },
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    ) {
                                                        Icon(Icons.Default.ViewStream, "Media scroll")
                                                    }
                                                    FloatingActionButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            fobOnClick()
                                                        }
                                                    ) {
                                                        Icon(Icons.Filled.Create, "Post")
                                                    }
                                                }
                                            }

                                            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

                                            Box(
                                                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                                            ) {
                                                when (val content = detailPaneContent) {
                                                    is DetailPaneContent.Thread -> DetailThreadPane(
                                                        timelineViewModel = timelineViewModel,
                                                        settingsState = settingsState,
                                                        isRefreshing = detailRefreshing.value,
                                                        onProfileTap = expandedOnProfileTap,
                                                        onReplyTap = onReplyTap,
                                                        onSeeMoreTap = expandedOnSeeMoreTap,
                                                        onClose = { detailPaneContent = DetailPaneContent.Empty },
                                                    )
                                                    is DetailPaneContent.Profile -> DetailProfilePane(
                                                        did = content.did,
                                                        timelineViewModel = timelineViewModel,
                                                        settingsState = settingsState,
                                                        onProfileTap = expandedOnProfileTap,
                                                        onThreadTap = expandedOnSeeMoreTap,
                                                        onReplyTap = onReplyTap,
                                                        onSettingsTap = onSettingsTap,
                                                        onClose = { detailPaneContent = DetailPaneContent.Empty },
                                                    )
                                                    is DetailPaneContent.Empty -> Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Forum,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(48.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                            Text(
                                                                "Tap a post to view the thread",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        SecondaryScrollableTabRow(
                                            selectedTabIndex = pagerState.currentPage.coerceIn(0, (feedItems.size - 1).coerceAtLeast(0)),
                                            edgePadding = 8.dp,
                                            divider = {},
                                        ) {
                                            feedItems.forEachIndexed { index, feed ->
                                                Tab(
                                                    selected = pagerState.currentPage == index,
                                                    onClick = {
                                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                                    },
                                                    text = {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (feed.avatar != null) {
                                                                AsyncImage(
                                                                    model = feed.avatar,
                                                                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                                    modifier = Modifier
                                                                        .size(18.dp)
                                                                        .clip(CircleShape),
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                            Text(feed.displayName)
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            beyondViewportPageCount = 0,
                                        ) { page ->
                                            val feedUri = feedItems.getOrNull(page)?.uri ?: return@HorizontalPager
                                            val pageData = timelineViewModel.feedSkeets[feedUri] ?: persistentListOf()
                                            val notYetLoaded = feedUri !in timelineViewModel.feedSkeets
                                            val pageListState = rememberLazyListState()
                                            pagerListStates[page] = pageListState
                                            ShowSkeets(
                                                viewModel = timelineViewModel,
                                                settingsState = settingsState,
                                                state = pageListState,
                                                onReplyTap = onReplyTap,
                                                data = pageData,
                                                isLoading = notYetLoaded || (timelineViewModel.isFetchingMoreTimeline && page == pagerState.settledPage),
                                                isScrollEnabled = isScrollEnabled,
                                                onSeeMoreTap = onSeeMoreTap,
                                                onProfileTap = onProfileTap,
                                                shouldFetchMoreData = page == pagerState.settledPage,
                                                searchFilter = timelineSearchQuery,
                                            )
                                        }
                                    }
                                    }
                                } else if (isExpandedScreen) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                            ShowSkeets(
                                                viewModel = timelineViewModel,
                                                settingsState = settingsState,
                                                state = timelineState,
                                                onReplyTap = onReplyTap,
                                                data = timelineViewModel.skeets,
                                                isLoading = timelineViewModel.isFetchingMoreTimeline,
                                                isScrollEnabled = isScrollEnabled,
                                                onSeeMoreTap = expandedOnSeeMoreTap,
                                                onProfileTap = expandedOnProfileTap,
                                                searchFilter = timelineSearchQuery,
                                            )
                                            Column(
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                SmallFloatingActionButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        val feedUri = feedItems.getOrNull(pagerState.settledPage)?.uri ?: "following"
                                                        mediaFeedPosts = timelineViewModel.feedSkeets[feedUri] ?: timelineViewModel.skeets
                                                    },
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                ) {
                                                    Icon(Icons.Default.ViewStream, "Media scroll")
                                                }
                                                FloatingActionButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        fobOnClick()
                                                    }
                                                ) {
                                                    Icon(Icons.Filled.Create, "Post")
                                                }
                                            }
                                        }

                                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

                                        Box(
                                            modifier = Modifier.weight(0.55f).fillMaxHeight(),
                                        ) {
                                            when (val content = detailPaneContent) {
                                                is DetailPaneContent.Thread -> DetailThreadPane(
                                                    timelineViewModel = timelineViewModel,
                                                    settingsState = settingsState,
                                                    isRefreshing = detailRefreshing.value,
                                                    onProfileTap = expandedOnProfileTap,
                                                    onReplyTap = onReplyTap,
                                                    onSeeMoreTap = expandedOnSeeMoreTap,
                                                    onClose = { detailPaneContent = DetailPaneContent.Empty },
                                                )
                                                is DetailPaneContent.Profile -> DetailProfilePane(
                                                    did = content.did,
                                                    timelineViewModel = timelineViewModel,
                                                    settingsState = settingsState,
                                                    onProfileTap = expandedOnProfileTap,
                                                    onThreadTap = expandedOnSeeMoreTap,
                                                    onReplyTap = onReplyTap,
                                                    onSettingsTap = onSettingsTap,
                                                    onClose = { detailPaneContent = DetailPaneContent.Empty },
                                                )
                                                is DetailPaneContent.Empty -> Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Forum,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(48.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Text(
                                                            "Tap a post to view the thread",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    ShowSkeets(
                                        viewModel = timelineViewModel,
                                        settingsState = settingsState,
                                        state = timelineState,
                                        onReplyTap = onReplyTap,
                                        data = timelineViewModel.skeets,
                                        isLoading = timelineViewModel.isFetchingMoreTimeline,
                                        isScrollEnabled = isScrollEnabled,
                                        onSeeMoreTap = onSeeMoreTap,
                                        onProfileTap = onProfileTap,
                                        searchFilter = timelineSearchQuery,
                                    )
                                }
                            }

                            TabBarDestinations.SEARCH -> SearchView(
                                viewModel = timelineViewModel,
                                postsListState = searchPostsState,
                                peopleListState = searchPeopleState,
                                modifier = Modifier,
                                isScrollEnabled = isScrollEnabled,
                                scaffoldPadding = PaddingValues(),
                                onThreadTap = { skeet ->
                                    if (isExpandedScreen) expandedOnSeeMoreTap(skeet)
                                    else onSeeMoreTap(skeet)
                                },
                                onProfileTap = if (isExpandedScreen) expandedOnProfileTap else onProfileTap,
                            )

                            TabBarDestinations.NOTIFICATIONS -> NotificationsView(
                                viewModel = timelineViewModel,
                                settingsState = settingsState,
                                state = notificationsState,
                                modifier = Modifier,
                                isScrollEnabled = isScrollEnabled,
                                onReplyTap = onReplyTap,
                                onProfileTap = if (isExpandedScreen) expandedOnProfileTap else onProfileTap,
                                scaffoldPadding = PaddingValues(),
                                onSeeMoreTap = if (isExpandedScreen) expandedOnSeeMoreTap else onSeeMoreTap
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailThreadPane(
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    isRefreshing: Boolean,
    onProfileTap: (Did) -> Unit,
    onReplyTap: (SkeetData, Boolean) -> Unit,
    onSeeMoreTap: (SkeetData) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                title = { Text("Thread") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier.padding(padding),
            isRefreshing = isRefreshing,
            onRefresh = {},
        ) {
            ShowSkeets(
                viewModel = timelineViewModel,
                isScrollEnabled = true,
                data = timelineViewModel.currentlyShownThread.flatten(),
                shouldFetchMoreData = false,
                isShowingThread = true,
                isLoading = isRefreshing,
                settingsState = settingsState,
                onProfileTap = onProfileTap,
                onReplyTap = onReplyTap,
                onSeeMoreTap = onSeeMoreTap,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailProfilePane(
    did: Did,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    onProfileTap: (Did) -> Unit,
    onThreadTap: (SkeetData) -> Unit,
    onReplyTap: (SkeetData, Boolean) -> Unit,
    onSettingsTap: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val profile = timelineViewModel.profileUser
    val isLoading = timelineViewModel.isFetchingProfile && profile == null
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        profile?.displayName ?: profile?.handle?.handle ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (profile != null && timelineViewModel.isOwnProfile()) {
                        IconButton(onClick = onSettingsTap) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator()
                } else if (timelineViewModel.profileNotFound) {
                    Text("Profile not found")
                }
            }
        } else {
            PullToRefreshBox(
                modifier = Modifier.padding(padding),
                isRefreshing = false,
                onRefresh = { timelineViewModel.openProfile(did) },
            ) {
                ProfileContent(
                    profile = profile,
                    timelineViewModel = timelineViewModel,
                    settingsState = settingsState,
                    listState = listState,
                    onThreadTap = onThreadTap,
                    onProfileTap = onProfileTap,
                    onReplyTap = onReplyTap,
                )
            }
        }
    }
}



@Composable
fun FeedsDrawer(
    state: WideNavigationRailValue,
    selectFeed: (uri: String, displayName: String, avatar: String?) -> Unit,
    timelineViewModel: TimelineViewModel,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WideNavigationRailItem(
            label = {
                Text(text = "Following")
            },
            selected = timelineViewModel.selectedFeed.lowercase() == "following",
            onClick = {
                selectFeed("following", "Following", null)
            },
            icon = {
                val userAvatar = timelineViewModel.user?.avatar?.uri
                if (userAvatar != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(userAvatar)
                            .crossfade(true)
                            .build(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                        contentDescription = "Following feed",
                    )
                } else {
                    Spacer(modifier = Modifier.size(20.dp))
                }
            },
            railExpanded = state == WideNavigationRailValue.Expanded,
        )

        timelineViewModel.feeds.forEach { feed ->
            WideNavigationRailItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = feed.displayName)
                    }
                },
                selected = timelineViewModel.selectedFeed == feed.uri.atUri,
                onClick = {
                    selectFeed(feed.uri.atUri, feed.displayName, feed.avatar?.uri)
                },
                icon = {
                    if (feed.avatar != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(feed.avatar?.uri)
                                .crossfade(true)
                                .build(),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape),
                            contentDescription = "Feed avatar",
                        )
                    } else {
                        Spacer(modifier = Modifier.size(20.dp))
                    }
                },
                railExpanded = state == WideNavigationRailValue.Expanded,
            )
        }
    }
}

@Composable
fun SearchFromAuthorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var handle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by author") },
        text = {
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("Handle") },
                placeholder = { Text("e.g. alice.bsky.social") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (handle.isNotBlank()) onConfirm(handle.trim()) },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
