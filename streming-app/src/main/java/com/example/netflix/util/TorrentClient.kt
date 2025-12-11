package com.example.netflix.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TorrentClient(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val baseDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir

    suspend fun fetchMetadata(host: String, port: Int, safeTitle: String): TorrentManifest? = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest(host, port, safeTitle) ?: return@withContext null
            val chunksDir = File(baseDir, "${safeTitle}_chunks").apply { if (!exists()) mkdirs() }
            saveManifestToDisk(manifest, safeTitle, chunksDir)
            return@withContext manifest
        } catch (e: Exception) {
            Log.e("TorrentClient", "Error fetching metadata", e)
            return@withContext null
        }
    }

    fun downloadContent(
        peers: List<String>,
        port: Int,
        safeTitle: String,
        manifest: TorrentManifest,
        concurrency: Int = 4,
        onFailure: () -> Unit
    ) {
        // Launch a fire-and-forget coroutine for the entire download process.
        scope.launch {
            val success = downloadContentInternal(peers, port, safeTitle, manifest, concurrency)
            if (!success) {
                onFailure()
            }
        }
    }

    private suspend fun downloadContentInternal(
        peers: List<String>,
        port: Int,
        safeTitle: String,
        manifest: TorrentManifest,
        concurrency: Int
    ): Boolean {
        return try {
            val chunksDir = File(baseDir, "${safeTitle}_chunks")
            
            val allChunks = manifest.chunks.sortedBy { chunk ->
                try { chunk.name.substringAfterLast("part").toInt() } catch (e: Exception) { 0 }
            }

            val missing = allChunks.filterNot { chunk ->
                val file = File(chunksDir, chunk.name)
                file.exists() && file.length() == chunk.size && file.sha256() == chunk.sha256
            }

            if (missing.isEmpty()) {
                Log.d("TorrentClient", "All chunks already present for $safeTitle")
                assembleIfNeeded(manifest, safeTitle, chunksDir)
                return true
            }

            val activePeers = Collections.synchronizedList(ArrayList(peers))
            Log.d("TorrentClient", "Swarming ${missing.size} chunks from ${activePeers.size} peers")

            val highPriorityCount = 2
            val highPriorityChunks = missing.take(highPriorityCount)
            val remainingChunks = missing.drop(highPriorityCount)

            if (highPriorityChunks.isNotEmpty()) {
                Log.d("TorrentClient", "Downloading ${highPriorityChunks.size} high-priority chunks...")
                val hpSuccess = downloadBatch(activePeers, port, safeTitle, highPriorityChunks, chunksDir, 2)
                if (!hpSuccess) {
                    Log.e("TorrentClient", "Failed to download high-priority chunks.")
                    return false
                }
            }

            if (remainingChunks.isNotEmpty()) {
                Log.d("TorrentClient", "Downloading remaining ${remainingChunks.size} chunks...")
                val success = downloadBatch(activePeers, port, safeTitle, remainingChunks, chunksDir, concurrency)
                if (!success) {
                    return false
                }
            }

            assembleIfNeeded(manifest, safeTitle, chunksDir)
            true
        } catch (e: Exception) {
            Log.e("TorrentClient", "Torrent content download failed for $safeTitle", e)
            false
        }
    }

    private suspend fun downloadBatch(
        activePeers: MutableList<String>,
        port: Int,
        safeTitle: String,
        chunksToDownload: List<ChunkInfo>,
        chunksDir: File,
        concurrency: Int
    ): Boolean {
        val chunksQueue = ConcurrentLinkedQueue(chunksToDownload)
        val jobs = (1..concurrency).map { workerId ->
            scope.async {
                while (isActive) {
                    if (activePeers.isEmpty()) return@async false
                    val chunk = chunksQueue.poll() ?: return@async true
                    
                    var chunkDownloaded = false
                    val peerCandidates = activePeers.toList().shuffled()
                    
                    for (host in peerCandidates) {
                        if (!activePeers.contains(host)) continue
                        var attempts = 0
                        var success = false
                        while (attempts < 4) {
                            if (downloadChunk(host, port, safeTitle, chunk, chunksDir)) {
                                success = true
                                break
                            }
                            attempts++
                            if (attempts < 4) delay(200)
                        }

                        if (success) {
                            chunkDownloaded = true
                            break
                        } else {
                            Log.w("TorrentClient", "Peer $host failed 4 times. Removing.")
                            activePeers.remove(host)
                            if (activePeers.isEmpty()) return@async false
                        }
                    }
                    if (!chunkDownloaded) {
                        Log.e("TorrentClient", "Failed to download chunk ${chunk.name}")
                        return@async false
                    }
                }
                true
            }
        }
        val results = jobs.awaitAll()
        return results.all { it }
    }

    private fun saveManifestToDisk(manifest: TorrentManifest, safeTitle: String, chunksDir: File) {
        val manifestFile = File(chunksDir, "${safeTitle}_manifest.sha256")
        try {
            manifestFile.bufferedWriter().use { writer ->
                manifest.chunks.forEach {
                    writer.write("${it.sha256}  ${it.name}")
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            Log.e("TorrentClient", "Failed to save .sha256 manifest", e)
        }

        val jsonFile = File(chunksDir, "${safeTitle}_manifest.json")
        try {
            jsonFile.writeText(gson.toJson(manifest))
        } catch (e: Exception) {
            Log.e("TorrentClient", "Failed to save .json manifest", e)
        }
    }

    private fun fetchManifest(host: String, port: Int, safeTitle: String): TorrentManifest? {
        val url = "http://$host:$port/manifest/$safeTitle"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    null
                } else {
                    resp.body?.string()?.let { gson.fromJson(it, TorrentManifest::class.java) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadChunk(host: String, port: Int, safeTitle: String, chunkInfo: ChunkInfo, chunksDir: File): Boolean {
        val url = "http://$host:$port/chunk/$safeTitle/${chunkInfo.name}"
        val request = Request.Builder().url(url).get().build()
        
        val outFile = File(chunksDir, chunkInfo.name)
        
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(outFile, false)
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var bytesSinceFlush = 0
                val flushThreshold = 256 * 1024
                
                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        bytesSinceFlush += bytesRead
                        if (bytesSinceFlush >= flushThreshold) {
                            outputStream.flush()
                            bytesSinceFlush = 0
                        }
                    }
                    outputStream.flush()
                } finally {
                    outputStream.close()
                    inputStream.close()
                }

                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != chunkInfo.sha256.lowercase()) {
                    Log.w("TorrentClient", "Hash mismatch from $host for ${chunkInfo.name}. Deleting.")
                    outFile.delete()
                    return false
                }
                true
            }
        } catch (e: Exception) {
            if (outFile.exists()) outFile.delete()
            false
        }
    }

    private fun assembleIfNeeded(manifest: TorrentManifest, safeTitle: String, chunksDir: File) {
        val finalFile = File(baseDir, safeTitle)
        if (finalFile.exists() && (manifest.fileSize <= 0 || finalFile.length() == manifest.fileSize)) {
            Log.d("TorrentClient", "File already exists and is complete: ${finalFile.name}")
            return
        }

        val tempAssemblyFile = File(baseDir, "$safeTitle.assembly_tmp")
        if (tempAssemblyFile.exists()) tempAssemblyFile.delete()

        Log.d("TorrentClient", "Starting file assembly to temporary file: ${tempAssemblyFile.name}")
        try {
            FileOutputStream(tempAssemblyFile).use { output ->
                val sortedChunks = manifest.chunks.sortedBy { chunk ->
                    try { chunk.name.substringAfterLast("part").toInt() } catch (e: Exception) { 0 }
                }
                
                sortedChunks.forEach { chunk ->
                    val chunkFile = File(chunksDir, chunk.name)
                    if (chunkFile.exists()) {
                        chunkFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } else {
                        throw Exception("Chunk ${chunk.name} missing during assembly")
                    }
                }
            }

            if (manifest.fileSize > 0 && tempAssemblyFile.length() != manifest.fileSize) {
                 Log.e("TorrentClient", "Assembly failed: incorrect size. Expected ${manifest.fileSize}, got ${tempAssemblyFile.length()}")
                 tempAssemblyFile.delete()
                 return
            }
            
            if (finalFile.exists()) finalFile.delete()
            if (tempAssemblyFile.renameTo(finalFile)) {
                Log.d("TorrentClient", "Successfully assembled file: ${finalFile.name}")
            } else {
                Log.e("TorrentClient", "Failed to rename temp assembly to final file")
                tempAssemblyFile.delete()
            }
            
        } catch (e: Exception) {
            Log.e("TorrentClient", "Exception during file assembly", e)
            if (tempAssemblyFile.exists()) tempAssemblyFile.delete()
        }
    }
}

private fun ByteArray.sha256(): String {
    return MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
