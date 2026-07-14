package org.marshsoft.bookreader.ui.screens.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class ForgotPasswordScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun forgotPasswordScreen_displaysElements() {
        composeTestRule.setContent {
            BookReaderTheme {
                ForgotPasswordScreen(
                    onSendLinkClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Forgot Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send Reset Link").assertIsDisplayed()
    }
}
