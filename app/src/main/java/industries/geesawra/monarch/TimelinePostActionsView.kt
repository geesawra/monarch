package industries.geesawra.monarch

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.AtUri
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

@Composable
fun TimelinePostActionsView(
    modifier: Modifier = Modifier,
    timelineViewModel: TimelineViewModel?,
    onReplyTap: (SkeetData) -> Unit = {},
    skeet: SkeetData
) {
    val likes = remember { mutableLongStateOf(skeet.likes ?: 0) }
    val reposts = remember { mutableLongStateOf(skeet.reposts ?: 0) }
    val replies = remember { mutableLongStateOf(skeet.replies ?: 0) }


    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier,
    ) {
        val ctx = LocalContext.current
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
                onReplyTap(skeet)
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
        IconButton(
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

            }
        ) {
            IconWithNumber(
                if (isReposted) Icons.Default.RepeatOn else Icons.Default.Repeat,
                contentDescription = "Repost",
                number = reposts,
                if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}