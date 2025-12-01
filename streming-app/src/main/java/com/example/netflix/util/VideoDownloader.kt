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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.BufferedWriter
import java.security.MessageDigest

class VideoDownloader(private val context: Context) {

    private val client = OkHttpClient()
    // Scope tied to the class instance, which is remembered in AppNavigation
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableSetOf<String>()

    private fun getSafeFileName(title: String): String {
        return toSafeFileName(title)
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
                    try {
                        createChunksAndHashes(finalFile, safeTitle)
                    } catch (e: Exception) {
                        Log.e("VideoDownloader", "Chunking/hash failed", e)
                    }
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

    private fun createChunksAndHashes(sourceFile: File, safeTitle: String) {
        val chunksDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "${safeTitle}_chunks")

        if (chunksDir.exists()) {
            chunksDir.listFiles()?.forEach { file -> file.delete() }
        } else if (!chunksDir.mkdirs()) {
            Log.e("VideoDownloader", "Could not create chunk directory at ${chunksDir.absolutePath}")
            return
        }

        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.w("VideoDownloader", "Source file missing or empty, skipping chunking")
            return
        }

        val manifestFile = File(chunksDir, "${safeTitle}_manifest.sha256")
        FileInputStream(sourceFile).use { input ->
            BufferedWriter(FileWriter(manifestFile, false)).use { manifest ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var chunkIndex = 0
                var bytesInChunk = 0L
                var chunkFile: File? = null
                var output: FileOutputStream? = null
                var digest: MessageDigest? = null

                fun chunkFileName(index: Int) = "${safeTitle}.part${index.toString().padStart(4, '0')}"

                fun startChunk() {
                    chunkFile = File(chunksDir, chunkFileName(chunkIndex))
                    output = FileOutputStream(chunkFile!!)
                    digest = MessageDigest.getInstance("SHA-256")
                    bytesInChunk = 0L
                }

                fun completeChunk() {
                    output?.flush()
                    output?.close()
                    val file = chunkFile
                    val hash = digest?.digest()?.toHexString().orEmpty()
                    manifest.write("$hash  ${file?.name}")
                    manifest.newLine()
                    Log.d("VideoDownloader", "Chunk $chunkIndex hashed: ${file?.name} ($bytesInChunk bytes) sha256=$hash")
                    chunkIndex++
                    bytesInChunk = 0L
                    chunkFile = null
                    output = null
                    digest = null
                }

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    var offset = 0
                    while (offset < bytesRead) {
                        if (output == null) startChunk()
                        val spaceLeft = (MAX_CHUNK_SIZE_BYTES - bytesInChunk).toInt()
                        val bytesToWrite = minOf(bytesRead - offset, spaceLeft)
                        output!!.write(buffer, offset, bytesToWrite)
                        digest!!.update(buffer, offset, bytesToWrite)
                        bytesInChunk += bytesToWrite
                        offset += bytesToWrite
                        if (bytesInChunk >= MAX_CHUNK_SIZE_BYTES) {
                            completeChunk()
                        }
                    }
                }

                if (bytesInChunk > 0 && output != null) {
                    completeChunk()
                } else {
                    output?.close()
                    chunkFile?.delete()
                }
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_CHUNK_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

        fun toSafeFileName(title: String): String {
            return title.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
        }
    }
}
