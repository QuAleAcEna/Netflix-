package com.example.cms_app.repository

import com.example.cms_app.model.Movie
import com.example.cms_app.network.RetrofitInstance
import retrofit2.Response

class MovieRepository {
    private val api = RetrofitInstance.api

    suspend fun fetchMovies() = api.getMovies()

    suspend fun uploadMovie(movie: Movie): Response<Movie> {
        val created = api.uploadMovie(movie)
        // Auto-request low-res generation when upload succeeds and we have an id
        val createdId = created.body()?.id
        if (created.isSuccessful && createdId != null && createdId != 0) {
            api.generateLowRes(createdId)
        }
        return created
    }

    suspend fun deleteMovie(id: Int) = api.deleteMovie(id)
}
