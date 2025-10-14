package com.example.netflix.view

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.net.Uri
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        // Splash Screen
        composable("splash") {
            SplashScreen(navController)
        }

        // Sign In Screen
        composable("signin") {
            SignInScreen(navController)
        }

        // Movie List Screen (Home)
        composable("home") {
            MovieListScreen(navController)
        }

        // Player Screen
        composable("player/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val decodedUrl = Uri.decode(encodedUrl)  // decode safely before using
            PlayerScreen(navController, decodedUrl)
        }
    }
}