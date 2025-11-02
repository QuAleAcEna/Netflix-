package com.example.netflix.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.netflix.util.VideoDownloader

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoDownloader = VideoDownloader(context)

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
        composable("player/{url}/{title}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val decodedUrl = Uri.decode(encodedUrl)
            val decodedTitle = Uri.decode(title)

            // Lock orientation to landscape for the player
            val activity = context.findActivity()
            if (activity != null) {
                DisposableEffect(Unit) {
                    val originalOrientation = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    onDispose {
                        // restore original orientation
                        activity.requestedOrientation = originalOrientation
                    }
                }
            }

            PlayerScreen(navController, decodedUrl)

            // Only download if the URL is a remote one
            if (decodedUrl.startsWith("http")) {
                LaunchedEffect(decodedUrl) {
                    videoDownloader.downloadVideo(decodedUrl, decodedTitle)
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
