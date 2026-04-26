@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.atproto.label.Label
import industries.geesawra.monarch.datalayer.LinkPreviewFetcher
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import industries.geesawra.monarch.datalayer.toFloat
import sh.christian.ozone.api.Did
import nl.jacobras.humanreadable.HumanReadable
import industries.geesawra.monarch.datalayer.PostInteraction
import industries.geesawra.monarch.datalayer.TranslationPhase
import industries.geesawra.monarch.datalayer.languageCodeToName
import industries.geesawra.monarch.datalayer.TRANSLATION_LANGUAGE_OPTIONS
import androidx.compose.material3.CircularProgressIndicator
import java.time.Instant as JavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    showConnectorDown: Boolean = false,
    showInReplyTo: Boolean = true,
    showLabels: Boolean = true,
    showPronouns: Boolean = false,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
    renderingReplyNotif: Boolean = false,
    renderingMention: Boolean = false,
    onShowThread: (SkeetData) -> Unit = {},
    onAvatarTap: ((Did) -> Unit)? = null,
    isVisible: Boolean = true,
    overrideAvatarSize: Dp? = null,
    translationEnabled: Boolean = true,
    targetTranslationLanguage: String = "en",
    carouselImageGallery: Boolean = false,
) {
    if (skeet.blocked) {
        SkeetBlockedPost(nested)
        return
    }

    if (skeet.notFound) {
        SkeetNotFoundPost(nested)
        return
    }

    val warningLabel = skeet.postLabels.firstOrNull { it.`val` in contentWarningLabels }
    var contentRevealed by remember { mutableStateOf(warningLabel == null) }

    if (nested) {
        Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable { onShowThread(skeet) }
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

            SkeetHeaderSection(
                skeet = skeet,
                avatarShape = avatarShape,
                showLabels = showLabels,
                viewModel = viewModel,
                onAvatarTap = onAvatarTap,
            )

            SkeetContentSection(
                skeet = skeet,
                nested = nested,
                disableEmbeds = disableEmbeds,
                warningLabel = warningLabel,
                contentRevealed = contentRevealed,
                onToggleContent = { contentRevealed = !contentRevealed },
                showActions = false,
                onShowThread = onShowThread,
                viewModel = viewModel,
                onMentionClick = onAvatarTap,
                postTextSize = postTextSize,
                avatarShape = avatarShape,
                isVisible = isVisible,
                showLabels = showLabels,
                translationEnabled = translationEnabled,
                targetTranslationLanguage = targetTranslationLanguage,
                carouselImageGallery = carouselImageGallery,
            )
        }
    } else {
        val minSize = overrideAvatarSize ?: avatarSize()
        Row(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable { onShowThread(skeet) }
                .padding(top = 8.dp, start = postHorizontalPadding(), end = postHorizontalPadding(), bottom = if (inThread) 0.dp else 8.dp)
                .then(if (inThread) Modifier.height(IntrinsicSize.Min) else Modifier)
        ) {
            SkeetThreadLine(
                skeet = skeet,
                avatarShape = avatarShape,
                inThread = inThread,
                showConnectorDown = showConnectorDown,
                onAvatarTap = onAvatarTap,
                overrideAvatarSize = overrideAvatarSize,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = avatarTextGap())
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
                    showPronouns = showPronouns,
                    labelDisplayName = { viewModel?.labelDisplayName(it) },
                    labelDescription = { viewModel?.labelDescription(it) },
                    labelerAvatar = { viewModel?.labelerAvatar(it) }
                )

                SkeetContentSection(
                    skeet = skeet,
                    nested = nested,
                    disableEmbeds = disableEmbeds,
                    warningLabel = warningLabel,
                    contentRevealed = contentRevealed,
                    onToggleContent = { contentRevealed = !contentRevealed },
                    showActions = !disableEmbeds,
                    onReplyTap = onReplyTap,
                    inThread = inThread,
                    onShowThread = onShowThread,
                    viewModel = viewModel,
                    onMentionClick = onAvatarTap,
                    postTextSize = postTextSize,
                    avatarShape = avatarShape,
                    isVisible = isVisible,
                    showLabels = showLabels,
                    translationEnabled = translationEnabled,
                    targetTranslationLanguage = targetTranslationLanguage,
                    carouselImageGallery = carouselImageGallery,
                )
            }
        }
    }
}

@Composable
fun EngagementStatsRow(
    likes: Long,
    reposts: Long,
    quotes: Long,
    onShowLikes: () -> Unit,
    onShowReposts: () -> Unit,
    onShowQuotes: () -> Unit,
) {
    val stats = buildList {
        if (likes > 0) add(Triple(likes, "like", onShowLikes))
        if (reposts > 0) add(Triple(reposts, "repost", onShowReposts))
        if (quotes > 0) add(Triple(quotes, "quote", onShowQuotes))
    }

    if (stats.isEmpty()) return

    Column {
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            stats.forEach { (count, label, onClick) ->
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "$count ${if (count == 1L) label else "${label}s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusedSkeetView(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel? = null,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    skeet: SkeetData,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
    showLabels: Boolean = true,
    showPronouns: Boolean = false,
    onShowThread: (SkeetData) -> Unit = {},
    onAvatarTap: ((Did) -> Unit)? = null,
    isVisible: Boolean = true,
    translationEnabled: Boolean = true,
    targetTranslationLanguage: String = "en",
    carouselImageGallery: Boolean = false,
    onShowLikes: () -> Unit = {},
    onShowReposts: () -> Unit = {},
    onShowQuotes: () -> Unit = {},
    alsoLikedEnabled: Boolean = false,
    onShowAlsoLiked: () -> Unit = {},
) {
    val warningLabel = skeet.postLabels.firstOrNull { it.`val` in contentWarningLabels }
    var contentRevealed by remember { mutableStateOf(warningLabel == null) }
    var quoteCount by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(skeet.uri) {
        viewModel?.getQuotes(skeet.uri)?.getOrNull()?.let {
            quoteCount = it.posts.size.toLong()
        }
    }

    val isBot = skeet.authorLabels.any { it.`val` == "bot" }
    val displayName = skeet.authorName?.ifEmpty { null } ?: skeet.authorHandle?.handle.orEmpty()

    Column(
        modifier = modifier
            .padding(top = 8.dp, start = postHorizontalPadding(), end = postHorizontalPadding(), bottom = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                url = skeet.authorAvatarURL,
                size = avatarSize() + 8.dp,
                shape = avatarShape,
                did = skeet.did,
                onTap = onAvatarTap,
            )
            Spacer(Modifier.width(avatarTextGap()))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (skeet.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (isBot) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = "Bot account",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    skeet.authorHandle?.let {
                        Text(
                            text = "@${it.handle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showPronouns && !skeet.authorPronouns.isNullOrBlank()) {
                        Text(
                            text = skeet.authorPronouns,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        SkeetLabelsRow(
            skeet = skeet,
            showLabels = showLabels,
            labelDisplayName = { viewModel?.labelDisplayName(it) },
            labelDescription = { viewModel?.labelDescription(it) },
            labelerAvatar = { viewModel?.labelerAvatar(it) },
        )

        SkeetContentSection(
            skeet = skeet,
            nested = false,
            disableEmbeds = false,
            warningLabel = warningLabel,
            contentRevealed = contentRevealed,
            onToggleContent = { contentRevealed = !contentRevealed },
            showActions = false,
            onShowThread = onShowThread,
            viewModel = viewModel,
            onMentionClick = onAvatarTap,
            postTextSize = postTextSize,
            avatarShape = avatarShape,
            isVisible = isVisible,
            showLabels = showLabels,
            translationEnabled = translationEnabled,
            targetTranslationLanguage = targetTranslationLanguage,
            carouselImageGallery = carouselImageGallery,
        )

        skeet.createdAt?.let { instant ->
            val formatter = remember {
                DateTimeFormatter.ofPattern("HH:mm · d MMM yyyy", Locale.getDefault())
            }
            val zoned = remember(instant) {
                JavaInstant
                    .ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong())
                    .atZone(ZoneId.systemDefault())
            }
            Text(
                text = formatter.format(zoned),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        val interactionState = viewModel?.postInteractionStore?.getState(skeet.cid) {
            PostInteraction.from(skeet)
        }
        val interaction = interactionState?.value
        val likesCount = interaction?.likes ?: skeet.likes ?: 0L
        val repostsCount = interaction?.reposts ?: skeet.reposts ?: 0L
        val quotesCount = quoteCount ?: 0L
        val hasEngagementStats = likesCount > 0 || repostsCount > 0 || quotesCount > 0

        EngagementStatsRow(
            likes = likesCount,
            reposts = repostsCount,
            quotes = quotesCount,
            onShowLikes = onShowLikes,
            onShowReposts = onShowReposts,
            onShowQuotes = onShowQuotes,
        )

        TimelinePostActionsView(
            onReplyTap = onReplyTap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            timelineViewModel = viewModel,
            skeet = skeet,
            inThread = true,
            translationEnabled = translationEnabled,
            targetTranslationLanguage = targetTranslationLanguage,
            showCounts = !hasEngagementStats,
            detailMode = true,
            alsoLikedEnabled = alsoLikedEnabled,
            onAlsoLikedTap = onShowAlsoLiked,
        )
    }
}

@Composable
private fun SkeetBlockedPost(nested: Boolean) {
    ConditionalCard(text = "Blocked :(", wrapWithCard = !nested)
}

@Composable
private fun SkeetNotFoundPost(nested: Boolean) {
    ConditionalCard(text = "Post not found", wrapWithCard = !nested)
}

@Composable
private fun SkeetHeaderSection(
    skeet: SkeetData,
    avatarShape: Shape,
    showLabels: Boolean,
    showPronouns: Boolean = false,
    viewModel: TimelineViewModel?,
    onAvatarTap: ((Did) -> Unit)?,
) {
    val minSize = avatarSize()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarImage(skeet.authorAvatarURL, minSize, avatarShape, skeet.did, onAvatarTap)

        SkeetHeader(
            modifier = Modifier.padding(start = avatarTextGap()),
            skeet = skeet,
            showLabels = showLabels,
            showPronouns = showPronouns,
            labelDisplayName = { viewModel?.labelDisplayName(it) },
            labelDescription = { viewModel?.labelDescription(it) },
            labelerAvatar = { viewModel?.labelerAvatar(it) }
        )
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    size: Dp,
    shape: Shape,
    did: Did?,
    onTap: ((Did) -> Unit)?,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        contentDescription = "Avatar",
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(
                if (onTap != null && did != null)
                    Modifier.clickable { onTap(did) }
                else Modifier
            )
    )
}

@Composable
private fun SkeetThreadLine(
    skeet: SkeetData,
    avatarShape: Shape,
    inThread: Boolean,
    showConnectorDown: Boolean,
    onAvatarTap: ((Did) -> Unit)?,
    overrideAvatarSize: Dp? = null,
) {
    val minSize = overrideAvatarSize ?: avatarSize()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(minSize)
            .fillMaxHeight()
    ) {
        AvatarImage(skeet.authorAvatarURL, minSize, avatarShape, skeet.did, onAvatarTap)
        if (inThread && showConnectorDown) {
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
}

@Composable
private fun SkeetContentSection(
    skeet: SkeetData,
    nested: Boolean,
    disableEmbeds: Boolean,
    warningLabel: Label?,
    contentRevealed: Boolean,
    onToggleContent: () -> Unit,
    showActions: Boolean,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    inThread: Boolean = false,
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel?,
    onMentionClick: ((Did) -> Unit)?,
    postTextSize: PostTextSize,
    avatarShape: Shape,
    isVisible: Boolean,
    showLabels: Boolean,
    translationEnabled: Boolean = true,
    targetTranslationLanguage: String = "en",
    carouselImageGallery: Boolean = false,
) {
    if (warningLabel != null) {
        ContentWarningCard(
            label = labelDefinition(warningLabel.`val`).plaintext,
            revealed = contentRevealed,
            onToggle = onToggleContent,
            wrapWithCard = false,
        )
    }
    if (contentRevealed) {
        SkeetContent(skeet, nested, disableEmbeds, onShowThread, viewModel, onMentionClick = onMentionClick, postTextSize = postTextSize, avatarShape = avatarShape, isVisible = isVisible, showLabels = showLabels, targetTranslationLanguage = targetTranslationLanguage, carouselImageGallery = carouselImageGallery)

        if (showActions) {
            TimelinePostActionsView(
                onReplyTap = onReplyTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                timelineViewModel = viewModel,
                skeet = skeet,
                inThread = inThread,
                translationEnabled = translationEnabled,
                targetTranslationLanguage = targetTranslationLanguage,
            )
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
    isVisible: Boolean = true,
    showLabels: Boolean = true,
    targetTranslationLanguage: String = "en",
    carouselImageGallery: Boolean = false,
) {
    val context = LocalContext.current
    val translation = viewModel?.postTranslationStore?.states?.get(skeet.cid)

    if (skeet.content.isNotEmpty()) {
        val textStyle = when (postTextSize) {
            PostTextSize.Small -> MaterialTheme.typography.bodySmall
            PostTextSize.Medium -> MaterialTheme.typography.bodyMedium
            PostTextSize.Large -> MaterialTheme.typography.bodyLarge
        }
        val uriHandler = if (LocalBaselineProfileMode.current) NoOpUriHandler else LocalUriHandler.current
        val primaryColor = MaterialTheme.colorScheme.primary

        val translatedText = translation?.translatedText
        val showTranslated = translatedText != null && !translation.showOriginal && !translation.isTranslating

        Column {
            if (showTranslated) {
                Text(
                    text = translatedText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = textStyle,
                )
            } else {
                val annotated = remember(skeet.cid, primaryColor, onMentionClick) {
                    skeet.buildAnnotated(primaryColor, onMentionClick)
                }
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    Text(
                        text = annotated,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = textStyle,
                    )
                }
            }

            when {
                translation?.isTranslating == true -> {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = when (translation.phase) {
                                TranslationPhase.DetectingLanguage -> "Detecting language..."
                                TranslationPhase.DownloadingModel -> "Downloading model..."
                                TranslationPhase.Translating -> "Translating..."
                                TranslationPhase.Idle -> "Translating..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                translation?.error != null -> {
                    Text(
                        text = translation.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                showTranslated -> {
                    TranslationMetadata(
                        detectedLanguage = translation.detectedLanguage,
                        onShowOriginal = { viewModel.toggleTranslationOriginal(skeet.cid) },
                        onWrongLanguage = { newSourceLang ->
                            viewModel.retranslatePost(skeet, targetTranslationLanguage, newSourceLang)
                        },
                    )
                }
                translation?.showOriginal == true -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { viewModel.toggleTranslationOriginal(skeet.cid) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Show translation",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (skeet.embed == null || disableEmbeds) {
        return
    }

    Embeds(context, nested, skeet.embed, onShowThread, viewModel, postTextSize, avatarShape, isVisible = isVisible, showLabels = showLabels, following = skeet.following, carouselImageGallery = carouselImageGallery)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationMetadata(
    detectedLanguage: String?,
    onShowOriginal: () -> Unit,
    onWrongLanguage: (String) -> Unit,
) {
    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        if (detectedLanguage != null) {
            Text(
                text = "Translated from ${languageCodeToName(detectedLanguage)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onShowOriginal,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Show original",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(
                onClick = { showLanguagePicker = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Wrong language?",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    if (showLanguagePicker) {
        ModalBottomSheet(
            onDismissRequest = { showLanguagePicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "Select source language",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                TRANSLATION_LANGUAGE_OPTIONS.forEach { (label, code) ->
                    TextButton(
                        onClick = {
                            showLanguagePicker = false
                            onWrongLanguage(code)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
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
    isVisible: Boolean = true,
    showLabels: Boolean = true,
    following: Boolean = false,
    carouselImageGallery: Boolean = false,
) {
    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            ImageView(embed.value.images, carouselImageGallery = carouselImageGallery)
        }

        is PostViewEmbedUnion.VideoView -> {
            val aspectRatio = embed.value.aspectRatio.toFloat()
            VideoView(
                embed.value.playlist.uri.toUri(),
                thumbnailUri = embed.value.thumbnail?.uri,
                aspectRatio = aspectRatio,
                isVisible = isVisible,
            )
        }

        is PostViewEmbedUnion.ExternalView -> {
            ExternalView(context, embed.value.external, isVisible = isVisible)
        }

        is PostViewEmbedUnion.RecordView -> run {
            if (nested) {
                return@run
            }

            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            ) {
                RecordView(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    embed.value,
                    onShowThread = onShowThread,
                    viewModel = viewModel,
                    postTextSize = postTextSize,
                    avatarShape = avatarShape,
                    showLabels = showLabels,
                    following = following,
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

            Embeds(context, false, mediaValue, onShowThread, viewModel, postTextSize, avatarShape, isVisible = isVisible, showLabels = showLabels, following = following, carouselImageGallery = carouselImageGallery)

            if (!nested) {
                Column(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    RecordWithMediaView(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        embed.value,
                        onShowThread = onShowThread,
                        viewModel = viewModel,
                        postTextSize = postTextSize,
                        avatarShape = avatarShape,
                        showLabels = showLabels,
                        following = following,
                    )
                }
            }
        }

        else -> {}
    }
}

@Composable
private fun ImageView(img: List<ImagesViewImage>, carouselImageGallery: Boolean = false) {
    val images = img.map {
        Image(
            url = it.thumb.uri,
            alt = it.alt,
            fullSize = it.fullsize.uri,
            width = it.aspectRatio?.width,
            height = it.aspectRatio?.height
        )
    }
    if (carouselImageGallery) {
        PostImageCarousel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            images = images,
        )
    } else {
        Column(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        ) {
            PostImageGallery(
                modifier = Modifier.fillMaxWidth(),
                images = images,
            )
        }
    }
}

@Composable
fun VideoView(uri: Uri, thumbnailUri: String? = null, aspectRatio: Float? = null, isVisible: Boolean = true) {
    var playing by remember { mutableStateOf(false) }

    val sizeModifier = if (aspectRatio != null)
        Modifier.aspectRatio(aspectRatio)
    else
        Modifier.heightIn(max = 500.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        if (playing) {
            val activeVideoKey = LocalActiveVideoKey.current
            val id = remember { java.util.UUID.randomUUID().toString() }
            LaunchedEffect(isVisible) {
                if (isVisible) activeVideoKey?.value = id
            }
            val isActive = if (activeVideoKey != null) isVisible && activeVideoKey.value == id else isVisible

            VideoPlayer(
                url = uri.toString(),
                isVisible = isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sizeModifier),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sizeModifier)
                    .clickable { playing = true },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            CircleShape
                        )
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun isTenorUrl(uri: String): Boolean {
    val host = runCatching { uri.toUri().host?.lowercase() }.getOrNull() ?: return false
    return host == "tenor.com" || host.endsWith(".tenor.com")
}

private fun tenorGifToWebm(gifUrl: String): String? {
    val regex = Regex("""https://media\d*\.tenor\.com/([^/]+?)AAAAC/(.+)\.gif""")
    val match = regex.find(gifUrl.split("?").first()) ?: return null
    val id = match.groupValues[1]
    val name = match.groupValues[2]
    return "https://t.gifs.bsky.app/${id}AAAP3/${name}.webm"
}

@Composable
private fun TenorGifView(context: Context, ev: ExternalViewExternal, isVisible: Boolean = true) {
    val activeVideoKey = LocalActiveVideoKey.current
    val id = remember { java.util.UUID.randomUUID().toString() }
    LaunchedEffect(isVisible) {
        if (isVisible) activeVideoKey?.value = id
    }
    val isActive = if (activeVideoKey != null) isVisible && activeVideoKey.value == id else isVisible

    val uri = ev.uri.uri
    val parsedUri = uri.toUri()
    val webmUrl = tenorGifToWebm(uri)
    val ww = parsedUri.getQueryParameter("ww")?.toFloatOrNull()
    val hh = parsedUri.getQueryParameter("hh")?.toFloatOrNull()
    val aspectRatio = if (ww != null && hh != null && hh > 0) ww / hh else null

    if (webmUrl != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (aspectRatio != null) Modifier.aspectRatio(aspectRatio)
                    else Modifier.heightIn(max = 300.dp)
                )
        ) {
            GifViewer(
                url = webmUrl,
                isVisible = isActive,
                modifier = Modifier.matchParentSize(),
            )
        }
    } else {
        ev.thumb?.let {
            val request = remember(it.uri) {
                ImageRequest.Builder(context)
                    .data(it.uri)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Fit,
                contentDescription = ev.title.ifEmpty { "GIF" },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(top = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        }
    }
}

@Composable
private fun ExternalView(context: Context, ev: ExternalViewExternal, isVisible: Boolean = true) {
    if (isTenorUrl(ev.uri.uri)) {
        TenorGifView(context, ev, isVisible = isVisible)
        return
    }

    val needsFetch = ev.title.isEmpty() && ev.description.isEmpty() && ev.thumb == null
    var enrichedTitle by remember(ev.uri.uri) { mutableStateOf(ev.title) }
    var enrichedDescription by remember(ev.uri.uri) { mutableStateOf(ev.description) }
    var enrichedThumb by remember(ev.uri.uri) { mutableStateOf(ev.thumb?.uri) }

    if (needsFetch && isVisible) {
        LaunchedEffect(ev.uri.uri) {
            LinkPreviewFetcher.fetch(ev.uri.uri)?.let { preview ->
                enrichedTitle = preview.title ?: ""
                enrichedDescription = preview.description ?: ""
                enrichedThumb = preview.imageUrl
            }
        }
    }

    val domain = runCatching { ev.uri.uri.toUri().host }.getOrNull() ?: ""

    Column(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (LocalBaselineProfileMode.current) Modifier else Modifier.clickable {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .setUrlBarHidingEnabled(true)
                        .build()
                    customTabsIntent.launchUrl(context, ev.uri.uri.toUri())
                })
        ) {
            val thumbUrl = if (needsFetch) enrichedThumb else ev.thumb?.uri
            if (thumbUrl != null) {
                val thumbRequest = remember(thumbUrl) {
                    ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = thumbRequest,
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

            val title = if (needsFetch) enrichedTitle else ev.title
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp),
                    maxLines = 3
                )
            }

            val description = if (needsFetch) enrichedDescription else ev.description
            if (description.isNotEmpty()) {
                Text(
                    text = description,
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun RecordView(
    modifier: Modifier = Modifier,
    rv: RecordView,
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
    showLabels: Boolean = true,
    following: Boolean = false,
) {
    val rv = rv.record
    when (rv) {
        is RecordViewRecordUnion.ViewBlocked -> SkeetView(
            viewModel = viewModel,
            skeet = SkeetData(blocked = true),
            nested = true,
            postTextSize = postTextSize,
            avatarShape = avatarShape,
            showLabels = showLabels,
            onShowThread = onShowThread
        )
        is RecordViewRecordUnion.ViewNotFound -> SkeetView(
            viewModel = viewModel,
            skeet = SkeetData(notFound = true),
            nested = true,
            postTextSize = postTextSize,
            avatarShape = avatarShape,
            showLabels = showLabels,
            onShowThread = onShowThread
        )
        is RecordViewRecordUnion.ViewRecord -> {
            val author = rv.value.author
            val isBlockedEmbed = following && (
                author.viewer?.blockedBy == true || author.viewer?.blocking != null
            )
            val s = if (isBlockedEmbed) {
                SkeetData(blocked = true)
            } else {
                SkeetData.fromRecordView(rv.value)
            }
            SkeetView(
                viewModel = viewModel,
                skeet = s,
                nested = true,
                postTextSize = postTextSize,
                avatarShape = avatarShape,
                showLabels = showLabels,
                onShowThread = onShowThread
            )
        }

        else -> {}
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun RecordWithMediaView(
    modifier: Modifier = Modifier,
    rv: RecordWithMediaView,
    onShowThread: (SkeetData) -> Unit,
    viewModel: TimelineViewModel? = null,
    postTextSize: PostTextSize = PostTextSize.Medium,
    avatarShape: Shape = CircleShape,
    showLabels: Boolean = true,
    following: Boolean = false,
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
        is RecordViewRecordUnion.ViewRecord -> {
            val author = rv.value.author
            val isBlockedEmbed = following && (
                author.viewer?.blockedBy == true || author.viewer?.blocking != null
            )
            if (isBlockedEmbed) {
                SkeetData(blocked = true)
            } else {
                SkeetData.fromRecordView(rv.value)
            }
        }
    }

    record?.let {
        SkeetView(
            viewModel = viewModel,
            skeet = record,
            nested = true,
            postTextSize = postTextSize,
            avatarShape = avatarShape,
            showLabels = showLabels,
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
                            val repostAvatarContext = LocalContext.current
                            val repostAvatarRequest = remember(avatarUri.uri) {
                                ImageRequest.Builder(repostAvatarContext)
                                    .data(avatarUri.uri)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = repostAvatarRequest,
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
            if (skeet.reply != null) {
                val parent = skeet.reply.parent
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

                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Replying to a thread",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            } else if (skeet.isReplyByRecord) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Replying to a thread",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelMedium,
                    )
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
private fun SkeetHeader(modifier: Modifier = Modifier, skeet: SkeetData, showLabels: Boolean, showPronouns: Boolean = false, labelDisplayName: (Label) -> String? = { null }, labelDescription: (Label) -> String? = { null }, labelerAvatar: (Label) -> String? = { null }) {
    val authorName = skeet.authorName?.ifEmpty { null } ?: (skeet.authorHandle?.handle ?: "")

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = if (!showLabels) 8.dp else 4.dp),
        ) {
            skeet.authorHandle?.let {
                Text(
                    text = "@$it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (showPronouns && !skeet.authorPronouns.isNullOrBlank()) {
                Text(
                    text = skeet.authorPronouns,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }

        SkeetLabelsRow(
            skeet = skeet,
            showLabels = showLabels,
            labelDisplayName = labelDisplayName,
            labelDescription = labelDescription,
            labelerAvatar = labelerAvatar,
        )

    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SkeetLabelsRow(
    skeet: SkeetData,
    showLabels: Boolean,
    labelDisplayName: (Label) -> String? = { null },
    labelDescription: (Label) -> String? = { null },
    labelerAvatar: (Label) -> String? = { null },
) {
    if (!showLabels) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        ) {
            skeet.authorLabels.forEach {
                it.neg?.let { neg ->
                    if (!neg) {
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avatarUrl = labelerAvatar(it)
                        if (avatarUrl != null) {
                            val labelerContext = LocalContext.current
                            val labelerRequest = remember(avatarUrl) {
                                ImageRequest.Builder(labelerContext)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = labelerRequest,
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                contentDescription = definition.plaintext,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = definition.icon,
                                contentDescription = definition.plaintext,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = definition.plaintext,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
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

@Suppress("SameParameterValue")
@Composable
private fun ContentWarningCard(
    label: String,
    revealed: Boolean = false,
    onToggle: () -> Unit,
    wrapWithCard: Boolean = true,
) {
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (wrapWithCard) 16.dp else 0.dp, vertical = 8.dp),
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
            val haptic = LocalHapticFeedback.current
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }) {
                Text(if (revealed) "Hide" else "Show")
            }
        }
    }

    if (wrapWithCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        ) {
            content()
        }
    } else {
        content()
    }
}

private object NoOpUriHandler : UriHandler {
    override fun openUri(uri: String) {}
}