package com.example.netflix.view

import android.app.DownloadManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

private suspend fun downloadVideo(context:Context, videoUrl: String, fileName: String = "video.mp4") {
    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()

    withContext(Dispatchers.IO) {
        val movieDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MyVideos")
        if (!movieDir.exists()) movieDir.mkdirs()
        val file = File(movieDir, fileName)

        URL(videoUrl).openStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
    Toast.makeText(context, "Download completed", Toast.LENGTH_SHORT).show()

}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
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
            LaunchedEffect(Unit){
                downloadVideo(context,decodedUrl, "a.mp4")
            }
        }
    }
}