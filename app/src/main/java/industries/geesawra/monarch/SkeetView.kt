@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.atproto.label.Label
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
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
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
    renderingReplyNotif: Boolean = false,
    renderingMention: Boolean = false,
    onShowThread: (SkeetData) -> Unit = {},
    onAvatarTap: ((Did) -> Unit)? = null,
) {
    if (skeet.blocked) {
        ConditionalCard(text = "Blocked :(", wrapWithCard = !nested)
        return
    }

    if (skeet.notFound) {
        ConditionalCard(text = "Post not found", wrapWithCard = !nested)
        return
    }

    val warningLabel = skeet.postLabels.firstOrNull { it.`val` in contentWarningLabels }
    var contentRevealed by remember { mutableStateOf(warningLabel == null) }

    val minSize = 40.dp

    if (nested) {
        // Embedded posts: simple stacked layout
        Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    Log.d("SkeetView", skeet.content)
                    onShowThread(skeet)
                }
                .padding(top = 8.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
        ) {
            if (showInReplyTo) {
                SkeetReason(
                    modifier = Modifier.padding(start = 4.dp),
                    skeet = skeet,
                    showInReplyTo,
                    renderingReplyNotif,
                    renderingMention,
                    onAvatarTap = onAvatarTap,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(skeet.authorAvatarURL)
                        .crossfade(true)
                        .build(),
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(minSize)
                        .clip(avatarShape)
                        .then(
                            if (onAvatarTap != null && skeet.did != null)
                                Modifier.clickable { onAvatarTap(skeet.did!!) }
                            else Modifier
                        )
                )

                SkeetHeader(
                    modifier = Modifier.padding(start = 12.dp),
                    skeet = skeet,
                    showLabels = showLabels,
                    labelDisplayName = { viewModel?.labelDisplayName(it) },
                    labelDescription = { viewModel?.labelDescription(it) },
                    labelerAvatar = { viewModel?.labelerAvatar(it) }
                )
            }

            if (!contentRevealed && warningLabel != null) {
                ContentWarningCard(
                    label = labelDefinition(warningLabel.`val`).plaintext,
                    onShow = { contentRevealed = true },
                    wrapWithCard = false,
                )
            } else {
                SkeetContent(skeet, nested, disableEmbeds, onShowThread, viewModel, onMentionClick = onAvatarTap, postTextSize = postTextSize, avatarShape = avatarShape)
            }
        }
    } else {
        // Top-level posts: two-column layout, thread line spans full height
        Row(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    Log.d("SkeetView", skeet.content)
                    onShowThread(skeet)
                }
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = if (inThread) 0.dp else 8.dp)
                .height(IntrinsicSize.Min)
        ) {
            // Left column: avatar + thread line (spans full post height)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(minSize)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(skeet.authorAvatarURL)
                        .crossfade(true)
                        .build(),
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(minSize)
                        .clip(avatarShape)
                        .then(
                            if (onAvatarTap != null && skeet.did != null)
                                Modifier.clickable { onAvatarTap(skeet.did!!) }
                            else Modifier
                        )
                )
                if (inThread) {
                    VerticalDivider(
                        thickness = 3.dp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            // Right column: reason + header + content + actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .sizeIn(minHeight = minSize),
            ) {
                SkeetReason(
                    modifier = Modifier.padding(start = 4.dp),
                    skeet = skeet,
                    showInReplyTo,
                    renderingReplyNotif,
                    renderingMention,
                    onAvatarTap = onAvatarTap,
                )

                SkeetHeader(
                    skeet = skeet,
                    showLabels = showLabels,
                    labelDisplayName = { viewModel?.labelDisplayName(it) },
                    labelDescription = { viewModel?.labelDescription(it) },
                    labelerAvatar = { viewModel?.labelerAvatar(it) }
                )

                if (!contentRevealed && warningLabel != null) {
                    ContentWarningCard(
                        label = labelDefinition(warningLabel.`val`).plaintext,
                        onShow = { contentRevealed = true },
                        wrapWithCard = false,
                    )
                } else {
                    SkeetContent(skeet, nested, disableEmbeds, onShowThread, viewModel, onMentionClick = onAvatarTap, postTextSize = postTextSize, avatarShape = avatarShape)

                    if (!disableEmbeds) {
                        TimelinePostActionsView(
                            onReplyTap = onReplyTap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            timelineViewModel = viewModel,
                            skeet = skeet,
                            inThread = inThread
                        )
                    }
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
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    onMentionClick: ((Did) -> Unit)? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
) {
    val context = LocalContext.current

    if (skeet.content.isNotEmpty()) {
        val textStyle = when (postTextSize) {
            PostTextSize.Small -> MaterialTheme.typography.bodySmall
            PostTextSize.Medium -> MaterialTheme.typography.bodyMedium
            PostTextSize.Large -> MaterialTheme.typography.bodyLarge
        }
        Text(
            text = skeet.annotatedContent(onMentionClick = onMentionClick),
            color = MaterialTheme.colorScheme.onSurface,
            style = textStyle,
        )
    }

    if (skeet.embed == null || disableEmbeds) {
        return
    }

    Embeds(context, nested, skeet.embed, onShowThread, viewModel, postTextSize, avatarShape)
}

@Composable
fun Embeds(
    context: Context,
    nested: Boolean,
    embed: PostViewEmbedUnion?,
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
) {
    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            ImageView(embed.value.images)
        }

        is PostViewEmbedUnion.VideoView -> {
            val ar = embed.value.aspectRatio
            VideoView(
                embed.value.playlist.uri.toUri(),
                aspectRatio = if (ar != null && ar.height > 0) ar.width.toFloat() / ar.height.toFloat() else null
            )
        }

        is PostViewEmbedUnion.ExternalView -> {
            ExternalView(context, embed.value.external)
        }

        is PostViewEmbedUnion.RecordView -> run {
            if (nested) {
                return@run
            }

            OutlinedCard(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                RecordView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    embed.value,
                    onShowThread = onShowThread,
                    viewModel = viewModel,
                    postTextSize = postTextSize,
                    avatarShape = avatarShape,
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

            Embeds(context, false, mediaValue, onShowThread, viewModel, postTextSize, avatarShape)

            OutlinedCard(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                RecordWithMediaView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    embed.value,
                    onShowThread = onShowThread,
                    viewModel = viewModel,
                    postTextSize = postTextSize,
                    avatarShape = avatarShape,
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
                .fillMaxWidth(),
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
fun VideoView(uri: Uri, aspectRatio: Float? = null) {
    OutlinedCard(
        modifier = Modifier
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
            volume = 0.5f,
            repeatMode = RepeatMode.NONE,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (aspectRatio != null)
                        Modifier.aspectRatio(aspectRatio)
                    else
                        Modifier.heightIn(max = 500.dp)
                )
        )
    }
}

@Composable
private fun ExternalView(context: Context, ev: ExternalViewExternal) {
    val domain = runCatching { ev.uri.uri.toUri().host }.getOrNull() ?: ""

    OutlinedCard(
        modifier = Modifier
            .padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    contentDescription = "External link thumbnail",
                    modifier = Modifier
                        .height(180.dp)
                        .fillMaxWidth()
                )
            }

            if (ev.title.isNotEmpty()) {
                Text(
                    text = ev.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp),
                    maxLines = 3
                )
            }

            if (ev.description.isNotEmpty()) {
                Text(
                    text = ev.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 12.dp, end = 12.dp),
                    maxLines = 3
                )
            }

            if (domain.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
fun RecordView(
    modifier: Modifier = Modifier,
    rv: RecordView,
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
) {
    val rv = rv.record
    when (rv) {
        is RecordViewRecordUnion.ViewBlocked -> SkeetData(blocked = true)
        is RecordViewRecordUnion.ViewNotFound -> SkeetData(notFound = true)
        is RecordViewRecordUnion.ViewRecord -> {
            val s = SkeetData.fromRecordView(rv.value)
            SkeetView(
                viewModel = viewModel,
                skeet = s,
                nested = true,
                postTextSize = postTextSize,
                avatarShape = avatarShape,
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
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
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
            viewModel = viewModel,
            skeet = record,
            nested = true,
            postTextSize = postTextSize,
            avatarShape = avatarShape,
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
    onAvatarTap: ((Did) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (renderingMention) {
            Text(
                text = "Mentioned you",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
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
                    .fillMaxWidth()
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .then(
                                if (onAvatarTap != null)
                                    Modifier.clickable { onAvatarTap(it.value.by.did) }
                                else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        it.value.by.avatar?.let { avatarUri ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatarUri.uri)
                                    .crossfade(true)
                                    .build(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Reposted by ${it.value.by.displayName ?: it.value.by.handle.toString()}",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
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
                                .fillMaxWidth()
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

private val contentWarningLabels = setOf(
    "porn", "sexual", "nudity", "sexual-figurative",
    "graphic-media", "gore",
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SkeetHeader(modifier: Modifier = Modifier, skeet: SkeetData, showLabels: Boolean, labelDisplayName: (Label) -> String? = { null }, labelDescription: (Label) -> String? = { null }, labelerAvatar: (Label) -> String? = { null }) {
    val authorName = skeet.authorName ?: (skeet.authorHandle?.handle ?: "")

    val isBot = skeet.authorLabels.any { it.`val` == "bot" }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = authorName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skeet.verified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (isBot) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "Bot account",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            skeet.createdAt?.let {
                Text(
                    text = HumanReadable.timeAgo(it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Text(
            text = "@" + skeet.authorHandle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        if (skeet.threadgate != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Replies limited",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (showLabels) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            ) {
                skeet.authorLabels.forEach {
                    it.neg?.let { it ->
                        if (!it) {
                            return@forEach
                        }
                    }
                    if (it.`val`.startsWith("!") || it.`val` == "bot") {
                        return@forEach
                    }

                    val resolvedName = labelDisplayName(it)
                    val definition = if (resolvedName != null) {
                        LabelDefinition(plaintext = resolvedName, icon = Icons.Filled.Visibility)
                    } else {
                        labelDefinition(it.`val`)
                    }
                    val description = labelDescription(it)

                    val chipContent = @Composable {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val avatarUrl = labelerAvatar(it)
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    contentDescription = definition.plaintext,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = definition.icon,
                                    contentDescription = definition.plaintext,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = definition.plaintext,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    if (description != null) {
                        var showSheet by remember { mutableStateOf(false) }

                        if (showSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showSheet = false },
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        start = 24.dp, end = 24.dp,
                                        bottom = 32.dp
                                    )
                                ) {
                                    Text(
                                        text = definition.plaintext,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { showSheet = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = chipContent,
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = chipContent,
                        )
                    }
                }
            }
            }
        }

    }
}

@Composable
private fun ContentWarningCard(
    label: String,
    onShow: () -> Unit,
    wrapWithCard: Boolean = true,
) {
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShow) {
                Text("Show")
            }
        }
    }

    if (wrapWithCard) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            content()
        }
    } else {
        content()
    }
}