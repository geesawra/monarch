@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileViewDetailed
import app.bsky.actor.VerificationStateVerifiedStatus
import app.bsky.feed.GetAuthorFeedFilter
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import sh.christian.ozone.api.Did
import kotlin.time.ExperimentalTime
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalUriHandler


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileView(
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState = SettingsState(),
    coroutineScope: CoroutineScope,
    backButton: () -> Unit,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
    onHandleTap: (String) -> Unit = {},
    onFollowersTap: (showFollowers: Boolean, name: String) -> Unit = { _, _ -> },
    onPublicationTap: () -> Unit = {},
    onDocumentTap: () -> Unit = {},
    profileKey: String,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listStates = profileNavTabs.associate { it.filter to rememberLazyListState() }

    val vmKey = timelineViewModel.currentProfileKey
    val isOurData = vmKey == profileKey

    var localProfile by remember(profileKey) { mutableStateOf<ProfileViewDetailed?>(null) }
    var localIsFetchingProfile by remember(profileKey) { mutableStateOf(false) }
    var localProfileNotFound by remember(profileKey) { mutableStateOf(false) }
    var localFeedFilter by remember(profileKey) { mutableStateOf<GetAuthorFeedFilter?>(null) }
    var localIsPublicationsTab by remember(profileKey) { mutableStateOf(false) }
    var localPosts by remember(profileKey) { mutableStateOf<ImmutableList<SkeetData>>(persistentListOf()) }
    var localIsFetchingProfileFeed by remember(profileKey) { mutableStateOf(false) }

    if (isOurData) {
        localProfile = timelineViewModel.profileUser
        localIsFetchingProfile = timelineViewModel.isFetchingProfile
        localProfileNotFound = timelineViewModel.profileNotFound
        localFeedFilter = timelineViewModel.profileFeedFilter
        localIsPublicationsTab = timelineViewModel.publicationsState.isTabActive
        localPosts = timelineViewModel.profilePosts
        localIsFetchingProfileFeed = timelineViewModel.isFetchingProfileFeed
    }

    val currentFilter = localFeedFilter
    val listState = listStates[currentFilter] ?: rememberLazyListState()
    val profile = localProfile
    val isLoading = localIsFetchingProfile && profile == null
    val wasEdited = remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDraftsSheet by remember { mutableStateOf(false) }
    var localDeviceId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        localDeviceId = timelineViewModel.deviceId()
    }
    var isMediaFeedMode by remember { mutableStateOf(false) }
    var mediaFeedSnapshot by remember { mutableStateOf<ImmutableList<SkeetData>?>(null) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var blockNoteText by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var editNoteText by remember { mutableStateOf("") }
    val isPublicationsTab = localIsPublicationsTab
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden) {
                    if (timelineViewModel.uploadingPost) {
                        timelineViewModel.cancelPost()
                        wasEdited.value = false
                        true
                    } else if (wasEdited.value) {
                        showDiscardDialog = true
                        false
                    } else {
                        true
                    }
                } else {
                    true
                }
            }
        )
    )
    val inReplyTo = remember { mutableStateOf<SkeetData?>(null) }
    val isQuotePost = remember { mutableStateOf(false) }
    val profileTextFieldState = rememberTextFieldState()
    val profileMediaSelected = remember { mutableStateOf(listOf<android.net.Uri>()) }
    val profileMediaAltTexts = remember { mutableStateOf(mapOf<android.net.Uri, String>()) }
    val profileMediaSelectedIsVideo = remember { mutableStateOf(false) }
    val profileThreadgateRules = remember { mutableStateOf<List<app.bsky.feed.ThreadgateAllowUnion>?>(null) }
    val profileLinkPreview = remember { mutableStateOf<industries.geesawra.monarch.datalayer.LinkPreviewData?>(null) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = scaffoldState.bottomSheetState.isVisible) {
        if (timelineViewModel.uploadingPost) {
            timelineViewModel.cancelPost()
            focusManager.clearFocus()
            coroutineScope.launch {
                scaffoldState.bottomSheetState.hide()
            }
        } else if (wasEdited.value) {
            showDiscardDialog = true
        } else {
            focusManager.clearFocus()
            coroutineScope.launch {
                scaffoldState.bottomSheetState.hide()
            }
        }
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscardDialog = false
                timelineViewModel.cancelPost()
                wasEdited.value = false
                focusManager.clearFocus()
                coroutineScope.launch {
                    scaffoldState.bottomSheetState.hide()
                }
            },
            onKeepEditing = { showDiscardDialog = false },
        )
    }

    if (showBlockDialog && profile != null) {
        AlertDialog(
            onDismissRequest = {
                showBlockDialog = false
                blockNoteText = ""
            },
            title = { Text("Block ${profile.displayName ?: profile.handle.handle}?") },
            text = {
                Column {
                    Text("Optionally add a note about why you're blocking this account.")
                    OutlinedTextField(
                        value = blockNoteText,
                        onValueChange = { blockNoteText = it },
                        label = { Text("Block note (optional)") },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        timelineViewModel.blockProfile(blockNoteText)
                        blockNoteText = ""
                    }
                ) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        blockNoteText = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showNoteDialog && profile != null) {
        AlertDialog(
            onDismissRequest = {
                showNoteDialog = false
                editNoteText = ""
            },
            title = { Text("Note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNoteText,
                        onValueChange = { editNoteText = it },
                        label = { Text("Note") },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoteDialog = false
                        if (editNoteText.isBlank()) {
                            timelineViewModel.deleteAccountNote(profile.did)
                        } else {
                            timelineViewModel.saveAccountNote(profile.did, editNoteText)
                        }
                        editNoteText = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNoteDialog = false
                        editNoteText = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showDraftsSheet) {
        DraftsListSheet(
            timelineViewModel = timelineViewModel,
            localDeviceId = localDeviceId,
            onDismiss = { showDraftsSheet = false },
            onLoadDraft = { draftView ->
                showDraftsSheet = false
                val post = draftView.draft.posts.firstOrNull() ?: return@DraftsListSheet
                profileTextFieldState.edit {
                    replace(0, length, post.text)
                    selection = androidx.compose.ui.text.TextRange(post.text.length)
                }
                val isFromThisDevice = draftView.draft.deviceId == null || draftView.draft.deviceId == localDeviceId
                if (isFromThisDevice) {
                    val imageUris = post.embedImages?.map { it.localRef.path.toUri() } ?: emptyList()
                    val videoUris = post.embedVideos?.map { it.localRef.path.toUri() } ?: emptyList()
                    if (videoUris.isNotEmpty()) {
                        profileMediaSelected.value = videoUris
                        profileMediaSelectedIsVideo.value = true
                    } else {
                        profileMediaSelected.value = imageUris
                        profileMediaSelectedIsVideo.value = false
                    }
                } else {
                    profileMediaSelected.value = emptyList()
                    profileMediaSelectedIsVideo.value = false
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

    // Show avatar in top bar once the header item is scrolled past
    val showAvatarInBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    BottomSheetScaffold(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
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
                scrollState = rememberScrollState(),
                inReplyTo = inReplyTo,
                isQuotePost = isQuotePost,
                wasEdited = wasEdited,
                initialText = if (profile != null) "@${profile.handle.handle} " else "",
                initialMentions = if (profile != null) mapOf(profile.handle.handle to profile.did) else emptyMap(),
                textfieldState = profileTextFieldState,
                mediaSelected = profileMediaSelected,
                mediaSelectedIsVideo = profileMediaSelectedIsVideo,
                mediaAltTexts = profileMediaAltTexts,
                threadgateRules = profileThreadgateRules,
                linkPreview = profileLinkPreview,
                onDraftsClick = { showDraftsSheet = true },
            )
        },
    ) { scaffoldPadding ->
    PullToRefreshBox(
        modifier = Modifier.padding(scaffoldPadding),
        isRefreshing = isLoading,
        onRefresh = {
            profile?.did?.let { timelineViewModel.openProfile(it, profileKey) }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
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
                        AnimatedVisibility(
                            visible = showAvatarInBar,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (profile?.avatar != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(profile.avatar?.uri)
                                            .crossfade(true)
                                            .build(),
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(settingsState.avatarClipShape)
                                    )
                                }
                                Text(
                                    text = profile?.displayName ?: profile?.handle?.handle ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (profile != null && !timelineViewModel.isOwnProfile()) {
                            ProfileOverflowMenu(
                                timelineViewModel = timelineViewModel,
                                profile = profile,
                                onBlockRequest = { showBlockDialog = true },
                                onEditNoteRequest = {
                                    editNoteText = timelineViewModel.getAccountNote(profile.did)?.note ?: ""
                                    showNoteDialog = true
                                },
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!LocalBaselineProfileMode.current && profile != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                mediaFeedSnapshot = timelineViewModel.profilePosts
                                isMediaFeedMode = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(Icons.Default.ViewStream, "Media scroll")
                        }

                        if (!timelineViewModel.isOwnProfile()) {
                            FloatingActionButton(
                                onClick = {
                                    inReplyTo.value = null
                                    isQuotePost.value = false
                                    coroutineScope.launch {
                                        scaffoldState.bottomSheetState.expand()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Create, "Post")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    val currentFilter = localFeedFilter
                    profileNavTabs.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = if (tab.isPublications) isPublicationsTab else (!isPublicationsTab && tab.filter == currentFilter),
                            onClick = {
                                if (tab.isPublications) {
                                    if (!isPublicationsTab) {
                                        profile?.did?.let { timelineViewModel.fetchPublications(it) }
                                    }
                                } else {
                                    timelineViewModel.setPublicationsTabActive(false)
                                    timelineViewModel.clearSelectedPublication()
                                    if (tab.filter == currentFilter) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    } else {
                                        timelineViewModel.changeProfileFeedFilter(tab.filter)
                                    }
                                }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            if (profile == null) {
                if (localProfileNotFound) {
                    ProfileNotFound(
                        modifier = Modifier.padding(padding),
                    )
                }
                return@Scaffold
            }

            val contentModifier = if (!settingsState.forceCompactLayout) {
                Modifier.widthIn(max = 600.dp)
            } else {
                Modifier
            }

            if (isMediaFeedMode) {
                val posts = (mediaFeedSnapshot!! + localPosts.filter { post ->
                    mediaFeedSnapshot!!.none { it.cid == post.cid }
                }).toPersistentList()
                Dialog(
                    onDismissRequest = { isMediaFeedMode = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    MediaFeedView(
                        posts = posts,
                        isLoading = localIsFetchingProfileFeed,
                        onLoadMore = { timelineViewModel.fetchProfileFeed(profile.did) },
                        onProfileTap = { did ->
                            isMediaFeedMode = false
                            onProfileTap(did)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            run {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = contentModifier) {
                        ProfileContent(
                            profile = profile,
                            profileKey = profileKey,
                            timelineViewModel = timelineViewModel,
                            settingsState = settingsState,
                            listState = listState,
                            onThreadTap = onThreadTap,
                            onProfileTap = onProfileTap,
                            onHandleTap = onHandleTap,
                            onReplyTap = { skeetData, quotePost ->
                                inReplyTo.value = skeetData
                                isQuotePost.value = quotePost
                                coroutineScope.launch {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            },
                            onFollowersTap = onFollowersTap,
                            isPublicationsTab = isPublicationsTab,
                            onPublicationTap = onPublicationTap,
                            onDocumentTap = onDocumentTap,
                            onLoadMore = { timelineViewModel.fetchProfileFeed(profile.did) },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ProfileOverflowMenu(
    timelineViewModel: TimelineViewModel,
    profile: ProfileViewDetailed,
    onBlockRequest: () -> Unit,
    onEditNoteRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isMuted = profile.viewer?.muted == true
    val isBlocked = profile.viewer?.blocking != null
    val hasNote = timelineViewModel.getAccountNote(profile.did) != null

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More options")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(if (hasNote) "Edit note" else "Add note") },
            onClick = {
                expanded = false
                onEditNoteRequest()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isMuted) "Unmute" else "Mute") },
            onClick = {
                expanded = false
                if (isMuted) timelineViewModel.unmuteProfile()
                else timelineViewModel.muteProfile()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isBlocked) "Unblock" else "Block") },
            onClick = {
                expanded = false
                if (isBlocked) {
                    timelineViewModel.unblockProfile()
                } else {
                    onBlockRequest()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileContent(
    modifier: Modifier = Modifier,
    profile: ProfileViewDetailed,
    profileKey: String,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState = SettingsState(),
    listState: LazyListState,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
    onHandleTap: (String) -> Unit = {},
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onFollowersTap: (showFollowers: Boolean, name: String) -> Unit = { _, _ -> },
    isPublicationsTab: Boolean = false,
    onPublicationTap: () -> Unit = {},
    onDocumentTap: () -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    val vmKey = timelineViewModel.currentProfileKey
    val isOurData = vmKey == profileKey

    var localPosts by remember(profileKey) { mutableStateOf<ImmutableList<SkeetData>>(persistentListOf()) }
    var localIsFetchingProfileFeed by remember(profileKey) { mutableStateOf(false) }
    var localPublicationsState by remember(profileKey) { mutableStateOf(timelineViewModel.publicationsState) }

    if (isOurData) {
        localPosts = timelineViewModel.profilePosts
        localIsFetchingProfileFeed = timelineViewModel.isFetchingProfileFeed
        localPublicationsState = timelineViewModel.publicationsState
    }

    val posts = localPosts
    val avatarClipShape = settingsState.avatarClipShape

    LazyColumn(
        state = listState,
        modifier = modifier
            .testTag("profile_list")
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Profile header as first item
        item(key = "profile_header") {
            ProfileHeader(
                profile = profile,
                timelineViewModel = timelineViewModel,
                avatarShape = avatarClipShape,
                onProfileTap = onProfileTap,
                onHandleTap = onHandleTap,
                onFollowersTap = {
                    onFollowersTap(true, profile.displayName ?: profile.handle.handle)
                },
                onFollowingTap = {
                    onFollowersTap(false, profile.displayName ?: profile.handle.handle)
                },
            )
        }

        if (isPublicationsTab) {
            val pubState = localPublicationsState

            if (pubState.isFetchingPublications) {
                item(key = "pub_loading") { LoadingBox() }
            } else {
                if (pubState.publications.isEmpty()) {
                    item(key = "no_pubs") {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No blogs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(pubState.publications.size, key = { "pub_$it" }) { idx ->
                    val pub = pubState.publications[idx]
                    OutlinedCard(
                        onClick = {
                            timelineViewModel.fetchDocuments(pub)
                            onPublicationTap()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(pub.publication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            pub.publication.description?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            pub.publication.url?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            if (profile.viewer?.blocking != null) {
                val blockNote = timelineViewModel.getBlockNote(profile.did)
                item(key = "block_reason") {
                    if (blockNote != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Block reason",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = blockNote.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                    }
                }
            }

            itemsIndexed(
                items = posts,
                key = { _, skeet -> "post_${skeet.key()}" }
            ) { _, skeet ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    SkeetView(
                        viewModel = timelineViewModel,
                        skeet = skeet,
                        onReplyTap = onReplyTap,
                        postTextSize = settingsState.postTextSize,
                        avatarShape = avatarClipShape,
                        showLabels = settingsState.showLabels,
                        showPronouns = settingsState.showPronounsInPosts,
                        onAvatarTap = onProfileTap,
                        onShowThread = { s ->
                            timelineViewModel.startThread(s)
                            onThreadTap(s)
                        },
                        translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                        targetTranslationLanguage = settingsState.targetTranslationLanguage,
                        carouselImageGallery = settingsState.carouselImageGallery,
                    )
                }
            }

            if (localIsFetchingProfileFeed && profile.viewer?.blocking == null) {
                item(key = "loading") {
                    LoadingBox()
                }
            }
        }
    }

    if (!isPublicationsTab) {
        OnEndOfListReached(
            listState = listState,
            items = posts,
            onEndReached = onLoadMore,
        )
    }
}

@Composable
internal fun ProfileHeader(
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
    avatarShape: Shape = CircleShape,
    onProfileTap: (Did) -> Unit = {},
    onHandleTap: (String) -> Unit = {},
    onFollowersTap: () -> Unit = {},
    onFollowingTap: () -> Unit = {},
) {
    var showImageViewer by remember { mutableStateOf<String?>(null) }
    val isBlocked = profile.viewer?.blocking != null
    val blockNote = timelineViewModel.getBlockNote(profile.did)
    val accountNote = timelineViewModel.getAccountNote(profile.did)

    if (showImageViewer != null) {
        GalleryViewer(
            imageUrls = listOf(Image(url = showImageViewer!!, fullSize = showImageViewer!!, alt = "")),
            onDismiss = { showImageViewer = null },
        )
    }

    Column {
        // Banner image
        if (profile.banner != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profile.banner?.uri)
                    .crossfade(true)
                    .build(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                contentDescription = "Profile banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (isBlocked) Modifier.blur(16.dp) else Modifier)
                    .clickable { showImageViewer = profile.banner?.uri }
            )
        } else {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }

        // Avatar + follow button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-32).dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profile.avatar?.uri)
                    .crossfade(true)
                    .build(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                contentDescription = "${profile.displayName ?: profile.handle.handle}'s avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(avatarShape)
                    .then(if (isBlocked) Modifier.blur(16.dp) else Modifier)
                    .then(
                        if (profile.avatar != null && !isBlocked)
                            Modifier.clickable { showImageViewer = profile.avatar?.uri }
                        else Modifier
                    )
            )

            if (timelineViewModel.isOwnProfile()) {
                EditProfileButton(profile, timelineViewModel)
            } else if (isBlocked) {
                SuggestionChip(
                    onClick = { },
                    label = { Text("Blocked") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                )
                TextButton(
                    onClick = { timelineViewModel.unblockProfile() }
                ) {
                    Text("Unblock")
                }
            } else {
                FollowButton(profile, timelineViewModel)
            }
        }

        // Name + handle + verification
        Column(
            modifier = Modifier
                .offset(y = (-20).dp)
                .padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = profile.displayName ?: profile.handle.handle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                val isVerified = isVerificationStateVerifiedStatus(profile.verification?.verifiedStatus)
                if (isVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                val isBot = profile.labels.orEmpty().any { it.`val` == "bot" }
                if (isBot) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "Bot account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val handle = profile.handle.handle
                val isCustomDomain = !handle.endsWith(".bsky.social")
                if (isCustomDomain) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val linkStyles = TextLinkStyles(style = SpanStyle(color = primaryColor))
                    val uriHandler = LocalUriHandler.current
                    Text(
                        text = buildAnnotatedString {
                            withLink(LinkAnnotation.Clickable(
                                tag = "handle",
                                styles = linkStyles,
                                linkInteractionListener = { uriHandler.openUri("https://$handle") },
                            )) {
                                append("@$handle")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "@$handle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!profile.pronouns.isNullOrBlank()) {
                    Text(
                        text = profile.pronouns!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // "Follows you" badge
            if (profile.viewer?.followedBy != null) {
                Text(
                    text = "Follows you",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Bio
            if (!profile.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val primaryColor = MaterialTheme.colorScheme.primary
                val bioAnnotated = remember(profile.description, primaryColor) {
                    buildBioAnnotatedString(
                        profile.description.orEmpty(),
                        primaryColor,
                        onMentionTap = { handle -> onHandleTap(handle) }
                    )
                }
                SelectionContainer {
                    Text(
                        text = bioAnnotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Account note
            if (accountNote != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = accountNote.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Stats row
            Spacer(modifier = Modifier.height(12.dp))
            ProfileStats(profile, onFollowersTap = onFollowersTap, onFollowingTap = onFollowingTap)
        }
    }
}

@Composable
private fun FollowButton(
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
) {
    val isFollowing = profile.viewer?.following != null
    val haptic = LocalHapticFeedback.current

    if (isFollowing) {
        FilledTonalButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                timelineViewModel.unfollowProfile()
            },
        ) {
            Icon(
                Icons.Default.PersonRemove,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Following")
        }
    } else {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                timelineViewModel.followProfile()
            },
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Follow")
        }
    }
}

@Composable
private fun ProfileStats(
    profile: ProfileViewDetailed,
    onFollowersTap: () -> Unit = {},
    onFollowingTap: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatItem(count = profile.followersCount ?: 0, label = "followers", onClick = onFollowersTap)
        StatItem(count = profile.followsCount ?: 0, label = "following", onClick = onFollowingTap)
        StatItem(count = profile.postsCount ?: 0, label = "posts")
    }
}

@Composable
private fun StatItem(count: Long, label: String, onClick: (() -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    ) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format("%.1fK", count / 1_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileButton(
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
) {
    var showEditor by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showEditor = true }) {
        Text("Edit Profile")
    }

    if (showEditor) {
        EditProfileSheet(
            profile = profile,
            timelineViewModel = timelineViewModel,
            onDismiss = { showEditor = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditProfileSheet(
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(profile.displayName ?: "") }
    var description by remember { mutableStateOf(profile.description ?: "") }
    var pronouns by remember { mutableStateOf(profile.pronouns ?: "") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var bannerUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { avatarUri = it } }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { bannerUri = it } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            // Banner + avatar overlapping
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                // Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { bannerPicker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = bannerUri ?: profile.banner?.uri,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                    )
                    FilledTonalIconButton(onClick = { bannerPicker.launch("image/*") }) {
                        Icon(Icons.Default.CameraAlt, "Change banner")
                    }
                }

                // Avatar overlapping the banner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp)
                        .offset(y = 32.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AsyncImage(
                        model = avatarUri ?: profile.avatar?.uri,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable { avatarPicker.launch("image/*") },
                    )
                    FilledTonalIconButton(
                        onClick = { avatarPicker.launch("image/*") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            "Change avatar",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Display name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
            )

            // Pronouns
            OutlinedTextField(
                value = pronouns,
                onValueChange = { pronouns = it },
                label = { Text("Pronouns") },
                placeholder = { Text("she/her, they/them, …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Description / Bio
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Bio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )

            // Save / Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    enabled = !isSaving,
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        isSaving = true
                        timelineViewModel.updateProfile(
                            displayName = displayName,
                            description = description,
                            pronouns = pronouns,
                            avatarUri = avatarUri,
                            bannerUri = bannerUri,
                        ) { success ->
                            isSaving = false
                            if (success) {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                        }
                    },
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class ProfileTab(
    val label: String,
    val icon: ImageVector,
    val filter: GetAuthorFeedFilter?,
    val isMediaFeed: Boolean = false,
    val isPublications: Boolean = false,
)

private val profileNavTabs = listOf(
    ProfileTab("Posts", Icons.AutoMirrored.Filled.Article, null),
    ProfileTab("Replies", Icons.AutoMirrored.Filled.Reply, GetAuthorFeedFilter.PostsWithReplies),
    ProfileTab("Media", Icons.Default.Image, GetAuthorFeedFilter.PostsWithMedia),
    ProfileTab("Video", Icons.Default.Videocam, GetAuthorFeedFilter.PostsWithVideo),
    ProfileTab("Blogs", Icons.AutoMirrored.Filled.MenuBook, null, isPublications = true),
)

private val bioTokenPattern = Regex(
    """(https?://[^\s\p{Cntrl})\]}>""]+)""" +
    """|([@#][\w][\w.-]*[\w])""",
)

private fun buildBioAnnotatedString(
    text: String,
    primary: Color,
    onMentionTap: (String) -> Unit,
): AnnotatedString {
    val linkStyles = TextLinkStyles(style = SpanStyle(color = primary))
    val accentStyle = SpanStyle(color = primary)

    return buildAnnotatedString {
        var cursor = 0
        for (match in bioTokenPattern.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("http") -> withLink(
                    LinkAnnotation.Url(token, linkStyles)
                ) { append(token) }

                token.startsWith("@") -> {
                    val handle = token.removePrefix("@")
                    withLink(LinkAnnotation.Clickable(
                        tag = "mention:$handle",
                        styles = linkStyles,
                        linkInteractionListener = { onMentionTap(handle) },
                    )) { append(token) }
                }

                token.startsWith("#") -> withStyle(accentStyle) { append(token) }

                else -> append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
