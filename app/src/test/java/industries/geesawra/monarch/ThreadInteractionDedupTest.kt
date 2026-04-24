package industries.geesawra.monarch

import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRef
import industries.geesawra.monarch.datalayer.SkeetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import kotlin.time.Instant

class ThreadInteractionDedupTest {

    private fun handle(did: String) = Handle("${did.substringAfterLast(':')}.test")

    private fun post(
        did: String,
        rkey: String,
        rootCid: Cid? = null,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        reason: FeedViewPostReasonUnion? = null,
    ): SkeetData = SkeetData(
        uri = AtUri("at://$did/app.bsky.feed.post/$rkey"),
        cid = Cid("bafy-$did-$rkey"),
        did = Did(did),
        reason = reason,
        content = "post $rkey",
        authorName = "User $did",
        authorHandle = handle(did),
        createdAt = createdAt,
        cachedRoot = rootCid?.let {
            SkeetData(
                uri = AtUri("at://$did/app.bsky.feed.post/root"),
                cid = it,
                did = Did(did),
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                authorHandle = handle(did),
                authorName = "Root author",
            )
        },
    )

    private fun repost(
        did: String,
        rkey: String,
        rootCid: Cid? = null,
        reposterDid: String,
        indexedAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ): SkeetData = post(did, rkey, rootCid, indexedAt).copy(
        reason = FeedViewPostReasonUnion.ReasonRepost(
            app.bsky.feed.ReasonRepost(
                by = app.bsky.actor.ProfileViewBasic(
                    did = Did(reposterDid),
                    handle = handle(reposterDid),
                ),
                indexedAt = indexedAt,
            ),
        ),
    )

    @Test
    fun firstSeenWins_whenToggleOff() {
        val rootCid = Cid("bafy-root")
        val reply1 = post("did:plc:alice", "reply1", rootCid, Instant.parse("2026-01-01T10:00:00Z"))
        val reply2 = post("did:plc:bob", "reply2", rootCid, Instant.parse("2026-01-01T12:00:00Z"))
        val reply3 = post("did:plc:carol", "reply3", rootCid, Instant.parse("2026-01-01T14:00:00Z"))

        val data = listOf(reply1, reply2, reply3)

        // Simulate first-seen dedup (toggle OFF)
        val seenRootCids = mutableSetOf<Cid>()
        val filtered = data.filter {
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val threadCid = it.threadRootCid() ?: return@filter true
            seenRootCids.add(threadCid)
        }

        assertEquals("First-seen should keep only the first reply", 1, filtered.size)
        assertEquals("First reply should be kept", reply1.cid, filtered[0].cid)
    }

    @Test
    fun latestWins_whenToggleOn() {
        val rootCid = Cid("bafy-root")
        val reply1 = post("did:plc:alice", "reply1", rootCid, Instant.parse("2026-01-01T10:00:00Z"))
        val reply2 = post("did:plc:bob", "reply2", rootCid, Instant.parse("2026-01-01T12:00:00Z"))
        val reply3 = post("did:plc:carol", "reply3", rootCid, Instant.parse("2026-01-01T14:00:00Z"))

        val data = listOf(reply1, reply2, reply3)

        // Simulate latest-wins dedup (toggle ON)
        val latestPerRoot = data
            .filter { it.reply != null && it.threadRootCid() != null }
            .groupBy { it.threadRootCid() }
            .mapValues { it.value.maxByOrNull { it.createdAt ?: Instant.fromEpochMilliseconds(0) } }
            .mapNotNull { it.value }
            .toSet()

        val filtered = data.filter {
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val isTopLevel = it.reply == null
            if (isTopLevel) return@filter true
            latestPerRoot.contains(it)
        }

        assertEquals("Latest-wins should keep only the latest reply", 1, filtered.size)
        assertEquals("Latest reply (carol) should be kept", reply3.cid, filtered[0].cid)
    }

    @Test
    fun repostsAreNeverDeduplicated() {
        val rootCid = Cid("bafy-root")
        val reply1 = post("did:plc:alice", "reply1", rootCid, Instant.parse("2026-01-01T10:00:00Z"))
        val reply2 = post("did:plc:bob", "reply2", rootCid, Instant.parse("2026-01-01T12:00:00Z"))
        val repost1 = repost("did:plc:dave", "repost1", rootCid, "did:plc:dave", Instant.parse("2026-01-01T11:00:00Z"))
        val repost2 = repost("did:plc:eve", "repost2", rootCid, "did:plc:eve", Instant.parse("2026-01-01T13:00:00Z"))

        val data = listOf(reply1, reply2, repost1, repost2)

        // Simulate latest-wins dedup (reposts excluded from latestPerRoot)
        val latestPerRoot = data
            .filter {
                val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
                if (isRepost) return@filter false
                it.reply != null && it.threadRootCid() != null
            }
            .groupBy { it.threadRootCid() }
            .mapValues { it.value.maxByOrNull { it.createdAt ?: Instant.fromEpochMilliseconds(0) } }
            .mapNotNull { it.value }
            .toSet()

        val filtered = data.filter {
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val isTopLevel = it.reply == null
            if (isTopLevel) return@filter true
            latestPerRoot.contains(it)
        }

        assertEquals("Should have 3 items: 1 latest reply + 2 reposts", 3, filtered.size)
        assertTrue("Latest reply should be present", filtered.any { it.cid == reply2.cid })
        assertTrue("First repost should be present", filtered.any { it.cid == repost1.cid })
        assertTrue("Second repost should be present", filtered.any { it.cid == repost2.cid })
    }

    @Test
    fun topLevelPostsAreNeverDeduplicated() {
        val topLevel1 = post("did:plc:alice", "top1", null)
        val topLevel2 = post("did:plc:bob", "top2", null)
        val reply = post("did:plc:carol", "reply", Cid("bafy-root"))

        val data = listOf(topLevel1, topLevel2, reply)

        // Simulate latest-wins dedup
        val latestPerRoot = data
            .filter { it.reply != null && it.threadRootCid() != null }
            .groupBy { it.threadRootCid() }
            .mapValues { it.value.maxByOrNull { it.createdAt ?: Instant.fromEpochMilliseconds(0) } }
            .mapNotNull { it.value }
            .toSet()

        val filtered = data.filter {
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val isTopLevel = it.reply == null
            if (isTopLevel) return@filter true
            latestPerRoot.contains(it)
        }

        assertEquals("All 3 items should pass through", 3, filtered.size)
    }

    @Test
    fun multipleRoots_eachGetsLatest() {
        val root1 = Cid("bafy-root-1")
        val root2 = Cid("bafy-root-2")

        val r1a = post("did:plc:alice", "r1a", root1, Instant.parse("2026-01-01T10:00:00Z"))
        val r1b = post("did:plc:bob", "r1b", root1, Instant.parse("2026-01-01T12:00:00Z"))
        val r2a = post("did:plc:carol", "r2a", root2, Instant.parse("2026-01-01T08:00:00Z"))
        val r2b = post("did:plc:dave", "r2b", root2, Instant.parse("2026-01-01T09:00:00Z"))

        val data = listOf(r1a, r1b, r2a, r2b)

        val latestPerRoot = data
            .filter { it.reply != null && it.threadRootCid() != null }
            .groupBy { it.threadRootCid() }
            .mapValues { it.value.maxByOrNull { it.createdAt ?: Instant.fromEpochMilliseconds(0) } }
            .mapNotNull { it.value }
            .toSet()

        val filtered = data.filter {
            val isRepost = it.reason is FeedViewPostReasonUnion.ReasonRepost
            if (isRepost) return@filter true
            val isTopLevel = it.reply == null
            if (isTopLevel) return@filter true
            latestPerRoot.contains(it)
        }

        assertEquals("Should have 2 latest replies (one per root)", 2, filtered.size)
        assertTrue("Latest of root1 (r1b) should be present", filtered.any { it.cid == r1b.cid })
        assertTrue("Latest of root2 (r2b) should be present", filtered.any { it.cid == r2b.cid })
    }
}
