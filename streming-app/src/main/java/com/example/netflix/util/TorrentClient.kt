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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
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

    // Map to hold active download queues: SafeTitle -> Deque of chunks
    private val activeDownloads = ConcurrentHashMap<String, ConcurrentLinkedDeque<ChunkInfo>>()

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

    fun prioritizeChunk(safeTitle: String, chunkName: String) {
        val queue = activeDownloads[safeTitle] ?: return
        
        // We synchronize on the queue to ensure atomicity of remove/addFirst if needed, 
        // though ConcurrentLinkedDeque is thread-safe, finding and moving needs care.
        // Simple approach: find, remove, addFirst.
        // Note: poll() removes from head. We want to be at head.
        
        // Ideally we want to move it to the FRONT.
        // ConcurrentLinkedDeque supports addFirst().
        
        // Check if chunk is in the queue (not yet processing)
        val iterator = queue.iterator()
        var targetChunk: ChunkInfo? = null
        while (iterator.hasNext()) {
            val chunk = iterator.next()
            if (chunk.name == chunkName) {
                targetChunk = chunk
                iterator.remove() // Remove from current position
                break
            }
        }

        if (targetChunk != null) {
            Log.d("TorrentClient", "Prioritizing chunk: $chunkName")
            queue.addFirst(targetChunk)
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

            // Use a Deque for the queue to allow prioritization (addFirst)
            val chunksQueue = ConcurrentLinkedDeque(missing)
            activeDownloads[safeTitle] = chunksQueue

            try {
                // Main download loop
                val jobs = (1..concurrency).map { workerId ->
                    scope.async {
                        while (isActive) {
                            if (activePeers.isEmpty()) return@async false
                            
                            // Poll from HEAD (prioritized chunks are here)
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
                                // Put back? No, failure logic bubbles up.
                                return@async false
                            }
                        }
                        true
                    }
                }
                
                val results = jobs.awaitAll()
                if (results.any { !it }) return false

                assembleIfNeeded(manifest, safeTitle, chunksDir)
                true
            } finally {
                activeDownloads.remove(safeTitle)
            }
        } catch (e: Exception) {
            Log.e("TorrentClient", "Torrent content download failed for $safeTitle", e)
            false
        }
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
                val flushThreshold = 64 * 1024 // More frequent flush (64KB) for faster streaming availability
                
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
