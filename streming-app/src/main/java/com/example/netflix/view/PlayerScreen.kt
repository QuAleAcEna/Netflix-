package com.example.netflix.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.example.netflix.repository.ProgressRepository
import com.example.netflix.util.WatchProgressManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val REMOTE_SYNC_INTERVAL_MS = 5_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    videoUrl: String,
    profileId: Int,
    movieId: Int,
    onPlayerError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val view = LocalView.current
    val progressManager = remember { WatchProgressManager(context) }
    val progressRepository = remember { ProgressRepository() }
    val coroutineScope = rememberCoroutineScope()
    var resumePosition by remember(profileId, movieId) {
        mutableStateOf(progressManager.getProgress(profileId, movieId))
    }
    var hasAppliedResume by remember(player) { mutableStateOf(false) }
    
    // Lock immersive mode and Keep Screen On
    DisposableEffect(Unit) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)

        // Enable immersive mode
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Effect to handle player setup and cleanup, RE-RUNS when videoUrl changes.
    DisposableEffect(videoUrl) {
        val mediaItem = MediaItem.fromUri(videoUrl.toUri())
        
        // Preserve position when switching sources
        val currentPosition = if (player.currentMediaItem != null) player.currentPosition else 0L
        if (currentPosition > 0) {
            resumePosition = currentPosition
            hasAppliedResume = false 
        }

        player.setMediaItem(mediaItem, resumePosition)
        player.prepare()
        player.playWhenReady = true

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    progressManager.clearProgress(profileId, movieId)
                    coroutineScope.launch {
                        runCatching { progressRepository.clearProgress(profileId, movieId) }
                    }
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                // Report error back to parent
                onPlayerError(error.message ?: "Unknown player error")
            }
        }

        player.addListener(listener)

        onDispose {
            val finalPosition = player.currentPosition
            if (finalPosition > 0L) {
                 progressManager.saveProgress(profileId, movieId, finalPosition)
                 coroutineScope.launch {
                    runCatching { progressRepository.saveProgress(profileId, movieId, finalPosition) }
                }
            }
            player.removeListener(listener)
            player.stop() 
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            player.release() 
        }
    }

    LaunchedEffect(player) {
        var lastSyncedPosition = 0L
        while (isActive) {
            if (player.isPlaying) {
                val currentPosition = player.currentPosition
                progressManager.saveProgress(profileId, movieId, currentPosition)
                if (abs(currentPosition - lastSyncedPosition) >= REMOTE_SYNC_INTERVAL_MS) {
                    runCatching { progressRepository.saveProgress(profileId, movieId, currentPosition) }
                    lastSyncedPosition = currentPosition
                }
            }
            delay(1000)
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
            }
        }
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
