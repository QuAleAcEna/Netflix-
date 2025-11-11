package com.example.netflix.model


data class Movie(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val genre: Int = 0,
    var thumbnailPath: String = "",
    var videoPath:String = ""

)