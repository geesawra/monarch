package industries.geesawra.monarch.datalayer

import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.PostReplyRef
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import com.atproto.label.Label
import com.atproto.repo.StrongRef
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.model.Timestamp

data class SkeetData(
    val likes: Long? = null,
    val reposts: Long? = null,
    val replies: Long? = null,
    val uri: AtUri = AtUri(""),
    val cid: Cid = Cid(""),
    val didRepost: Boolean = false,
    val didLike: Boolean = false,
    val authorAvatarURL: String? = null,
    val authorName: String? = null,
    val authorHandle: Handle? = null,
    val authorLabels: List<Label> = listOf(),
    val content: String = "",
    val embed: PostViewEmbedUnion? = null,
    val reason: FeedViewPostReasonUnion? = null,
    val reply: ReplyRef? = null,
    val createdAt: Timestamp? = null,

    val blocked: Boolean = false,
    val notFound: Boolean = false,
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
                reply = post.reply,
                createdAt = content.createdAt
            )
        }

        fun fromPostView(post: PostView): SkeetData {
            val content: Post = (post.record.decodeAs())

            return SkeetData(
                likes = post.likeCount,
                reposts = post.repostCount,
                replies = post.replyCount,
                uri = post.uri,
                cid = post.cid,
                didRepost = post.viewer?.repost != null,
                didLike = post.viewer?.like != null,
                authorAvatarURL = post.author.avatar?.uri,
                authorName = post.author.displayName,
                authorHandle = post.author.handle,
                authorLabels = post.author.labels,
                content = content.text,
                embed = post.embed,
                createdAt = content.createdAt
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
                reply = null,
                createdAt = content.createdAt
            )
        }
    }

    fun replyRef(): PostReplyRef {
        val thisPostRef = StrongRef(this.uri, this.cid)

        val maybeRoot = this.reply?.root
        val rootRef = when (maybeRoot) {
            is ReplyRefRootUnion.BlockedPost -> null
            is ReplyRefRootUnion.NotFoundPost -> null
            is ReplyRefRootUnion.PostView -> StrongRef(maybeRoot.value.uri, maybeRoot.value.cid)
            is ReplyRefRootUnion.Unknown -> null
            null -> null
        }

        return PostReplyRef(
            parent = thisPostRef,
            root = rootRef ?: thisPostRef
        )
    }

    fun parent(): SkeetData? {
        val rawParent = this.reply?.parent
        return when (rawParent) {
            is ReplyRefParentUnion.BlockedPost -> SkeetData(
                authorName = "Blocked",
                uri = rawParent.value.uri,
                blocked = rawParent.value.blocked
            )

            is ReplyRefParentUnion.NotFoundPost -> SkeetData(
                authorName = "Post not found",
                uri = rawParent.value.uri,
                notFound = rawParent.value.notFound
            )

            is ReplyRefParentUnion.PostView -> fromPostView(rawParent.value)

            else -> null
        }
    }

    fun root(): SkeetData? {
        val p = this.parent()

        val rawRoot = this.reply?.root
        val r = when (rawRoot) {
            is ReplyRefRootUnion.BlockedPost -> SkeetData(
                uri = rawRoot.value.uri,
                blocked = rawRoot.value.blocked
            )

            is ReplyRefRootUnion.NotFoundPost -> SkeetData(
                uri = rawRoot.value.uri,
                notFound = rawRoot.value.notFound
            )

            is ReplyRefRootUnion.PostView -> fromPostView(rawRoot.value)

            else -> null
        }

        if (r?.cid == p?.cid) {
            return null
        }

        return r
    }

    // TODO: detect if thread is made of more than the posts we have,
    // if so, show a (more) button to load the thread.

    fun key(): String {
        return this.uri.split("/").last()
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