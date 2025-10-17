@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.ExternalView
import app.bsky.embed.ExternalViewExternal
import app.bsky.embed.ImagesView
import app.bsky.embed.ImagesViewImage
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.VideoView
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.feed.PostReplyRef
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRef
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.ReplyRefRootUnion
import com.atproto.label.Label
import com.atproto.repo.StrongRef
import kotlinx.datetime.toStdlibInstant
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.Blob
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class CDNImageSize(
    val size: String
) {
    Full("feed_fullsize"),
    Thumb("feed_thumbnail")
}

private fun cdnBlobURL(authorDid: Did, blob: Blob?, size: CDNImageSize): Uri? {
    val id = when (blob) {
        is Blob.LegacyBlob -> blob.cid
        is Blob.StandardBlob -> blob.ref.link
        null -> return null
    }

    return Uri("https://cdn.bsky.app/img/${size.size}/plain/${authorDid.did}/${id}@jpeg")
}

private fun cdnVideoThumb(authorDid: Did, blob: Blob?): Uri? {
    val id = when (blob) {
        is Blob.LegacyBlob -> blob.cid
        is Blob.StandardBlob -> blob.ref.link
        null -> return null
    }
    return Uri("https://video.cdn.bsky.app/hls/${authorDid.did}/${id}/thumbnail.jpg")
}

private fun cdnVideoPlaylist(authorDid: Did, blob: Blob?): Uri? {
    val id = when (blob) {
        is Blob.LegacyBlob -> blob.cid
        is Blob.StandardBlob -> blob.ref.link
        null -> return null
    }
    return Uri("https://video.cdn.bsky.app/hls/${authorDid.did}/${id}/playlist.m3u8")
}

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
    var embed: PostViewEmbedUnion? = null,
    val reason: FeedViewPostReasonUnion? = null,
    val reply: ReplyRef? = null,
    val createdAt: Instant? = null,

    val blocked: Boolean = false,
    val notFound: Boolean = false,
    val following: Boolean = false,
    val follower: Boolean = false,
    var replyToNotFollowing: Boolean = false,
) {
    companion object {
        fun fromFeedViewPost(post: FeedViewPost): SkeetData {
            val content: Post = (post.post.record.decodeAs())

            val sd = SkeetData(
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
                createdAt = content.createdAt.toStdlibInstant(),
                following = post.post.author.viewer?.following != null,
                follower = post.post.author.viewer?.followedBy != null,
            )

            sd.replyToNotFollowing = run {
                when (sd.reason) {
                    is FeedViewPostReasonUnion.ReasonPin,
                    FeedViewPostReasonUnion.Unknown,
                    FeedViewPostReasonUnion.ReasonRepost -> false

                    else -> {
                        val (parent, _) = sd.parent()
                        val root = sd.root()

                        parent?.let {
                            val parentFollowing = parent.following
                            val rootFollowing = root?.following ?: false

                            if (!parentFollowing && root == null) {
                                return@run false
                            }

                            val res = parentFollowing || rootFollowing
                            return@run !res
                        }

                        return@run false
                    }
                }
            }

            return sd
        }

        fun fromPostView(post: PostView, author: ProfileViewBasic): SkeetData {
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
                createdAt = content.createdAt.toStdlibInstant(),
                following = author.viewer?.following != null,
                follower = author.viewer?.followedBy != null,
            )
        }

        fun fromPost(parent: Pair<Cid, AtUri>, post: Post, author: ProfileView): SkeetData {
            return SkeetData(
                cid = parent.first,
                uri = parent.second,
                authorAvatarURL = author.avatar?.uri,
                authorName = author.displayName,
                authorHandle = author.handle,
                authorLabels = author.labels,
                content = post.text,
                embed = when (post.embed) {
                    is PostEmbedUnion.External -> {
                        val c = (post.embed as PostEmbedUnion.External)
                        PostViewEmbedUnion.ExternalView(
                            ExternalView(
                                ExternalViewExternal(
                                    uri = c.value.external.uri,
                                    title = c.value.external.title,
                                    description = c.value.external.description,
                                    thumb = cdnBlobURL(
                                        author.did,
                                        c.value.external.thumb,
                                        CDNImageSize.Thumb
                                    )
                                )
                            )
                        )
                    }

                    is PostEmbedUnion.Images -> {
                        val c = (post.embed as PostEmbedUnion.Images)
                        PostViewEmbedUnion.ImagesView(
                            ImagesView(c.value.images.map {
                                ImagesViewImage(
                                    fullsize = cdnBlobURL(
                                        author.did,
                                        it.image,
                                        CDNImageSize.Full
                                    )!!,
                                    thumb = cdnBlobURL(
                                        author.did,
                                        it.image,
                                        CDNImageSize.Thumb
                                    )!!,
                                    alt = it.alt,
                                    aspectRatio = it.aspectRatio,
                                )
                            })
                        )
                    }

                    // Record need to be hydrated before being rendered!

//                    is PostEmbedUnion.Record -> {
//                        val c = (post.embed as PostEmbedUnion.Record).value
//
//                        PostViewEmbedUnion.RecordView(
//                            RecordView(post.embed.value.record)
//                        )
//                    }
//
//                    is PostEmbedUnion.RecordWithMedia -> PostViewEmbedUnion.RecordWithMediaView(
//                        RecordWithMediaView(
//                            post.embed.value.record,
//                            post.embed.value.media
//                        )
//                    )
//
//                    is PostEmbedUnion.Unknown -> PostViewEmbedUnion.Unknown(post.embed.value)
                    is PostEmbedUnion.Video -> {
                        val c = (post.embed as PostEmbedUnion.Video).value
                        PostViewEmbedUnion.VideoView(
                            VideoView(
                                playlist = cdnVideoPlaylist(author.did, c.video)!!,
                                thumbnail = cdnVideoThumb(author.did, c.video),
                                alt = c.alt,
                                aspectRatio = c.aspectRatio,
                                cid = parent.first
                            )
                        )
                    }

                    null -> null
                    else -> null
                },
                // TODO: fix embeds
                createdAt = post.createdAt.toStdlibInstant()
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
                createdAt = content.createdAt.toStdlibInstant()
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

    fun parent(): Pair<SkeetData?, StrongRef?> {
        val rawParent = this.reply?.parent
        return when (rawParent) {
            is ReplyRefParentUnion.BlockedPost -> SkeetData(
                authorName = "Blocked",
                uri = rawParent.value.uri,
                blocked = rawParent.value.blocked
            ) to null

            is ReplyRefParentUnion.NotFoundPost -> SkeetData(
                authorName = "Post not found",
                uri = rawParent.value.uri,
                notFound = rawParent.value.notFound
            ) to null

            is ReplyRefParentUnion.PostView -> {
                val content: Post = (rawParent.value.record.decodeAs())
                fromPostView(
                    rawParent.value, rawParent.value.author
                ) to content.reply?.parent
            }

            else -> null to null
        }
    }

    fun root(): SkeetData? {
        val (p, _) = this.parent()

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

            is ReplyRefRootUnion.PostView -> fromPostView(rawRoot.value, rawRoot.value.author)

            else -> null
        }

        if (r?.cid == p?.cid) {
            return null
        }

        return r
    }

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

sealed class Notification {
    data class RawLike(val post: Post, val author: ProfileView, val createdAt: Instant) :
        Notification()

    data class Like(val data: RepeatedNotification) :
        Notification()

    data class Repost(val repost: Post, val author: ProfileView, val createdAt: Instant) :
        Notification()

    data class Reply(
        val parent: Pair<Cid, AtUri>,
        val reply: Post,
        val author: ProfileView,
        val createdAt: Instant
    ) :
        Notification()

    data class Follow(val follow: ProfileView, val createdAt: Instant) : Notification()
    data class Mention(
        val parent: Pair<Cid, AtUri>,
        val mention: Post,
        val author: ProfileView,
        val createdAt: Instant
    ) :
        Notification()

    data class Quote(
        val parent: Pair<Cid, AtUri>,
        val quote: Post,
        val author: ProfileView,
        val createdAt: Instant
    ) :
        Notification()

    fun createdAt(): Instant {
        return when (this) {
            is RawLike -> this.createdAt
            is Follow -> this.createdAt
            is Like -> this.data.timestamp
            is Mention -> this.createdAt
            is Quote -> this.createdAt
            is Reply -> this.createdAt
            is Repost -> this.createdAt
        }
    }
}

data class Notifications(
    var list: List<Notification> = listOf()
)

enum class RepeatableNotification(val u: Unit) {
    Like(Unit),
    Repost(Unit)
}

data class RepeatedNotification(
    val kind: RepeatableNotification,
    val post: Post,
    var authors: List<RepeatedAuthor>,
    var timestamp: Instant
) {
    fun sorted(): RepeatedNotification {
        return this.copy(kind, post, authors.sortedByDescending { it.timestamp }, timestamp)
    }
}

data class RepeatedAuthor(
    val author: ProfileView,
    val timestamp: Instant,
)