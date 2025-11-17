package com.example.netflix.view

import android.app.Activity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.example.netflix.util.WatchProgressManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val savedProgress = remember(profileId, movieId) {
        progressManager.getProgress(profileId, movieId)
    }

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
        if (savedProgress > 0L) {
            player.seekTo(savedProgress)
        }
        player.playWhenReady = true

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    progressManager.clearProgress(profileId, movieId)
                }
            }
        }

        player.addListener(listener)

        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            progressManager.saveProgress(profileId, movieId, player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, profileId, movieId) {
        while (isActive) {
            if (player.isPlaying) {
                progressManager.saveProgress(profileId, movieId, player.currentPosition)
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
