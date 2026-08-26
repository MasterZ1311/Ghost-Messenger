package org.ghostmessenger.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Chat : Screen("chat/{userCode}") {
        fun createRoute(userCode: String) = "chat/$userCode"
    }
    data object Settings : Screen("settings")
}
