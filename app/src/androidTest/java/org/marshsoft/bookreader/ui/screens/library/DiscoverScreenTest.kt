package org.marshsoft.bookreader.ui.screens.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun discoverScreen_displaysElements() {
        composeTestRule.setContent {
            BookReaderTheme {
                DiscoverScreen()
            }
        }

        composeTestRule.onNodeWithText("Curated Collections").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trending Now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Explore Genres").assertIsDisplayed()
    }
}
