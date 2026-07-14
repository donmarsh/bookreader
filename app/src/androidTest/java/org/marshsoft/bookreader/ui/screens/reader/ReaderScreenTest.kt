package org.marshsoft.bookreader.ui.screens.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readerScreen_displaysElements() {
        composeTestRule.setContent {
            BookReaderTheme {
                ReaderScreen(
                    bookId = "1",
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("The Architecture of Silence").assertIsDisplayed()
        composeTestRule.onNodeWithText("CHAPTER IV").assertIsDisplayed()
    }
}
