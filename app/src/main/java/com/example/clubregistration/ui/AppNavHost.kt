package com.example.clubregistration.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("registration") {
            RegistrationScreen(navController = navController)
        }
        composable(
            route = "home/{role}?club={club}&name={name}",
            arguments = listOf(
                navArgument("role") { type = NavType.StringType },
                navArgument("club") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "Member"
            val club = backStackEntry.arguments?.getString("club") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            HomeScreen(navController = navController, role = role, initialClub = club, initialName = name)
        }
    }
}
