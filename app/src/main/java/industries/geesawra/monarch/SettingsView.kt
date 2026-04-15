package industries.geesawra.monarch

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.window.core.layout.WindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import industries.geesawra.monarch.BuildConfig
import industries.geesawra.monarch.datalayer.AltTextAvailability
import industries.geesawra.monarch.datalayer.AltTextGenerator
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.PushNotificationManager
import industries.geesawra.monarch.datalayer.ReplyFilterMode
import industries.geesawra.monarch.datalayer.SettingsViewModel
import industries.geesawra.monarch.datalayer.AppTheme
import industries.geesawra.monarch.datalayer.ThemeMode
import industries.geesawra.monarch.datalayer.TRANSLATION_LANGUAGE_OPTIONS
import industries.geesawra.monarch.datalayer.TimelineViewModel
import industries.geesawra.monarch.datalayer.CachedFeedInfo
import industries.geesawra.monarch.datalayer.StoredAccount
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    timelineViewModel: TimelineViewModel? = null,
    pushNotificationManager: PushNotificationManager? = null,
    altTextGenerator: AltTextGenerator? = null,
    backButton: () -> Unit,
    onLogout: () -> Unit = {},
    onMutedWordsTap: () -> Unit = {},
) {
    val settings = settingsViewModel.settingsState

    var altTextAvailable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(altTextGenerator) {
        altTextAvailable = altTextGenerator?.let {
            it.availability() != AltTextAvailability.Unavailable
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isLargeScreen = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    val context = LocalContext.current
    val activity = context as? Activity
    val windowLayoutInfo = activity?.let {
        WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it)
    }?.collectAsState(initial = null)?.value
    val isFoldable = windowLayoutInfo?.displayFeatures?.any { it is FoldingFeature } == true
    val showCompactLayoutOption = isLargeScreen || isFoldable

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = backButton) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { settingsViewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                            ) {
                                Text(mode.name)
                            }
                        }
                    }
                },
            )

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

            ListItem(
                headlineContent = { Text("Color scheme") },
                supportingContent = {
                    Text(
                        text = "Themes by witchsky.app",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://tangled.org/jollywhoppers.com/witchsky.app")
                        },
                    )
                },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(settings.appTheme.name)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AppTheme.entries.forEach { theme ->
                                DropdownMenuItem(
                                    text = { Text(theme.name) },
                                    onClick = {
                                        settingsViewModel.setAppTheme(theme)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = { Text("Use colors from your wallpaper") },
                    trailingContent = {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = { settingsViewModel.setDynamicColor(it) }
                        )
                    },
                )
            }

            ListItem(
                headlineContent = { Text("Post text size") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        PostTextSize.entries.forEachIndexed { idx, size ->
                            SegmentedButton(
                                selected = settings.postTextSize == size,
                                onClick = { settingsViewModel.setPostTextSize(size) },
                                shape = SegmentedButtonDefaults.itemShape(idx, PostTextSize.entries.size),
                            ) {
                                Text(
                                    when (size) {
                                        PostTextSize.Small -> "S"
                                        PostTextSize.Medium -> "M"
                                        PostTextSize.Large -> "L"
                                    }
                                )
                            }
                        }
                    }
                },
            )

            ListItem(
                headlineContent = { Text("Avatar shape") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        AvatarShape.entries.forEachIndexed { idx, shape ->
                            SegmentedButton(
                                selected = settings.avatarShape == shape,
                                onClick = { settingsViewModel.setAvatarShape(shape) },
                                shape = SegmentedButtonDefaults.itemShape(idx, AvatarShape.entries.size),
                            ) {
                                Text(
                                    when (shape) {
                                        AvatarShape.Circle -> "Circle"
                                        AvatarShape.RoundedSquare -> "Rounded"
                                    }
                                )
                            }
                        }
                    }
                },
            )

            ListItem(
                headlineContent = { Text("Open links in browser") },
                supportingContent = { Text("Use default browser instead of in-app tabs") },
                trailingContent = {
                    Switch(
                        checked = settings.openLinksInBrowser,
                        onCheckedChange = { settingsViewModel.setOpenLinksInBrowser(it) }
                    )
                },
            )

            if (showCompactLayoutOption) {
                ListItem(
                    headlineContent = { Text("Force compact layout") },
                    supportingContent = { Text("Use phone layout on large screens") },
                    trailingContent = {
                        Switch(
                            checked = settings.forceCompactLayout,
                            onCheckedChange = { settingsViewModel.setForceCompactLayout(it) }
                        )
                    },
                )
            }

            ListItem(
                headlineContent = { Text("Auto-like on reply") },
                supportingContent = { Text("Automatically like a post when you reply to it") },
                trailingContent = {
                    Switch(
                        checked = settings.autoLikeOnReply,
                        onCheckedChange = { settingsViewModel.setAutoLikeOnReply(it) }
                    )
                },
            )

            ListItem(
                headlineContent = { Text("@psingletary.com mode") },
                supportingContent = { Text("Auto-like posts as you scroll past them") },
                trailingContent = {
                    Switch(
                        checked = settings.autoLikeOnScroll,
                        onCheckedChange = { settingsViewModel.setAutoLikeOnScroll(it) }
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Content",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            if (timelineViewModel != null) {
                val feeds = timelineViewModel.feeds
                val cachedMetadata = timelineViewModel.cachedFeedMetadata
                val orderedUris = timelineViewModel.orderedFeedUris
                var showReorderDialog by remember { mutableStateOf(false) }

                data class FeedReorderItem(val uri: String, val name: String, val avatar: String?)
                val userAvatar = timelineViewModel.user?.avatar?.uri
                val feedMap = remember(feeds) { feeds.associateBy { it.uri.atUri } }
                val cachedMap = remember(cachedMetadata) { cachedMetadata.associateBy { it.uri } }
                var reorderList by remember(orderedUris, feeds, cachedMetadata, userAvatar) {
                    mutableStateOf(
                        orderedUris.mapNotNull { uri ->
                            if (uri == "following") {
                                FeedReorderItem("following", "Following", userAvatar)
                            } else {
                                feedMap[uri]?.let { FeedReorderItem(it.uri.atUri, it.displayName, it.avatar?.uri) }
                                    ?: cachedMap[uri]?.let { FeedReorderItem(it.uri, it.name, it.avatar) }
                            }
                        }.ifEmpty {
                            listOf(FeedReorderItem("following", "Following", userAvatar)) +
                            feeds.map { FeedReorderItem(it.uri.atUri, it.displayName, it.avatar?.uri) }
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("Reorder feeds") },
                    supportingContent = { Text("Change the order of feeds in the tab row") },
                    modifier = Modifier.clickable { showReorderDialog = true },
                )

                if (showReorderDialog) {
                    AlertDialog(
                        onDismissRequest = { showReorderDialog = false },
                        title = { Text("Reorder feeds") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                reorderList.forEachIndexed { index, feed ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (feed.avatar != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(feed.avatar)
                                                    .crossfade(true)
                                                    .build(),
                                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(32.dp))
                                        }
                                        Text(
                                            text = feed.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    reorderList = reorderList.toMutableList().apply {
                                                        add(index - 1, removeAt(index))
                                                    }
                                                }
                                            },
                                            enabled = index > 0,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, "Move up")
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < reorderList.size - 1) {
                                                    reorderList = reorderList.toMutableList().apply {
                                                        add(index + 1, removeAt(index))
                                                    }
                                                }
                                            },
                                            enabled = index < reorderList.size - 1,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, "Move down")
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    timelineViewModel.reorderFeeds(reorderList.map { it.uri })
                                    showReorderDialog = false
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showReorderDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }

            ListItem(
                headlineContent = { Text("Reply filtering") },
                supportingContent = {
                    Column {
                        Text(
                            text = when (settings.replyFilterMode) {
                                ReplyFilterMode.None -> "All replies from the feed are shown, even from people you don't follow"
                                ReplyFilterMode.OnlyFilterDeepThreads -> "Hides long back-and-forth threads between people you don't follow, but keeps direct replies visible"
                                ReplyFilterMode.Strict -> "Only shows replies where both the author and the person they're replying to are people you follow"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val modes = ReplyFilterMode.entries
                            modes.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.replyFilterMode == mode,
                                    onClick = { settingsViewModel.setReplyFilterMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, modes.size),
                                ) {
                                    Text(
                                        when (mode) {
                                            ReplyFilterMode.None -> "Off"
                                            ReplyFilterMode.OnlyFilterDeepThreads -> "Normal"
                                            ReplyFilterMode.Strict -> "Strict"
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
            )

            ListItem(
                headlineContent = { Text("Show labels") },
                supportingContent = { Text("Show content labels on posts") },
                trailingContent = {
                    Switch(
                        checked = settings.showLabels,
                        onCheckedChange = { settingsViewModel.setShowLabels(it) }
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Show pronouns in posts") },
                supportingContent = { Text("Render the author's pronouns next to their handle") },
                trailingContent = {
                    Switch(
                        checked = settings.showPronounsInPosts,
                        onCheckedChange = { settingsViewModel.setShowPronounsInPosts(it) }
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Require alt text") },
                supportingContent = { Text("Prevent posting images without alt text") },
                trailingContent = {
                    Switch(
                        checked = settings.requireAltText,
                        onCheckedChange = { settingsViewModel.setRequireAltText(it) }
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Auto-thread long posts") },
                supportingContent = { Text("Automatically split posts over 300 characters into a thread") },
                trailingContent = {
                    Switch(
                        checked = settings.autoThreadOnOverflow,
                        onCheckedChange = { settingsViewModel.setAutoThreadOnOverflow(it) }
                    )
                },
            )

            val mutedWordsCount = timelineViewModel?.mutedWords?.size ?: 0
            ListItem(
                headlineContent = { Text("Muted words") },
                supportingContent = {
                    Text(
                        if (mutedWordsCount == 0) "No muted words"
                        else "$mutedWordsCount muted word${if (mutedWordsCount != 1) "s" else ""}"
                    )
                },
                modifier = Modifier.clickable { onMutedWordsTap() },
            )

            if (pushNotificationManager != null && timelineViewModel != null) {
                val accounts = timelineViewModel.accounts
                var pushErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> }

                if (accounts.size <= 1) {
                    var pushError by remember { mutableStateOf<String?>(null) }
                    val account = accounts.firstOrNull()

                    val enablePush: () -> Unit = {
                        val did = account?.did ?: timelineViewModel.user?.did?.did
                        if (did != null) {
                            timelineViewModel.setAccountNotificationsEnabled(did, true) { result ->
                                result.onFailure {
                                    pushError = it.message ?: "Failed to register"
                                }.onSuccess {
                                    pushError = null
                                }
                            }
                        }
                    }

                    val singlePermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) enablePush()
                    }

                    ListItem(
                        headlineContent = { Text("Push notifications") },
                        supportingContent = {
                            if (pushError != null) {
                                Text(
                                    "Registration failed: $pushError",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text("Receive push notifications")
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = account?.notificationsEnabled == true,
                                onCheckedChange = { enabled ->
                                    val did = account?.did ?: timelineViewModel.user?.did?.did
                                    if (did != null) {
                                        if (enabled) {
                                            pushError = null
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) enablePush()
                                                else singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                enablePush()
                                            }
                                        } else {
                                            pushError = null
                                            timelineViewModel.setAccountNotificationsEnabled(did, false)
                                        }
                                    }
                                }
                            )
                        },
                    )
                } else {
                    Text(
                        text = "Push notifications",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    )

                    accounts.forEach { account ->
                        val accountError = pushErrors[account.did]
                        ListItem(
                            headlineContent = {
                                Text(account.displayName ?: account.handle)
                            },
                            supportingContent = {
                                if (accountError != null) {
                                    Text(
                                        "Registration failed: $accountError",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    Text("@${account.handle}")
                                }
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(account.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = account.notificationsEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            pushErrors = pushErrors - account.did
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    timelineViewModel.setAccountNotificationsEnabled(account.did, true) { result ->
                                                        result.onFailure {
                                                            pushErrors = pushErrors + (account.did to (it.message ?: "Failed"))
                                                        }
                                                    }
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            } else {
                                                timelineViewModel.setAccountNotificationsEnabled(account.did, true) { result ->
                                                    result.onFailure {
                                                        pushErrors = pushErrors + (account.did to (it.message ?: "Failed"))
                                                    }
                                                }
                                            }
                                        } else {
                                            pushErrors = pushErrors - account.did
                                            timelineViewModel.setAccountNotificationsEnabled(account.did, false)
                                        }
                                    }
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ListItem(
                headlineContent = {
                    Text(
                        text = "AI Features",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = {
                    Text("All AI features run on-device using local models. Your data is never sent to external servers.")
                },
            )

            ListItem(
                headlineContent = { Text("Enable AI features") },
                supportingContent = { Text("Master toggle for all AI-powered features") },
                trailingContent = {
                    Switch(
                        checked = settings.aiEnabled,
                        onCheckedChange = { settingsViewModel.setAiEnabled(it) }
                    )
                },
            )

            if (settings.aiEnabled) {
                if (altTextAvailable == true) {
                    ListItem(
                        headlineContent = { Text("Alt text generation") },
                        supportingContent = { Text("Generate image descriptions when composing posts") },
                        trailingContent = {
                            Switch(
                                checked = settings.aiAltTextEnabled,
                                onCheckedChange = { settingsViewModel.setAiAltTextEnabled(it) }
                            )
                        },
                    )
                }

                ListItem(
                    headlineContent = { Text("Post translation") },
                    supportingContent = { Text("Translate posts to your preferred language") },
                    trailingContent = {
                        Switch(
                            checked = settings.translationEnabled,
                            onCheckedChange = { settingsViewModel.setTranslationEnabled(it) }
                        )
                    },
                )

                if (settings.translationEnabled) {
                    ListItem(
                        headlineContent = { Text("Translation language") },
                        trailingContent = {
                            var expanded by remember { mutableStateOf(false) }
                            val currentLabel = TRANSLATION_LANGUAGE_OPTIONS
                                .firstOrNull { it.second == settings.targetTranslationLanguage }
                                ?.first ?: "English"
                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Text(currentLabel)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    TRANSLATION_LANGUAGE_OPTIONS.forEach { (label, code) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                settingsViewModel.setTargetTranslationLanguage(code)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (timelineViewModel != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingContent = {
                        Text("AppViews process and display network data. Different AppViews offer unique moderation, algorithms, and features while your posts remain portable.")
                    },
                )

                data class AppviewOption(
                    val name: String,
                    val did: String,
                    val description: String,
                )

                val knownAppviews = listOf(
                    AppviewOption(
                        name = "Bluesky",
                        did = "did:web:api.bsky.app#bsky_appview",
                        description = "The default AppView operated by Bluesky PBC",
                    ),
                    AppviewOption(
                        name = "Blacksky",
                        did = "did:web:api.blacksky.community#bsky_appview",
                        description = "Self-governed community spaces with collective moderation",
                    ),
                )
                var currentProxy by remember { mutableStateOf(timelineViewModel.appviewProxy() ?: "") }
                var showCustomDialog by remember { mutableStateOf(false) }

                knownAppviews.forEach { appview ->
                    ListItem(
                        headlineContent = { Text(appview.name) },
                        supportingContent = { Text(appview.description) },
                        leadingContent = {
                            androidx.compose.material3.RadioButton(
                                selected = currentProxy == appview.did,
                                onClick = {
                                    if (currentProxy != appview.did) {
                                        currentProxy = appview.did
                                        timelineViewModel.changeAppview(appview.did)
                                    }
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            if (currentProxy != appview.did) {
                                currentProxy = appview.did
                                timelineViewModel.changeAppview(appview.did)
                            }
                        }
                    )
                }

                val isCustom = currentProxy.isNotEmpty() && knownAppviews.none { it.did == currentProxy }
                ListItem(
                    headlineContent = { Text("Custom") },
                    supportingContent = {
                        Text(if (isCustom) currentProxy else "Connect to a different AppView")
                    },
                    leadingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = isCustom,
                            onClick = { showCustomDialog = true }
                        )
                    },
                    modifier = Modifier.clickable { showCustomDialog = true }
                )

                if (showCustomDialog) {
                    var customDid by remember { mutableStateOf(if (isCustom) currentProxy else "") }
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("Custom Appview") },
                        text = {
                            Column {
                                Text(
                                    text = "Enter the DID of the appview service",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customDid,
                                    onValueChange = { customDid = it },
                                    label = { Text("Appview DID") },
                                    placeholder = { Text("did:web:example.com#bsky_appview") },
                                    singleLine = true,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (customDid.isNotBlank()) {
                                        currentProxy = customDid.trim()
                                        timelineViewModel.changeAppview(customDid.trim())
                                        showCustomDialog = false
                                    }
                                }
                            ) { Text("Apply") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
                        },
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Debug \uD83D\uDD25",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )

                var showServerUrlDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Notification server URL") },
                    supportingContent = { Text(settings.notificationServerUrl) },
                    modifier = Modifier.clickable { showServerUrlDialog = true },
                )

                if (showServerUrlDialog) {
                    var urlValue by remember { mutableStateOf(settings.notificationServerUrl) }
                    AlertDialog(
                        onDismissRequest = { showServerUrlDialog = false },
                        title = { Text("Notification Server URL") },
                        text = {
                            OutlinedTextField(
                                value = urlValue,
                                onValueChange = { urlValue = it },
                                label = { Text("URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    settingsViewModel.setNotificationServerUrl(urlValue.trim())
                                    showServerUrlDialog = false
                                }
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerUrlDialog = false }) { Text("Cancel") }
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text("Log out")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Monarch v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT_SHA})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            val bottomUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                text = "Source code on GitHub",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bottomUriHandler.openUri("https://github.com/geesawra/monarch") }
                    .padding(top = 4.dp, bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        }
    }
}
