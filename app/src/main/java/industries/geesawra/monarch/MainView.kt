package industries.geesawra.monarch

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int
) {
    TIMELINE(R.string.timeline, Icons.Filled.Home, R.string.timeline),
    NOTIFICATIONS(R.string.notifications, Icons.Filled.Notifications, R.string.notifications)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    timelineViewModel: TimelineViewModel,
    coroutineScope: CoroutineScope,
    onLoginError: () -> Unit,
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
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle()
        },
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
                loginError = onLoginError
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
            Toast.makeText(ctx, "Error: $it", Toast.LENGTH_LONG)
                .show()
        }
    }
    val isRefreshing = remember { mutableStateOf(true) }

    PullToRefreshBox(
        isRefreshing = isRefreshing.value,
        onRefresh = {
            isRefreshing.value = true
            when (currentDestination) {
                TabBarDestinations.TIMELINE -> {
                    timelineViewModel.reset()
                    timelineViewModel.fetchTimeline { isRefreshing.value = false }
                }

                TabBarDestinations.NOTIFICATIONS -> {
                    timelineViewModel.reset()
                    timelineViewModel.fetchNotifications { isRefreshing.value = false }
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
                        ) { isRefreshing.value = false }
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    },
                    timelineViewModel
                )
            }
        ) {
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
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                when (currentDestination) {
                                    TabBarDestinations.TIMELINE -> timelineState.animateScrollToItem(
                                        0
                                    )

                                    TabBarDestinations.NOTIFICATIONS -> notificationsState.animateScrollToItem(
                                        0
                                    )

                                }
                            }
                        },
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
                        TabBarDestinations.TIMELINE -> FloatingActionButton(
                            onClick = fobOnClick
                        ) {
                            Icon(Icons.Filled.Create, "Post")
                        }

                        TabBarDestinations.NOTIFICATIONS -> {}
                    }
                },
                bottomBar = {
                    NavigationBar {
                        TabBarDestinations.entries.forEach {
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        it.icon,
                                        contentDescription = stringResource(it.contentDescription)
                                    )
                                },
                                label = { Text(stringResource(it.label)) },
                                selected = it == currentDestination,
                                onClick = { currentDestination = it }
                            )
                        }
                    }
                }
            ) { values ->
                when (currentDestination) {
                    TabBarDestinations.TIMELINE -> ShowSkeets(
                        viewModel = timelineViewModel,
                        state = timelineState,
                        modifier = Modifier.padding(values),
                        onReplyTap = onReplyTap
                    ) { isRefreshing.value = false }

                    TabBarDestinations.NOTIFICATIONS -> NotificationsView(
                        viewModel = timelineViewModel,
                        state = notificationsState,
                        modifier = Modifier.padding(values)
                    ) {
                        isRefreshing.value = false
                    }
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
                                model = feed.avatar?.uri,
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
