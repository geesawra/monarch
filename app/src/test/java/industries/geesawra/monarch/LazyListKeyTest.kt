package industries.geesawra.monarch

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Post
import app.bsky.feed.ReasonRepost
import industries.geesawra.monarch.datalayer.Notification
import industries.geesawra.monarch.datalayer.SkeetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import kotlin.time.Instant

class LazyListKeyTest {

    private fun skeet(
        did: String,
        rkey: String,
        reason: FeedViewPostReasonUnion? = null,
    ): SkeetData = SkeetData(
        uri = AtUri("at://$did/app.bsky.feed.post/$rkey"),
        cid = Cid("bafy-$did-$rkey"),
        did = Did(did),
        reason = reason,
    )

    private fun handleFor(did: String): Handle =
        Handle("${did.substringAfterLast(':')}.test")

    private fun repostReason(
        reposterDid: String,
        indexedAtIso: String,
    ): FeedViewPostReasonUnion.ReasonRepost = FeedViewPostReasonUnion.ReasonRepost(
        ReasonRepost(
            by = ProfileViewBasic(
                did = Did(reposterDid),
                handle = handleFor(reposterDid),
            ),
            indexedAt = Instant.parse(indexedAtIso),
        ),
    )

    @Test
    fun timeline_differentAuthors_sameRkey_produceUniqueKeys() {
        val a = skeet(did = "did:plc:alice", rkey = "3mjbkrhqbk22n")
        val b = skeet(did = "did:plc:bob", rkey = "3mjbkrhqbk22n")

        assertEquals("rkeys collide across repos as expected", a.rkey, b.rkey)
        assertNotEquals(
            "lazyListKey() must distinguish different authors with the same rkey",
            a.lazyListKey(),
            b.lazyListKey(),
        )
    }

    @Test
    fun timeline_samePost_seenAsRepostAndOriginal_produceUniqueKeys() {
        val original = skeet(did = "did:plc:alice", rkey = "3kaaa")
        val asRepost = original.copy(
            reason = repostReason("did:plc:bob", "2026-04-12T10:00:00Z"),
        )

        assertNotEquals(
            "original and its repost must not collide",
            original.lazyListKey(),
            asRepost.lazyListKey(),
        )
    }

    @Test
    fun timeline_twoUsersRepostSamePost_produceUniqueKeys() {
        val original = skeet(did = "did:plc:alice", rkey = "3kaaa")
        val repostByBob = original.copy(
            reason = repostReason("did:plc:bob", "2026-04-12T10:00:00Z"),
        )
        val repostByCarol = original.copy(
            reason = repostReason("did:plc:carol", "2026-04-12T10:05:00Z"),
        )

        assertNotEquals(
            "two reposts of the same post by different users must not collide",
            repostByBob.lazyListKey(),
            repostByCarol.lazyListKey(),
        )
    }

    @Test
    fun timeline_wholeFeed_hasNoDuplicateKeys() {
        val feed = listOf(
            skeet("did:plc:alice", "3mjbkrhqbk22n"),
            skeet("did:plc:bob", "3mjbkrhqbk22n"),
            skeet("did:plc:alice", "3zzz"),
            skeet("did:plc:alice", "3kaaa").copy(
                reason = repostReason("did:plc:bob", "2026-04-12T10:00:00Z"),
            ),
            skeet("did:plc:alice", "3kaaa").copy(
                reason = repostReason("did:plc:carol", "2026-04-12T10:05:00Z"),
            ),
            skeet("did:plc:alice", "3kaaa"),
        )

        val keys = feed.map { it.lazyListKey() }
        assertEquals(
            "LazyColumn will crash if any two items share a key",
            keys.size,
            keys.toSet().size,
        )
    }

    private fun profile(did: String) = ProfileView(
        did = Did(did),
        handle = handleFor(did),
    )

    private fun emptyPost() = Post(
        text = "",
        createdAt = Instant.parse("2026-04-12T09:00:00Z"),
    )

    @Test
    fun notifications_distinctRows_produceUniqueKeys() {
        val aliceReply1Cid = Cid("bafy-reply-1")
        val aliceReply2Cid = Cid("bafy-reply-2")
        val parentUri = AtUri("at://did:plc:me/app.bsky.feed.post/3parent")

        val reply1 = Notification.Reply(
            parent = Pair(aliceReply1Cid, parentUri),
            reply = emptyPost(),
            author = profile("did:plc:alice"),
            createdAt = Instant.parse("2026-04-12T10:00:00Z"),
            new = true,
        )
        val reply2 = Notification.Reply(
            parent = Pair(aliceReply2Cid, parentUri),
            reply = emptyPost(),
            author = profile("did:plc:alice"),
            createdAt = Instant.parse("2026-04-12T10:05:00Z"),
            new = true,
        )
        val mention = Notification.Mention(
            parent = Pair(Cid("bafy-mention-1"), parentUri),
            mention = emptyPost(),
            author = profile("did:plc:bob"),
            createdAt = Instant.parse("2026-04-12T10:10:00Z"),
            new = true,
        )
        val follow = Notification.Follow(
            follow = profile("did:plc:carol"),
            createdAt = Instant.parse("2026-04-12T10:15:00Z"),
            new = true,
        )

        val items = listOf(reply1, reply2, mention, follow)
        val keys = items.map { it.uniqueKey() }
        assertEquals(
            "notification keys must be unique across distinct rows",
            keys.size,
            keys.toSet().size,
        )
    }

    @Test
    fun notifications_sameAuthorRepliesTwiceToSameParent_produceUniqueKeys() {
        val parentUri = AtUri("at://did:plc:me/app.bsky.feed.post/3parent")
        val alice = profile("did:plc:alice")

        val first = Notification.Reply(
            parent = Pair(Cid("bafy-reply-first"), parentUri),
            reply = emptyPost(),
            author = alice,
            createdAt = Instant.parse("2026-04-12T10:00:00Z"),
            new = true,
        )
        val second = Notification.Reply(
            parent = Pair(Cid("bafy-reply-second"), parentUri),
            reply = emptyPost(),
            author = alice,
            createdAt = Instant.parse("2026-04-12T10:01:00Z"),
            new = true,
        )

        assertNotEquals(
            "same author replying twice to the same parent must not collide",
            first.uniqueKey(),
            second.uniqueKey(),
        )
    }
}
