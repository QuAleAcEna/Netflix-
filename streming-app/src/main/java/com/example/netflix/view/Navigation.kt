package com.example.netflix.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.netflix.util.VideoDownloader
import com.example.netflix.util.TorrentClient
import com.example.netflix.util.TorrentServer
import com.example.netflix.util.PeerDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoDownloader = remember(context) { VideoDownloader(context) }
    val torrentServer = remember(context) { TorrentServer(9000, context) }
    val torrentClient = remember(context) { TorrentClient(context) }
    val peerDiscovery = remember(context) { PeerDiscovery(context) }
    
    val peerFlow = remember(peerDiscovery) { peerDiscovery.discoverPeers() }
    val discoveredPeersState = peerFlow.collectAsState(initial = emptyList())
    
    var retryTrigger by remember { mutableIntStateOf(0) }

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
        composable("splash") { SplashScreen(navController) }
        composable("signin") { SignInScreen(navController) }
        
        composable(
            route = "profiles/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.IntType },
                navArgument("userName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: -1
            val encodedUserName = backStackEntry.arguments?.getString("userName") ?: ""
            val userName = if (encodedUserName.isNotEmpty()) Uri.decode(encodedUserName) else ""
            ProfileSelectionScreen(navController, userId = userId, accountName = userName)
        }

        composable(
            route = "home/{userId}/{accountName}/{profileId}/{profileName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.IntType },
                navArgument("accountName") { type = NavType.StringType; defaultValue = "_" },
                navArgument("profileId") { type = NavType.IntType },
                navArgument("profileName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: -1
            val encodedAccountName = backStackEntry.arguments?.getString("accountName") ?: "_"
            val accountName = Uri.decode(encodedAccountName).takeIf { it != "_" } ?: ""
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: -1
            val encodedProfileName = backStackEntry.arguments?.getString("profileName") ?: ""
            val profileName = if (encodedProfileName.isNotEmpty()) Uri.decode(encodedProfileName) else null
            MovieListScreen(navController, userId, accountName, profileId, profileName)
        }

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

            val activity = context.findActivity()
            if (activity != null) {
                DisposableEffect(Unit) {
                    val originalOrientation = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    onDispose { activity.requestedOrientation = originalOrientation }
                }
            }

            var videoUri by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(decodedUrl, decodedTitle, retryTrigger) {
                try {
                    videoUri = null 

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

                    var startedStreamingFromPeer = false
                    if (decodedUrl.startsWith("http")) {
                        Log.d("AppNavigation", "Scanning for peers...")
                        
                        // Retry logic: Attempt to find peers and manifest up to 3 times
                        // This handles cases where peer discovery is slow or initial connection fails
                        repeat(3) { attempt ->
                            if (startedStreamingFromPeer) return@repeat // Break if already successful

                            val initialPeers = withTimeoutOrNull(2500) { // Slightly reduced timeout per attempt
                                 snapshotFlow { discoveredPeersState.value }
                                     .first { it.isNotEmpty() }
                            } ?: discoveredPeersState.value

                            if (initialPeers.isNotEmpty()) {
                                val peerIps = initialPeers.mapNotNull { it.hostAddress }.filter { it.isNotEmpty() }
                                
                                if (peerIps.isNotEmpty()) {
                                    Log.d("AppNavigation", "Attempt $attempt/3: Found ${peerIps.size} peers. Checking for manifest...")
                                    
                                    var manifest: com.example.netflix.util.TorrentManifest? = null
                                    
                                    // Try all peers in this attempt
                                    for (peerIp in peerIps) {
                                        manifest = torrentClient.fetchMetadata(peerIp, 9000, safeTitle)
                                        if (manifest != null) {
                                            break
                                        }
                                    }

                                    if (manifest != null) {
                                        Log.d("AppNavigation", "Starting swarm download from ${peerIps.size} peers.")
                                        
                                        torrentClient.downloadContent(peerIps, 9000, safeTitle, manifest, onFailure = {
                                            Log.e("AppNavigation", "Swarm download failed. Switching to origin.")
                                            scope.launch {
                                                if (videoUri?.startsWith("http://127.0.0.1") == true) {
                                                    videoUri = decodedUrl 
                                                }
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        videoDownloader.downloadVideo(decodedUrl, decodedTitle)
                                                    } catch (e: Exception) {
                                                        Log.e("AppNavigation", "Failed to start fallback download", e)
                                                    }
                                                }
                                            }
                                        })

                                        videoUri = "http://127.0.0.1:9000/stream/$safeTitle"
                                        startedStreamingFromPeer = true
                                        
                                        scope.launch(Dispatchers.IO) {
                                            while(isActive) {
                                                delay(3000)
                                                val local = videoDownloader.getLocalVideoUri(decodedUrl, decodedTitle)
                                                if (local != null) {
                                                    withContext(Dispatchers.Main) {
                                                        Log.d("AppNavigation", "Assembly complete. Switching to local file.")
                                                        videoUri = local.toString()
                                                    }
                                                    break
                                                }
                                                if (videoUri?.startsWith("http://127.0.0.1") != true && videoUri?.startsWith("file") != true && videoUri != decodedUrl) {
                                                    break
                                                }
                                            }
                                        }
                                        return@repeat // Success, exit retry loop
                                    } else {
                                        Log.w("AppNavigation", "Attempt $attempt/3: Peers found but no manifest available. Retrying...")
                                    }
                                }
                            } else {
                                Log.d("AppNavigation", "Attempt $attempt/3: No peers found yet.")
                            }
                            
                            // Wait a bit before next attempt if we haven't succeeded
                            if (attempt < 2) delay(1000)
                        }

                        if (!startedStreamingFromPeer) {
                             Log.d("AppNavigation", "Falling back to origin server.")
                             videoUri = decodedUrl
                             withContext(Dispatchers.IO) {
                                 try {
                                     videoDownloader.downloadVideo(decodedUrl, decodedTitle)
                                 } catch (e: Exception) {
                                     Log.e("AppNavigation", "Failed to start download from origin", e)
                                 }
                             }
                             
                             scope.launch(Dispatchers.IO) {
                                while(isActive) {
                                    delay(3000)
                                    val local = videoDownloader.getLocalVideoUri(decodedUrl, decodedTitle)
                                    if (local != null) {
                                        withContext(Dispatchers.Main) {
                                            Log.d("AppNavigation", "Server download complete. Switching to local file.")
                                            videoUri = local.toString()
                                        }
                                        break
                                    }
                                    if (videoUri != decodedUrl && videoUri?.startsWith("file") != true) {
                                        break
                                    }
                                }
                             }
                        }
                    } else {
                        videoUri = decodedUrl
                    }
                
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AppNavigation", "Critical error", e)
                    if (videoUri == null) videoUri = decodedUrl
                }
            }

            if (videoUri != null) {
                key(videoUri) {
                    PlayerScreen(
                        navController = navController,
                        videoUrl = videoUri!!,
                        profileId = profileId,
                        movieId = movieId,
                        onPlayerError = { errorMessage ->
                            Log.e("AppNavigation", "Player Error: $errorMessage")
                            if (videoUri?.startsWith("file") == true) {
                                Log.e("AppNavigation", "Local file corrupted. Deleting and retrying...")
                                videoDownloader.deleteVideoAndChunks(decodedTitle)
                                retryTrigger++ 
                            }
                        }
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
