package industries.geesawra.monarch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.RepeatableNotification
import industries.geesawra.monarch.datalayer.RepeatedNotification
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

fun name(p: ProfileView): String {
    return when (p.displayName) {
        null -> p.handle.handle
        else -> when (p.displayName!!.isEmpty()) {
            true -> p.handle.handle
            else -> p.displayName!!
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun LikeRepostRowView(
    modifier: Modifier = Modifier,
    data: RepeatedNotification
) {
    val minSize = 24.dp
    val showAvatars = remember { mutableStateOf(false) }

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .animateContentSize()
    ) {

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState = showAvatars.value,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150, 150)) togetherWith
                            fadeOut(animationSpec = tween(150)) using
                            SizeTransform { initialSize, targetSize ->
                                if (targetState) {
                                    keyframes {
                                        IntSize(targetSize.width, initialSize.height) at 150
                                        durationMillis = 300
                                    }
                                } else {
                                    keyframes {
                                        IntSize(initialSize.width, targetSize.height) at 150
                                        durationMillis = 300
                                    }
                                }
                            }
                }, label = "size transform"
            ) {
                when (it) {
                    true -> Column(
                        modifier = Modifier
                            .clickable {
                                if (data.authors.count() > 1) {
                                    showAvatars.value = !showAvatars.value
                                }
                            }
                    ) {
                        data.authors.take(8).forEachIndexed { idx, it ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(it.author.avatar?.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(
                                            when (data.kind) {
                                                RepeatableNotification.Like -> minSize + 8.dp
                                                RepeatableNotification.Repost -> minSize
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = CircleShape
                                        )
                                        .clip(CircleShape)
                                )

                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp),
                                    text = name(it.author),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    false -> Box(
                        modifier = Modifier
                            .clickable {
                                if (data.authors.count() > 1) {
                                    showAvatars.value = !showAvatars.value
                                }
                            }
                            .fillMaxWidth()
                    ) {
                        data.authors.take(8).forEachIndexed { idx, it ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(it.author.avatar?.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(
                                        when (data.kind) {
                                            RepeatableNotification.Like -> minSize + 8.dp
                                            RepeatableNotification.Repost -> minSize
                                        }
                                    )
                                    .offset(
                                        x = when (idx) {
                                            0 -> 0.dp
                                            else -> (idx * 16).dp
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Image(
                    imageVector = when (data.kind) {
                        RepeatableNotification.Like -> {
                            HeartFilled
                        }

                        RepeatableNotification.Repost -> {
                            Icons.Default.Repeat
                        }
                    },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    contentDescription = "${
                        when (data.kind) {
                            RepeatableNotification.Like -> "Like"
                            RepeatableNotification.Repost -> "Repost"
                        }
                    } icon",
                    modifier = Modifier
                        .size(minSize)
                )

                val authors = data.authors
                val firstAuthor = authors.first()
                val firstAuthorName = name(firstAuthor.author)
                val remainingCount = authors.size - 1
                val text = when {
                    remainingCount > 1 -> "$firstAuthorName and $remainingCount others ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"

                    remainingCount == 1 -> "$firstAuthorName and 1 other ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"

                    else -> "$firstAuthorName ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = HumanReadable.timeAgo(data.timestamp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = data.post.text,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}