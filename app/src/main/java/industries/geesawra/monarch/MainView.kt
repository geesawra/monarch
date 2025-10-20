@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int,
    val badgeValue: MutableIntState? = null,
    val badgeDescFmt: (Int) -> String = { "" },
) {
    TIMELINE(R.string.timeline, Icons.Filled.Home, R.string.timeline),
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
    coroutineScope: CoroutineScope,
    onLoginError: () -> Unit,
    onThreadTap: (SkeetData) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    )
    val inReplyTo = remember { mutableStateOf<SkeetData?>(null) }
    val isQuotePost = remember { mutableStateOf(false) }

    BottomSheetScaffold(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetSwipeEnabled = false,
        sheetContent = {
            ComposeView(
                context = LocalContext.current,
                coroutineScope = coroutineScope,
                timelineViewModel = timelineViewModel,
                scaffoldState = scaffoldState,
                scrollState = scrollState,
                inReplyTo = inReplyTo,
                isQuotePost = isQuotePost
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InnerTimelineView(
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    timelineViewModel: TimelineViewModel,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
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
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val isRefreshing = remember { mutableStateOf(true) }
    val isScrollEnabled = !isRefreshing.value
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        timelineViewModel.feeds()
    }


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
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing.value,
        onRefresh = {
            isRefreshing.value = true
            when (currentDestination) {
                TabBarDestinations.TIMELINE -> {
                    timelineViewModel.fetchTimeline(fresh = true) {
                        coroutineScope.launch {
                            isRefreshing.value = false
                            timelineState.scrollToItem(0)
                        }
                    }
                }

                TabBarDestinations.NOTIFICATIONS -> {
                    timelineViewModel.fetchNotifications(fresh = true) {
                        coroutineScope.launch {
                            isRefreshing.value = false
                            notificationsState.scrollToItem(0)
                        }
                    }
                }
            }
        },
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier,
            drawerContent = {
                FeedsDrawer(
                    { uri: String, displayName: String, avatar: String? ->
                        isRefreshing.value = true
                        timelineViewModel.selectFeed(
                            uri,
                            displayName,
                            avatar
                        ) {
                            coroutineScope.launch {
                                isRefreshing.value = false
                                timelineState.scrollToItem(0)
                            }
                        }

                        coroutineScope.launch {
                            drawerState.close()
                        }
                    },
                    timelineViewModel
                )
            }
        ) {
            LaunchedEffect(timelineViewModel.uiState.unreadNotificationsAmt) {
                TabBarDestinations.NOTIFICATIONS.badgeValue?.intValue =
                    timelineViewModel.uiState.unreadNotificationsAmt
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground, // Ensuring correct contrast
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                            subtitleContentColor = MaterialTheme.colorScheme.onBackground
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
                                            modifier = Modifier
                                                .size(42.dp)
                                                .shadow(10.dp, CircleShape)
                                                .clip(CircleShape),
                                            contentDescription = "Feed avatar",
                                        )
                                    }

                                    Text(text = timelineViewModel.uiState.feedName)
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
                                        drawerState.open()
                                    }
                                }) {
                                    Icon(Icons.Default.Tag, "Feeds")
                                }

                                TabBarDestinations.NOTIFICATIONS -> {}
                            }
                        },
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

                                FloatingActionButton(
                                    onClick = fobOnClick
                                ) {
                                    Icon(Icons.Filled.Create, "Post")
                                }
                            }
                        }

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
                                onClick = { currentDestination = it }
                            )
                        }
                    }
                }
            ) { values ->
                LaunchedEffect(Unit) {
                    timelineViewModel.fetchNewData {
                        isRefreshing.value = false
                    }
                }

                when (currentDestination) {
                    TabBarDestinations.TIMELINE -> ShowSkeets(
                        viewModel = timelineViewModel,
                        state = timelineState,
                        modifier = Modifier.padding(values),
                        onReplyTap = onReplyTap,
                        data = timelineViewModel.uiState.skeets,
                        isScrollEnabled = isScrollEnabled,
                        onSeeMoreTap = onSeeMoreTap
                    )

                    TabBarDestinations.NOTIFICATIONS -> NotificationsView(
                        viewModel = timelineViewModel,
                        state = notificationsState,
                        modifier = Modifier.padding(values),
                        isScrollEnabled = isScrollEnabled,
                        onReplyTap = onReplyTap
                    )
                }
            }
        }
    }
}

@Composable
fun FeedsDrawer(
    selectFeed: (uri: String, displayName: String, avatar: String?) -> Unit,
    timelineViewModel: TimelineViewModel,
) {
    ModalDrawerSheet {
        Text(
            "Feeds",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        NavigationDrawerItem(
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(20.dp))
                    Text(text = "Following")
                }
            },
            selected = timelineViewModel.uiState.selectedFeed.lowercase() == "following",
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            onClick = {
                selectFeed("following", "Following", null)
            }
        )

        timelineViewModel.uiState.feeds.forEach { feed ->
            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (feed.avatar != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(feed.avatar?.uri)
                                    .crossfade(true)
                                    .build(),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentDescription = "Feed avatar",
                            )
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }

                        Text(text = feed.displayName)
                    }
                },
                selected = timelineViewModel.uiState.selectedFeed == feed.uri.atUri,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                onClick = {
                    selectFeed(feed.uri.atUri, feed.displayName, feed.avatar?.uri)
                }
            )
        }

    }
}
