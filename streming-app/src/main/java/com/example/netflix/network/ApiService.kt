package com.example.netflix.network

import com.example.netflix.model.Movie
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {

    @GET("movie")
    suspend fun getMovies(): Response<List<Movie>>
}