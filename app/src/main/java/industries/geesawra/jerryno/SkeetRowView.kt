package industries.geesawra.jerryno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import kotlinx.serialization.json.decodeFromJsonElement
import sh.christian.ozone.BlueskyJson

@Composable
fun SkeetRowView(viewModel: TimelineViewModel, skeet: FeedViewPost) {
    val likes = skeet.post.likeCount
    val reposts = skeet.post.repostCount
    val replies = skeet.post.replyCount

    val minSize = 55.dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
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
                            .data(skeet.post.author.avatar?.toString())
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(minSize)
                            .clip(CircleShape)
                    )

                    SkeetHeader(modifier = Modifier.padding(start = 16.dp), skeet = skeet)
                }

                SkeetContent(skeet)

                TimelinePostActionsView(
                    modifier = Modifier
                        .fillMaxWidth(),
                    timelineViewModel = viewModel,
                    replies = replies,
                    likes = likes,
                    reposts = reposts,
                    postUrl = "https://bsky.app/profile/${skeet.post.author.handle.handle}/post/${
                        skeet.post.uri.split(
                            "/"
                        ).last()
                    }",
                    uri = skeet.post.uri,
                    cid = skeet.post.cid,
                    reposted = skeet.post.viewer?.repost != null,
                    liked = skeet.post.viewer?.like != null,
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
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
        style = MaterialTheme.typography.bodyLarge,
    )

    val embed = skeet.post.embed

    if (embed == null) {
        return
    }

    Card(
        modifier = Modifier
            .heightIn(max = 180.dp)
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        when (embed) {
            is PostViewEmbedUnion.ImagesView -> {
                val img = embed.value.images

                PostImageGallery(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    images = img.map {
                        Image(url = it.thumb.uri, alt = it.alt)
                    },
                )
            }

            is PostViewEmbedUnion.VideoView -> {
                embed.value
                Text("Videos TBD")
            } // TODO: build this
            else -> {}
        }
    }
}

@Composable
private fun SkeetHeader(skeet: FeedViewPost, modifier: Modifier = Modifier) {
    val authorName = skeet.post.author.displayName ?: skeet.post.author.handle.toString()

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
            text = "@" + skeet.post.author.handle.handle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 4.dp),
        )

        skeet.post.author.labels.forEach {
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
