package industries.geesawra.monarch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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


@Composable
private fun IconWithNumber(
    imageVector: ImageVector,
    contentDescription: String,
    number: MutableLongState,
    tint: Color,
    scale: Float = 1f,
) {
    val iconSize = actionIconSize()
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .scale(scale),
            tint = tint
        )
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = number.longValue.toString(),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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
) {
    val interactionState = timelineViewModel?.postInteractionStore?.getState(
        skeet.cid, PostInteraction.from(skeet)
    )
    val interaction = interactionState?.value
    val likes = remember { mutableLongStateOf(interaction?.likes ?: skeet.likes ?: 0) }
    val reposts = remember { mutableLongStateOf(interaction?.reposts ?: skeet.reposts ?: 0) }
    val replies = remember { mutableLongStateOf(interaction?.replies ?: skeet.replies ?: 0) }
    if (interaction != null) {
        likes.longValue = interaction.likes
        reposts.longValue = interaction.reposts
        replies.longValue = interaction.replies
    }
    val haptic = LocalHapticFeedback.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRedraftDialog by remember { mutableStateOf(false) }
    val isOwnPost = timelineViewModel?.isOwnPost(skeet) == true

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
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
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val ctx = LocalContext.current

        if (inThread) {
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
                modifier = Modifier.size(actionIconSize()),
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isOwnPost) {
            LongPressIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDeleteDialog = true
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showRedraftDialog = true
                },
            ) {
                Icon(
                    modifier = Modifier.size(actionIconSize()),
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete (long press to redraft)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val replyDisabled = skeet.replyDisabled && !isOwnPost

        IconButton(
            enabled = !replyDisabled,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onReplyTap(skeet, false)
            }
        ) {
            IconWithNumber(
                imageVector = {
                    if (replies.longValue == 0.toLong()) {
                        Icons.AutoMirrored.Filled.Reply
                    }

                    val r = replies
                    if (r.longValue > 0) {
                        Icons.AutoMirrored.Filled.ReplyAll
                    } else {
                        Icons.AutoMirrored.Filled.Reply
                    }

                }(),
                contentDescription = "Reply",
                number = replies,
                if (replyDisabled) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var isLiked by rememberSaveable { mutableStateOf(interaction?.didLike ?: skeet.didLike) }
        if (interaction != null) { isLiked = interaction.didLike }
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

        IconButton(
            onClick = {
                likeBounce = true
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                when (isLiked) {
                    false -> timelineViewModel?.like(skeet.uri, skeet.cid)
                    true -> timelineViewModel?.deleteLike(skeet.cid)
                }
            }
        ) {
            IconWithNumber(
                if (isLiked) HeartFilled else Heart,
                contentDescription = "Like",
                number = likes,
                tint = likeColor,
                scale = likeScale,
            )
        }

        var isReposted by rememberSaveable { mutableStateOf(interaction?.didRepost ?: skeet.didRepost) }
        if (interaction != null) { isReposted = interaction.didRepost }
        var repostBounce by remember { mutableStateOf(false) }
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

        LongPressIconButton(
            onClick = {
                repostBounce = true
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                when (isReposted) {
                    false -> timelineViewModel?.repost(skeet.uri, skeet.cid)
                    true -> timelineViewModel?.deleteRepost(skeet.cid)
                }
            },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReplyTap(skeet, true)
            }
        ) {
            IconWithNumber(
                if (isReposted) Icons.Default.RepeatOn else Icons.Default.Repeat,
                contentDescription = "Repost",
                number = reposts,
                tint = repostColor,
                scale = repostScale,
            )
        }

        var showMenu by remember { mutableStateOf(false) }
        var showLikesSheet by remember { mutableStateOf(false) }
        var showRepostsSheet by remember { mutableStateOf(false) }
        var showQuotesSheet by remember { mutableStateOf(false) }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    modifier = Modifier.size(actionIconSize()),
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
                DropdownMenuItem(
                    text = { Text("Liked by") },
                    onClick = { showMenu = false; showLikesSheet = true },
                )
                DropdownMenuItem(
                    text = { Text("Reposted by") },
                    onClick = { showMenu = false; showRepostsSheet = true },
                )
                DropdownMenuItem(
                    text = { Text("Quotes") },
                    onClick = { showMenu = false; showQuotesSheet = true },
                )
            }
        }

        if (showLikesSheet) {
            UserListSheet(
                title = "Liked by",
                onDismiss = { showLikesSheet = false },
                fetchUsers = { cursor ->
                    timelineViewModel?.getLikes(skeet.uri, cursor)?.getOrNull()?.let {
                        it.likes.map { like -> like.actor } to it.cursor
                    }
                },
            )
        }

        if (showRepostsSheet) {
            UserListSheet(
                title = "Reposted by",
                onDismiss = { showRepostsSheet = false },
                fetchUsers = { cursor ->
                    timelineViewModel?.getRepostedBy(skeet.uri, cursor)?.getOrNull()?.let {
                        it.repostedBy to it.cursor
                    }
                },
            )
        }

        if (showQuotesSheet) {
            QuotesSheet(
                onDismiss = { showQuotesSheet = false },
                fetchQuotes = { cursor ->
                    timelineViewModel?.getQuotes(skeet.uri, cursor)?.getOrNull()?.let {
                        it.posts.map { pv -> SkeetData.fromPostView(pv, pv.author) } to it.cursor
                    }
                },
                timelineViewModel = timelineViewModel,
            )
        }
    }
}


@Composable
fun LongPressIconButton(
    modifier: Modifier = Modifier,
    stepDelay: Long = 100L, // Minimum value is 1L milliseconds.
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedListener by rememberUpdatedState(onLongClick)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(stepDelay.coerceIn(1L, Long.MAX_VALUE))
            pressedListener()
        }
    }

    IconButton(
        modifier = modifier,
        onClick = if (isPressed) {
            {}
        } else {
            onClick
        },
        interactionSource = interactionSource
    ) {
        content()
    }
}
