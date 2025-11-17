package com.example.netflix.repository

import com.example.netflix.model.WatchProgressRequest
import com.example.netflix.network.RetrofitInstance

class ProgressRepository {
    private val api = RetrofitInstance.api

    suspend fun getProfileProgress(profileId: Int) = api.getProfileProgress(profileId)

    suspend fun getProgress(profileId: Int, movieId: Int) = api.getProgress(profileId, movieId)

    suspend fun saveProgress(profileId: Int, movieId: Int, positionMs: Long) =
        api.saveProgress(WatchProgressRequest(profileId, movieId, positionMs))

    suspend fun clearProgress(profileId: Int, movieId: Int) =
        api.clearProgress(profileId, movieId)
}
