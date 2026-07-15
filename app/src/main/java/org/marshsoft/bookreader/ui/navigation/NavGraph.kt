package org.marshsoft.bookreader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.marshsoft.bookreader.ui.screens.library.DiscoverScreen
import org.marshsoft.bookreader.ui.screens.library.LibraryScreen
import org.marshsoft.bookreader.ui.screens.login.ForgotPasswordScreen
import org.marshsoft.bookreader.ui.screens.login.LoginScreen
import org.marshsoft.bookreader.ui.screens.reader.ReaderScreen
import org.marshsoft.bookreader.ui.screens.settings.SettingsScreen
import org.marshsoft.bookreader.ui.screens.signup.SignUpScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onMenuClick: () -> Unit,
    startDestination: String = Screen.Library.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onGoogleSignInClick = { navController.navigate(Screen.Library.route) }
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onSendLinkClick = { /* TODO: Implement email sending logic */ },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpClick = { navController.navigate(Screen.Library.route) },
                onSignInClick = { navController.navigate(Screen.Login.route) },
                onCloseClick = { navController.popBackStack() },
                onGoogleSignUpClick = { navController.navigate(Screen.Library.route) }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onMenuClick = onMenuClick,
                onBookClick = { bookId -> navController.navigate(Screen.Reader.createRoute(bookId)) }
            )
        }
        composable(Screen.Discover.route) {
            DiscoverScreen()
        }
        composable(Screen.Profile.route) {
            // Placeholder for Profile
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Profile Screen")
            }
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Screen.Login.route) }
            )
        }
        composable(Screen.Reader.route) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            ReaderScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
