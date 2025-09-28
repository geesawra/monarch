package industries.geesawra.jerryno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.serialization.json.decodeFromJsonElement
import sh.christian.ozone.BlueskyJson

@Composable
fun SkeetRowView(skeet: FeedViewPost) {
    val likes = skeet.post.likeCount;
    val reposts = skeet.post.repostCount;
    val replies = skeet.post.replyCount;

    val minSize = 55.dp;

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .sizeIn(minHeight = minSize)
                .padding(start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(skeet.post.author.avatar?.toString())
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .padding(end = 8.dp, start = 8.dp, top = 10.dp)
                            .size(minSize)
                            .dropShadow(shape = RoundedCornerShape(12.dp), block = {
                                radius = 2f
                            })
                            .clip(RoundedCornerShape(12.dp))

                    )

                    SkeetHeader(skeet)
                }

                SkeetContent(skeet)

                TimelinePostActionsView(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .fillMaxWidth(),
                    replies = replies,
                    likes = likes,
                    reposts = reposts,
                )
            }
        }

    }
}

@Composable
private fun SkeetContent(skeet: FeedViewPost) {
    val content = BlueskyJson.decodeFromJsonElement<Post>(skeet.post.record.value)

    Text(
        text = content.text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
    )

    val embed = skeet.post.embed
    when (embed) {
        is PostViewEmbedUnion.ImagesView -> {
            val img = embed.value.images

            LazyVerticalGrid(
                columns = GridCells.Fixed({
                    when (img.size) {
                        1 -> 1
                        else -> 2
                    }
                }()),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                userScrollEnabled = false,
                content = {
                    items(img.size) { index ->
                        val img = img[index]

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(img.thumb.toString())
                                .crossfade(true)
                                .build(),
                            contentScale = ContentScale.Crop,
                            contentDescription = img.alt,
                            modifier = Modifier
                                .height(200.dp)
                                .padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    bottom = 10.dp
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .dropShadow(shape = RoundedCornerShape(12.dp), block = {
                                    radius = 2f
                                })
                                .fillMaxWidth()
                        )
                    }
                }
            )
        }

        is PostViewEmbedUnion.VideoView -> {
            val video = embed.value
            ExoPlayerView(
                video.playlist.uri,
                modifier = Modifier // TODO: https://github.com/fengdai/compose-media
                    .height(200.dp)
            )
        } // TODO: build this
        else -> {}
    }
}

@Composable
private fun SkeetHeader(skeet: FeedViewPost) {
    val authorName = skeet.post.author.displayName ?: skeet.post.author.handle.toString();
    var headerSet = false

    Column {
        skeet.reason?.let {
            it
            when (it) {
                is FeedViewPostReasonUnion.ReasonRepost -> {
                    headerSet = true
                    Text(
                        text = "Reposted by ${it.value.by.displayName ?: it.value.by.handle.toString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> {}
            }
        }

        val titlePadding = {
            when (headerSet) {
                true -> PaddingValues(top = 1.dp, bottom = 1.dp, start = 4.dp, end = 4.dp)
                false -> PaddingValues(top = 4.dp, bottom = 1.dp, start = 4.dp, end = 4.dp)
            }
        }()

        Text(
            text = authorName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(paddingValues = titlePadding),
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "@" + skeet.post.author.handle.handle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),

            )

        skeet.reply?.let {
            it
            val parent = it.parent;
            when (parent) {
                is ReplyRefParentUnion.PostView -> {
                    Text(
                        text = "↪ In reply to ${parent.value.author.displayName ?: parent.value.author.handle.toString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    )
                }

                else -> {}
            }
        }
    }
}
