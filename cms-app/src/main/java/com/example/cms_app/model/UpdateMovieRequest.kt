package com.example.cms_app.model

data class UpdateMovieRequest(
    val name: String? = null,
    val description: String? = null,
    val genre: Int? = null,
    val thumbnailPath: String? = null
)
