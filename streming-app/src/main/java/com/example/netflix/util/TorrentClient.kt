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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Lightweight client to fetch manifests and chunks from a Netty seeder.
 * Uses OkHttp for simplicity while keeping the Netty server on the seeder side.
 */
class TorrentClient(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val baseDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir

    suspend fun fetchAndStore(
        host: String,
        port: Int,
        safeTitle: String,
        parallelism: Int = 3
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest(host, port, safeTitle) ?: return@withContext false
            val chunksDir = File(baseDir, "${safeTitle}_chunks").apply { if (!exists()) mkdirs() }

            val missing = manifest.chunks.filterNot { chunk ->
                val file = File(chunksDir, chunk.name)
                file.exists() && file.length() == chunk.size && file.sha256() == chunk.sha256
            }

            if (missing.isEmpty()) {
                Log.d("TorrentClient", "All chunks already present for $safeTitle")
                assembleIfNeeded(manifest, safeTitle, chunksDir)
                return@withContext true
            }

            Log.d("TorrentClient", "Downloading ${missing.size} chunks from $host for $safeTitle")

            val batches = missing.chunked(parallelism)
            for (batch in batches) {
                val jobs = batch.map { chunk ->
                    scope.async {
                        downloadChunk(host, port, safeTitle, chunk, chunksDir)
                    }
                }
                val results = jobs.awaitAll()
                if (results.any { it.not() }) {
                    Log.e("TorrentClient", "Failed chunk batch for $safeTitle")
                    return@withContext false
                }
            }

            assembleIfNeeded(manifest, safeTitle, chunksDir)
            true
        } catch (e: Exception) {
            Log.e("TorrentClient", "Torrent fetch failed", e)
            false
        }
    }

    private fun fetchManifest(host: String, port: Int, safeTitle: String): TorrentManifest? {
        val url = "http://$host:$port/manifest/$safeTitle"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return gson.fromJson(body, TorrentManifest::class.java)
        }
    }

    private fun downloadChunk(
        host: String,
        port: Int,
        safeTitle: String,
        chunkInfo: ChunkInfo,
        chunksDir: File
    ): Boolean {
        val url = "http://$host:$port/chunk/$safeTitle/${chunkInfo.name}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val bytes = resp.body?.bytes() ?: return false
            val hash = bytes.sha256()
            if (!hash.equals(chunkInfo.sha256, ignoreCase = true)) {
                Log.e("TorrentClient", "Hash mismatch for ${chunkInfo.name}: expected ${chunkInfo.sha256} got $hash")
                return false
            }
            val outFile = File(chunksDir, chunkInfo.name)
            FileOutputStream(outFile).use { it.write(bytes) }
            return true
        }
    }

    private fun assembleIfNeeded(manifest: TorrentManifest, safeTitle: String, chunksDir: File) {
        val finalFile = File(baseDir, safeTitle)
        if (finalFile.exists()) return

        FileOutputStream(finalFile).use { output ->
            manifest.chunks.sortedBy { it.name }.forEach { chunk ->
                val chunkFile = File(chunksDir, chunk.name)
                chunkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        val finalHash = finalFile.sha256()
        if (manifest.fileSha256 != null && !manifest.fileSha256.equals(finalHash, ignoreCase = true)) {
            Log.e("TorrentClient", "Final file hash mismatch for $safeTitle")
            finalFile.delete()
        } else {
            Log.d("TorrentClient", "Assembled file for $safeTitle")
        }
    }
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(this)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
