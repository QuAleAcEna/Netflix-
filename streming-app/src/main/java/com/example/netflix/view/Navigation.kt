package com.example.netflix.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.netflix.util.VideoDownloader
import com.example.netflix.util.TorrentClient
import com.example.netflix.util.TorrentServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoDownloader = remember(context) { VideoDownloader(context) }
    val torrentServer = remember(context) { TorrentServer(9000, context) }
    val torrentClient = remember(context) { TorrentClient(context) }

    DisposableEffect(Unit) {
        torrentServer.start()
        onDispose {
            torrentServer.stop()
        }
    }

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
        composable(
            route = "player/{profileId}/{movieId}/{url}/{title}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.IntType },
                navArgument("movieId") { type = NavType.IntType },
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: -1
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: -1
            val decodedUrl = Uri.decode(encodedUrl)
            val decodedTitle = Uri.decode(title)
            val safeTitle = VideoDownloader.toSafeFileName(decodedTitle)

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

            var videoUri by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(decodedUrl, decodedTitle) {
                try {
                    // 1) Check local storage first
                    val localUri = withContext(Dispatchers.IO) {
                        try {
                            videoDownloader.getLocalVideoUri(decodedUrl, decodedTitle)
                        } catch (e: Exception) {
                            Log.e("AppNavigation", "Error checking local video", e)
                            null
                        }
                    }
                    
                    if (localUri != null) {
                        videoUri = localUri.toString()
                        Log.d("AppNavigation", "Playing locally: $localUri")
                        return@LaunchedEffect
                    }

                    // 2) Try to fetch chunks from a peer (Netty seeder) before hitting the origin server
                    if (decodedUrl.startsWith("http")) {
                        val uri = Uri.parse(decodedUrl)
                        val host = uri.host
                        if (host != null) {
                            val fetchedFromPeer = withContext(Dispatchers.IO) {
                                try {
                                    torrentClient.fetchAndStore(host, 9000, safeTitle)
                                } catch (e: Exception) {
                                    Log.e("AppNavigation", "Torrent sync failed", e)
                                    false
                                }
                            }
                            if (fetchedFromPeer) {
                                val freshLocal = withContext(Dispatchers.IO) {
                                    try {
                                        videoDownloader.getLocalVideoUri(decodedUrl, decodedTitle)
                                    } catch (e: Exception) {
                                        Log.e("AppNavigation", "Error re-checking local video after peer fetch", e)
                                        null
                                    }
                                }
                                if (freshLocal != null) {
                                    videoUri = freshLocal.toString()
                                    Log.d("AppNavigation", "Playing from peer-downloaded file: $freshLocal")
                                    return@LaunchedEffect
                                }
                            }
                        }

                        // 3) Fallback to origin server: play while also downloading in background
                        videoUri = decodedUrl
                        withContext(Dispatchers.IO) {
                            try {
                                videoDownloader.downloadVideo(decodedUrl, decodedTitle)
                            } catch (e: Exception) {
                                Log.e("AppNavigation", "Failed to start download from origin", e)
                            }
                        }
                        Log.d("AppNavigation", "Playing from origin: $decodedUrl")
                    } else {
                        // Non-HTTP sources: just play the provided URI
                        videoUri = decodedUrl
                    }
                
                } catch (e: CancellationException) {
                    throw e // Don't catch cancellation
                } catch (e: Exception) {
                    Log.e("AppNavigation", "Critical error in navigation effect", e)
                    // Ensure video plays from server if logic crashes
                    if (videoUri == null) {
                        videoUri = decodedUrl
                    }
                }
            }

            if (videoUri == null) {
                videoUri = decodedUrl
            }
            PlayerScreen(navController, videoUri!!, profileId, movieId)
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
