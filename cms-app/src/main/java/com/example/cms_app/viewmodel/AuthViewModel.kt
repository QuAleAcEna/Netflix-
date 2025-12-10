package com.example.cms_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cms_app.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _error.value = null
            if (username.isBlank() || password.isBlank()) {
                _error.value = "Please enter username and password"
                return@launch
            }
            _isLoading.value = true
            try {
                val response = repository.login(username, password)
                if (response.isSuccessful && response.body() != null) {
                    _isAuthenticated.value = true
                } else {
                    _error.value = "Invalid credentials"
                    _isAuthenticated.value = false
                }
            } catch (e: Exception) {
                _error.value = "Login failed (${e.message})"
                _isAuthenticated.value = false
            }
            _isLoading.value = false
        }
    }
}
