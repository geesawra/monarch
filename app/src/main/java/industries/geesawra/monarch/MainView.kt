@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did
import kotlin.time.ExperimentalTime

private sealed class DetailPaneContent {
    data object Empty : DetailPaneContent()
    data object Thread : DetailPaneContent()
    data class Profile(val did: Did) : DetailPaneContent()
}

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
    var showDiscardDialog by remember { mutableStateOf(false) }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden && wasEdited.value) {
                    showDiscardDialog = true
                    false
                } else {
                    true
                }
            }
        )
    )
    val inReplyTo = remember { mutableStateOf<SkeetData?>(null) }
    val isQuotePost = remember { mutableStateOf(false) }

    LaunchedEffect(settingsState.loaded) {
        if (settingsState.loaded) {
            onFirstLoad()
        }
    }

    val focusManager = LocalFocusManager.current
    BackHandler(enabled = scaffoldState.bottomSheetState.isVisible) {
        if (wasEdited.value) {
            showDiscardDialog = true
        } else {
            focusManager.clearFocus()
            coroutineScope.launch {
                scaffoldState.bottomSheetState.hide()
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard post?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    wasEdited.value = false
                    focusManager.clearFocus()
                    coroutineScope.launch {
                        scaffoldState.bottomSheetState.hide()
                    }
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
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
    val isRefreshing =
        timelineViewModel.uiState.isFetchingMoreTimeline || timelineViewModel.uiState.isFetchingMoreNotifications
    val isScrollEnabled = true
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current


    LaunchedEffect(timelineViewModel.uiState.loginError) {
        timelineViewModel.uiState.loginError?.let {
            Toast.makeText(ctx, "Authentication error: $it", Toast.LENGTH_LONG)
                .show()
            loginError()
        }
    }

    LaunchedEffect(timelineViewModel.uiState.error) {
        timelineViewModel.uiState.error?.let {
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

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchedEffect(timelineViewModel.uiState.unreadNotificationsAmt) {
                TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue =
                    timelineViewModel.uiState.unreadNotificationsAmt
            }

            val navLayoutType = if (settingsState.forceCompactLayout) {
                NavigationSuiteType.NavigationBar
            } else {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
            }

            NavigationSuiteScaffold(
                layoutType = navLayoutType,
                navigationSuiteItems = {
                    TabBarDestinations.entries.forEach { dest ->
                        item(
                            icon = {
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
                            },
                            label = if (navLayoutType == NavigationSuiteType.NavigationBar) {
                                { Text(stringResource(dest.label)) }
                            } else null,
                            selected = dest == currentDestination,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (dest == currentDestination) {
                                    val state = when (dest) {
                                        TabBarDestinations.TIMELINE -> timelineState
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
                        )
                    }
                },
            ) {
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
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        title = {
                            when (currentDestination) {
                                TabBarDestinations.TIMELINE -> Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (timelineViewModel.uiState.feedAvatar != null) {
                                        AsyncImage(
                                            model = timelineViewModel.uiState.feedAvatar,
                                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentDescription = "Feed avatar",
                                        )
                                    }

                                    Text(text = timelineViewModel.uiState.feedName)
                                }

                                TabBarDestinations.SEARCH -> {
                                    val authorFilter = timelineViewModel.uiState.searchAuthorFilter
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
                                TabBarDestinations.TIMELINE -> IconButton(onClick = {
                                    coroutineScope.launch {
                                        drawerState.expand()
                                    }
                                }) {
                                    Icon(Icons.Default.Tag, "Feeds")
                                }

                                TabBarDestinations.SEARCH -> {}
                                TabBarDestinations.NOTIFICATIONS -> {}
                            }
                        },
                        actions = {
                            when (currentDestination) {
                                TabBarDestinations.TIMELINE -> {
                                    val user = timelineViewModel.uiState.user
                                    var showAccountSwitcher by remember { mutableStateOf(false) }
                                    val avatarClipShape = if (settingsState.avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(8.dp) else CircleShape

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
                                                onClick = { user?.let { onProfileTap(it.did) } },
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
                                    val authorFilter = timelineViewModel.uiState.searchAuthorFilter
                                    if (authorFilter != null) {
                                        IconButton(onClick = {
                                            timelineViewModel.setSearchAuthorFilter(null)
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
                                                    timelineViewModel.setSearchAuthorFilter(handle)
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
                    when (currentDestination) {
                        TabBarDestinations.TIMELINE -> {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    fobOnClick()
                                }
                            ) {
                                Icon(Icons.Filled.Create, "Post")
                            }
                        }

                        TabBarDestinations.SEARCH -> {}
                        TabBarDestinations.NOTIFICATIONS -> {}
                    }
                },
            ) { values ->
                LaunchedEffect(notificationsState.canScrollBackward) {
                    TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue = 0
                }

                var detailPaneContent by remember { mutableStateOf<DetailPaneContent>(DetailPaneContent.Empty) }
                val detailRefreshing = remember { mutableStateOf(false) }

                val expandedOnSeeMoreTap: (SkeetData) -> Unit = { skeet ->
                    detailPaneContent = DetailPaneContent.Thread
                    detailRefreshing.value = true
                    timelineViewModel.setThread(skeet)
                    timelineViewModel.getThread {
                        detailRefreshing.value = false
                    }
                }

                val expandedOnProfileTap: (Did) -> Unit = { did ->
                    detailPaneContent = DetailPaneContent.Profile(did)
                    timelineViewModel.openProfile(did)
                }

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
                                if (isExpandedScreen) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                                            ShowSkeets(
                                                viewModel = timelineViewModel,
                                                settingsState = settingsState,
                                                state = timelineState,
                                                onReplyTap = onReplyTap,
                                                data = timelineViewModel.uiState.skeets,
                                                isLoading = timelineViewModel.uiState.isFetchingMoreTimeline,
                                                isScrollEnabled = isScrollEnabled,
                                                onSeeMoreTap = expandedOnSeeMoreTap,
                                                onProfileTap = expandedOnProfileTap,
                                            )
                                        }

                                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

                                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                            when (val content = detailPaneContent) {
                                                is DetailPaneContent.Thread -> DetailThreadPane(
                                                    timelineViewModel = timelineViewModel,
                                                    settingsState = settingsState,
                                                    isRefreshing = detailRefreshing.value,
                                                    onProfileTap = expandedOnProfileTap,
                                                    onReplyTap = onReplyTap,
                                                    onSeeMoreTap = expandedOnSeeMoreTap,
                                                )
                                                is DetailPaneContent.Profile -> DetailProfilePane(
                                                    did = content.did,
                                                    timelineViewModel = timelineViewModel,
                                                    settingsState = settingsState,
                                                    onProfileTap = expandedOnProfileTap,
                                                    onThreadTap = expandedOnSeeMoreTap,
                                                    onReplyTap = onReplyTap,
                                                    onBack = { detailPaneContent = DetailPaneContent.Empty },
                                                )
                                                is DetailPaneContent.Empty -> Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        "Select a post to view its thread",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
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
                                        data = timelineViewModel.uiState.skeets,
                                        isLoading = timelineViewModel.uiState.isFetchingMoreTimeline,
                                        isScrollEnabled = isScrollEnabled,
                                        onSeeMoreTap = onSeeMoreTap,
                                        onProfileTap = onProfileTap,
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
                data = timelineViewModel.uiState.currentlyShownThread.flatten(),
                shouldFetchMoreData = false,
                isShowingThread = true,
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
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val profile = timelineViewModel.uiState.profileUser
    val isLoading = timelineViewModel.uiState.isFetchingProfile && profile == null
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                title = {
                    Text(
                        profile?.displayName ?: profile?.handle?.handle ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier.padding(padding),
            isRefreshing = isLoading,
            onRefresh = { timelineViewModel.openProfile(did) },
        ) {
            if (profile == null) {
                if (timelineViewModel.uiState.profileNotFound) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Profile not found")
                    }
                }
            } else {
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
    WideNavigationRailItem(
        label = {
            Text(text = "Following")
        },
        selected = timelineViewModel.uiState.selectedFeed.lowercase() == "following",
        onClick = {
            selectFeed("following", "Following", null)
        },
        icon = {
            val userAvatar = timelineViewModel.uiState.user?.avatar?.uri
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

    timelineViewModel.uiState.feeds.forEach { feed ->
        WideNavigationRailItem(
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = feed.displayName)
                }
            },
            selected = timelineViewModel.uiState.selectedFeed == feed.uri.atUri,
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
