package com.example.netflix.view

import android.app.Activity
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
    movieId: Int
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
    
    var retryCount by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)

        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val mediaItem = MediaItem.fromUri(videoUrl.toUri())
        player.setMediaItem(mediaItem)
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
                // Reset retry count on successful playback (if we get to READY state)
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < 3) {
                    retryCount++
                    Toast.makeText(context, "Connection lost. Retrying... ($retryCount/3)", Toast.LENGTH_SHORT).show()
                    // Re-prepare player to retry
                    player.prepare()
                    player.play()
                } else {
                    Toast.makeText(context, "Connection failed. Returning to login.", Toast.LENGTH_LONG).show()
                    // Navigate back to login screen
                    navController.navigate("signin") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        player.addListener(listener)

        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            val finalPosition = player.currentPosition
            val shouldClear = player.playbackState == Player.STATE_ENDED ||
                (player.duration > 0 && finalPosition >= player.duration - 750)
            coroutineScope.launch {
                if (shouldClear) {
                    progressManager.clearProgress(profileId, movieId)
                    runCatching { progressRepository.clearProgress(profileId, movieId) }
                } else if (finalPosition > 0L) {
                    progressManager.saveProgress(profileId, movieId, finalPosition)
                    runCatching { progressRepository.saveProgress(profileId, movieId, finalPosition) }
                }
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(profileId, movieId) {
        val response = runCatching { progressRepository.getProgress(profileId, movieId) }.getOrNull()
        if (response != null && response.isSuccessful) {
            response.body()?.let { remote ->
                if (remote.positionMs > 0L) {
                    hasAppliedResume = false
                    resumePosition = remote.positionMs
                    progressManager.saveProgress(profileId, movieId, remote.positionMs)
                }
            }
        }
    }

    LaunchedEffect(player, resumePosition) {
        if (!hasAppliedResume && resumePosition > 0L) {
            player.seekTo(resumePosition)
            hasAppliedResume = true
        }
    }

    LaunchedEffect(player, profileId, movieId) {
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
            }
        }
    )
}
