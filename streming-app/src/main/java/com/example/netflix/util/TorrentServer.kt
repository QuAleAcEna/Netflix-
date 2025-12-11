package com.example.netflix.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.stream.ChunkedFile
import io.netty.handler.stream.ChunkedStream
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class TorrentServer(private val port: Int, context: Context) {
    private val baseDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    private val running = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var channelFuture: ChannelFuture? = null
    private val gson = Gson()
    private val peerDiscovery = PeerDiscovery(context)

    fun start() {
        if (running.compareAndSet(false, true).not()) return

        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        addLast(HttpServerCodec())
                        addLast(HttpObjectAggregator(1 shl 20)) // 1MB aggregator
                        addLast(ChunkedWriteHandler())
                        addLast(HttpHandler(baseDir, gson))
                    }
                }
            })

        channelFuture = bootstrap.bind(port).addListener {
            if (it.isSuccess) {
                Log.d("TorrentServer", "Netty seeder started on $port")
                peerDiscovery.register(port)
            } else {
                Log.e("TorrentServer", "Failed to start Netty seeder", it.cause())
            }
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false).not()) return
        peerDiscovery.unregister()
        try {
            channelFuture?.channel()?.close()
            bossGroup?.shutdownGracefully()
            workerGroup?.shutdownGracefully()
        } catch (e: Exception) {
            Log.e("TorrentServer", "Error stopping server", e)
        }
    }
}

private class HttpHandler(
    private val baseDir: File,
    private val gson: Gson
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: io.netty.channel.ChannelHandlerContext, msg: FullHttpRequest) {
        val uri = msg.uri()
        val decoder = QueryStringDecoder(uri)
        val segments = decoder.path().split("/").filter { it.isNotEmpty() }

        when {
            segments.size == 2 && segments[0].equals("manifest", ignoreCase = true) -> {
                val title = segments[1]
                sendManifest(ctx, msg, title)
            }
            segments.size == 3 && segments[0].equals("chunk", ignoreCase = true) -> {
                val title = segments[1]
                val chunkName = segments[2]
                sendChunk(ctx, msg, title, chunkName)
            }
            segments.size == 2 && segments[0].equals("stream", ignoreCase = true) -> {
                val title = segments[1]
                streamVideo(ctx, msg, title)
            }
            else -> {
                sendStatus(ctx, msg, HttpResponseStatus.NOT_FOUND)
            }
        }
    }

    private fun sendManifest(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, title: String) {
        val chunksDir = File(baseDir, "${title}_chunks")
        val manifestFile = File(chunksDir, "${title}_manifest.sha256")
        val jsonManifestFile = File(chunksDir, "${title}_manifest.json")

        if (jsonManifestFile.exists()) {
            try {
                val json = jsonManifestFile.readText()
                val content = Unpooled.wrappedBuffer(json.toByteArray())
                val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
                response.headers()[HttpHeaders.Names.CONTENT_TYPE] = "application/json"
                HttpUtil.setContentLength(response, content.readableBytes().toLong())
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                return
            } catch (e: Exception) {
                Log.e("TorrentServer", "Failed to serve JSON manifest", e)
            }
        }

        if (!manifestFile.exists() || !chunksDir.exists()) {
            sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND)
            return
        }

        val chunkInfos = manifestFile.readLines()
            .mapNotNull { line ->
                val parts = line.split("  ")
                if (parts.size == 2) {
                    val hash = parts[0].trim()
                    val name = parts[1].trim()
                    val file = File(chunksDir, name)
                    val size = if (file.exists()) file.length() else 0L
                    ChunkInfo(name = name, size = size, sha256 = hash)
                } else null
            }

        val assembled = File(baseDir, title)
        val fileSize = assembled.takeIf { it.exists() }?.length() ?: chunkInfos.sumOf { it.size }

        val manifest = TorrentManifest(
            fileName = title,
            fileSize = fileSize,
            fileSha256 = if (assembled.exists()) assembled.sha256() else null,
            chunks = chunkInfos
        )

        val json = gson.toJson(manifest)
        val content = Unpooled.wrappedBuffer(json.toByteArray())
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
        response.headers()[HttpHeaders.Names.CONTENT_TYPE] = "application/json"
        HttpUtil.setContentLength(response, content.readableBytes().toLong())
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun sendChunk(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, title: String, chunkName: String) {
        val chunksDir = File(baseDir, "${title}_chunks")
        val file = File(chunksDir, chunkName)
        if (!file.exists()) {
            sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND)
            return
        }

        val bytes = FileInputStream(file).use { it.readBytes() }
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes))
        response.headers()[HttpHeaders.Names.CONTENT_TYPE] = "application/octet-stream"
        HttpUtil.setContentLength(response, bytes.size.toLong())
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun streamVideo(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, title: String) {
        val assembled = File(baseDir, title)
        
        // --- 1. OPTIMIZATION: If full file exists, serve it directly ---
        if (assembled.exists() && assembled.length() > 0) {
            val totalSize = assembled.length()
            val rangeHeader = req.headers().get(HttpHeaders.Names.RANGE)
            var startOffset: Long = 0
            var endOffset: Long = totalSize - 1
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val parts = rangeHeader.substring(6).split("-")
                startOffset = parts[0].toLong()
                if (parts.size > 1 && parts[1].isNotEmpty()) {
                    endOffset = parts[1].toLong()
                }
            }
            if (endOffset >= totalSize) endOffset = totalSize - 1

            val contentLength = endOffset - startOffset + 1
            val raf = RandomAccessFile(assembled, "r")
            
            val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PARTIAL_CONTENT)
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "video/mp4")
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, contentLength)
            response.headers().set(HttpHeaders.Names.CONTENT_RANGE, "bytes $startOffset-$endOffset/$totalSize")
            response.headers().set(HttpHeaders.Names.ACCEPT_RANGES, "bytes")
            
            ctx.write(response)
            
            val chunkedFile = ChunkedFile(raf, startOffset, contentLength, 8192)
            ctx.writeAndFlush(chunkedFile).addListener(ChannelFutureListener.CLOSE)
            Log.d("TorrentServer", "Serving fully assembled file: $title")
            return
        }

        // --- 2. Fallback: Serve from chunks (streaming while downloading) ---
        val chunksDir = File(baseDir, "${title}_chunks")
        var totalSize = 0L
        val jsonManifestFile = File(chunksDir, "${title}_manifest.json")
        val manifestFile = File(chunksDir, "${title}_manifest.sha256")
        var chunkInfos: List<ChunkInfo> = emptyList()

        if (jsonManifestFile.exists()) {
             try {
                 val m = gson.fromJson(jsonManifestFile.readText(), TorrentManifest::class.java)
                 totalSize = m.fileSize
                 chunkInfos = m.chunks
             } catch (e: Exception) { Log.e("TorrentServer", "Error reading json manifest", e) }
        }
        
        if (chunkInfos.isEmpty() && manifestFile.exists()) {
            chunkInfos = manifestFile.readLines().mapNotNull { line ->
                val parts = line.split("  ")
                if (parts.size == 2) {
                    val name = parts[1].trim()
                    ChunkInfo(name = name, size = 10 * 1024 * 1024L, sha256 = parts[0])
                } else null
            }
            // Better guess than -1 if we can sum them up
             if (totalSize == 0L && chunkInfos.isNotEmpty()) {
                 totalSize = chunkInfos.sumOf { it.size }
                 if (totalSize == 0L) totalSize = chunkInfos.size * 10 * 1024 * 1024L // Last resort estimate
             }
        }

        if (chunkInfos.isEmpty()) {
            Log.e("TorrentServer", "No chunks found for $title")
            sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND)
            return
        }

        val rangeHeader = req.headers().get(HttpHeaders.Names.RANGE)
        var startOffset: Long = 0
        var endOffset: Long = -1 
        
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val parts = rangeHeader.substring(6).split("-")
            startOffset = parts[0].toLong()
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                endOffset = parts[1].toLong()
            }
        }

        if (endOffset == -1L) endOffset = totalSize - 1
        val contentLength = endOffset - startOffset + 1
        
        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PARTIAL_CONTENT)
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "video/mp4")
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, contentLength)
        response.headers().set(HttpHeaders.Names.CONTENT_RANGE, "bytes $startOffset-$endOffset/$totalSize")
        response.headers().set(HttpHeaders.Names.ACCEPT_RANGES, "bytes")
        
        ctx.write(response)

        Log.d("TorrentServer", "Streaming $title: $startOffset-$endOffset (Total: $totalSize) via chunks")
        // Sort chunks numerically to ensure correct stream order!
        val sortedChunks = chunkInfos.sortedBy { 
            try { it.name.substringAfterLast("part").toInt() } catch(e:Exception) { 0 }
        }
        val stream = ChunkSequenceInputStream(chunksDir, sortedChunks, startOffset, contentLength)
        ctx.writeAndFlush(ChunkedStream(stream)).addListener(ChannelFutureListener.CLOSE)
    }

    private fun sendStatus(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, status: HttpResponseStatus) {
        val response: FullHttpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
}

private class ChunkSequenceInputStream(
    private val chunksDir: File,
    private val chunks: List<ChunkInfo>,
    private val startOffset: Long,
    private val length: Long
) : InputStream() {
    
    private var bytesReadTotal = 0L
    private var currentChunkIndex = 0
    private var currentChunkStream: InputStream? = null
    private var currentChunkOffset = 0L
    
    init {
        var accumulatedSize = 0L
        for (i in chunks.indices) {
            val size = chunks[i].size
            if (startOffset < accumulatedSize + size) {
                currentChunkIndex = i
                currentChunkOffset = startOffset - accumulatedSize
                break
            }
            accumulatedSize += size
        }
    }

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) == 1) b[0].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesReadTotal >= length) return -1
        
        if (currentChunkIndex >= chunks.size) return -1
        
        val chunkInfo = chunks[currentChunkIndex]
        val chunkFile = File(chunksDir, chunkInfo.name)
        val expectedSize = chunkInfo.size
        
        var attempts = 0
        while (!chunkFile.exists() || chunkFile.length() <= 0L) {
             try {
                 if (attempts % 10 == 0) Log.d("TorrentServer", "Waiting for chunk ${chunkInfo.name}...")
                 Thread.sleep(500)
                 attempts++
                 if (attempts > 120) { 
                     Log.e("TorrentServer", "Timeout waiting for chunk ${chunkInfo.name}")
                     return -1 
                 }
             } catch (e: InterruptedException) { return -1 }
        }
        
        if (currentChunkStream == null) {
            currentChunkStream = FileInputStream(chunkFile)
            if (currentChunkOffset > 0) {
                var skipped = 0L
                while (skipped < currentChunkOffset) {
                    val actualSkipped = currentChunkStream!!.skip(currentChunkOffset - skipped)
                    if (actualSkipped == 0L) {
                        Thread.sleep(100)
                        if (!chunkFile.exists()) return -1
                    }
                    skipped += actualSkipped
                }
                currentChunkOffset = 0 
            }
        }
        
        val remainingInRequest = (length - bytesReadTotal).toInt()
        val toRead = minOf(len, remainingInRequest)
        
        var totalReadInStep = 0
        while (totalReadInStep < toRead) {
            val n = currentChunkStream!!.read(b, off + totalReadInStep, toRead - totalReadInStep)
            if (n == -1) {
                // If EOF but chunk size mismatch, keep waiting
                if (chunkFile.length() < expectedSize) {
                    Thread.sleep(100)
                    continue
                } else {
                    break
                }
            } else {
                totalReadInStep += n
            }
        }

        if (totalReadInStep > 0) {
            bytesReadTotal += totalReadInStep
            return totalReadInStep
        } else {
            currentChunkStream?.close()
            currentChunkStream = null
            currentChunkIndex++
            return read(b, off, len)
        }
    }

    override fun close() {
        currentChunkStream?.close()
    }
}

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
