package industries.geesawra.monarch

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.draft.DraftView
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Tid
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun DraftsListSheet(
    timelineViewModel: TimelineViewModel,
    localDeviceId: String,
    onDismiss: () -> Unit,
    onLoadDraft: (DraftView) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draftsState = timelineViewModel.draftsState
    var deleteConfirmId by remember { mutableStateOf<Tid?>(null) }

    LaunchedEffect(Unit) {
        timelineViewModel.fetchDrafts()
    }

    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete draft?") },
            text = { Text("This draft will be permanently deleted.") },
            confirmButton = {
                Button(onClick = {
                    deleteConfirmId?.let { timelineViewModel.deleteDraft(it) }
                    deleteConfirmId = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Drafts",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalDivider()

            if (draftsState.drafts.isEmpty() && !draftsState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No drafts",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Saved drafts will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val reachedEnd by remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= listState.layoutInfo.totalItemsCount - 2
                    }
                }

                LaunchedEffect(reachedEnd) {
                    if (reachedEnd && !draftsState.isLoading && draftsState.cursor != null) {
                        timelineViewModel.fetchDrafts(fresh = false)
                    }
                }

                LazyColumn(state = listState) {
                    items(draftsState.drafts, key = { it.id.tid }) { draftView ->
                        DraftItem(
                            draftView = draftView,
                            isFromOtherDevice = draftView.draft.deviceId != null && draftView.draft.deviceId != localDeviceId,
                            onTap = { onLoadDraft(draftView) },
                            onDelete = { deleteConfirmId = draftView.id },
                        )
                        HorizontalDivider()
                    }
                    if (draftsState.isLoading) {
                        item { LoadingBox() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DraftItem(
    draftView: DraftView,
    isFromOtherDevice: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val post = draftView.draft.posts.firstOrNull() ?: return
    val previewText = post.text.take(120).ifEmpty { "(no text)" }
    val imageCount = post.embedImages?.size ?: 0
    val hasVideo = !post.embedVideos.isNullOrEmpty()

    ListItem(
        modifier = Modifier.clickable(onClick = onTap),
        headlineContent = {
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = relativeTimestamp(draftView.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (imageCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$imageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasVideo) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isFromOtherDevice && draftView.draft.deviceName != null) {
                    Text(
                        text = "From ${draftView.draft.deviceName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete draft",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    )
}

@OptIn(ExperimentalTime::class)
private fun relativeTimestamp(timestamp: sh.christian.ozone.api.model.Timestamp): String {
    val now = Clock.System.now()
    val diff = now - timestamp
    return when {
        diff.inWholeMinutes < 1 -> "just now"
        diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes}m ago"
        diff.inWholeHours < 24 -> "${diff.inWholeHours}h ago"
        diff.inWholeDays < 30 -> "${diff.inWholeDays}d ago"
        else -> "${diff.inWholeDays / 30}mo ago"
    }
}
