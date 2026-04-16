package industries.geesawra.monarch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import industries.geesawra.monarch.datalayer.PostInteraction
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.delay
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey

private fun formatCount(count: Long): String {
    return when {
        count < 1_000 -> count.toString()
        count < 10_000 -> String.format("%.1fk", count / 1_000.0).replace(".0k", "k")
        count < 1_000_000 -> "${count / 1_000}k"
        count < 10_000_000 -> String.format("%.1fm", count / 1_000_000.0).replace(".0m", "m")
        else -> "${count / 1_000_000}m"
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    count: Long,
    tint: Color,
    scale: Float = 1f,
    isActive: Boolean = false,
    activeContainerColor: Color = Color.Transparent,
    iconSize: Dp? = null,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeContainerColor else Color.Transparent,
        label = "containerColor"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (isActive) 8.dp else 0.dp,
        label = "pillPadding"
    )

    val effectiveSize = iconSize ?: actionIconSize()

    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(effectiveSize)
                    .scale(scale),
                tint = tint
            )
            if (count > 0) {
                Text(
                    modifier = Modifier.padding(start = 2.dp),
                    text = formatCount(count),
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

fun AtUri.rkey(): RKey {
    return RKey(this.atUri.toUri().lastPathSegment!!)
}

fun AtUri.did(): Did {
    return Did(this.atUri.toUri().host.toString())
}

fun AtUri.collection(): Nsid {
    return Nsid(this.atUri.toUri().pathSegments[0])
}

@Composable
fun TimelinePostActionsView(
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel?,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onRedraft: (SkeetData) -> Unit = { s -> timelineViewModel?.setRedraft(s.content) },
    skeet: SkeetData,
    inThread: Boolean = false,
    translationEnabled: Boolean = true,
    targetTranslationLanguage: String = "en",
    showCounts: Boolean = true,
    detailMode: Boolean = false,
) {
    val effectiveIconSize: Dp? = if (detailMode) 24.dp else null
    val actionArrangement = if (detailMode) Arrangement.SpaceBetween else Arrangement.Start

    val interactionState = timelineViewModel?.postInteractionStore?.getState(skeet.cid) {
        PostInteraction.from(skeet)
    }
    val interaction = interactionState?.value
    val likes = interaction?.likes ?: skeet.likes ?: 0
    val reposts = interaction?.reposts ?: skeet.reposts ?: 0
    val replies = interaction?.replies ?: skeet.replies ?: 0
    val isLiked = interaction?.didLike ?: skeet.didLike
    val isReposted = interaction?.didRepost ?: skeet.didRepost
    val isBookmarked = interaction?.didBookmark ?: skeet.didBookmark
    val haptic = LocalHapticFeedback.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var threadUris by remember(skeet.uri) { mutableStateOf<List<AtUri>?>(null) }
    var showRedraftDialog by remember { mutableStateOf(false) }
    val isOwnPost = timelineViewModel?.isOwnPost(skeet) == true

    LaunchedEffect(showDeleteDialog, skeet.uri) {
        if (showDeleteDialog && timelineViewModel != null && threadUris == null) {
            threadUris = timelineViewModel.findSelfAuthoredThreadUris(skeet)
        }
    }

    if (showDeleteDialog) {
        val uris = threadUris
        when {
            uris == null -> androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { androidx.compose.material3.Text("Delete post?") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Text("Checking thread…")
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                        androidx.compose.material3.Text("Cancel")
                    }
                },
            )
            uris.size == 1 -> androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { androidx.compose.material3.Text("Delete post?") },
                text = { androidx.compose.material3.Text("This cannot be undone.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showDeleteDialog = false
                        timelineViewModel?.deletePost(skeet.uri) {}
                    }) {
                        androidx.compose.material3.Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                        androidx.compose.material3.Text("Cancel")
                    }
                }
            )
            else -> androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { androidx.compose.material3.Text("Delete thread?") },
                text = {
                    androidx.compose.material3.Text(
                        "This post is part of a ${uris.size}-post thread you wrote. Delete the entire thread, or just this post?"
                    )
                },
                confirmButton = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.TextButton(onClick = {
                            showDeleteDialog = false
                            timelineViewModel?.deleteThreadPosts(uris) {}
                        }) {
                            androidx.compose.material3.Text(
                                "Delete entire thread",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            showDeleteDialog = false
                            timelineViewModel?.deletePost(skeet.uri) {}
                        }) {
                            androidx.compose.material3.Text(
                                "Delete this post only",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                        androidx.compose.material3.Text("Cancel")
                    }
                }
            )
        }
    }

    if (showRedraftDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRedraftDialog = false },
            title = { androidx.compose.material3.Text("Delete and redraft?") },
            text = { androidx.compose.material3.Text("The post will be deleted and its text copied to the composer.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRedraftDialog = false
                    timelineViewModel?.deletePost(skeet.uri) {}
                    onRedraft(skeet)
                }) {
                    androidx.compose.material3.Text(
                        "Redraft",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRedraftDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    Row(
        horizontalArrangement = actionArrangement,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        val ctx = LocalContext.current
        val replyDisabled = skeet.replyDisabled && !isOwnPost

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !replyDisabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReplyTap(skeet, false)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(
                icon = Icons.Outlined.ModeComment,
                contentDescription = "Reply",
                count = if (showCounts) replies else 0,
                tint = if (replyDisabled) MaterialTheme.colorScheme.outlineVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = effectiveIconSize,
            )
        }

        var likeBounce by remember { mutableStateOf(false) }
        val likeScale by animateFloatAsState(
            targetValue = if (likeBounce) 1.3f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "likeScale"
        )
        LaunchedEffect(likeBounce) {
            if (likeBounce) {
                delay(150)
                likeBounce = false
            }
        }
        val likeColor by animateColorAsState(
            targetValue = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "likeColor"
        )

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        likeBounce = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        when (isLiked) {
                            false -> timelineViewModel?.like(skeet.uri, skeet.cid)
                            true -> timelineViewModel?.deleteLike(skeet.cid)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                count = if (showCounts) likes else 0,
                tint = likeColor,
                scale = likeScale,
                isActive = isLiked,
                activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconSize = effectiveIconSize,
            )
        }

        var repostBounce by remember { mutableStateOf(false) }
        var showRepostMenu by remember { mutableStateOf(false) }
        val repostScale by animateFloatAsState(
            targetValue = if (repostBounce) 1.3f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "repostScale"
        )
        LaunchedEffect(repostBounce) {
            if (repostBounce) {
                delay(150)
                repostBounce = false
            }
        }
        val repostColor by animateColorAsState(
            targetValue = if (isReposted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "repostColor"
        )

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showRepostMenu = true
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            ActionIcon(
                icon = Icons.Default.Autorenew,
                contentDescription = "Repost",
                count = if (showCounts) reposts else 0,
                tint = repostColor,
                scale = repostScale,
                isActive = isReposted,
                activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconSize = effectiveIconSize,
            )
            DropdownMenu(expanded = showRepostMenu, onDismissRequest = { showRepostMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isReposted) "Undo repost" else "Repost") },
                    onClick = {
                        showRepostMenu = false
                        repostBounce = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        when (isReposted) {
                            false -> timelineViewModel?.repost(skeet.uri, skeet.cid)
                            true -> timelineViewModel?.deleteRepost(skeet.cid)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Quote") },
                    onClick = {
                        showRepostMenu = false
                        onReplyTap(skeet, true)
                    },
                )
            }
        }

        if (!detailMode) {
            Spacer(Modifier.weight(1f))
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, skeet.shareURL())
                }
                ctx.startActivity(
                    Intent.createChooser(sendIntent, "Share Bluesky post")
                )
            }
        ) {
            Icon(
                modifier = Modifier.size(effectiveIconSize ?: actionIconSize()),
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var bookmarkBounce by remember { mutableStateOf(false) }
        val bookmarkScale by animateFloatAsState(
            targetValue = if (bookmarkBounce) 1.3f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "bookmarkScale"
        )
        LaunchedEffect(bookmarkBounce) {
            if (bookmarkBounce) {
                delay(150)
                bookmarkBounce = false
            }
        }
        val bookmarkColor by animateColorAsState(
            targetValue = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "bookmarkColor"
        )

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        bookmarkBounce = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        when (isBookmarked) {
                            false -> timelineViewModel?.bookmark(skeet.uri, skeet.cid)
                            true -> timelineViewModel?.deleteBookmark(skeet.uri, skeet.cid)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier
                    .size(effectiveIconSize ?: actionIconSize())
                    .scale(bookmarkScale),
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = bookmarkColor,
            )
        }

        var showMenu by remember { mutableStateOf(false) }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    modifier = Modifier.size(effectiveIconSize ?: actionIconSize()),
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        showMenu = false
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Post", skeet.content))
                    },
                )
                if (translationEnabled && skeet.content.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Translate") },
                        onClick = {
                            showMenu = false
                            timelineViewModel?.translatePost(skeet, targetTranslationLanguage)
                        },
                    )
                }
                if (isOwnPost) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            val mightBeInThread = skeet.reply != null || (skeet.replies ?: 0) > 0
                            threadUris = if (mightBeInThread) null else listOf(skeet.uri)
                            showDeleteDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete and redraft") },
                        onClick = {
                            showMenu = false
                            showRedraftDialog = true
                        },
                    )
                }
            }
        }
    }
}
