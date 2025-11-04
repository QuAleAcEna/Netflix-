package com.example.netflix.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import java.io.File
class VideoDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("video_downloader_prefs", Context.MODE_PRIVATE)
    @Synchronized
    fun downloadVideo(url: String, title: String) {
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), title)
        val tempFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "$title.tmp")

        if (finalFile.exists() || tempFile.exists()) {
            return
        }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(title)
            .setDescription("Downloading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(tempFile))

        val downloadId = downloadManager.enqueue(request)
        prefs.edit()
            .putLong("download_id_$url", downloadId)
            .putString("temp_path_$url", tempFile.absolutePath)
            .apply()
    }

    fun isDownloaded(url: String, title: String): Boolean {
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), title)
        if (finalFile.exists()) {
            return true
        }

        val downloadId = prefs.getLong("download_id_$url", -1L)
        if (downloadId == -1L) return false

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    val tempPath = prefs.getString("temp_path_$url", null)
                    if (tempPath != null) {
                        val tempFile = File(tempPath)
                        if (tempFile.exists()) {
                            finalFile.parentFile?.mkdirs()
                            if (tempFile.renameTo(finalFile)) {
                                prefs.edit()
                                    .remove("download_id_$url")
                                    .remove("temp_path_$url")
                                    .apply()
                                return true
                            } else {
                                // If rename fails, delete the temp file to allow redownload
                                tempFile.delete()
                                prefs.edit()
                                    .remove("download_id_$url")
                                    .remove("temp_path_$url")
                                    .apply()
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    fun getLocalVideoUri(url: String, title: String): Uri? {
        val isDownloadFinish = isDownloaded(url, title)
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), title)
        // isDownloaded will handle the rename if needed.
        if ( isDownloadFinish && finalFile.exists()) {
            return finalFile.toUri()
        }
        return null
    }
}