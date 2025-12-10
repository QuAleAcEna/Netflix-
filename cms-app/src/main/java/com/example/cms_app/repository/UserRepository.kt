package com.example.cms_app.repository

import com.example.cms_app.model.CreateUserRequest
import com.example.cms_app.network.RetrofitInstance

class UserRepository {
    private val api = RetrofitInstance.api

    suspend fun fetchUsers() = api.getUsers()
    suspend fun createUser(name: String, password: String) =
        api.createUser(CreateUserRequest(name = name, password = password))

    suspend fun deleteUser(id: Int) = api.deleteUser(id)
}
