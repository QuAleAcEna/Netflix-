package com.example.cms_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cms_app.model.Movie
import com.example.cms_app.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MovieViewModel(
    private val repository: MovieRepository = MovieRepository()
) : ViewModel() {

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _lastOperationSucceeded = MutableStateFlow(false)
    val lastOperationSucceeded: StateFlow<Boolean> = _lastOperationSucceeded

    fun setError(message: String) {
        _error.value = message
    }

    fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = repository.fetchMovies()
                if (response.isSuccessful) {
                    _movies.value = response.body().orEmpty()
                    _lastOperationSucceeded.value = true
                } else {
                    _error.value = "Failed to load movies (${response.code()})"
                    _lastOperationSucceeded.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to load movies (${e.message})"
                _lastOperationSucceeded.value = false
            }
            _isLoading.value = false
        }
    }

    fun uploadMovieFile(filePath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _error.value = "File not found at $filePath"
                    _lastOperationSucceeded.value = false
                } else {
                    val response = repository.uploadMovieFile(file)
                    if (response.isSuccessful) {
                        _lastOperationSucceeded.value = true
                        loadMovies()
                    } else {
                        _error.value = "Failed to upload file (${response.code()})"
                        _lastOperationSucceeded.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to upload file (${e.message})"
                _lastOperationSucceeded.value = false
            }
            _isLoading.value = false
        }
    }

    fun uploadMovie(movie: Movie) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = repository.uploadMovie(movie)
                if (response.isSuccessful) {
                    _lastOperationSucceeded.value = true
                    loadMovies()
                } else {
                    _error.value = "Failed to upload movie (${response.code()})"
                    _lastOperationSucceeded.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to upload movie (${e.message})"
                _lastOperationSucceeded.value = false
            }
            _isLoading.value = false
        }
    }

    fun deleteMovie(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.deleteMovie(id)
                if (response.isSuccessful) {
                    _movies.value = _movies.value.filterNot { it.id == id }
                    _lastOperationSucceeded.value = true
                } else {
                    _error.value = "Failed to delete movie (${response.code()})"
                    _lastOperationSucceeded.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete movie (${e.message})"
                _lastOperationSucceeded.value = false
            }
            _isLoading.value = false
        }
    }
}
