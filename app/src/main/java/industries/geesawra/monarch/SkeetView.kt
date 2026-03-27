@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.atproto.label.Label
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
    viewModel: TimelineViewModel? = null,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    skeet: SkeetData,
    nested: Boolean = false,
    disableEmbeds: Boolean = false,
    inThread: Boolean = false,
    showInReplyTo: Boolean = true,
    showLabels: Boolean = true,
    renderingReplyNotif: Boolean = false,
    renderingMention: Boolean = false,
    onShowThread: (SkeetData) -> Unit = {},
) {
    if (skeet.blocked) {
        ConditionalCard(text = "Blocked :(", wrapWithCard = !nested)
        return
    }

    if (skeet.notFound) {
        ConditionalCard(text = "Post not found", wrapWithCard = !nested)
        return
    }

    val minSize = 55.dp

    Surface(
        color = Color.Transparent,
        modifier =
            modifier
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                .background(Color.Transparent)
                .clickable {
                    Log.d("SkeetView", skeet.content)
                    onShowThread(skeet)
                }
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

                SkeetReason(
                    modifier = Modifier.padding(start = 4.dp),
                    skeet = skeet,
                    showInReplyTo,
                    renderingReplyNotif,
                    renderingMention
                )

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

                    SkeetHeader(
                        modifier = Modifier.padding(start = 16.dp),
                        skeet = skeet,
                        showLabels = showLabels,
                        labelDisplayName = { viewModel?.labelDisplayName(it) }
                    )
                }

                SkeetContent(skeet, nested, disableEmbeds, onShowThread)

                if (!nested && !disableEmbeds) {
                    TimelinePostActionsView(
                        onReplyTap = onReplyTap,
                        modifier = Modifier
                            .height(48.dp)
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
    onShowThread: (SkeetData) -> Unit
) {
    val context = LocalContext.current

    if (skeet.content.isNotEmpty()) {
        Text(
            text = skeet.annotatedContent(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    if (skeet.embed == null || disableEmbeds) {
        return
    }

    Embeds(context, nested, skeet.embed, onShowThread)
}

@Composable
fun Embeds(
    context: Context,
    nested: Boolean,
    embed: PostViewEmbedUnion?,
    onShowThread: (SkeetData) -> Unit
) {
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
                    embed.value,
                    onShowThread = onShowThread,
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

            Embeds(context, false, mediaValue, onShowThread)

            OutlinedCard(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                RecordWithMediaView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    embed.value,
                    onShowThread = onShowThread,
                )
            }
        }

        else -> {}
    }
}

@Composable
private fun ImageView(img: List<ImagesViewImage>) {
    OutlinedCard(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        PostImageGallery(
            modifier = Modifier
                .fillMaxSize(),
            images = img.map {
                Image(
                    url = it.thumb.uri,
                    alt = it.alt,
                    fullSize = it.fullsize.uri,
                    width = it.aspectRatio?.width,
                    height = it.aspectRatio?.height
                )
            },
        )
    }
}

@Composable
fun VideoView(uri: Uri) {
    OutlinedCard(
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
fun RecordView(
    modifier: Modifier = Modifier,
    rv: RecordView,
    onShowThread: (SkeetData) -> Unit
) {
    val rv = rv.record
    when (rv) {
        is RecordViewRecordUnion.ViewBlocked -> SkeetData(blocked = true)
        is RecordViewRecordUnion.ViewNotFound -> SkeetData(notFound = true)
        is RecordViewRecordUnion.ViewRecord -> {
            val s = SkeetData.fromRecordView(rv.value)
            SkeetView(
                viewModel = null,
                skeet = s,
                nested = true,
                onShowThread = onShowThread
            )
        }

        else -> {}
    }
}

@Composable
private fun RecordWithMediaView(
    modifier: Modifier = Modifier,
    rv: RecordWithMediaView,
    onShowThread: (SkeetData) -> Unit
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
            viewModel = null,
            skeet = record,
            nested = true,
            onShowThread = onShowThread
        )
    }
}

@Composable
private fun SkeetReason(
    modifier: Modifier = Modifier,
    skeet: SkeetData,
    showInReplyTo: Boolean = true,
    renderingReplyNotif: Boolean = false,
    renderingMention: Boolean = false,
) {
    Column(modifier = modifier) {
        if (renderingMention) {
            Text(
                text = "Mentioned you",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 4.dp),
                fontWeight = FontWeight.Bold
            )
            return@Column
        }
        if (renderingReplyNotif) {
            Text(
                text = "Replied to you",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 4.dp),
                fontWeight = FontWeight.Bold
            )
            return@Column
        }

        var isRepost = false
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
                    isRepost = true
                }

                else -> {}
            }
        }

        if (!isRepost && showInReplyTo) {
            skeet.reply?.let {
                it
                val parent = it.parent
                when (parent) {
                    is ReplyRefParentUnion.PostView -> {
                        Text(
                            text = "In reply to ${parent.value.author.displayName ?: parent.value.author.handle.toString()}",
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
        }
    }
}

private data class LabelDefinition(
    val plaintext: String,
    val icon: ImageVector,
)

private val knownLabels: Map<String, LabelDefinition> = mapOf(
    "porn" to LabelDefinition("Adult Content", Icons.Filled.VisibilityOff),
    "sexual" to LabelDefinition("Sexually Suggestive", Icons.Filled.VisibilityOff),
    "nudity" to LabelDefinition("Nudity", Icons.Filled.VisibilityOff),
    "sexual-figurative" to LabelDefinition("Figurative Nudity", Icons.Filled.VisibilityOff),
    "graphic-media" to LabelDefinition("Graphic Media", Icons.Filled.Warning),
    "gore" to LabelDefinition("Gore", Icons.Filled.Warning),
    "impersonation" to LabelDefinition("Impersonation", Icons.Filled.Person),
    "spam" to LabelDefinition("Spam", Icons.Filled.Report),
    "scam" to LabelDefinition("Scam", Icons.Filled.Report),
    "intolerance" to LabelDefinition("Intolerance", Icons.Filled.Block),
    "icon-intolerance" to LabelDefinition("Intolerant Imagery", Icons.Filled.Block),
    "misleading" to LabelDefinition("Misleading", Icons.Filled.Warning),
    "threat" to LabelDefinition("Threatening", Icons.Filled.Warning),
    "rude" to LabelDefinition("Rude", Icons.Filled.Warning),
    "violation" to LabelDefinition("Community Violation", Icons.Filled.Report),
    "dmca-violation" to LabelDefinition("DMCA Violation", Icons.Filled.Shield),
    "doxxing" to LabelDefinition("Doxxing", Icons.Filled.Report),
)

private fun labelDefinition(rawValue: String): LabelDefinition {
    return knownLabels[rawValue] ?: LabelDefinition(
        plaintext = rawValue.replace("-", " ")
            .replaceFirstChar { it.uppercaseChar() },
        icon = Icons.Filled.Visibility,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkeetHeader(modifier: Modifier = Modifier, skeet: SkeetData, showLabels: Boolean, labelDisplayName: (Label) -> String? = { null }) {
    val authorName = skeet.authorName ?: (skeet.authorHandle?.handle ?: "")

    Column(modifier = modifier) {
        Text(
            text = authorName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "@" + skeet.authorHandle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        if (showLabels) {
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

                    val resolvedName = labelDisplayName(it)
                    val definition = if (resolvedName != null) {
                        LabelDefinition(plaintext = resolvedName, icon = Icons.Filled.Visibility)
                    } else {
                        labelDefinition(it.`val`)
                    }
                    OutlinedCard(
                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = definition.icon,
                                contentDescription = definition.plaintext,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = definition.plaintext,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
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