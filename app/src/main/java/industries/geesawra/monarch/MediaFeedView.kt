package industries.geesawra.monarch

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.PostViewEmbedUnion
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.SkeetData

sealed class MediaItem(val skeet: SkeetData) {
    class ImageMedia(val url: String, val alt: String, skeet: SkeetData) : MediaItem(skeet)
    class VideoMedia(val playlistUrl: String, val thumbnailUrl: String?, skeet: SkeetData) : MediaItem(skeet)
}

private fun extractMediaFromEmbed(embed: PostViewEmbedUnion?, skeet: SkeetData, items: MutableList<MediaItem>) {
    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            embed.value.images.forEach { img ->
                items += MediaItem.ImageMedia(img.fullsize.uri, img.alt, skeet)
            }
        }
        is PostViewEmbedUnion.VideoView -> {
            items += MediaItem.VideoMedia(embed.value.playlist.uri, embed.value.thumbnail?.uri, skeet)
        }
        is PostViewEmbedUnion.RecordWithMediaView -> {
            when (val media = embed.value.media) {
                is RecordWithMediaViewMediaUnion.ImagesView -> {
                    media.value.images.forEach { img ->
                        items += MediaItem.ImageMedia(img.fullsize.uri, img.alt, skeet)
                    }
                }
                is RecordWithMediaViewMediaUnion.VideoView -> {
                    items += MediaItem.VideoMedia(media.value.playlist.uri, media.value.thumbnail?.uri, skeet)
                }
                else -> {}
            }
        }
        is PostViewEmbedUnion.RecordView -> {
            val record = embed.value.record
            if (record is RecordViewRecordUnion.ViewRecord) {
                record.value.embeds.forEach { innerEmbed ->
                    val converted = when (innerEmbed) {
                        is RecordViewRecordEmbedUnion.ImagesView -> PostViewEmbedUnion.ImagesView(innerEmbed.value)
                        is RecordViewRecordEmbedUnion.VideoView -> PostViewEmbedUnion.VideoView(innerEmbed.value)
                        is RecordViewRecordEmbedUnion.RecordWithMediaView -> PostViewEmbedUnion.RecordWithMediaView(innerEmbed.value)
                        else -> null
                    }
                    if (converted != null) extractMediaFromEmbed(converted, skeet, items)
                }
            }
        }
        else -> {}
    }
}

fun extractMedia(posts: List<SkeetData>): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    posts.forEach { skeet ->
        extractMediaFromEmbed(skeet.embed, skeet, items)
    }
    return items
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaFeedView(
    posts: List<SkeetData>,
    isLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val decorView = activity?.window?.decorView
        @Suppress("DEPRECATION")
        decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        onDispose {
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    val mediaItems = remember(posts) { extractMedia(posts) }
    var isMuted by remember { mutableStateOf(true) }

    if (mediaItems.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularWavyProgressIndicator()
            } else {
                Text("No media", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val pagerState = rememberPagerState { mediaItems.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page >= mediaItems.size - 3) {
                onLoadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = mediaItems[page]
            val isActive = pagerState.settledPage == page

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is MediaItem.ImageMedia -> {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.url)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.alt,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularWavyProgressIndicator()
                                }
                            },
                        )
                    }
                    is MediaItem.VideoMedia -> {
                        MediaFeedVideoPlayer(
                            url = item.playlistUrl,
                            isMuted = isMuted,
                            onToggleMute = { isMuted = !isMuted },
                            isVisible = isActive,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = 48.dp),
                ) {
                    if (item.skeet.authorName != null) {
                        Text(
                            text = item.skeet.authorName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.skeet.content.isNotBlank()) {
                        Text(
                            text = item.skeet.content,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (isLoading && mediaItems.isNotEmpty()) {
            CircularWavyProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            )
        }
    }
}
