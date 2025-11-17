package com.example.netflix.model

data class WatchProgress(
    val profileId: Int,
    val movieId: Int,
    val positionMs: Long
)

data class WatchProgressRequest(
    val profileId: Int,
    val movieId: Int,
    val positionMs: Long
)
