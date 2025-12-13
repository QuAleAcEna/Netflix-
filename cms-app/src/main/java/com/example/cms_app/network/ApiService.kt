package com.example.cms_app.network

import com.example.cms_app.model.CreateUserRequest
import com.example.cms_app.model.Movie
import com.example.cms_app.model.User
import com.example.cms_app.model.CmsUser
import com.example.cms_app.model.LoginRequest
import com.example.cms_app.model.UpdateMovieRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ApiService {

    // Movies
    @GET("movie")
    suspend fun getMovies(): Response<List<Movie>>

    @POST("movie")
    suspend fun uploadMovie(@Body movie: Movie): Response<Movie>

    @PUT("movie/{id}")
    suspend fun updateMovie(
        @Path("id") id: Int,
        @Body request: UpdateMovieRequest
    ): Response<Movie>

    @POST("movie/{id}/transcode-lowres")
    suspend fun generateLowRes(@Path("id") id: Int): Response<Unit>

    @DELETE("movie/{id}")
    suspend fun deleteMovie(@Path("id") id: Int): Response<Unit>

    @Multipart
    @POST("file/upload")
    suspend fun uploadMovieFile(
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("file/upload-thumbnail")
    suspend fun uploadThumbnailFile(
        @Part file: MultipartBody.Part,
        @Part("movieName") movieName: RequestBody
    ): Response<String>

    // Users (for streaming app)
    @GET("user/all")
    suspend fun getUsers(): Response<List<User>>

    @POST("user")
    suspend fun createUser(@Body request: CreateUserRequest): Response<User>

    @DELETE("user/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Unit>

    // CMS auth
    @POST("cms/login")
    suspend fun login(@Body request: LoginRequest): Response<CmsUser>
}
