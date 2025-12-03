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
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight HTTP-based seeder built on Netty.
 * Exposes:
 *  - GET /manifest/{title}
 *  - GET /chunk/{title}/{chunkName}
 */
class TorrentServer(private val port: Int, context: Context) {
    private val baseDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    private val running = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var channelFuture: ChannelFuture? = null
    private val gson = Gson()

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
                        addLast(HttpObjectAggregator(1 shl 20))
                        addLast(ChunkedWriteHandler())
                        addLast(HttpHandler(baseDir, gson))
                    }
                }
            })

        channelFuture = bootstrap.bind(port).addListener {
            if (it.isSuccess) {
                Log.d("TorrentServer", "Netty seeder started on $port")
            } else {
                Log.e("TorrentServer", "Failed to start Netty seeder", it.cause())
            }
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false).not()) return
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
            else -> {
                sendStatus(ctx, msg, HttpResponseStatus.NOT_FOUND)
            }
        }
    }

    private fun sendManifest(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, title: String) {
        val chunksDir = File(baseDir, "${title}_chunks")
        val manifestFile = File(chunksDir, "${title}_manifest.sha256")

        if (!manifestFile.exists() || !chunksDir.exists()) {
            sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND)
            return
        }

        val chunkInfos = manifestFile.readLines()
            .mapNotNull { line ->
                val parts = line.split("  ")
                if (parts.size == 2) {
                    val name = parts[1].trim()
                    val file = File(chunksDir, name)
                    if (file.exists()) {
                        ChunkInfo(name = name, size = file.length(), sha256 = parts[0])
                    } else null
                } else null
            }

        // Attempt to compute full file hash from assembled file if present
        val assembled = File(baseDir, title)
        val fileHash = if (assembled.exists()) assembled.sha256() else null
        val fileSize = assembled.takeIf { it.exists() }?.length() ?: chunkInfos.sumOf { it.size }

        val manifest = TorrentManifest(
            fileName = title,
            fileSize = fileSize,
            fileSha256 = fileHash,
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

    private fun sendStatus(ctx: io.netty.channel.ChannelHandlerContext, req: FullHttpRequest, status: HttpResponseStatus) {
        val response: FullHttpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
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
