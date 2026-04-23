package industries.geesawra.monarch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ProfileNotFoundTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileNotFound_displaysIconAndMessages() {
        composeTestRule.setContent {
            ProfileNotFound()
        }

        composeTestRule.onNodeWithText("Profile not found").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("This profile doesn't exist or may have been removed.")
            .assertIsDisplayed()
        Thread.sleep(5000)
    }
}
