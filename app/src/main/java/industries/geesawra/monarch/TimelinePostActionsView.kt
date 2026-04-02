package industries.geesawra.monarch

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
    skeet: SkeetData,
    inThread: Boolean = false,
) {
    val likes = remember { mutableLongStateOf(skeet.likes ?: 0) }
    val reposts = remember { mutableLongStateOf(skeet.reposts ?: 0) }
    val replies = remember { mutableLongStateOf(skeet.replies ?: 0) }
    val haptic = LocalHapticFeedback.current
    var showDeleteDialog by remember { mutableStateOf(false) }
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
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDeleteDialog = true
                }
            ) {
                Icon(
                    modifier = Modifier.size(actionIconSize()),
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val replyDisabled = skeet.threadgate != null && !isOwnPost

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

        var isLiked by rememberSaveable { mutableStateOf(skeet.didLike) }
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
                    false -> timelineViewModel?.like(skeet.uri, skeet.cid) {
                        isLiked = true
                        likes.longValue++
                    }

                    true -> timelineViewModel?.deleteLike(skeet.cid) {
                        isLiked = false
                        likes.longValue--
                    }
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

        var isReposted by rememberSaveable { mutableStateOf(skeet.didRepost) }
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
                    false -> timelineViewModel?.repost(skeet.uri, skeet.cid) {
                        isReposted = true
                        reposts.longValue++
                    }

                    true -> timelineViewModel?.deleteRepost(skeet.cid) {
                        isReposted = false
                        reposts.longValue--
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserListSheet(
    title: String,
    onDismiss: () -> Unit,
    fetchUsers: suspend (cursor: String?) -> Pair<List<app.bsky.actor.ProfileView>, String?>?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var users by remember { mutableStateOf<List<app.bsky.actor.ProfileView>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val result = fetchUsers(null)
        if (result != null) {
            users = result.first
            cursor = result.second
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 200.dp, max = 500.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (users.isEmpty()) {
                Text(
                    text = "None yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(users.size) { idx ->
                        val user = users[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = user.avatar?.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName ?: user.handle.handle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${user.handle.handle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotesSheet(
    onDismiss: () -> Unit,
    fetchQuotes: suspend (cursor: String?) -> Pair<List<SkeetData>, String?>?,
    timelineViewModel: TimelineViewModel?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var quotes by remember { mutableStateOf<List<SkeetData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val result = fetchQuotes(null)
        if (result != null) {
            quotes = result.first
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 200.dp, max = 500.dp)
        ) {
            Text(
                text = "Quotes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (quotes.isEmpty()) {
                Text(
                    text = "No quotes yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(quotes.size) { idx ->
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                        ) {
                            SkeetView(
                                viewModel = timelineViewModel,
                                skeet = quotes[idx],
                                nested = true,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
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
