package industries.geesawra.jerryno

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import industries.geesawra.jerryno.datalayer.BlueskyConn
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import industries.geesawra.jerryno.ui.theme.JerryNoTheme


@HiltAndroidApp
class Application : Application() {}

enum class TimelineScreen() {
    Login,
    Timeline,
    Compose
}

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int
) {
    HOME(R.string.timeline, Icons.Filled.Home, R.string.timeline),
    NOTIFICATIONS(R.string.notifications, Icons.Filled.Notifications, R.string.notifications)
}

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JerryNoTheme {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    rememberTopAppBarState()
                )
                val conn = BlueskyConn(LocalContext.current)
                val timelineViewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
                    creationCallback = { factory ->
                        factory.create(conn)
                    }
                )
                var currentDestination by rememberSaveable { mutableStateOf(TabBarDestinations.HOME) }
                val navController = rememberNavController()
                val loggingIn = remember { mutableStateOf(true) }
                val modalSheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { sv ->
                        sv != SheetValue.PartiallyExpanded
                    }
                )
                var showBottomSheet by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                val coroutineScope = rememberCoroutineScope()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NavigationSuiteScaffold(
                        navigationSuiteItems = {
                            if (loggingIn.value) {
                                return@NavigationSuiteScaffold
                            }

                            TabBarDestinations.entries.forEach {
                                item(
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
                    ) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            topBar = {
                                if (loggingIn.value) {
                                    return@Scaffold
                                }

                                MediumTopAppBar(
                                    colors = TopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        navigationIconContentColor = MaterialTheme.colorScheme.surfaceContainer,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    title = {
                                        Text(text = "Jerry No")
                                    },
                                    scrollBehavior = scrollBehavior
                                )
                            },
                            floatingActionButton = {
                                if (loggingIn.value) {
                                    return@Scaffold
                                }

                                FloatingActionButton(
                                    onClick = {
                                        navController.navigate(TimelineScreen.Compose.name)
                                    },
                                ) {
                                    Icon(Icons.Filled.Create, "Post")
                                }
                            },
                        ) { values ->

                            timelineViewModel.loadSession()
                            if (!timelineViewModel.uiState.sessionChecked) {
                                Box(
                                    modifier = Modifier.fillMaxSize(), // Make the Box take the full available space
                                    contentAlignment = Alignment.Center // Align content (LoginView) to the center
                                ) {
                                }

                                return@Scaffold
                            }

                            val initialRoute =
                                if (timelineViewModel.uiState.authenticated) TimelineScreen.Timeline.name else TimelineScreen.Login.name

                            NavHost(
                                navController = navController,
                                startDestination = initialRoute,
                                modifier = Modifier.padding(values)
                            ) {
                                composable(route = TimelineScreen.Timeline.name) {
                                    loggingIn.value = false
                                    timelineViewModel.create()

                                    val listState = rememberLazyListState()
                                    listState.canScrollBackward

                                    ShowSkeets(
                                        viewModel = timelineViewModel,
                                        state = listState
                                    )

                                    if (showBottomSheet) {
                                        ComposeView(
                                            modalSheetState = modalSheetState,
                                            focusRequester = focusRequester,
                                            timelineViewModel = timelineViewModel,
                                            onDismissRequest = {
                                                showBottomSheet = false
                                            }
                                        )
                                    }
                                }
                                composable(route = TimelineScreen.Compose.name) {
                                    Text(
                                        "Compose",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    )
                                }
                                composable(route = TimelineScreen.Login.name) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(), // Make the Box take the full available space
                                        contentAlignment = Alignment.Center // Align content (LoginView) to the center
                                    ) {
                                        LoginView {
                                            navController.navigate(TimelineScreen.Timeline.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExoPlayerView(uri: String, modifier: Modifier) {
    // Get the current context
    val context = LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = ExoPlayer.Builder(context).build()

    // Create a MediaSource
    val mediaSource = remember(uri) {
        MediaItem.fromUri(uri)
    }

    // Set MediaSource to ExoPlayer
    LaunchedEffect(mediaSource) {
        exoPlayer.setMediaItem(mediaSource)
        exoPlayer.prepare()
    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Use AndroidView to embed an Android View (PlayerView) into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
            }
        },
        modifier = modifier
    )
}

@Composable
fun ShowSkeets(
    viewModel: TimelineViewModel,
    state: LazyListState = rememberLazyListState()
) {
    LaunchedEffect(key1 = viewModel.uiState.skeets.isEmpty()) {
        if (viewModel.uiState.skeets.isEmpty()) {
            // Check if not already fetching, if you have such a state in ViewModel
            // e.g., if (!viewModel.uiState.isFetchingTimeline)
            viewModel.fetchTimeline()
        }
    }


    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (viewModel.uiState.skeets.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(64.dp),
                    )
                }
            }
        } else {
            viewModel.uiState.skeets.forEach { skeet ->
                item(key = skeet.post.uri.toString()) { // Added a key for better performance
                    SkeetRowView(skeet)
                }
            }

            // Optional: Show a loading indicator at the bottom while fetching more items
            if (viewModel.uiState.isFetchingMoreTimeline) { // Add isFetchingMore to your UiState
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(64.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Effect to detect scrolling to the bottom
    val endOfListReached by remember {
        derivedStateOf {
            // Check if the last visible item is the last item in the list
            // And if there are items to display
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

    // Trigger fetchTimeline when the end of the list is reached
    // and not currently fetching more.
    LaunchedEffect(endOfListReached) {
        if (endOfListReached && viewModel.uiState.skeets.isNotEmpty()) {
            // You might want to pass a cursor or page number to fetchTimeline
            // if your API supports pagination.
            // For example: viewModel.fetchTimeline(cursor = viewModel.uiState.lastCursor)
            viewModel.fetchTimeline() // Or a specific function like viewModel.fetchMoreSkeets()
        }
    }
}

