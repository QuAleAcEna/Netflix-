package com.example.netflix.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class VideoDownloader(private val context: Context) {

    private val client = OkHttpClient()
    // Scope tied to the class instance, which is remembered in AppNavigation
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableSetOf<String>()

    private fun getSafeFileName(title: String): String {
        return title.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
    }

    fun downloadVideo(url: String, title: String) {
        val safeTitle = getSafeFileName(title)
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeTitle)
        
        if (finalFile.exists() || activeDownloads.contains(safeTitle)) {
            return
        }

        activeDownloads.add(safeTitle)
        
        scope.launch {
            try {
                val tempFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "$safeTitle.tmp")
                if (tempFile.exists()) tempFile.delete()

                Log.d("VideoDownloader", "Starting download: $url -> ${tempFile.name}")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e("VideoDownloader", "Download failed: ${response.code}")
                    return@launch
                }

                val body = response.body ?: return@launch
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)
                
                val buffer = ByteArray(8192)
                var bytesRead = 0
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                response.close()
                
                if (tempFile.renameTo(finalFile)) {
                    Log.d("VideoDownloader", "Download complete: ${finalFile.name}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download Complete: $title", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("VideoDownloader", "Failed to rename temp file")
                    // Attempt cleanup
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e("VideoDownloader", "Download error", e)
            } finally {
                activeDownloads.remove(safeTitle)
            }
        }
    }

    fun getLocalVideoUri(url: String, title: String): Uri? {
        val safeTitle = getSafeFileName(title)
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeTitle)
        if (finalFile.exists()) {
            return finalFile.toUri()
        }
        return null
    }
}