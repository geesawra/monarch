package industries.geesawra.monarch

import android.content.Intent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var fontSize by remember {
            mutableStateOf(10.dp)
        }
        Icon(
            imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
            tint = tint
        )
        Text(
            modifier = Modifier.padding(start = 2.dp),
            text = number.longValue.toString(),
            color = tint,
            maxLines = 1,
            onTextLayout = { textLayout ->
                if (textLayout.multiParagraph.didExceedMaxLines) {
                    fontSize -= 1.dp
                }
            }
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

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier,
    ) {
        val ctx = LocalContext.current

        if (inThread) {
            VerticalDivider(
                thickness = 4.dp,
                modifier = Modifier
                    .padding(start = 25.dp, top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.weight(1f))
        }

        IconButton(
            onClick = {
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
                modifier = Modifier.size(15.dp),
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = {
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
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var isLiked by rememberSaveable { mutableStateOf(skeet.didLike) }
        IconButton(
            onClick = {
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
                if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                contentDescription = "Like",
                number = likes,
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var isReposted by rememberSaveable { mutableStateOf(skeet.didRepost) }
        LongPressIconButton(
            onClick = {
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
                onReplyTap(skeet, true)
            }
        ) {
            IconWithNumber(
                if (isReposted) Icons.Default.RepeatOn else Icons.Default.Repeat,
                contentDescription = "Repost",
                number = reposts,
                if (isReposted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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