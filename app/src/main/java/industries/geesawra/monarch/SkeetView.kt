@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import app.bsky.embed.ExternalViewExternal
import app.bsky.embed.ImagesViewImage
import app.bsky.embed.RecordView
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaView
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@Composable
fun SkeetView(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    viewModel: TimelineViewModel? = null,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    skeet: SkeetData,
    nested: Boolean = false,
    disableEmbeds: Boolean = false,
    inThread: Boolean = false,
) {
    if (skeet.blocked) {
        ConditionalCard("Blocked :(", wrapWithCard = !nested)
        return
    }

    if (skeet.notFound) {
        ConditionalCard("Post not found", wrapWithCard = !nested)
        return
    }

    val minSize = 55.dp

    val (parent, _) = skeet.parent()
    val hasParent = parent != null

    Surface(
        color = color,
        modifier = if (!inThread && !hasParent) {
            modifier.padding(start = 16.dp, end = 16.dp)
        } else {
            modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
        }.background(color)
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
                    verticalAlignment = Alignment.Top
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

                SkeetContent(skeet, nested, disableEmbeds)

                if (!nested && !disableEmbeds) {
                    TimelinePostActionsView(
                        onReplyTap = onReplyTap,
                        modifier = Modifier
                            .height(50.dp)
                            .fillMaxWidth(),
                        timelineViewModel = viewModel,
                        skeet = skeet,
                        inThread = inThread
                    )
                }
            }

        }
    }
}

@Composable
private fun SkeetContent(
    skeet: SkeetData,
    nested: Boolean = false,
    disableEmbeds: Boolean = false,
) {
    val context = LocalContext.current

    Text(
        text = skeet.content,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )

    if (skeet.embed == null || disableEmbeds) {
        return
    }

    Embeds(context, nested, skeet.embed)
}

@Composable
fun Embeds(context: Context, nested: Boolean, embed: PostViewEmbedUnion?) {
    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            ImageView(embed.value.images)
        }

        is PostViewEmbedUnion.VideoView -> {
            VideoView(embed.value.playlist.uri.toUri())
        }

        is PostViewEmbedUnion.ExternalView -> {
            ExternalView(context, embed.value.external)
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
                    embed.value
                )
            }
        }

        is PostViewEmbedUnion.RecordWithMediaView -> run {
            val media = embed.value.media
            val mediaValue = when (media) {
                is RecordWithMediaViewMediaUnion.ExternalView -> PostViewEmbedUnion.ExternalView(
                    media.value
                )

                is RecordWithMediaViewMediaUnion.ImagesView -> PostViewEmbedUnion.ImagesView(media.value)
                is RecordWithMediaViewMediaUnion.Unknown -> PostViewEmbedUnion.Unknown(media.value)
                is RecordWithMediaViewMediaUnion.VideoView -> PostViewEmbedUnion.VideoView(media.value)
            }

            Embeds(context, false, mediaValue)

            OutlinedCard(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                RecordWithMediaView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    embed.value
                )
            }
        }

        else -> {}
    }
}

@Composable
private fun ImageView(img: List<ImagesViewImage>) {
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

@Composable
fun VideoView(uri: Uri) {
    Card(
        modifier = Modifier
            .heightIn(max = 500.dp)
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        VideoPlayer(
            mediaItems = listOf(
                VideoPlayerMediaItem.NetworkMediaItem(
                    url = uri.toString(),
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

@Composable
private fun ExternalView(context: Context, ev: ExternalViewExternal) {
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
            }
            if (ev.title.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Text(
                    text = ev.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
                    maxLines = 3
                )
            }

            if (ev.description.isNotEmpty()) {
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
}

@Composable
private fun RecordView(
    modifier: Modifier = Modifier,
    rv: RecordView
) {
    val rv = rv.record
    when (rv) {
        is RecordViewRecordUnion.ViewRecord -> {
            SkeetView(
                modifier = modifier,
                viewModel = null,
                skeet = SkeetData.fromRecordView(rv.value),
                nested = true
            )
        }

        else -> {}
    }
}

@Composable
private fun RecordWithMediaView(
    modifier: Modifier = Modifier,
    rv: RecordWithMediaView
) {
    val rv = rv.record.record
    val record = when (rv) {
        is RecordViewRecordUnion.FeedGeneratorView -> null
        is RecordViewRecordUnion.GraphListView -> null
        is RecordViewRecordUnion.GraphStarterPackViewBasic -> null
        is RecordViewRecordUnion.LabelerLabelerView -> null
        is RecordViewRecordUnion.Unknown -> null
        is RecordViewRecordUnion.ViewDetached -> null
        is RecordViewRecordUnion.ViewBlocked -> SkeetData(blocked = true)
        is RecordViewRecordUnion.ViewNotFound -> SkeetData(notFound = true)
        is RecordViewRecordUnion.ViewRecord -> SkeetData.fromRecordView(rv.value)
    }

    record?.let {
        SkeetView(
            modifier = modifier,
            viewModel = null,
            skeet = record,
            nested = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkeetHeader(skeet: SkeetData, modifier: Modifier = Modifier) {
    val authorName = skeet.authorName ?: (skeet.authorHandle?.handle ?: "")

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
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            skeet.authorLabels.forEach {
                it.neg?.let { it ->
                    if (!it) {
                        return@forEach
                    }
                }
                if (it.`val`.startsWith("!")) {
                    return@forEach
                }

                Card(
                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                    shape = CircleShape
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        text = it.`val`,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }
            }
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

        skeet.createdAt?.let {
            Text(
                text = HumanReadable.timeAgo(it),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
