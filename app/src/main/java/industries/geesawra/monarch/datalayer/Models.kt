@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.VerifiedStatus
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
import app.bsky.feed.ThreadgateView
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetFeatureUnion
import com.atproto.label.Label
import com.atproto.repo.StrongRef
import kotlinx.datetime.toStdlibInstant
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.Blob
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class ReplyFilterMode {
    None,
    OnlyFilterDeepThreads,
    Strict
}

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

enum class ThreadConnectorType { PASS_THROUGH, BRANCH, LAST_BRANCH }
data class ThreadConnector(val level: Int, val type: ThreadConnectorType)

data class SkeetData(
    val nestingLevel: Int = 0,
    val isReplyToRoot: Boolean = false,
    val isSameAuthorContinuation: Boolean = false,
    val threadConnectors: List<ThreadConnector> = listOf(),
    val hasMoreReplies: Boolean = false,
    val likes: Long? = null,
    val reposts: Long? = null,
    val replies: Long? = null,
    val uri: AtUri = AtUri(""),
    val cid: Cid = Cid(""),
    val did: Did? = null,
    val didRepost: Boolean = false,
    val didLike: Boolean = false,
    val likeRkey: RKey? = null,
    val repostRkey: RKey? = null,
    val authorAvatarURL: String? = null,
    val authorName: String? = null,
    val authorHandle: Handle? = null,
    val authorLabels: List<Label> = listOf(),
    val verified: Boolean = false,
    val content: String = "",
    val embed: PostViewEmbedUnion? = null,
    val reason: FeedViewPostReasonUnion? = null,
    val reply: ReplyRef? = null,
    val createdAt: Instant? = null,
    val facets: List<Facet> = listOf(),
    val postLabels: List<Label> = listOf(),
    val threadgate: ThreadgateView? = null,
    val replyDisabled: Boolean = false,
    val blocked: Boolean = false,
    val notFound: Boolean = false,
    val following: Boolean = false,
    val follower: Boolean = false,
    val replyToNotFollowing: Boolean = false,
    val cachedParent: Pair<SkeetData?, StrongRef?>? = null,
    val cachedRoot: SkeetData? = null,
) {
    companion object {
        fun fromFeedViewPost(post: FeedViewPost, currentUserDid: Did? = null, replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads): SkeetData {
            val content: Post = (post.post.record.decodeAs())
            val reason = post.reason
            val reply = post.reply
            val did = post.post.author.did
            val following = post.post.author.viewer?.following != null

            val computedParent = computeParent(reply)
            val computedRoot = computeRoot(reply, computedParent)

            val replyToNotFollowing = run {
                if (replyFilterMode == ReplyFilterMode.None) return@run false
                if (currentUserDid != null && did == currentUserDid) return@run false

                when (reason) {
                    is FeedViewPostReasonUnion.ReasonPin,
                    FeedViewPostReasonUnion.Unknown,
                    FeedViewPostReasonUnion.ReasonRepost -> false

                    else -> {
                        val (parent, _) = computedParent
                        val root = computedRoot

                        fun isFollowedOrSelf(skeet: SkeetData?): Boolean {
                            if (skeet == null) return false
                            if (currentUserDid != null && skeet.did == currentUserDid) return true
                            return skeet.following
                        }

                        if (parent == null) {
                            return@run false
                        }

                        when (replyFilterMode) {
                            ReplyFilterMode.OnlyFilterDeepThreads -> {
                                if (root == null) return@run false

                                val parentsParent = computeParentsParentRef(reply)
                                if (parentsParent != null && parentsParent.uri == root.uri) {
                                    return@run false
                                }

                                if (!isFollowedOrSelf(parent)) return@run true

                                val grandfather = reply?.grandparentAuthor
                                val grandfatherFollowed = if (currentUserDid != null && grandfather?.did == currentUserDid) {
                                    true
                                } else {
                                    grandfather?.viewer?.following?.isNotEmpty() ?: false
                                }
                                return@run !(isFollowedOrSelf(root) && grandfatherFollowed)
                            }

                            ReplyFilterMode.Strict -> {
                                if (root == null) {
                                    return@run !isFollowedOrSelf(parent)
                                }

                                if (!isFollowedOrSelf(parent)) return@run true

                                val parentsParent = computeParentsParentRef(reply)
                                if (parentsParent != null && parentsParent.uri == root.uri) {
                                    return@run !isFollowedOrSelf(root)
                                }

                                val grandfather = reply?.grandparentAuthor
                                val grandfatherFollowed = if (currentUserDid != null && grandfather?.did == currentUserDid) {
                                    true
                                } else {
                                    grandfather?.viewer?.following?.isNotEmpty() ?: false
                                }
                                return@run !(isFollowedOrSelf(root) && grandfatherFollowed)
                            }

                            else -> return@run false
                        }
                    }
                }
            }

            return SkeetData(
                likes = post.post.likeCount,
                reposts = post.post.repostCount,
                replies = post.post.replyCount,
                uri = post.post.uri,
                cid = post.post.cid,
                didRepost = post.post.viewer?.repost != null,
                didLike = post.post.viewer?.like != null,
                likeRkey = post.post.viewer?.like?.let { RKey(it.atUri.substringAfterLast("/")) },
                repostRkey = post.post.viewer?.repost?.let { RKey(it.atUri.substringAfterLast("/")) },
                replyDisabled = post.post.viewer?.replyDisabled == true,
                authorAvatarURL = post.post.author.avatar?.uri,
                authorName = post.post.author.displayName,
                authorHandle = post.post.author.handle,
                authorLabels = post.post.author.labels,
                postLabels = post.post.labels,
                threadgate = post.post.threadgate
                    ?: (post.reply?.root as? ReplyRefRootUnion.PostView)?.value?.threadgate
                    ?: (post.reply?.parent as? ReplyRefParentUnion.PostView)?.value?.threadgate,
                verified = post.post.author.verification?.verifiedStatus == VerifiedStatus.Valid,
                content = content.text,
                embed = post.post.embed,
                reason = reason,
                reply = reply,
                facets = content.facets,
                createdAt = content.createdAt.toStdlibInstant(),
                following = following,
                follower = post.post.author.viewer?.followedBy != null,
                did = did,
                replyToNotFollowing = replyToNotFollowing,
                cachedParent = computedParent,
                cachedRoot = computedRoot,
            )
        }

        private fun computeParent(reply: ReplyRef?): Pair<SkeetData?, StrongRef?> {
            val rawParent = reply?.parent
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

        private fun computeRoot(reply: ReplyRef?, parent: Pair<SkeetData?, StrongRef?>): SkeetData? {
            val (p, _) = parent

            val rawRoot = reply?.root
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

        private fun computeParentsParentRef(reply: ReplyRef?): StrongRef? {
            val rawParent = reply?.parent
            return when (rawParent) {
                is ReplyRefParentUnion.PostView -> {
                    val content: Post = (rawParent.value.record.decodeAs())
                    when (content.reply) {
                        is PostReplyRef -> content.reply!!.parent
                        else -> null
                    }
                }
                else -> null
            }
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
                likeRkey = post.viewer?.like?.let { RKey(it.atUri.substringAfterLast("/")) },
                repostRkey = post.viewer?.repost?.let { RKey(it.atUri.substringAfterLast("/")) },
                replyDisabled = post.viewer?.replyDisabled == true,
                authorAvatarURL = post.author.avatar?.uri,
                authorName = post.author.displayName,
                authorHandle = post.author.handle,
                authorLabels = post.author.labels,
                postLabels = post.labels,
                threadgate = post.threadgate,
                verified = post.author.verification?.verifiedStatus == VerifiedStatus.Valid,
                content = content.text,
                facets = content.facets,
                embed = post.embed,
                createdAt = content.createdAt.toStdlibInstant(),
                following = author.viewer?.following != null,
                follower = author.viewer?.followedBy != null,
                did = author.did,
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
                verified = author.verification?.verifiedStatus == VerifiedStatus.Valid,
                did = author.did,
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
                createdAt = post.createdAt.toStdlibInstant(),
                facets = post.facets,
            )
        }

        fun fromPost(parent: Pair<Cid, AtUri>, post: Post, author: ProfileViewBasic): SkeetData {
            return SkeetData(
                cid = parent.first,
                uri = parent.second,
                authorAvatarURL = author.avatar?.uri,
                authorName = author.displayName,
                authorHandle = author.handle,
                authorLabels = author.labels,
                verified = author.verification?.verifiedStatus == VerifiedStatus.Valid,
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
                createdAt = post.createdAt.toStdlibInstant(),
                facets = post.facets,
                did = author.did,
            )
        }

        fun fromPost(
            parent: Pair<Cid, AtUri>,
            post: Post,
            author: ProfileView,
            embed: PostViewEmbedUnion?
        ): SkeetData {
            return fromPost(parent, post, author).copy(embed = embed)
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
                verified = post.author.verification?.verifiedStatus == VerifiedStatus.Valid,
                content = content.text,
                embed = embed,
                reason = null,
                reply = null,
                createdAt = content.createdAt.toStdlibInstant(),
                facets = content.facets,
                did = post.author.did,
            )
        }
    }

    sealed class AnnotatedData {
        data class NoAnnotation(val data: String) : AnnotatedData()
        data class WithAnnotation(val data: Facet, val content: String) : AnnotatedData()
    }

    val annotatedSegments: List<AnnotatedData> by lazy {
        if (this.facets.isEmpty()) return@lazy emptyList()

        val c = this.content.toByteArray(Charsets.UTF_8)
        var lastIdx: Long = 0
        val segments = mutableListOf<AnnotatedData>()
        this.facets.forEachIndexed { idx, f ->
            segments.add(
                AnnotatedData.NoAnnotation(
                    c.slice(
                        lastIdx.toInt()..
                                f.index.byteStart.toInt() - 1
                    ).toByteArray().toString(Charsets.UTF_8)
                )
            )
            segments.add(
                AnnotatedData.WithAnnotation(
                    data = f, content = c.slice(
                        f.index.byteStart.toInt()..
                                f.index.byteEnd.toInt() - 1
                    ).toByteArray().toString(Charsets.UTF_8)
                )
            )

            lastIdx = f.index.byteEnd

            if (this.facets.lastIndex == idx) {
                segments.add(
                    AnnotatedData.NoAnnotation(
                        c.slice(
                            lastIdx.toInt()..c.size - 1
                        ).toByteArray().toString(Charsets.UTF_8)
                    )
                )
            }
        }
        segments
    }

    @Composable
    fun annotatedContent(onMentionClick: ((Did) -> Unit)? = null): AnnotatedString {
        if (this.facets.isEmpty()) {
            return buildAnnotatedString {
                append(this@SkeetData.content)
            }
        }

        return buildAnnotatedString {
            annotatedSegments.forEach { segment ->
                when (segment) {
                    is AnnotatedData.NoAnnotation -> append(segment.data)
                    is AnnotatedData.WithAnnotation -> {
                        val f = segment.data.features.first()
                        when (f) {
                            is FacetFeatureUnion.Link -> withLink(
                                LinkAnnotation.Url(
                                    f.value.uri.uri,
                                    TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
                                )
                            ) {
                                append(segment.content)
                            }

                            is FacetFeatureUnion.Mention -> withLink(
                                LinkAnnotation.Clickable(
                                    tag = f.value.did.did,
                                    styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
                                    linkInteractionListener = { onMentionClick?.invoke(f.value.did) },
                                )
                            ) {
                                append(segment.content)
                            }

                            is FacetFeatureUnion.Tag -> withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append(segment.content)
                            }

                            is FacetFeatureUnion.Unknown -> append(segment.content)
                        }
                    }
                }
            }
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

    fun parentsParentRef(): StrongRef? {
        if (cachedParent != null) return cachedParent.second
        val rawParent = this.reply?.parent
        return when (rawParent) {
            is ReplyRefParentUnion.PostView -> {
                val content: Post = (rawParent.value.record.decodeAs())
                when (content.reply) {
                    is PostReplyRef -> content.reply!!.parent
                    else -> null
                }
            }

            else -> null
        }
    }

    fun parent(): Pair<SkeetData?, StrongRef?> {
        if (cachedParent != null) return cachedParent
        return computeParent(this.reply)
    }

    fun grandparentAuthor(): ProfileViewBasic? {
        return this.reply?.grandparentAuthor
    }

    fun root(): SkeetData? {
        if (cachedRoot != null) return cachedRoot
        // Check if we explicitly have no root (cachedParent set but cachedRoot not)
        if (cachedParent != null) return null
        return computeRoot(this.reply, this.parent())
    }

    val rkey: String by lazy { this.uri.atUri.split("/").last() }

    fun key(): String = rkey

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
    data class RawLike(
        val subject: StrongRef,
        val post: SkeetData,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean
    ) :
        Notification()

    data class RawRepost(
        val subject: StrongRef,
        val post: SkeetData,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean
    ) :
        Notification()

    data class Like(val data: RepeatedNotification, val new: Boolean) :
        Notification()

    data class Repost(val data: RepeatedNotification, val new: Boolean) :
        Notification()

    data class Reply(
        val parent: Pair<Cid, AtUri>,
        val reply: Post,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean,
        val hydratedPost: SkeetData? = null,
    ) :
        Notification()

    data class Follow(val follow: ProfileView, val createdAt: Instant, val new: Boolean) :
        Notification()

    data class Mention(
        val parent: Pair<Cid, AtUri>,
        val mention: Post,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean,
        val hydratedPost: SkeetData? = null,
    ) :
        Notification()

    data class Quote(
        val parent: Pair<Cid, AtUri>,
        val quote: Post,
        val quotedPost: PostViewEmbedUnion,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean,
        val hydratedPost: SkeetData? = null,
    ) :
        Notification()

    fun createdAt(): Instant {
        return when (this) {
            is RawLike -> this.createdAt
            is RawRepost -> this.createdAt
            is Follow -> this.createdAt
            is Like -> this.data.timestamp
            is Mention -> this.createdAt
            is Quote -> this.createdAt
            is Reply -> this.createdAt
            is Repost -> this.data.timestamp
        }
    }

    fun uniqueKey(): String {
        return when (this) {
            is RawLike -> "rawlike_${subject.cid}_${author.did}"
            is RawRepost -> "rawrepost_${subject.cid}_${author.did}"
            is Like -> "like_${data.post.cid}"
            is Repost -> "repost_${data.post.cid}"
            is Reply -> "reply_${parent.first}_${author.did}"
            is Follow -> "follow_${follow.did}"
            is Mention -> "mention_${parent.first}_${author.did}"
            is Quote -> "quote_${parent.first}_${author.did}"
        }
    }

    fun new(): Boolean {
        return when (this) {
            is RawLike -> this.new
            is RawRepost -> this.new
            is Follow -> this.new
            is Like -> this.new
            is Mention -> this.new
            is Quote -> this.new
            is Reply -> this.new
            is Repost -> this.new
        }
    }
}


enum class RepeatableNotification() {
    Like,
    Repost
}

data class RepeatedNotification(
    val kind: RepeatableNotification,
    val post: SkeetData,
    var authors: List<RepeatedAuthor>,
    var timestamp: Instant,
    val new: Boolean,
)

data class RepeatedAuthor(
    val author: ProfileView,
    val timestamp: Instant,
)

data class ThreadPost(
    val post: SkeetData = SkeetData(),
    val level: Int = 0,
    val replies: List<ThreadPost> = listOf(),
    val hasMoreReplies: Boolean = false,
) {
    companion object {
        private const val MAX_NESTING = 5
    }

    fun flatten(): List<SkeetData> {
        val out = mutableListOf<SkeetData>()
        flattenInner(out, activeConnectors = mutableListOf(), parentDid = null, effectiveLevel = 0)
        return out
    }

    private fun flattenInner(
        out: MutableList<SkeetData>,
        activeConnectors: MutableList<Boolean>,
        parentDid: Did?,
        effectiveLevel: Int,
    ) {
        val isContinuation = parentDid != null &&
                post.did == parentDid &&
                level == 1 &&
                effectiveLevel == 0

        val myLevel = if (isContinuation) effectiveLevel else effectiveLevel.coerceAtMost(MAX_NESTING)

        val connectors = if (myLevel > 0 && !isContinuation) {
            activeConnectors.mapIndexed { idx, hasMoreSiblings ->
                if (idx >= myLevel) return@mapIndexed null
                if (idx == activeConnectors.lastIndex || idx == myLevel - 1) {
                    ThreadConnector(
                        idx,
                        if (hasMoreSiblings) ThreadConnectorType.BRANCH else ThreadConnectorType.LAST_BRANCH
                    )
                } else {
                    if (hasMoreSiblings) ThreadConnector(idx, ThreadConnectorType.PASS_THROUGH)
                    else null
                }
            }.filterNotNull()
        } else {
            listOf()
        }

        out.add(
            post.copy(
                nestingLevel = myLevel,
                isReplyToRoot = level == 1,
                isSameAuthorContinuation = isContinuation,
                threadConnectors = connectors,
                hasMoreReplies = hasMoreReplies,
            )
        )

        val sortedReplies = replies.sortedWith(compareByDescending { it.post.did == post.did })

        sortedReplies.forEachIndexed { i, reply ->
            val isLast = i == sortedReplies.lastIndex
            val willBeContinuation = level == 0 && reply.post.did == post.did
            val childLevel = if (willBeContinuation) {
                myLevel
            } else {
                (myLevel + 1).coerceAtMost(MAX_NESTING)
            }

            if (!willBeContinuation) {
                activeConnectors.add(!isLast)
            }

            reply.flattenInner(out, activeConnectors, post.did, childLevel)

            if (!willBeContinuation) {
                activeConnectors.removeAt(activeConnectors.lastIndex)
            }
        }
    }
}