package com.example.netflix.model

data class CreateProfileRequest(
    val userId: Int,
    val name: String,
    val avatarColor: String,
    val kids: Boolean
)
