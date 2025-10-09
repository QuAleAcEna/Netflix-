package com.example.netflix.model


data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val genre: String,
    val duration: String,
    val thumbnailUrl: String,
    val videoUrl1080p: String,
    val videoUrl360p: String
)