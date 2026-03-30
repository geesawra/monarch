@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.bsky.actor.VerifiedStatus
import app.bsky.feed.GetAuthorFeedFilter
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
    onSettingsTap: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val profile = timelineViewModel.uiState.profileUser
    val isLoading = timelineViewModel.uiState.isFetchingProfile && profile == null
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
            )
        },
    ) { scaffoldPadding ->
    PullToRefreshBox(
        modifier = Modifier.padding(scaffoldPadding),
        isRefreshing = isLoading,
        onRefresh = {
            profile?.did?.let { timelineViewModel.openProfile(it) }
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
                                            .clip(if (settingsState.avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(6.dp) else CircleShape)
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
                        if (profile != null && timelineViewModel.isOwnProfile()) {
                            IconButton(onClick = onSettingsTap) {
                                Icon(Icons.Default.Settings, "Settings")
                            }
                        }
                        if (profile != null && !timelineViewModel.isOwnProfile()) {
                            ProfileOverflowMenu(timelineViewModel, profile)
                        }
                    }
                )
            },
        ) { padding ->
            if (profile == null) {
                if (timelineViewModel.uiState.profileNotFound) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Profile not found")
                    }
                }
                return@Scaffold
            }

            ProfileContent(
                modifier = Modifier.padding(padding),
                profile = profile,
                timelineViewModel = timelineViewModel,
                settingsState = settingsState,
                listState = listState,
                onThreadTap = onThreadTap,
                onProfileTap = onProfileTap,
                onReplyTap = { skeetData, quotePost ->
                    inReplyTo.value = skeetData
                    isQuotePost.value = quotePost
                    coroutineScope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                },
            )
        }
    }
    }
}

@Composable
private fun ProfileOverflowMenu(
    timelineViewModel: TimelineViewModel,
    profile: ProfileViewDetailed,
) {
    var expanded by remember { mutableStateOf(false) }
    val isMuted = profile.viewer?.muted == true

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More options")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(if (isMuted) "Unmute" else "Mute") },
            onClick = {
                expanded = false
                if (isMuted) timelineViewModel.unmuteProfile()
                else timelineViewModel.muteProfile()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState = SettingsState(),
    listState: LazyListState,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
) {
    val posts = timelineViewModel.uiState.profilePosts
    val avatarClipShape = if (settingsState.avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(8.dp) else CircleShape

    LazyColumn(
        state = listState,
        modifier = modifier
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
            )
        }

        // Feed tabs
        item(key = "feed_tabs") {
            ProfileFeedTabs(timelineViewModel)
        }

        // Posts
        itemsIndexed(
            items = posts,
            key = { _, skeet -> "post_${skeet.key()}" }
        ) { _, skeet ->
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
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
                    onAvatarTap = onProfileTap,
                    onShowThread = { s ->
                        timelineViewModel.setThread(s)
                        onThreadTap(s)
                    }
                )
            }
        }

        if (timelineViewModel.uiState.isFetchingProfileFeed) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }
            }
        }
    }

    // Pagination
    val endOfListReached by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
            }
        }
    }

    LaunchedEffect(endOfListReached) {
        if (endOfListReached && posts.isNotEmpty()) {
            timelineViewModel.fetchProfileFeed()
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileViewDetailed,
    timelineViewModel: TimelineViewModel,
    avatarShape: Shape = CircleShape,
) {
    var showImageViewer by remember { mutableStateOf<String?>(null) }

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
                    .then(
                        if (profile.avatar != null)
                            Modifier.clickable { showImageViewer = profile.avatar?.uri }
                        else Modifier
                    )
            )

            if (timelineViewModel.isOwnProfile()) {
                EditProfileButton(profile, timelineViewModel)
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

                val isVerified = when (profile.verification?.verifiedStatus) {
                    is VerifiedStatus.Valid -> true
                    else -> false
                }
                if (isVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                val isBot = profile.labels.any { it.`val` == "bot" }
                if (isBot) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "Bot account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text = "@${profile.handle.handle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                Text(
                    text = profile.description!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Stats row
            Spacer(modifier = Modifier.height(12.dp))
            ProfileStats(profile)
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
private fun ProfileStats(profile: ProfileViewDetailed) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatItem(count = profile.followersCount ?: 0, label = "followers")
        StatItem(count = profile.followsCount ?: 0, label = "following")
        StatItem(count = profile.postsCount ?: 0, label = "posts")
    }
}

@Composable
private fun StatItem(count: Long, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

private val profileTabs = listOf(
    "Posts" to null,
    "Replies" to GetAuthorFeedFilter.PostsWithReplies,
    "Media" to GetAuthorFeedFilter.PostsWithMedia,
    "Video" to GetAuthorFeedFilter.PostsWithVideo,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFeedTabs(timelineViewModel: TimelineViewModel) {
    val currentFilter = timelineViewModel.uiState.profileFeedFilter
    val selectedIndex = profileTabs.indexOfFirst { it.second == currentFilter }.coerceAtLeast(0)

    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        profileTabs.forEachIndexed { index, (label, filter) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { timelineViewModel.setProfileFeedFilter(filter) },
                text = { Text(label) },
            )
        }
    }
}
