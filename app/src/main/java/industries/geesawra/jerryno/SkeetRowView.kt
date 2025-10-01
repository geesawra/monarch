package industries.geesawra.jerryno

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.jerryno.datalayer.SkeetData
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem

@Composable
fun SkeetRowView(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    skeet: SkeetData,
    nested: Boolean = false
) {
    val likes = skeet.likes
    val reposts = skeet.reposts
    val replies = skeet.replies
    val minSize = 55.dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.padding(start = 16.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = minSize),
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(skeet.authorAvatarURL)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(minSize)
                            .clip(CircleShape)
                    )

                    SkeetHeader(modifier = Modifier.padding(start = 16.dp), skeet = skeet)
                }

                SkeetContent(viewModel, skeet, nested)

                if (!nested) {
                    TimelinePostActionsView(
                        modifier = Modifier
                            .fillMaxWidth(),
                        timelineViewModel = viewModel,
                        replies = replies,
                        likes = likes,
                        reposts = reposts,
                        postUrl = skeet.shareURL(),
                        uri = skeet.uri,
                        cid = skeet.cid,
                        reposted = skeet.didRepost,
                        liked = skeet.didLike,
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

        }
    }
}

@Composable
private fun SkeetContent(
    timelineViewModel: TimelineViewModel,
    skeet: SkeetData,
    nested: Boolean = false
) {
    val context = LocalContext.current

    Text(
        text = skeet.content,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )

    if (skeet.embed == null) {
        return
    }

    val embed = skeet.embed

    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            val img = embed.value.images

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                PostImageGallery(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    images = img.map {
                        Image(url = it.thumb.uri, alt = it.alt)
                    },
                )
            }
        }

        is PostViewEmbedUnion.VideoView -> {
            Card(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                VideoPlayer(
                    mediaItems = listOf(
                        VideoPlayerMediaItem.NetworkMediaItem(
                            url = embed.value.playlist.uri,
                            mimeType = MimeTypes.APPLICATION_M3U8,
                        )
                    ),
                    handleLifecycle = false,
                    autoPlay = false,
                    usePlayerController = true,
                    enablePip = false,
                    handleAudioFocus = true,
                    controllerConfig = VideoPlayerControllerConfig(
                        showSpeedAndPitchOverlay = false,
                        showSubtitleButton = false,
                        showCurrentTimeAndTotalTime = true,
                        showBufferingProgress = false,
                        showForwardIncrementButton = true,
                        showBackwardIncrementButton = true,
                        showBackTrackButton = false,
                        showNextTrackButton = false,
                        showRepeatModeButton = true,
                        controllerShowTimeMilliSeconds = 5_000,
                        controllerAutoShow = true,
                        showFullScreenButton = true,
                    ),
                    volume = 0.5f,  // volume 0.0f to 1.0f
                    repeatMode = RepeatMode.NONE,       // or RepeatMode.ALL, RepeatMode.ONE
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }

        is PostViewEmbedUnion.ExternalView -> {
            val ev = embed.value.external

            OutlinedCard(
                modifier = Modifier
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            val customTabsIntent = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setUrlBarHidingEnabled(true)
                                .build()
                            customTabsIntent.launchUrl(context, ev.uri.uri.toUri())
                        }
                ) {
                    ev.thumb?.let {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(it.uri)
                                .crossfade(true)
                                .build(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            contentDescription = "External link thumbnail",
                            modifier = Modifier
                                .height(180.dp)
                                .fillMaxWidth()
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Text(
                        text = ev.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
                        maxLines = 3
                    )
                    Text(
                        text = ev.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                        maxLines = 8
                    )
                }

            }
        }

        is PostViewEmbedUnion.RecordView -> run {
            if (nested) {
                return@run
            }

            OutlinedCard(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                RecordView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    timelineViewModel,
                    embed.value
                )
            }
        }

        else -> {}
    }

}

@Composable
private fun RecordView(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    rv: RecordView
) {
    val rv = rv.record
    when (rv) {
        is RecordViewRecordUnion.ViewRecord -> {
            SkeetRowView(modifier, viewModel, SkeetData.fromRecordView(rv.value), nested = true)
        }

        else -> {}
    }
}

@Composable
private fun SkeetHeader(skeet: SkeetData, modifier: Modifier = Modifier) {
    val authorName = skeet.authorName ?: skeet.authorHandle.handle

    Column(modifier = modifier) {
        skeet.reason?.let {
            it
            when (it) {
                is FeedViewPostReasonUnion.ReasonRepost -> {
                    Text(
                        text = "Reposted by ${it.value.by.displayName ?: it.value.by.handle.toString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> {}
            }
        }

        Text(
            text = authorName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "@" + skeet.authorHandle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 4.dp),
        )

        skeet.authorLabels.forEach {
            it.neg?.let { it ->
                if (!it) {
                    return@forEach
                }
            }
            if (it.`val`.startsWith("!")) {
                return@forEach
            }

            FilterChip(
                leadingIcon = {
                },
                enabled = true,
                onClick = {},
                selected = true,
                label = {
                    Text(text = it.`val`)
                }
            )
        }

        skeet.reply?.let {
            it
            val parent = it.parent
            when (parent) {
                is ReplyRefParentUnion.PostView -> {
                    Text(
                        text = "In reply to ${parent.value.author.displayName ?: parent.value.author.handle.toString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp),
                    )
                }

                else -> {}
            }
        }
    }
}
