package org.marshsoft.bookreader.ui.screens.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysElements() {
        composeTestRule.setContent {
            BookReaderTheme {
                LoginScreen(
                    onLoginClick = {},
                    onSignUpClick = {},
                    onForgotPasswordClick = {},
                    onGoogleSignInClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter the Library").assertIsDisplayed()
    }

    @Test
    fun loginScreen_clickLogin_triggersCallback() {
        var loginClicked = false
        composeTestRule.setContent {
            BookReaderTheme {
                LoginScreen(
                    onLoginClick = { loginClicked = true },
                    onSignUpClick = {},
                    onForgotPasswordClick = {},
                    onGoogleSignInClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Enter the Library").performClick()
        assert(loginClicked)
    }
}
