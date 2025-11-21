package com.example.netflix.util

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Properties

class P2PServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start(filesDir: File) {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d("P2PServer", "Server started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept()
                    client?.let {
                        Thread { handleClient(it, filesDir) }.start()
                    }
                }
            } catch (e: IOException) {
                Log.e("P2PServer", "Server error: ${e.message}")
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket, filesDir: File) {
        try {
            val inputStream = socket.getInputStream()
            val reader = inputStream.bufferedReader()
            val requestLine = reader.readLine() ?: return
            
            // Read headers to find Range
            val headers = HashMap<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0]] = parts[1]
                }
                line = reader.readLine()
            }

            // Expecting GET /filename HTTP/1.1 or HEAD /filename HTTP/1.1
            val parts = requestLine.split(" ")
            if (parts.size > 1) {
                val method = parts[0]
                if (method == "GET" || method == "HEAD") {
                    var fileNameRaw = parts[1].substring(1) // Remove leading /
                    // Proper URL decoding
                    val fileName = try {
                        URLDecoder.decode(fileNameRaw, "UTF-8")
                    } catch (e: Exception) {
                        fileNameRaw.replace("%20", " ")
                    }
                    
                    // Try to find the file, ignoring extension case or if one was missing in request but present in file
                    var file = File(filesDir, fileName)
                    
                    if (!file.exists()) {
                        // Try adding .mp4 if missing
                         if (!fileName.endsWith(".mp4")) {
                             file = File(filesDir, "$fileName.mp4")
                         }
                    }
                    
                    // Try .tmp if still not found (sharing while downloading)
                    if (!file.exists()) {
                        // Reset to original search if mp4 check failed
                        val originalFile = File(filesDir, fileName)
                         if (!fileName.endsWith(".mp4")) {
                             file = File(filesDir, "$fileName.mp4.tmp")
                             if (!file.exists()) {
                                 file = File(filesDir, "$fileName.tmp")
                             }
                         } else {
                             file = File(filesDir, "$fileName.tmp")
                         }
                    }

                    val outputStream = socket.getOutputStream()

                    if (file.exists() && file.isFile) {
                        val fileLength = file.length()
                        var start: Long = 0
                        var end: Long = fileLength - 1
                        
                        val range = headers["Range"]
                        var isPartial = false
                        
                        if (range != null && range.startsWith("bytes=")) {
                            val rangeParts = range.substring(6).split("-")
                            try {
                                start = rangeParts[0].toLong()
                                if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                                    end = rangeParts[1].toLong()
                                }
                                isPartial = true
                            } catch (e: NumberFormatException) {
                                // Ignore, serve full content
                            }
                        }

                        if (end >= fileLength) {
                            end = fileLength - 1
                        }
                        val contentLength = end - start + 1
                        
                        val status = if (isPartial) "206 Partial Content" else "200 OK"
                        val contentRange = if (isPartial) "Content-Range: bytes $start-$end/$fileLength\r\n" else ""

                        val header = "HTTP/1.1 $status\r\n" +
                                "Content-Length: $contentLength\r\n" +
                                "Content-Type: video/mp4\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                contentRange +
                                "\r\n"
                        outputStream.write(header.toByteArray())

                        // Only write body if method is GET
                        if (method == "GET") {
                            val fileInputStream = FileInputStream(file)
                            fileInputStream.skip(start)
                            
                            val buffer = ByteArray(8192) // 8KB buffer
                            var bytesToRead = contentLength
                            var bytesRead: Int
                            
                            while (bytesToRead > 0) {
                                val len = if (bytesToRead > buffer.size) buffer.size else bytesToRead.toInt()
                                bytesRead = fileInputStream.read(buffer, 0, len)
                                if (bytesRead == -1) break
                                outputStream.write(buffer, 0, bytesRead)
                                bytesToRead -= bytesRead
                            }
                            fileInputStream.close()
                        }
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\n\r\n"
                        outputStream.write(notFound.toByteArray())
                    }
                    outputStream.flush()
                }
            }
        } catch (e: IOException) {
            // Client disconnected or error
        } finally {
             try {
                socket.close()
            } catch (e: IOException) {
                 e.printStackTrace()
            }
        }
    }
}