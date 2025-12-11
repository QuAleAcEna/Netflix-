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
import com.google.gson.Gson

class VideoDownloader(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    // Scope tied to the class instance, which is remembered in AppNavigation
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = activeDownloadsSet

    private fun getSafeFileName(title: String): String {
        return toSafeFileName(title)
    }

    fun downloadVideo(url: String, title: String) {
        val safeTitle = getSafeFileName(title)
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeTitle)
        
        if (finalFile.exists() || activeDownloads.contains(safeTitle)) {
            Log.d("VideoDownloader", "Download ignored: File exists or already downloading: $safeTitle")
            return
        }

        Log.d("VideoDownloader", "ATTEMPTING DOWNLOAD FROM ORIGIN SERVER: $url")
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

    fun deleteVideoAndChunks(title: String) {
        val safeTitle = getSafeFileName(title)
        val finalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeTitle)
        if (finalFile.exists()) {
            Log.d("VideoDownloader", "Deleting corrupt file: ${finalFile.name}")
            finalFile.delete()
        }
        val chunksDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "${safeTitle}_chunks")
        if (chunksDir.exists()) {
            Log.d("VideoDownloader", "Deleting associated chunks directory: ${chunksDir.name}")
            chunksDir.deleteRecursively()
        }
        activeDownloads.remove(safeTitle)
    }

    private fun createChunksAndHashes(sourceFile: File, safeTitle: String) {
        val chunksDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "${safeTitle}_chunks")

        if (chunksDir.exists()) {
            chunksDir.listFiles()?.forEach { file -> file.delete() }
        } else if (!chunksDir.mkdirs()) {
            Log.e("VideoDownloader", "Could not create chunk directory at ${chunksDir.absolutePath}")
            return
        }

        Log.d("VideoDownloader", "Source file '${sourceFile.name}' length: ${sourceFile.length()}")

        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.w("VideoDownloader", "Source file missing or empty, skipping chunking")
            return
        }

        val manifestFile = File(chunksDir, "${safeTitle}_manifest.sha256")
        val chunkInfos = mutableListOf<ChunkInfo>()
        val totalSize = sourceFile.length()
        val fileSha256 = sourceFile.sha256()

        var bufferedWriter: BufferedWriter? = null
        try {
            FileInputStream(sourceFile).use { input ->
                bufferedWriter = BufferedWriter(FileWriter(manifestFile, false))
                bufferedWriter?.use { manifest ->
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
                        
                        Log.d("VideoDownloader", "Attempting to write to manifest: Chunk $chunkIndex, Name: ${file?.name}, Hash: $hash")
                        
                        manifest.write("$hash  ${file?.name}")
                        manifest.newLine()
                        
                        chunkInfos.add(ChunkInfo(name = file?.name ?: "", size = bytesInChunk, sha256 = hash))
                        
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
                    manifest.flush()
                }
            }
            
            // Save JSON manifest as well
            val torrentManifest = TorrentManifest(
                fileName = safeTitle,
                fileSize = totalSize,
                fileSha256 = fileSha256,
                chunks = chunkInfos
            )
            val jsonFile = File(chunksDir, "${safeTitle}_manifest.json")
            jsonFile.writeText(gson.toJson(torrentManifest))
            Log.d("VideoDownloader", "Saved JSON manifest to ${jsonFile.name}")

        } catch (e: Exception) {
            Log.e("VideoDownloader", "Error during manifest creation: ", e)
        } finally {
            bufferedWriter?.close()
            Log.d("VideoDownloader", "Manifest file generation completed for: ${manifestFile.name}. Final size: ${manifestFile.length()} bytes.")
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
    
    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_CHUNK_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
        // Shared active downloads to avoid duplicates across recompositions
        val activeDownloadsSet = mutableSetOf<String>()

        fun toSafeFileName(title: String): String {
            return title.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
        }
    }
}
