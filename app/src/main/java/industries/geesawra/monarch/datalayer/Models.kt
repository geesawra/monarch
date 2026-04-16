@file:OptIn(ExperimentalTime::class)

package industries.geesawra.monarch.datalayer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.VerificationStateVerifiedStatus
import app.bsky.embed.AspectRatio
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
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.Blob
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun AspectRatio?.toFloat(): Float? {
    if (this == null || height <= 0) return null
    return width.toFloat() / height.toFloat()
}

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

private fun Blob?.blobId(): String? = when (this) {
    is Blob.LegacyBlob -> cid
    is Blob.StandardBlob -> ref.link.cid
    null -> null
}

private fun cdnBlobURL(authorDid: Did, blob: Blob?, size: CDNImageSize): Uri? {
    val id = blob.blobId() ?: return null
    return Uri("https://cdn.bsky.app/img/${size.size}/plain/${authorDid.did}/${id}@jpeg")
}

private fun cdnVideoThumb(authorDid: Did, blob: Blob?): Uri? {
    val id = blob.blobId() ?: return null
    return Uri("https://video.cdn.bsky.app/hls/${authorDid.did}/${id}/thumbnail.jpg")
}

private fun cdnVideoPlaylist(authorDid: Did, blob: Blob?): Uri? {
    val id = blob.blobId() ?: return null
    return Uri("https://video.cdn.bsky.app/hls/${authorDid.did}/${id}/playlist.m3u8")
}

enum class ThreadConnectorType { PASS_THROUGH, BRANCH, LAST_BRANCH }

@Immutable
data class ThreadConnector(val level: Int, val type: ThreadConnectorType)

@Immutable
data class SkeetData(
    val nestingLevel: Int = 0,
    val isReplyToRoot: Boolean = false,
    val isSameAuthorContinuation: Boolean = false,
    val threadConnectors: List<ThreadConnector> = listOf(),
    val hasMoreReplies: Boolean = false,
    val isFocused: Boolean = false,
    val likes: Long? = null,
    val reposts: Long? = null,
    val replies: Long? = null,
    val uri: AtUri = AtUri(""),
    val cid: Cid = Cid(""),
    val did: Did? = null,
    val didRepost: Boolean = false,
    val didLike: Boolean = false,
    val didBookmark: Boolean = false,
    val likeRkey: RKey? = null,
    val repostRkey: RKey? = null,
    val authorAvatarURL: String? = null,
    val authorName: String? = null,
    val authorHandle: Handle? = null,
    val authorPronouns: String? = null,
    val authorLabels: List<Label> = listOf(),
    val verified: Boolean = false,
    val content: String = "",
    val embed: PostViewEmbedUnion? = null,
    val reason: FeedViewPostReasonUnion? = null,
    val reply: ReplyRef? = null,
    val recordReply: PostReplyRef? = null,
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
    val isReplyByRecord: Boolean = false,
    val isMuted: Boolean = false,
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
                val (parentPost, _) = computedParent
                if (parentPost?.notFound == true || parentPost?.blocked == true) return@run true
                if (computedRoot?.notFound == true || computedRoot?.blocked == true) return@run true

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
                            ReplyFilterMode.None -> return@run false
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
                didBookmark = post.post.viewer?.bookmarked == true,
                likeRkey = post.post.viewer?.like?.let { RKey(it.atUri.substringAfterLast("/")) },
                repostRkey = post.post.viewer?.repost?.let { RKey(it.atUri.substringAfterLast("/")) },
                replyDisabled = post.post.viewer?.replyDisabled == true,
                authorAvatarURL = post.post.author.avatar?.uri,
                authorName = post.post.author.displayName,
                authorHandle = post.post.author.handle,
                authorPronouns = post.post.author.pronouns,
                authorLabels = post.post.author.labels.orEmpty(),
                postLabels = post.post.labels.orEmpty(),
                threadgate = post.post.threadgate
                    ?: (post.reply?.root as? ReplyRefRootUnion.PostView)?.value?.threadgate
                    ?: (post.reply?.parent as? ReplyRefParentUnion.PostView)?.value?.threadgate,
                verified = post.post.author.verification?.verifiedStatus == VerificationStateVerifiedStatus.Valid,
                content = content.text,
                embed = post.post.embed,
                reason = reason,
                reply = reply,
                recordReply = content.reply,
                facets = content.facets.orEmpty(),
                createdAt = content.createdAt,
                following = following,
                follower = post.post.author.viewer?.followedBy != null,
                did = did,
                replyToNotFollowing = replyToNotFollowing,
                isReplyByRecord = content.reply != null,
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
                    val parent = fromPostView(rawParent.value, rawParent.value.author)
                    parent.copy(replies = maxOf(parent.replies ?: 0, 1)) to content.reply?.parent
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

                is ReplyRefRootUnion.PostView -> {
                    val root = fromPostView(rawRoot.value, rawRoot.value.author)
                    root.copy(replies = maxOf(root.replies ?: 0, 1))
                }

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
                didBookmark = post.viewer?.bookmarked == true,
                likeRkey = post.viewer?.like?.let { RKey(it.atUri.substringAfterLast("/")) },
                repostRkey = post.viewer?.repost?.let { RKey(it.atUri.substringAfterLast("/")) },
                replyDisabled = post.viewer?.replyDisabled == true,
                authorAvatarURL = post.author.avatar?.uri,
                authorName = post.author.displayName,
                authorHandle = post.author.handle,
                authorPronouns = post.author.pronouns,
                authorLabels = post.author.labels.orEmpty(),
                postLabels = post.labels.orEmpty(),
                threadgate = post.threadgate,
                verified = post.author.verification?.verifiedStatus == VerificationStateVerifiedStatus.Valid,
                content = content.text,
                facets = content.facets.orEmpty(),
                embed = post.embed,
                recordReply = content.reply,
                createdAt = content.createdAt,
                following = author.viewer?.following != null,
                follower = author.viewer?.followedBy != null,
                did = author.did,
            )
        }

        private fun transformEmbed(embed: PostEmbedUnion?, authorDid: Did, cid: Cid): PostViewEmbedUnion? {
            return when (embed) {
                is PostEmbedUnion.External -> {
                    val c = embed
                    PostViewEmbedUnion.ExternalView(
                        ExternalView(
                            ExternalViewExternal(
                                uri = c.value.external.uri,
                                title = c.value.external.title,
                                description = c.value.external.description,
                                thumb = cdnBlobURL(
                                    authorDid,
                                    c.value.external.thumb,
                                    CDNImageSize.Thumb
                                )
                            )
                        )
                    )
                }

                is PostEmbedUnion.Images -> {
                    val c = embed
                    PostViewEmbedUnion.ImagesView(
                        ImagesView(c.value.images.map {
                            ImagesViewImage(
                                fullsize = cdnBlobURL(
                                    authorDid,
                                    it.image,
                                    CDNImageSize.Full
                                )!!,
                                thumb = cdnBlobURL(
                                    authorDid,
                                    it.image,
                                    CDNImageSize.Thumb
                                )!!,
                                alt = it.alt,
                                aspectRatio = it.aspectRatio,
                            )
                        })
                    )
                }

                is PostEmbedUnion.Video -> {
                    val c = embed.value
                    PostViewEmbedUnion.VideoView(
                        VideoView(
                            playlist = cdnVideoPlaylist(authorDid, c.video)!!,
                            thumbnail = cdnVideoThumb(authorDid, c.video),
                            alt = c.alt,
                            aspectRatio = c.aspectRatio,
                            cid = cid
                        )
                    )
                }

                null -> null
                else -> null
            }
        }

        private fun fromPostCommon(
            parent: Pair<Cid, AtUri>,
            post: Post,
            avatarUri: String?,
            displayName: String?,
            handle: Handle,
            pronouns: String?,
            labels: List<Label>,
            verified: Boolean,
            did: Did,
        ) = SkeetData(
            cid = parent.first,
            uri = parent.second,
            authorAvatarURL = avatarUri,
            authorName = displayName,
            authorHandle = handle,
            authorPronouns = pronouns,
            authorLabels = labels,
            verified = verified,
            did = did,
            content = post.text,
            embed = transformEmbed(post.embed, did, parent.first),
            createdAt = post.createdAt,
            facets = post.facets.orEmpty(),
            recordReply = post.reply,
        )

        fun fromPost(parent: Pair<Cid, AtUri>, post: Post, author: ProfileView) = fromPostCommon(
            parent, post, author.avatar?.uri, author.displayName, author.handle,
            author.pronouns, author.labels.orEmpty(),
            author.verification?.verifiedStatus == VerificationStateVerifiedStatus.Valid, author.did,
        )

        fun fromPost(parent: Pair<Cid, AtUri>, post: Post, author: ProfileViewBasic) = fromPostCommon(
            parent, post, author.avatar?.uri, author.displayName, author.handle,
            author.pronouns, author.labels.orEmpty(),
            author.verification?.verifiedStatus == VerificationStateVerifiedStatus.Valid, author.did,
        )

        fun fromPost(
            parent: Pair<Cid, AtUri>,
            post: Post,
            author: ProfileView,
            embed: PostViewEmbedUnion?
        ) = fromPost(parent, post, author).copy(embed = embed)


        fun fromRecordView(post: RecordViewRecord): SkeetData {
            val content: Post = (post.value.decodeAs())

            val maybeEmbed = post.embeds?.firstOrNull()
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
                authorPronouns = post.author.pronouns,
                authorLabels = post.author.labels.orEmpty(),
                verified = post.author.verification?.verifiedStatus == VerificationStateVerifiedStatus.Valid,
                content = content.text,
                embed = embed,
                reason = null,
                reply = null,
                recordReply = content.reply,
                createdAt = content.createdAt,
                facets = content.facets.orEmpty(),
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

    fun buildAnnotated(
        primary: Color,
        onMentionClick: ((Did) -> Unit)? = null,
    ): AnnotatedString {
        if (this.facets.isEmpty()) {
            return buildAnnotatedString {
                append(this@SkeetData.content)
            }
        }

        val linkStyles = TextLinkStyles(style = SpanStyle(color = primary))
        val tagStyle = SpanStyle(color = primary)

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
                                    linkStyles,
                                )
                            ) {
                                append(segment.content)
                            }

                            is FacetFeatureUnion.Mention -> withLink(
                                LinkAnnotation.Clickable(
                                    tag = f.value.did.did,
                                    styles = linkStyles,
                                    linkInteractionListener = { onMentionClick?.invoke(f.value.did) },
                                )
                            ) {
                                append(segment.content)
                            }

                            is FacetFeatureUnion.Tag -> withStyle(tagStyle) {
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

        val rootFromRecord = this.recordReply?.root

        val rootFromHydrated = when (val maybeRoot = this.reply?.root) {
            is ReplyRefRootUnion.PostView -> StrongRef(maybeRoot.value.uri, maybeRoot.value.cid)
            else -> null
        }

        return PostReplyRef(
            parent = thisPostRef,
            root = rootFromRecord ?: rootFromHydrated ?: thisPostRef
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

    fun lazyListKey(): String {
        val reasonSuffix = when (val r = reason) {
            is FeedViewPostReasonUnion.ReasonRepost -> "|rp:${r.value.by.did.did}:${r.value.indexedAt}"
            else -> ""
        }
        return "${uri.atUri}${reasonSuffix}"
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

@Immutable
sealed class Notification {
    @Immutable
    data class RawLike(
        val subject: StrongRef,
        val post: SkeetData,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean
    ) :
        Notification()

    @Immutable
    data class RawRepost(
        val subject: StrongRef,
        val post: SkeetData,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean
    ) :
        Notification()

    @Immutable
    data class Like(val data: RepeatedNotification, val new: Boolean) :
        Notification()

    @Immutable
    data class Repost(val data: RepeatedNotification, val new: Boolean) :
        Notification()

    @Immutable
    data class Reply(
        val parent: Pair<Cid, AtUri>,
        val reply: Post,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean,
        val hydratedPost: SkeetData? = null,
    ) :
        Notification()

    @Immutable
    data class Follow(val follow: ProfileView, val createdAt: Instant, val new: Boolean) :
        Notification()

    @Immutable
    data class Mention(
        val parent: Pair<Cid, AtUri>,
        val mention: Post,
        val author: ProfileView,
        val createdAt: Instant,
        val new: Boolean,
        val hydratedPost: SkeetData? = null,
    ) :
        Notification()

    @Immutable
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

@Immutable
data class RepeatedNotification(
    val kind: RepeatableNotification,
    val post: SkeetData,
    val authors: List<RepeatedAuthor>,
    val timestamp: Instant,
    val new: Boolean,
)

@Immutable
data class RepeatedAuthor(
    val author: ProfileView,
    val timestamp: Instant,
)

@Immutable
data class ThreadPost(
    val post: SkeetData = SkeetData(),
    val level: Int = 0,
    val replies: List<ThreadPost> = listOf(),
    val hasMoreReplies: Boolean = false,
) {
    companion object {
        private const val MAX_NESTING = 5
    }

    fun flatten(focusedUri: AtUri? = null): ImmutableList<SkeetData> {
        val out = mutableListOf<SkeetData>()
        flattenInner(
            out = out,
            activeConnectors = mutableListOf(),
            parentDid = null,
            effectiveLevel = 0,
            parentIsInChain = true,
        )
        val focusIdx = if (focusedUri != null) {
            out.indexOfFirst { it.uri == focusedUri }
        } else {
            out.indexOfLast { it.nestingLevel == 0 && it.threadConnectors.isEmpty() }
        }
        if (focusIdx >= 0) {
            out[focusIdx] = out[focusIdx].copy(isFocused = true)
        }
        if (focusedUri != null && focusIdx > 0) {
            val focusNesting = out[focusIdx].nestingLevel
            for (i in out.indices) {
                out[i] = if (i <= focusIdx) {
                    out[i].copy(nestingLevel = 0, threadConnectors = emptyList())
                } else {
                    val newLevel = (out[i].nestingLevel - focusNesting).coerceAtLeast(0)
                    val newConnectors = out[i].threadConnectors
                        .filter { it.level >= focusNesting }
                        .map { it.copy(level = it.level - focusNesting) }
                    out[i].copy(nestingLevel = newLevel, threadConnectors = newConnectors)
                }
            }
        }
        return out.toPersistentList()
    }

    private fun flattenInner(
        out: MutableList<SkeetData>,
        activeConnectors: MutableList<Boolean>,
        parentDid: Did?,
        effectiveLevel: Int,
        parentIsInChain: Boolean,
    ) {
        val isContinuation = parentIsInChain &&
                parentDid != null &&
                post.did == parentDid
        val myIsInChain = parentDid == null || isContinuation

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
            val willBeContinuation = myIsInChain && reply.post.did == post.did
            val childLevel = if (willBeContinuation) {
                myLevel
            } else {
                (myLevel + 1).coerceAtMost(MAX_NESTING)
            }

            if (!willBeContinuation) {
                activeConnectors.add(!isLast)
            }

            reply.flattenInner(
                out = out,
                activeConnectors = activeConnectors,
                parentDid = post.did,
                effectiveLevel = childLevel,
                parentIsInChain = myIsInChain,
            )

            if (!willBeContinuation) {
                activeConnectors.removeAt(activeConnectors.lastIndex)
            }
        }
    }
}

private fun String.containsWordBoundary(word: String): Boolean {
    var idx = 0
    while (true) {
        idx = indexOf(word, idx)
        if (idx < 0) return false
        val before = if (idx > 0) this[idx - 1] else ' '
        val after = if (idx + word.length < length) this[idx + word.length] else ' '
        if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
        idx += 1
    }
}

private fun matchesMutedWord(skeet: SkeetData, mutedWords: List<app.bsky.actor.MutedWord>, now: Instant): Boolean {
    if (mutedWords.isEmpty()) return false
    val contentLower = skeet.content.lowercase()
    val tags = skeet.facets.flatMap { facet ->
        facet.features.mapNotNull { feature ->
            (feature as? app.bsky.richtext.FacetFeatureUnion.Tag)?.value?.tag?.lowercase()
        }
    }
    return mutedWords.any { word ->
        val expiresAt = word.expiresAt
        if (expiresAt != null && expiresAt < now) return@any false
        if (word.actorTarget is app.bsky.actor.MutedWordActorTarget.ExcludeFollowing && skeet.following) return@any false
        val valueLower = word.value.lowercase()
        val matchesContent = word.targets.contains(app.bsky.actor.MutedWordTarget.Content) &&
            contentLower.containsWordBoundary(valueLower)
        val matchesTag = word.targets.contains(app.bsky.actor.MutedWordTarget.Tag) &&
            tags.any { it == valueLower }
        matchesContent || matchesTag
    }
}

/**
 * Mark each skeet with isMuted=true if itself, its parent, or its root matches any muted
 * word rule. Called once per fetch and whenever the muted-words list changes.
 */
@Immutable
data class StandardPublication(
    val name: String,
    val url: String? = null,
    val description: String? = null,
)

enum class ContentBlockType { PARAGRAPH, HEADING, LIST_ITEM, CODE, BLOCKQUOTE, IMAGE, HORIZONTAL_RULE, WEBSITE, LINK, UNKNOWN }

@Immutable
data class ContentBlock(
    val type: ContentBlockType,
    val text: String = "",
    val level: Int = 0,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val embeddedPost: SkeetData? = null,
)

@Immutable
data class StandardDocument(
    val title: String,
    val path: String? = null,
    val description: String? = null,
    val textContent: String? = null,
    val contentBlocks: List<ContentBlock> = emptyList(),
    val publishedAt: String? = null,
    val updatedAt: String? = null,
    val tags: List<String> = emptyList(),
    val site: String? = null,
)

@Immutable
data class PublicationRecord(
    val uri: AtUri,
    val cid: Cid,
    val publication: StandardPublication,
)

@Immutable
data class DocumentRecord(
    val uri: AtUri,
    val cid: Cid,
    val document: StandardDocument,
    val authorDid: Did,
)

fun List<SkeetData>.withMuteFlags(mutedWords: List<app.bsky.actor.MutedWord>): ImmutableList<SkeetData> {
    if (mutedWords.isEmpty()) {
        val out = if (any { it.isMuted }) map { it.copy(isMuted = false) } else this
        return out.toImmutableList()
    }
    val now = Clock.System.now()
    return map { skeet ->
        val self = matchesMutedWord(skeet, mutedWords, now)
        val parentMuted = skeet.cachedParent?.first?.let { matchesMutedWord(it, mutedWords, now) } == true
        val rootMuted = skeet.cachedRoot?.let { matchesMutedWord(it, mutedWords, now) } == true
        val muted = self || parentMuted || rootMuted
        if (muted != skeet.isMuted) skeet.copy(isMuted = muted) else skeet
    }.toPersistentList()
}