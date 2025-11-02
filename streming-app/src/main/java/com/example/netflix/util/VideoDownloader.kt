package com.example.netflix.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class VideoDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    fun downloadVideo(url: String, title: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), title)

        // If file already exists, don't download it again
        if (file.exists()) {
            return
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, title)

        downloadManager.enqueue(request)
    }
}
