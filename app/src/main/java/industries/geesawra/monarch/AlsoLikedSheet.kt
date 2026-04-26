package industries.geesawra.monarch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlsoLikedSheet(
    onDismiss: () -> Unit,
    fetchAlsoLiked: suspend (cursor: String?) -> Pair<List<SkeetData>, String?>?,
    timelineViewModel: TimelineViewModel?,
    settingsState: SettingsState = SettingsState(),
    onShowThread: (SkeetData) -> Unit = {},
    onProfileTap: (Did) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    var posts by remember { mutableStateOf<List<SkeetData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val avatarClipShape = settingsState.avatarClipShape

    LaunchedEffect(Unit) {
        val result = fetchAlsoLiked(null)
        if (result != null) {
            posts = result.first
            if (result.first.isEmpty()) {
                errorMessage = "Not in database (only last 90 days available)"
            }
        } else {
            errorMessage = "Failed to load"
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "People who liked this also liked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = feedHorizontalPadding()),
                    verticalArrangement = Arrangement.spacedBy(feedItemSpacing()),
                ) {
                    items(
                        items = posts,
                        key = { "${it.cid.cid}_${it.uri.atUri}" },
                    ) { post ->
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            SkeetView(
                                viewModel = timelineViewModel,
                                skeet = post,
                                postTextSize = settingsState.postTextSize,
                                avatarShape = avatarClipShape,
                                showLabels = settingsState.showLabels,
                                showPronouns = settingsState.showPronounsInPosts,
                                onAvatarTap = {
                                    onProfileTap(it)
                                    onDismiss()
                                },
                                onShowThread = {
                                    onShowThread(it)
                                    onDismiss()
                                },
                                translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                                targetTranslationLanguage = settingsState.targetTranslationLanguage,
                                carouselImageGallery = settingsState.carouselImageGallery,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
