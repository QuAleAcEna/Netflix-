package com.example.netflix.model


data class Movie(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val genre: Int = 0,
  //  val duration: String = "",
    var thumbnailPath: String = "",
    var videoPath:String = ""
 //   val videoUrl1080p: String = "",
   // val videoUrl360p: String = ""
)