package com.example.netflix
import android.annotation.SuppressLint
import android.app.Instrumentation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.netflix.view.AppNavigation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toFile
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType;
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody


class MainActivity : ComponentActivity() {

    // on below line we are creating
    // a variable for our video url.
    var videoUrl = "http://192.168.1.76:80/movie/popeye/1080"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }

    }




}
   //     setContentView(R.layout.activity_main)

        //    playerView = findViewById<PlayerView?>(R.id.playerView)


        // Initialize ExoPlayer
        // player = ExoPlayer.Builder(this).build()
        // playerView.setPlayer(player)


        // Build the MediaItem
        // val uri = Uri.parse(videoUrl)
        // val mediaItem = MediaItem.fromUri(uri)


        // Prepare the player with the media item
        // player.setMediaItem(mediaItem)
        // player.prepare()
        // player.setPlayWhenReady(true) // Start playing when ready

    //override fun onDestroy() {
      //  super.onDestroy()
        // player.release()
    //}




