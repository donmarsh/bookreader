package org.marshsoft.bookreader.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
}
