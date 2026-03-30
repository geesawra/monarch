@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
        )
    )
    val inReplyTo = remember { mutableStateOf<SkeetData?>(null) }
    val isQuotePost = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onFirstLoad()
    }

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden && wasEdited.value) {
            scaffoldState.bottomSheetState.expand()
            showDiscardDialog = true
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        rememberTopAppBarState()
    )
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
                        { uri: String, displayName: String, avatar: String? ->
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
                        timelineViewModel
                    )
                }
            )
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchedEffect(timelineViewModel.uiState.unreadNotificationsAmt) {
                TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue =
                    timelineViewModel.uiState.unreadNotificationsAmt
            }

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
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AnimatedVisibility(
                                    visible = timelineState.canScrollBackward,
                                    enter = slideInVertically(),
                                    exit = slideOutVertically()
                                ) {
                                    FloatingActionButton(
                                        modifier = Modifier
                                            .size(40.dp),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch {
                                                launch {
                                                    if (timelineState.firstVisibleItemIndex > 8) {
                                                        timelineState.scrollToItem(0)
                                                    } else {
                                                        timelineState.animateScrollToItem(0)
                                                    }
                                                }

                                                launch {
                                                    animate(
                                                        initialValue = scrollBehavior.state.heightOffset,
                                                        targetValue = 0f
                                                    ) { value, /* velocity */ _ ->
                                                        scrollBehavior.state.heightOffset =
                                                            value
                                                    }
                                                }
                                            }
                                        },
                                        shape = FloatingActionButtonDefaults.smallShape,
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, "Scroll to top")
                                    }
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

                        TabBarDestinations.SEARCH -> {}

                        TabBarDestinations.NOTIFICATIONS -> {
                            AnimatedVisibility(
                                visible = notificationsState.canScrollBackward,
                                enter = slideInVertically(),
                                exit = slideOutVertically()
                            ) {
                                FloatingActionButton(
                                    modifier = Modifier
                                        .size(40.dp),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            launch {
                                                if (notificationsState.firstVisibleItemIndex > 8) {
                                                    notificationsState.scrollToItem(0)
                                                } else {
                                                    notificationsState.animateScrollToItem(0)
                                                }
                                            }

                                            launch {
                                                animate(
                                                    initialValue = scrollBehavior.state.heightOffset,
                                                    targetValue = 0f
                                                ) { value, /* velocity */ _ ->
                                                    scrollBehavior.state.heightOffset = value
                                                }
                                            }
                                        }
                                    },
                                    shape = FloatingActionButtonDefaults.smallShape,
                                ) {
                                    Icon(Icons.Default.ArrowUpward, "Scroll to top")
                                }
                            }

                        }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        TabBarDestinations.entries.forEach {
                            NavigationBarItem(
                                icon = {
                                    if (it.badgeValue != null) {
                                        val badgeValue = remember { it.badgeValue }
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
                                                                    it.badgeDescFmt(it.badgeValue.intValue)
                                                            },
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                it.icon,
                                                contentDescription = stringResource(it.contentDescription)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            it.icon,
                                            contentDescription = stringResource(it.contentDescription)
                                        )
                                    }
                                },
                                label = { Text(stringResource(it.label)) },
                                selected = it == currentDestination,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentDestination = it
                                }
                            )
                        }
                    }
                }
            ) { values ->
                LaunchedEffect(notificationsState.canScrollBackward) {
                    TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue = 0
                }

                when (currentDestination) {
                    TabBarDestinations.TIMELINE -> ShowSkeets(
                        viewModel = timelineViewModel,
                        settingsState = settingsState,
                        state = timelineState,
                        modifier = Modifier.padding(values),
                        onReplyTap = onReplyTap,
                        data = timelineViewModel.uiState.skeets,
                        isLoading = timelineViewModel.uiState.isFetchingMoreTimeline,
                        isScrollEnabled = isScrollEnabled,
                        onSeeMoreTap = onSeeMoreTap,
                        onProfileTap = onProfileTap,
                    )

                    TabBarDestinations.SEARCH -> SearchView(
                        viewModel = timelineViewModel,
                        postsListState = searchPostsState,
                        peopleListState = searchPeopleState,
                        modifier = Modifier,
                        isScrollEnabled = isScrollEnabled,
                        scaffoldPadding = values,
                        onThreadTap = { skeet ->
                            onSeeMoreTap(skeet)
                        },
                        onProfileTap = onProfileTap,
                    )

                    TabBarDestinations.NOTIFICATIONS -> NotificationsView(
                        viewModel = timelineViewModel,
                        settingsState = settingsState,
                        state = notificationsState,
                        modifier = Modifier,
                        isScrollEnabled = isScrollEnabled,
                        onReplyTap = onReplyTap,
                        onProfileTap = onProfileTap,
                        scaffoldPadding = values,
                        onSeeMoreTap = onSeeMoreTap
                    )
                }
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
            Spacer(modifier = Modifier.size(20.dp))
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
