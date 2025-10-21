package com.example.netflix.repository

import com.example.netflix.network.RetrofitInstance

class MovieRepository {
    public val BASE_URL = "http://172.17.19.191:55555/" // mudar conforme seu backend

    suspend fun getMovies() = RetrofitInstance.api.getMovies()
}