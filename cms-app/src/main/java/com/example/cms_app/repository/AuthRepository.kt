package com.example.cms_app.repository

import com.example.cms_app.model.LoginRequest
import com.example.cms_app.network.RetrofitInstance

class AuthRepository {
    private val api = RetrofitInstance.api

    suspend fun login(username: String, password: String) =
        api.login(LoginRequest(username, password))
}
