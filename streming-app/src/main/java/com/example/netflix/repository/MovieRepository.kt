package com.example.netflix.repository

import com.example.netflix.network.RetrofitInstance

class MovieRepository {

    suspend fun getMovies() = RetrofitInstance.api.getMovies()
}