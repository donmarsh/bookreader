package org.marshsoft.bookreader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.marshsoft.bookreader.ui.screens.library.LibraryScreen
import org.marshsoft.bookreader.ui.screens.login.LoginScreen
import org.marshsoft.bookreader.ui.screens.reader.ReaderScreen
import org.marshsoft.bookreader.ui.screens.settings.SettingsScreen
import org.marshsoft.bookreader.ui.screens.signup.SignUpScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    pendingBookUri: Uri? = null,
    onPendingBookUriHandled: (Uri) -> Unit = {},
    startDestination: String = Screen.Library.route,

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
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onCloseClick = { navController.popBackStack() },
                onGoogleSignUpClick = { navController.navigate(Screen.Library.route) }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onMenuClick = onMenuClick,
                onBookClick = { bookId -> navController.navigate(Screen.Reader.createRoute(bookId)) },
                onLoginClick = { navController.navigate(Screen.Login.route) },
                pendingBookUri = pendingBookUri,
                onPendingBookUriHandled = onPendingBookUriHandled
            )
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
                onBackClick = { navController.navigateUp() }
            )
        }
    }
}
