package com.example.netflix.util

data class ChunkInfo(
    val name: String,
    val size: Long,
    val sha256: String
)

data class TorrentManifest(
    val fileName: String,
    val fileSize: Long,
    val fileSha256: String?,
    val chunks: List<ChunkInfo>
)
