package com.example.cms_app.model

data class Movie(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val genre: Int = 0,
    val thumbnailPath: String = "",
    val videoPath: String = ""
)
