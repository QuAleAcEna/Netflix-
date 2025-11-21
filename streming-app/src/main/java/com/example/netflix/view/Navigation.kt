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
import com.example.netflix.util.NetworkUtils
import com.example.netflix.util.P2PManager
import com.example.netflix.util.P2PServer
import com.example.netflix.util.VideoDownloader
import com.example.netflix.util.UDPBeacon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val videoDownloader = remember(context) { VideoDownloader(context) }

    // Initialize P2P Manager, Server and UDP Beacon
    val p2pManager = remember(context) { P2PManager(context) }
    val p2pServer = remember(context) { P2PServer(8888) }
    val udpBeacon = remember(context) { 
        UDPBeacon(context, 8889, context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir) 
    }

    DisposableEffect(Unit) {
        p2pManager.initialize()
        p2pManager.discoverPeers()
        // Start P2P server to share downloaded files
        p2pServer.start(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir)
        udpBeacon.start()
        onDispose {
            p2pManager.cleanup()
            p2pServer.stop()
            udpBeacon.stop()
        }
    }

    val peers by p2pManager.peers.collectAsState()
    val isConnected by p2pManager.isConnected.collectAsState()
    val discoveredIps by udpBeacon.discoveredIps.collectAsState()

    // Auto connect to first peer for demo purposes
    LaunchedEffect(peers) {
        if (peers.isNotEmpty() && !isConnected) {
            p2pManager.connect(peers[0])
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

            LaunchedEffect(decodedUrl, decodedTitle, isConnected, discoveredIps) {
                try {
                    // 1. Check locally - Offload IO to prevent ANR and catch crashes
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

                    // 2. Check peers if connected or discovered via UDP
                    if (decodedUrl.startsWith("http")) {
                        // Keep maxAttempts and delay as requested
                        val maxAttempts = if (videoUri == null) 25 else 1
                        var foundPeerUrl: String? = null
                        
                        // Short timeout for scanning to keep attempts fast
                        val client = OkHttpClient.Builder()
                            .connectTimeout(1000, TimeUnit.MILLISECONDS)
                            .readTimeout(1000, TimeUnit.MILLISECONDS)
                            .build()
                            
                        val safeTitle = Uri.encode(decodedTitle)

                        for (attempt in 0 until maxAttempts) {
                            val potentialIps = mutableListOf<String>()
                            
                            // Add Wifi-Direct Group Owner
                            if (isConnected) {
                                val ownerAddress = p2pManager.getGroupOwnerAddress()
                                if (ownerAddress != null) {
                                    potentialIps.add(ownerAddress.hostAddress ?: "")
                                }
                                // Fallback to ARP scan
                                potentialIps.addAll(NetworkUtils.getPeerIpAddresses())
                            }
                            // Add UDP Discovered IPs
                            potentialIps.addAll(discoveredIps)

                            if (potentialIps.isNotEmpty()) {
                                withContext(Dispatchers.IO) {
                                    // Parallel scan for faster results
                                    val jobs = potentialIps.distinct().map { ip ->
                                        async {
                                            if (ip.isEmpty() || ip == "0.0.0.0" || ip == "IP") return@async null
                                            val testUrl = "http://$ip:8888/$safeTitle"
                                            try {
                                                val request = Request.Builder().url(testUrl).head().build()
                                                val response = client.newCall(request).execute()
                                                if (response.isSuccessful) {
                                                    response.close()
                                                    return@async testUrl
                                                }
                                                response.close()
                                                null
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                    }
                                    val results = jobs.awaitAll()
                                    foundPeerUrl = results.firstOrNull { it != null }
                                }
                            }

                            if (foundPeerUrl != null) break
                            
                            // Keep delay as requested
                            if (attempt < maxAttempts - 1) {
                                delay(75)
                            }
                        }

                        if (foundPeerUrl != null) {
                            videoUri = foundPeerUrl
                            Log.d("AppNavigation", "Playing from peer: $foundPeerUrl")
                            
                            // Delay download to allow player to buffer and prevent lag/crash due to race conditions or resource contention
                            delay(3000)
                            
                            Log.d("AppNavigation", "Starting peer download: $foundPeerUrl")
                            // Download from peer to become a seeder.
                            // Removed the delay that was preventing download start, wrapped in IO to be safe.
                            withContext(Dispatchers.IO) {
                                try {
                                    videoDownloader.downloadVideo(foundPeerUrl, decodedTitle)
                                } catch (e: Exception) {
                                    Log.e("AppNavigation", "Failed to start peer download", e)
                                }
                            }
                            return@LaunchedEffect
                        }
                    }

                    // 3. Fallback to server URL and download
                    videoUri = decodedUrl
                    if (decodedUrl.startsWith("http")) {
                        // Offload to IO to prevent ANR/Crash on slow disk ops
                        withContext(Dispatchers.IO) {
                            try {
                                videoDownloader.downloadVideo(decodedUrl, decodedTitle)
                            } catch (e: Exception) {
                                Log.e("AppNavigation", "Failed to start fallback download", e)
                            }
                        }
                    }
                    Log.d("AppNavigation", "Playing from server: $decodedUrl")
                
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
