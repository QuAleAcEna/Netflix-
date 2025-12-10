package com.example.cms_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cms_app.model.User
import com.example.cms_app.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val repository: UserRepository = UserRepository()
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = repository.fetchUsers()
                if (response.isSuccessful) {
                    _users.value = response.body().orEmpty()
                } else {
                    _error.value = "Failed to load users (${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Failed to load users (${e.message})"
            }
            _isLoading.value = false
        }
    }

    fun createUser(name: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = repository.createUser(name, password)
                if (response.isSuccessful) {
                    loadUsers()
                } else {
                    _error.value = "Failed to create user (${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Failed to create user (${e.message})"
            }
            _isLoading.value = false
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.deleteUser(id)
                if (response.isSuccessful) {
                    _users.value = _users.value.filterNot { it.id == id }
                } else {
                    _error.value = "Failed to delete user (${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete user (${e.message})"
            }
            _isLoading.value = false
        }
    }
}
