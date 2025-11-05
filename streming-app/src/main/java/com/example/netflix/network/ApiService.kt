package com.example.netflix.network

import com.example.netflix.model.CreateProfileRequest
import com.example.netflix.model.Movie
import com.example.netflix.model.Profile
import com.example.netflix.model.User
import retrofit2.Response
import retrofit2.http.Body
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
}
