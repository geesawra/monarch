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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import industries.geesawra.monarch.BuildConfig
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.PushNotificationManager
import industries.geesawra.monarch.datalayer.ReplyFilterMode
import industries.geesawra.monarch.datalayer.SettingsViewModel
import industries.geesawra.monarch.datalayer.ThemeMode
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    timelineViewModel: TimelineViewModel? = null,
    pushNotificationManager: PushNotificationManager? = null,
    backButton: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val settings = settingsViewModel.settingsState

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
                headlineContent = { Text("Force compact layout") },
                supportingContent = { Text("Use phone layout on large screens") },
                trailingContent = {
                    Switch(
                        checked = settings.forceCompactLayout,
                        onCheckedChange = { settingsViewModel.setForceCompactLayout(it) }
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Swipeable feeds") },
                supportingContent = { Text("Swipe left/right on the timeline to switch feeds") },
                trailingContent = {
                    Switch(
                        checked = settings.swipeableFeeds,
                        onCheckedChange = { settingsViewModel.setSwipeableFeeds(it) }
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

            ListItem(
                headlineContent = { Text("Reply filtering") },
                supportingContent = {
                    Column {
                        Text(
                            text = when (settings.replyFilterMode) {
                                ReplyFilterMode.None -> "Show all replies"
                                ReplyFilterMode.OnlyFilterDeepThreads -> "Hide deep thread noise"
                                ReplyFilterMode.Strict -> "Only direct replies"
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
                                            ReplyFilterMode.None -> "None"
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

            if (pushNotificationManager != null && timelineViewModel != null) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                var pushError by remember { mutableStateOf<String?>(null) }

                val enablePush: () -> Unit = {
                    settingsViewModel.setPushNotificationsEnabled(true)
                    val did = timelineViewModel.uiState.user?.did?.did
                    if (did != null) {
                        coroutineScope.launch {
                            pushNotificationManager.getAndRegisterToken(did).onFailure {
                                pushError = it.message ?: "Failed to register"
                                settingsViewModel.setPushNotificationsEnabled(false)
                            }.onSuccess {
                                pushError = null
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
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
                            checked = settings.pushNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    pushError = null
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) enablePush()
                                        else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        enablePush()
                                    }
                                } else {
                                    pushError = null
                                    settingsViewModel.setPushNotificationsEnabled(false)
                                }
                            }
                        )
                    },
                )
            }

            if (timelineViewModel != null) {
                var showFeedPicker by remember { mutableStateOf(false) }
                val feeds = timelineViewModel.uiState.feeds

                ListItem(
                    headlineContent = { Text("Default feed") },
                    supportingContent = { Text(settings.defaultFeed.displayName) },
                    modifier = Modifier.clickable { showFeedPicker = true },
                )

                if (showFeedPicker) {
                    AlertDialog(
                        onDismissRequest = { showFeedPicker = false },
                        title = { Text("Default feed") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                FeedPickerItem(
                                    name = "Following",
                                    avatarUrl = null,
                                    isDefault = settings.defaultFeed.uri == "following",
                                    onClick = {
                                        settingsViewModel.setDefaultFeed("following", "Following", null)
                                        showFeedPicker = false
                                    },
                                )
                                feeds.forEach { feed ->
                                    FeedPickerItem(
                                        name = feed.displayName,
                                        avatarUrl = feed.avatar?.uri,
                                        isDefault = settings.defaultFeed.uri == feed.uri.atUri,
                                        onClick = {
                                            settingsViewModel.setDefaultFeed(feed.uri.atUri, feed.displayName, feed.avatar?.uri)
                                            showFeedPicker = false
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFeedPicker = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }

            if (timelineViewModel != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Network",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )

                val knownAppviews = listOf(
                    "Bluesky" to "did:web:api.bsky.app#bsky_appview",
                    "Blacksky" to "did:web:api.blacksky.community#bsky_appview",
                )
                var currentProxy by remember { mutableStateOf(timelineViewModel.appviewProxy() ?: "") }
                var showCustomDialog by remember { mutableStateOf(false) }

                knownAppviews.forEach { (name, did) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(did) },
                        leadingContent = {
                            androidx.compose.material3.RadioButton(
                                selected = currentProxy == did,
                                onClick = {
                                    if (currentProxy != did) {
                                        currentProxy = did
                                        timelineViewModel.changeAppview(did)
                                    }
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            if (currentProxy != did) {
                                currentProxy = did
                                timelineViewModel.changeAppview(did)
                            }
                        }
                    )
                }

                val isCustom = currentProxy.isNotEmpty() && knownAppviews.none { it.second == currentProxy }
                ListItem(
                    headlineContent = { Text("Custom") },
                    supportingContent = {
                        if (isCustom) Text(currentProxy)
                        else Text("Use a custom appview")
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
        }
        }
    }
}

@Composable
private fun FeedPickerItem(
    name: String,
    avatarUrl: String?,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentDescription = null,
            )
        } else {
            Spacer(modifier = Modifier.size(28.dp))
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        if (isDefault) {
            Icon(
                Icons.Default.Home,
                contentDescription = "Default",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
