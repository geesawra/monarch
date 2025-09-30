package industries.geesawra.jerryno

// import androidx.compose.foundation.layout.height // Will be removed for the sheet content Box
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int
) {
    HOME(R.string.timeline, Icons.Filled.Home, R.string.timeline),
    NOTIFICATIONS(R.string.notifications, Icons.Filled.Notifications, R.string.notifications)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineView(
    timelineViewModel: TimelineViewModel,
    coroutineScope: CoroutineScope,
    onLoginError: () -> Unit,
) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Keep true if you only want fully expanded or hidden
        ),
    )
    var postText by remember { mutableStateOf("") }
    val maxChars = 300

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val mediaSelected = remember { mutableStateOf(mapOf<Uri, String?>()) }

    LaunchedEffect(scaffoldState.bottomSheetState.isVisible) {
        if (scaffoldState.bottomSheetState.isVisible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            mediaSelected.value = mapOf()
        }
    }


    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)) { uris ->
            if (uris.isEmpty()) {
                return@rememberLauncherForActivityResult
            }

            val urisMap = uris.associateWith {
                val mimeType: String? = context.contentResolver.getType(it)
                mimeType?.let {
                    if (mimeType.startsWith("image/")) {
                        return@associateWith "image"
                    } else if (mimeType.startsWith("video/")) {
                        return@associateWith "video"
                    }
                }

                return@associateWith null
            }

            if (urisMap.size > 1 && urisMap.values.find {
                    it?.let {
                        return@find (it == "video")
                    }

                    return@find false
                } != null) {
                Toast.makeText(
                    context,
                    "Can only post up to 1 video or 4 pictures",
                    Toast.LENGTH_SHORT
                ).show()

                return@rememberLauncherForActivityResult
            }

            mediaSelected.value = urisMap
        }


    BottomSheetScaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            val uploadingPost = remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.ime
                    )
                    .padding(16.dp) // General content padding
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(), // Removed .fillMaxHeight()
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row {
                        Text(
                            "New Post",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    val charCount = remember { mutableIntStateOf(0) }
                    val wasEdited = remember { mutableStateOf(false) }

                    OutlinedTextField(
                        keyboardActions = KeyboardActions(
                            onDone = {
                                this.defaultKeyboardAction(ImeAction.Done)
                                keyboardController?.hide()
                            }
                        ),
                        value = postText,
                        onValueChange = {
                            wasEdited.value = true
                            postText = it
                            charCount.intValue = it.length
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRequester(focusRequester),
                        label = {
                            if (wasEdited.value) {
                                Text(
                                    text = "${maxChars - charCount.intValue}",
                                    color = if (postText.length > maxChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = "Less cringe this time, okay?",
                                )
                            }
                        },
                        isError = postText.length > maxChars
                    )

                    Spacer(modifier = Modifier.padding(4.dp))

                    if (mediaSelected.value.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .fillMaxWidth()
                                .padding(8.dp) // This padding is for the Card itself, not the Box's content
                        ) {
                            PostImageGallery(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                images = mediaSelected.value.keys.map {
                                    Image(
                                        url = it.toString(),
                                        alt = ""
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.padding(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                        ) {
                            Icon(Icons.Default.CameraRoll, contentDescription = "Attach media")
                        }

                        if (uploadingPost.value) {
                            CircularProgressIndicator()
                        }

                        val postButtonEnabled = remember { mutableStateOf(true) }
                        Button(
                            onClick = {
                                if (postText.isNotBlank() && postText.length <= maxChars) {
                                    coroutineScope.launch {
                                        postButtonEnabled.value = false
                                        uploadingPost.value = true
                                        timelineViewModel.post(
                                            postText,
                                            mediaSelected.value.keys.toList().ifEmpty { null }
                                        ).onSuccess {
                                            scaffoldState.bottomSheetState.hide()
                                            postText = ""
                                            wasEdited.value = false
                                            postButtonEnabled.value = true
                                            uploadingPost.value = false
                                        }.onFailure {
                                            postButtonEnabled.value = true
                                            Toast.makeText(
                                                context,
                                                "Could not post: ${it.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            uploadingPost.value = false
                                            postButtonEnabled.value = true
                                        }
                                    }
                                }
                            },
                            enabled = (postText.isNotBlank() || mediaSelected.value.isNotEmpty()) && postText.length <= maxChars && postButtonEnabled.value
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Skeet")
                        }
                    }
                }
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
    fobOnClick: () -> Unit,
    loginError: () -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(TabBarDestinations.HOME) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        rememberTopAppBarState()
    )
    val listState = rememberLazyListState()
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
            timelineViewModel.reset()
            timelineViewModel.fetchTimeline {
                isRefreshing.value = false
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
                        timelineViewModel.selectFeed(uri, displayName, avatar) {
                            isRefreshing.value = false
                        }
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
                            Row(
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
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Tag, "Feeds")
                            }
                        },
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = fobOnClick
                    ) {
                        Icon(Icons.Filled.Create, "Post")
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
                ShowSkeets(
                    viewModel = timelineViewModel,
                    state = listState,
                    modifier = Modifier.padding(values)
                ) {
                    isRefreshing.value = false
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

        timelineViewModel.uiState.feeds.forEach {
            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (it.avatar != null) {
                            AsyncImage(
                                model = it.avatar?.uri,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentDescription = "Feed avatar",
                            )
                        } else {
                            Spacer(modifier = Modifier.size(20.dp))
                        }

                        Text(text = it.displayName)
                    }
                },
                selected = timelineViewModel.uiState.selectedFeed == it.uri.atUri,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                onClick = {
                    selectFeed(it.uri.atUri, it.displayName, it.avatar?.uri)
                }
            )
        }

    }
}
