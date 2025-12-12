package com.example.netflix.model

data class UpdateProfileRequest(
    val name: String? = null,
    val avatarColor: String? = null,
    val kids: Boolean? = null
)
