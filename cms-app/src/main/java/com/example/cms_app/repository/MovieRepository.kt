package com.example.cms_app.repository

import com.example.cms_app.model.Movie
import com.example.cms_app.model.UpdateMovieRequest
import com.example.cms_app.network.RetrofitInstance
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun updateMovie(id: Int, request: UpdateMovieRequest) = api.updateMovie(id, request)

    suspend fun deleteMovie(id: Int) = api.deleteMovie(id)

    suspend fun uploadMovieFile(file: File, movieName: String): Response<Unit> {
        val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
        // Use movie name as filename so the backend stores consistent paths
        val body = MultipartBody.Part.createFormData("file", "$movieName.mp4", requestFile)
        return api.uploadMovieFile(body)
    }

    suspend fun uploadThumbnailFile(file: File, movieName: String): Response<String> {
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val namePart = movieName.toRequestBody("text/plain".toMediaTypeOrNull())
        return api.uploadThumbnailFile(body, namePart)
    }
}
