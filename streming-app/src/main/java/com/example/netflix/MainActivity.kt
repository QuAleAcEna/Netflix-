package com.example.netflix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView


class MainActivity : ComponentActivity() {

    // on below line we are creating
    // a variable for our video url.
    var videoUrl = "http://192.168.1.76:80/movie/popeye/1080"

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        playerView = findViewById<PlayerView?>(R.id.playerView)


        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.setPlayer(player)


        // Build the MediaItem
        val uri = Uri.parse(videoUrl)
        val mediaItem = MediaItem.fromUri(uri)


        // Prepare the player with the media item
        player.setMediaItem(mediaItem)
        player.prepare()
        player.setPlayWhenReady(true) // Start playing when ready
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }


}
