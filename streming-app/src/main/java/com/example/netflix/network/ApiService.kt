package com.example.netflix.network

import com.example.netflix.model.CreateProfileRequest
import com.example.netflix.model.Movie
import com.example.netflix.model.Profile
import com.example.netflix.model.User
import com.example.netflix.model.WatchProgress
import com.example.netflix.model.WatchProgressRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @GET("movie")
    suspend fun getMovies(): Response<List<Movie>>

    @GET("user/{name}/{password}")
    suspend fun createUser(
        @Path("name") name: String,
        @Path("password") password: String
    ): Response<User?>

    @GET("user/connect/{name}/{password}")
    suspend fun connectUser(
        @Path("name") name: String,
        @Path("password") password: String
    ): Response<User?>

    @GET("profile/user/{userId}")
    suspend fun getProfiles(
        @Path("userId") userId: Int
    ): Response<List<Profile>>

    @POST("profile")
    suspend fun createProfile(
        @Body request: CreateProfileRequest
    ): Response<Profile>

    @GET("progress/profile/{profileId}")
    suspend fun getProfileProgress(
        @Path("profileId") profileId: Int
    ): Response<List<WatchProgress>>

    @GET("progress/{profileId}/{movieId}")
    suspend fun getProgress(
        @Path("profileId") profileId: Int,
        @Path("movieId") movieId: Int
    ): Response<WatchProgress?>

    @POST("progress")
    suspend fun saveProgress(
        @Body request: WatchProgressRequest
    ): Response<Unit>

    @DELETE("progress/{profileId}/{movieId}")
    suspend fun clearProgress(
        @Path("profileId") profileId: Int,
        @Path("movieId") movieId: Int
    ): Response<Unit>
}
