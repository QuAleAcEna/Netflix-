package com.example.netflix.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.netflix.util.VideoDownloader
import androidx.navigation.NavType
import androidx.navigation.navArgument

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoDownloader = remember(context) { VideoDownloader(context) }

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

        // Profile Selection Screen
        composable(
            route = "profiles/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") {
                    type = NavType.IntType
                },
                navArgument("userName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: -1
            val encodedUserName = backStackEntry.arguments?.getString("userName") ?: ""
            val userName = if (encodedUserName.isNotEmpty()) Uri.decode(encodedUserName) else ""
            ProfileSelectionScreen(navController, userId = userId, accountName = userName)
        }

        // Movie List Screen (Home)
        composable(
            route = "home/{userId}/{accountName}/{profileId}/{profileName}",
            arguments = listOf(
                navArgument("userId") {
                    type = NavType.IntType
                },
                navArgument("accountName") {
                    type = NavType.StringType
                    defaultValue = "_"
                },
                navArgument("profileId") {
                    type = NavType.IntType
                },
                navArgument("profileName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: -1
            val encodedAccountName = backStackEntry.arguments?.getString("accountName") ?: "_"
            val accountNameDecoded = Uri.decode(encodedAccountName)
            val accountName = accountNameDecoded.takeIf { it != "_" } ?: ""
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: -1
            val encodedProfileName = backStackEntry.arguments?.getString("profileName") ?: ""
            val profileName = if (encodedProfileName.isNotEmpty()) {
                Uri.decode(encodedProfileName)
            } else {
                null
            }
            MovieListScreen(
                navController,
                userId = userId,
                accountName = accountName,
                profileId = profileId,
                profileName = profileName
            )
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

            val localUri = videoDownloader.getLocalVideoUri(decodedUrl, decodedTitle)
            val videoUri = localUri?.toString() ?: decodedUrl

            PlayerScreen(navController, videoUri)

            // Only download if the URL is a remote one and not already downloaded
            if (decodedUrl.startsWith("http") && localUri == null) {
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
