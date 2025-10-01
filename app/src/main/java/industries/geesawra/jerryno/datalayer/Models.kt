package industries.geesawra.jerryno.datalayer

import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRef
import com.atproto.label.Label
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Handle

data class SkeetData(
    val likes: Long?,
    val reposts: Long?,
    val replies: Long?,
    val uri: AtUri,
    val cid: Cid,
    val didRepost: Boolean,
    val didLike: Boolean,
    val authorAvatarURL: String?,
    val authorName: String?,
    val authorHandle: Handle,
    val authorLabels: List<Label>,
    val content: String,
    val embed: PostViewEmbedUnion?,
    val reason: FeedViewPostReasonUnion?,
    val reply: ReplyRef?,
) {
    companion object {
        fun fromFeedViewPost(post: FeedViewPost): SkeetData {
            val content: Post = (post.post.record.decodeAs())

            return SkeetData(
                likes = post.post.likeCount,
                reposts = post.post.repostCount,
                replies = post.post.replyCount,
                uri = post.post.uri,
                cid = post.post.cid,
                didRepost = post.post.viewer?.repost != null,
                didLike = post.post.viewer?.like != null,
                authorAvatarURL = post.post.author.avatar?.uri,
                authorName = post.post.author.displayName,
                authorHandle = post.post.author.handle,
                authorLabels = post.post.author.labels,
                content = content.text,
                embed = post.post.embed,
                reason = post.reason,
                reply = post.reply
            )
        }

        fun fromRecordView(post: RecordViewRecord): SkeetData {
            val content: Post = (post.value.decodeAs())

            val maybeEmbed = post.embeds.firstOrNull()
            val embed = when (maybeEmbed) {
                is RecordViewRecordEmbedUnion.ExternalView -> PostViewEmbedUnion.ExternalView(
                    maybeEmbed.value
                )

                is RecordViewRecordEmbedUnion.ImagesView -> PostViewEmbedUnion.ImagesView(maybeEmbed.value)
                is RecordViewRecordEmbedUnion.RecordView -> PostViewEmbedUnion.RecordView(maybeEmbed.value)
                is RecordViewRecordEmbedUnion.RecordWithMediaView -> PostViewEmbedUnion.RecordWithMediaView(
                    maybeEmbed.value
                )

                is RecordViewRecordEmbedUnion.Unknown -> PostViewEmbedUnion.Unknown(maybeEmbed.value)
                is RecordViewRecordEmbedUnion.VideoView -> PostViewEmbedUnion.VideoView(maybeEmbed.value)
                null -> null
            }

            return SkeetData(
                likes = post.likeCount,
                reposts = post.repostCount,
                replies = post.replyCount,
                uri = post.uri,
                cid = post.cid,
                didRepost = false,
                didLike = false,
                authorAvatarURL = post.author.avatar?.uri,
                authorName = post.author.displayName,
                authorHandle = post.author.handle,
                authorLabels = post.author.labels,
                content = content.text,
                embed = embed,
                reason = null,
                reply = null
            )
        }
    }

    fun shareURL(): String {
        val u = "https://bsky.app/profile/${this.authorHandle}/post/${
            this.uri.split(
                "/"
            ).last()
        }"

        return u
    }
}