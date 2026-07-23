package org.marshsoft.bookreader.ui.screens.signup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class SignUpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun signUpScreen_displaysElements() {
        composeTestRule.setContent {
            BookReaderTheme {
                SignUpScreen(
                    onCloseClick = {},
                    onGoogleSignUpClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Join the Sanctuary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign up with Google").assertIsDisplayed()
    }
}
