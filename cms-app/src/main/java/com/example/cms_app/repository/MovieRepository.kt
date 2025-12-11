package com.example.cms_app.repository

import com.example.cms_app.model.Movie
import com.example.cms_app.network.RetrofitInstance
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

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

    suspend fun uploadMovieFile(file: File): Response<Unit> {
        val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        return api.uploadMovieFile(body)
    }
}
