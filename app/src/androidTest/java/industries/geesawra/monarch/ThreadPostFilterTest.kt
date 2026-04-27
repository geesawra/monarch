package industries.geesawra.monarch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.ThreadChainSelection
import industries.geesawra.monarch.datalayer.ThreadPost
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import kotlin.random.Random

class ThreadPostFilterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun threadPostFlatten_hidesBlockedPosts() {
        val rootDid = Did("did:plc:root")
        val aliceDid = Did("did:plc:alice")
        val blockedDid = Did("did:plc:blocked")
        val charlieDid = Did("did:plc:charlie")

        val rootPost = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = rootDid,
                authorName = "Root Author",
                content = "Root post content",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:alice/app.bsky.feed.post/alice"),
                        cid = Cid("alice-cid"),
                        did = aliceDid,
                        authorName = "Alice",
                        content = "Hello from Alice",
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:blocked/app.bsky.feed.post/blocked"),
                        cid = Cid("blocked-cid"),
                        did = blockedDid,
                        authorName = "Blocked User",
                        content = "You should not see this",
                        hidden = true,
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:charlie/app.bsky.feed.post/charlie"),
                        cid = Cid("charlie-cid"),
                        did = charlieDid,
                        authorName = "Charlie",
                        content = "Hello from Charlie",
                    ),
                    level = 1,
                ),
            ),
        )

        val flattened = rootPost.flatten(selection = ThreadChainSelection.Chronological)

        assertEquals(
            "Root + two visible top-level replies = 3",
            3,
            flattened.size,
        )

        assertTrue(
            "No hidden posts should appear",
            flattened.none { it.hidden },
        )

        val names = flattened.map { it.authorName }
        assertTrue("Root Author should be present", names.contains("Root Author"))
        assertTrue("Alice should be present", names.contains("Alice"))
        assertTrue("Charlie should be present", names.contains("Charlie"))
        assertTrue("Blocked User should NOT be present", !names.contains("Blocked User"))

        assertEquals(
            "Root should be focused",
            true,
            flattened.first { it.authorName == "Root Author" }.isFocused,
        )

        assertEquals(
            "Both visible replies should appear as separate chain blocks",
            2,
            flattened.count { it.chainBlockId == 1 } + flattened.count { it.chainBlockId == 2 },
        )
    }

    @Test
    fun threadPostFlatten_singleBranch_deeplyNested() {
        val root = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = Did("did:plc:root"),
                authorName = "Root",
                content = "Root post",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:reply1/app.bsky.feed.post/r1"),
                        cid = Cid("r1-cid"),
                        did = Did("did:plc:reply1"),
                        authorName = "Reply 1",
                        content = "Reply 1 content",
                    ),
                    level = 1,
                    replies = persistentListOf(
                        ThreadPost(
                            post = SkeetData(
                                uri = AtUri("at://did:plc:reply11/app.bsky.feed.post/r1.1"),
                                cid = Cid("r1.1-cid"),
                                did = Did("did:plc:reply11"),
                                authorName = "Reply 1.1",
                                content = "Reply 1.1 content",
                            ),
                            level = 2,
                            replies = persistentListOf(
                                ThreadPost(
                                    post = SkeetData(
                                        uri = AtUri("at://did:plc:reply111/app.bsky.feed.post/r1.1.1"),
                                        cid = Cid("r1.1.1-cid"),
                                        did = Did("did:plc:reply111"),
                                        authorName = "Reply 1.1.1",
                                        content = "Reply 1.1.1 content",
                                    ),
                                    level = 3,
                                ),
                            ),
                        ),
                        ThreadPost(
                            post = SkeetData(
                                uri = AtUri("at://did:plc:reply12/app.bsky.feed.post/r1.2"),
                                cid = Cid("r1.2-cid"),
                                did = Did("did:plc:reply12"),
                                authorName = "Reply 1.2",
                                content = "Reply 1.2 content",
                            ),
                            level = 2,
                        ),
                    ),
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:reply2/app.bsky.feed.post/r2"),
                        cid = Cid("r2-cid"),
                        did = Did("did:plc:reply2"),
                        authorName = "Reply 2",
                        content = "Reply 2 content",
                    ),
                    level = 1,
                    replies = persistentListOf(
                        ThreadPost(
                            post = SkeetData(
                                uri = AtUri("at://did:plc:reply21/app.bsky.feed.post/r2.1"),
                                cid = Cid("r2.1-cid"),
                                did = Did("did:plc:reply21"),
                                authorName = "Reply 2.1",
                                content = "Reply 2.1 content",
                            ),
                            level = 2,
                            replies = persistentListOf(
                                ThreadPost(
                                    post = SkeetData(
                                        uri = AtUri("at://did:plc:reply211/app.bsky.feed.post/r2.1.1"),
                                        cid = Cid("r2.1.1-cid"),
                                        did = Did("did:plc:reply211"),
                                        authorName = "Reply 2.1.1",
                                        content = "Reply 2.1.1 content",
                                    ),
                                    level = 3,
                                ),
                            ),
                        ),
                    ),
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:reply3/app.bsky.feed.post/r3"),
                        cid = Cid("r3-cid"),
                        did = Did("did:plc:reply3"),
                        authorName = "Reply 3",
                        content = "Reply 3 content",
                    ),
                    level = 1,
                ),
            ),
        )

        val flattened = root.flatten(selection = ThreadChainSelection.Chronological)

        assertTrue("No hidden posts should appear", flattened.none { it.hidden })
        assertEquals("Root should be focused", true, flattened.first().isFocused)

        assertTrue("All posts should be marked isInChain", flattened.all { it.isInChain })

        assertEquals(
            "Chronological: Root + (Reply1→1.1→1.1.1) + (Reply2→2.1→2.1.1) + Reply3 = 8 posts",
            8,
            flattened.size,
        )

        val chainBlocks = flattened.groupBy { it.chainBlockId }
        assertEquals("4 distinct chain blocks expected (0=root, 1,2,3=top-level replies)", 4, chainBlocks.size)

        assertEquals("Chain 0 (root) should have 1 post", 1, chainBlocks[0]?.size)
        assertEquals("Chain 1 (Reply1 branch) should have 3 posts", 3, chainBlocks[1]?.size)
        assertEquals("Chain 2 (Reply2 branch) should have 3 posts", 3, chainBlocks[2]?.size)
        assertEquals("Chain 3 (Reply3) should have 1 post", 1, chainBlocks[3]?.size)

        val expandedNames = chainBlocks[1]?.map { it.authorName }.orEmpty()
        assertTrue("Reply 1 should be in chain 1", expandedNames.contains("Reply 1"))
        assertTrue("Reply 1.1 should be in chain 1", expandedNames.contains("Reply 1.1"))
        assertTrue("Reply 1.1.1 should be in chain 1", expandedNames.contains("Reply 1.1.1"))

        assertEquals(
            "Reply 1.2 should NOT appear (skipped sibling)",
            false,
            flattened.any { it.authorName == "Reply 1.2" },
        )
    }

    @Test
    fun threadPostFlatten_mostLikesPicksHighestLikedBranch() {
        val root = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = Did("did:plc:root"),
                authorName = "Root",
                content = "Root",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:low/app.bsky.feed.post/low"),
                        cid = Cid("low-cid"),
                        did = Did("did:plc:low"),
                        authorName = "Low Likes",
                        content = "low",
                        likes = 2,
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:high/app.bsky.feed.post/high"),
                        cid = Cid("high-cid"),
                        did = Did("did:plc:high"),
                        authorName = "High Likes",
                        content = "high",
                        likes = 100,
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:mid/app.bsky.feed.post/mid"),
                        cid = Cid("mid-cid"),
                        did = Did("did:plc:mid"),
                        authorName = "Mid Likes",
                        content = "mid",
                        likes = 50,
                    ),
                    level = 1,
                ),
            ),
        )

        val flattened = root.flatten(selection = ThreadChainSelection.MostLikes)

        assertEquals(
            "Root + all 3 top-level replies = 4 posts (each reply gets its own group)",
            4,
            flattened.size,
        )

        val chain1 = flattened.filter { it.chainBlockId == 1 }.map { it.authorName }
        val chain2 = flattened.filter { it.chainBlockId == 2 }.map { it.authorName }
        val chain3 = flattened.filter { it.chainBlockId == 3 }.map { it.authorName }

        assertTrue(
            "Chain 1 should contain only the highest-liked reply (High Likes), got: $chain1",
            chain1.size == 1 && chain1.contains("High Likes"),
        )

        assertTrue(
            "Mid Likes and Low Likes should appear as chain 2 and 3 (order may vary)",
            setOf("Mid Likes", "Low Likes") == (chain2 + chain3).toSet(),
        )
    }

    @Test
    fun threadPostFlatten_randomProducesDifferentBranchAcrossRuns() {
        val root = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = Did("did:plc:root"),
                authorName = "Root",
                content = "Root",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:a/app.bsky.feed.post/a"),
                        cid = Cid("a-cid"),
                        did = Did("did:plc:a"),
                        authorName = "Branch A",
                        content = "A",
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:b/app.bsky.feed.post/b"),
                        cid = Cid("b-cid"),
                        did = Did("did:plc:b"),
                        authorName = "Branch B",
                        content = "B",
                    ),
                    level = 1,
                ),
            ),
        )

        val run1 = root.flatten(selection = ThreadChainSelection.Random, random = Random(42))
        val run2 = root.flatten(selection = ThreadChainSelection.Random, random = Random(999))

        assertEquals("Both runs should produce 3 posts (root + 2 top-level replies)", 3, run1.size)
        assertEquals("Both runs should produce 3 posts (root + 2 top-level replies)", 3, run2.size)

        assertTrue(
            "Each run should contain Branch A",
            run1.any { it.authorName == "Branch A" } && run2.any { it.authorName == "Branch A" },
        )

        assertTrue(
            "Each run should contain Branch B",
            run1.any { it.authorName == "Branch B" } && run2.any { it.authorName == "Branch B" },
        )
    }

    @Test
    fun showSkeets_renders_singleBranchTree() {
        val random = Random(0)
        val root = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = Did("did:plc:root"),
                authorName = "Root Author",
                content = "Root post content",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:alice/app.bsky.feed.post/alice"),
                        cid = Cid("alice-cid"),
                        did = Did("did:plc:alice"),
                        authorName = "Alice",
                        content = "Hello from Alice",
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:charlie/app.bsky.feed.post/charlie"),
                        cid = Cid("charlie-cid"),
                        did = Did("did:plc:charlie"),
                        authorName = "Charlie",
                        content = "Hello from Charlie",
                    ),
                    level = 1,
                ),
            ),
        )

        val flattened = root.flatten(selection = ThreadChainSelection.Chronological)

        composeTestRule.setContent {
            MaterialTheme {
                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 8.dp),
                    ) {
                        items(flattened) { skeet ->
                            SkeetView(
                                skeet = skeet,
                                nested = false,
                                inThread = true,
                                viewModel = null,
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Root Author").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charlie").assertIsDisplayed()

        assertTrue(
            "All posts should appear (no collapsed flag exists anymore)",
            flattened.none { it.authorName == "Blocked User" },
        )
    }

    @Test
    fun threadPostFlatten_hidesBlockedPosts_visual() {
        val rootDid = Did("did:plc:root")
        val aliceDid = Did("did:plc:alice")
        val blockedDid = Did("did:plc:blocked")
        val charlieDid = Did("did:plc:charlie")

        val rootPost = ThreadPost(
            post = SkeetData(
                uri = AtUri("at://did:plc:root/app.bsky.feed.post/root"),
                cid = Cid("root-cid"),
                did = rootDid,
                authorName = "Root Author",
                content = "Root post content",
            ),
            level = 0,
            replies = persistentListOf(
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:alice/app.bsky.feed.post/alice"),
                        cid = Cid("alice-cid"),
                        did = aliceDid,
                        authorName = "Alice",
                        content = "Hello from Alice",
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:blocked/app.bsky.feed.post/blocked"),
                        cid = Cid("blocked-cid"),
                        did = blockedDid,
                        authorName = "Blocked User",
                        content = "You should not see this blocked post",
                        hidden = true,
                    ),
                    level = 1,
                ),
                ThreadPost(
                    post = SkeetData(
                        uri = AtUri("at://did:plc:charlie/app.bsky.feed.post/charlie"),
                        cid = Cid("charlie-cid"),
                        did = charlieDid,
                        authorName = "Charlie",
                        content = "Hello from Charlie",
                    ),
                    level = 1,
                ),
            ),
        )

        val flattened = rootPost.flatten(selection = ThreadChainSelection.Chronological)

        composeTestRule.setContent {
            MaterialTheme {
                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 8.dp),
                    ) {
                        items(flattened) { skeet ->
                            SkeetView(
                                skeet = skeet,
                                nested = false,
                                inThread = true,
                                viewModel = null,
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Root Author").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charlie").assertIsDisplayed()
        composeTestRule.onNodeWithText("Blocked User").assertDoesNotExist()
        composeTestRule.onNodeWithText("Blocked :(").assertDoesNotExist()
    }
}
