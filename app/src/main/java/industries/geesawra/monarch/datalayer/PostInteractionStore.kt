package industries.geesawra.monarch.datalayer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.RKey
import javax.inject.Inject
import javax.inject.Singleton

data class PostInteraction(
    val likes: Long,
    val reposts: Long,
    val replies: Long,
    val didLike: Boolean,
    val didRepost: Boolean,
    val didBookmark: Boolean,
    val likeRkey: RKey? = null,
    val repostRkey: RKey? = null,
) {
    companion object {
        fun from(skeet: SkeetData) = PostInteraction(
            likes = skeet.likes ?: 0,
            reposts = skeet.reposts ?: 0,
            replies = skeet.replies ?: 0,
            didLike = skeet.didLike,
            didRepost = skeet.didRepost,
            didBookmark = skeet.didBookmark,
            likeRkey = skeet.likeRkey,
            repostRkey = skeet.repostRkey,
        )
    }
}

@Singleton
class PostInteractionStore @Inject constructor() {
    private val states = mutableMapOf<Cid, MutableState<PostInteraction>>()

    fun getState(cid: Cid, initial: () -> PostInteraction): MutableState<PostInteraction> {
        return states.getOrPut(cid) { mutableStateOf(initial()) }
    }

    fun peek(cid: Cid): PostInteraction? = states[cid]?.value

    fun update(cid: Cid, transform: (PostInteraction) -> PostInteraction) {
        states[cid]?.let { it.value = transform(it.value) }
    }

    fun seed(skeet: SkeetData) {
        val fresh = PostInteraction.from(skeet)
        val existing = states[skeet.cid]
        if (existing == null) {
            states[skeet.cid] = mutableStateOf(fresh)
        } else if (existing.value != fresh) {
            existing.value = fresh
        }
    }

    fun clear() {
        states.clear()
    }
}
