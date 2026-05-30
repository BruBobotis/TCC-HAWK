package com.example.tcc_hawk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tcc_hawk.ui.screens.alerts.AlertFullScreen
import com.example.tcc_hawk.ui.screens.alerts.AlertsScreen
import com.example.tcc_hawk.ui.screens.auth.AuthScreen
import com.example.tcc_hawk.ui.screens.main.MainScreen
import com.example.tcc_hawk.ui.screens.onboarding.OnboardingScreen
import com.example.tcc_hawk.ui.screens.splash.SplashScreen
import com.example.tcc_hawk.ui.viewmodel.DashboardViewModel
import com.example.tcc_hawk.ui.viewmodel.AlertsViewModel
import androidx.navigation.NavController


object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
}

@Composable
fun AppNav(startDestination: String = Routes.SPLASH, dashboardVm: DashboardViewModel, alertsVm:AlertsViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = startDestination) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onDone = {
                    nav.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onLoggedIn = { hasProfile ->
                    nav.navigate(if (hasProfile) Routes.MAIN else Routes.ONBOARDING) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                dashboardVm = dashboardVm,
                alertsVm = alertsVm,
                navController = nav
            )
        }

        composable("alerts") {
            AlertsScreen(vm = alertsVm, navController = nav)
        }

        composable("alert/{type}") { backStack ->
            val type = backStack.arguments?.getString("type") ?: "GENERAL"
            AlertFullScreen(type = type, onDismiss = { nav.popBackStack() })
        }

    }
}