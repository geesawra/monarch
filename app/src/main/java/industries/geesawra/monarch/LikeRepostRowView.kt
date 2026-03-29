package industries.geesawra.monarch

import android.util.Log
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.RepeatableNotification
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.RepeatedNotification
import industries.geesawra.monarch.datalayer.SkeetData
import nl.jacobras.humanreadable.HumanReadable
import sh.christian.ozone.api.Did
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
    data: RepeatedNotification,
    settingsState: SettingsState = SettingsState(),
    onShowThread: (SkeetData) -> Unit = {},
    onProfileTap: ((Did) -> Unit)? = null,
) {
    val avatarSize = 28.dp
    val showAvatars = remember { mutableStateOf(false) }
    val avatarClipShape = if (settingsState.avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(8.dp) else CircleShape

    Column(
        modifier = modifier
            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .animateContentSize()
    ) {

        val authors = data.authors
        val firstAuthor = authors.first()
        val firstAuthorName = name(firstAuthor.author)
        val remainingCount = authors.size - 1
        val actionVerb = when (data.kind) {
            RepeatableNotification.Like -> "liked"
            RepeatableNotification.Repost -> "reposted"
        }
        val reasonLine = when {
            remainingCount > 1 -> "$firstAuthorName and $remainingCount others $actionVerb this"
            remainingCount == 1 -> "$firstAuthorName and 1 other $actionVerb this"
            else -> "$firstAuthorName $actionVerb this"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Image(
                imageVector = when (data.kind) {
                    RepeatableNotification.Like -> HeartFilled
                    RepeatableNotification.Repost -> Icons.Default.Repeat
                },
                colorFilter = ColorFilter.tint(
                    when (data.kind) {
                        RepeatableNotification.Like -> MaterialTheme.colorScheme.error
                        RepeatableNotification.Repost -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                contentDescription = "${
                    when (data.kind) {
                        RepeatableNotification.Like -> "Like"
                        RepeatableNotification.Repost -> "Repost"
                    }
                } icon",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = reasonLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = HumanReadable.timeAgo(data.timestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }

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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            showAvatars.value = !showAvatars.value
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Close avatar list",
                        )
                    }
                    data.authors.take(8).forEach {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProfileTap?.invoke(it.author.did)
                                },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(it.author.avatar?.uri)
                                    .crossfade(true)
                                    .build(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(avatarSize + 4.dp)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = avatarClipShape
                                    )
                                    .clip(avatarClipShape)
                            )
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
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
                        .padding(bottom = 4.dp)
                ) {
                    data.authors.take(8).reversed().forEachIndexed { idx, it ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(it.author.avatar?.uri)
                                .crossfade(true)
                                .build(),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(avatarSize)
                                .offset(
                                    x = when (idx) {
                                        0 -> 0.dp
                                        else -> (idx * 18).dp
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = avatarClipShape
                                )
                                .clip(avatarClipShape)
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.padding(start = 28.dp)
        ) {
            SkeetView(
                modifier = Modifier.padding(bottom = 8.dp),
                viewModel = null,
                skeet = data.post,
                nested = true,
                showLabels = settingsState.showLabels,
                postTextSize = settingsState.postTextSize,
                avatarShape = avatarClipShape,
                onShowThread = onShowThread,
            )
        }
    }
}