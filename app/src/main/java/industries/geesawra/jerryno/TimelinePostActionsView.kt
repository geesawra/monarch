package industries.geesawra.jerryno

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp


@Composable
private fun IconWithNumber(imageVector: ImageVector, contentDescription: String, number: Long?) {
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant // Added tint
        )
        Text(
            modifier = Modifier.padding(start = 2.dp),
            text = (number ?: 0).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant, // Added color
            maxLines = 1,
            onTextLayout = { textLayout ->
                if (textLayout.multiParagraph.didExceedMaxLines) {
                    fontSize -= 1.dp
                }
            }
        )
    }
}

@Composable
fun TimelinePostActionsView(
    modifier: Modifier = Modifier,
    replies: Long?,
    likes: Long?,
    reposts: Long?,
    uri: String
) {

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
                    putExtra(Intent.EXTRA_TEXT, uri)
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
            onClick = {}
        ) {
            IconWithNumber(
                imageVector = {
                    if (replies == null) {
                        Icons.AutoMirrored.Filled.Reply
                    }

                    val r = replies!!
                    if (r > 0) {
                        Icons.AutoMirrored.Filled.ReplyAll
                    } else {
                        Icons.AutoMirrored.Filled.Reply
                    }

                }(),
                contentDescription = "Reply",
                number = replies,
            )
        }
        IconButton(
            onClick = {}
        ) {
            IconWithNumber(
                Icons.Default.ThumbUp,
                contentDescription = "Like",
                number = likes
            )
        }
        IconButton(
            onClick = {}
        ) {
            IconWithNumber(
                Icons.Default.Repeat,
                contentDescription = "Repost",
                number = reposts,
            )
        }
    }
}