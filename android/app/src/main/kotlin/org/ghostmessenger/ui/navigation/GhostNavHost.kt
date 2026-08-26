package org.ghostmessenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.ghostmessenger.ui.chat.ChatScreen
import org.ghostmessenger.ui.chat.ChatViewModel
import org.ghostmessenger.ui.home.HomeScreen
import org.ghostmessenger.ui.home.HomeViewModel
import org.ghostmessenger.ui.onboarding.OnboardingScreen
import org.ghostmessenger.ui.onboarding.OnboardingViewModel
import org.ghostmessenger.ui.settings.SettingsScreen
import org.ghostmessenger.ui.settings.SettingsViewModel

@Composable
fun GhostNavHost(
    hasIdentity: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (hasIdentity) Screen.Home.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            val viewModel = hiltViewModel<OnboardingViewModel>()
            OnboardingScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val viewModel = hiltViewModel<HomeViewModel>()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToChat = { userCode ->
                    navController.navigate(Screen.Chat.createRoute(userCode))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("userCode") { type = NavType.StringType }
            )
        ) {
            val viewModel = hiltViewModel<ChatViewModel>()
            ChatScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel = hiltViewModel<SettingsViewModel>()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVaultPurged = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
