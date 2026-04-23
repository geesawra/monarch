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
import industries.geesawra.monarch.datalayer.ThreadPost
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did

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

        val flattened = rootPost.flatten()

        assertEquals(
            "Hidden posts should be excluded from flattened output",
            3,
            flattened.size,
        )

        assertTrue(
            "No SkeetData with hidden=true should appear in flattened list",
            flattened.none { it.hidden },
        )

        val names = flattened.map { it.authorName }
        assertTrue("Root Author should be present", names.contains("Root Author"))
        assertTrue("Alice should be present", names.contains("Alice"))
        assertTrue("Charlie should be present", names.contains("Charlie"))
        assertTrue("Blocked User should NOT be present", !names.contains("Blocked User"))
    }

    @Test
    fun showSkeets_blockedPostsHidden_visual() {
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

        val flattened = rootPost.flatten()

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

        Thread.sleep(Long.MAX_VALUE)
    }
}
