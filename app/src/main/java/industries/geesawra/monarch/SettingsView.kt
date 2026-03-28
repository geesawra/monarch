package industries.geesawra.monarch

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.ReplyFilterMode
import industries.geesawra.monarch.datalayer.SettingsViewModel
import industries.geesawra.monarch.datalayer.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    backButton: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
        }
    }
}
