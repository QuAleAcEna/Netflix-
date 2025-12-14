package com.example.cms_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cms_app.model.Movie
import com.example.cms_app.model.UpdateMovieRequest
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
    private val _uploadedThumbnailUrl = MutableStateFlow<String?>(null)
    val uploadedThumbnailUrl: StateFlow<String?> = _uploadedThumbnailUrl

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

    fun uploadMovieFile(filePath: String, movieName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _error.value = "File not found at $filePath"
                    _lastOperationSucceeded.value = false
                } else {
                    val response = repository.uploadMovieFile(file, movieName)
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

    fun uploadMovieWithContent(
        movie: Movie,
        videoPath: String,
        thumbnailPath: String?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 1. Upload Thumbnail (if exists)
                var finalThumbnailPath = movie.thumbnailPath
                if (!thumbnailPath.isNullOrBlank()) {
                    val thumbFile = File(thumbnailPath)
                    if (thumbFile.exists()) {
                        val thumbResponse = repository.uploadThumbnailFile(thumbFile, movie.name)
                        if (!thumbResponse.isSuccessful) {
                            throw Exception("Thumbnail upload failed: ${thumbResponse.code()}")
                        }
                        val responseBody = thumbResponse.body()?.string()
                        if (!responseBody.isNullOrBlank()) {
                            finalThumbnailPath = responseBody
                        }
                    }
                }

                // 2. Upload Video
                val videoFile = File(videoPath)
                if (!videoFile.exists()) {
                     throw Exception("Video file not found at $videoPath")
                }
                
                val safeName = movie.name.trim().replace("[^A-Za-z0-9._-]".toRegex(), "_")
                val videoResponse = repository.uploadMovieFile(videoFile, safeName)
                
                if (!videoResponse.isSuccessful) {
                    throw Exception("Video upload failed: ${videoResponse.code()}")
                }

                val finalVideoPath = videoResponse.body()?.string()
                if (finalVideoPath.isNullOrBlank()) {
                     throw Exception("Video upload succeeded but returned empty URL")
                }

                // 3. Upload Movie to DB
                val finalMovie = movie.copy(
                    thumbnailPath = finalThumbnailPath,
                    videoPath = finalVideoPath
                )
                
                val movieResponse = repository.uploadMovie(finalMovie)
                if (movieResponse.isSuccessful) {
                    _lastOperationSucceeded.value = true
                    loadMovies()
                } else {
                     throw Exception("Movie creation failed: ${movieResponse.code()}")
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
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

    fun updateMovie(id: Int, request: UpdateMovieRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = repository.updateMovie(id, request)
                if (response.isSuccessful) {
                    val updated = response.body()
                    _lastOperationSucceeded.value = true
                    if (updated != null) {
                        _movies.value = _movies.value.map { if (it.id == id) updated else it }
                    } else {
                        loadMovies()
                    }
                } else {
                    _error.value = "Failed to update movie (${response.code()})"
                    _lastOperationSucceeded.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to update movie (${e.message})"
                _lastOperationSucceeded.value = false
            }
            _isLoading.value = false
        }
    }

    fun uploadThumbnailFile(filePath: String, movieName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _error.value = "Thumbnail not found at $filePath"
                    _lastOperationSucceeded.value = false
                } else {
                    val response = repository.uploadThumbnailFile(file, movieName)
                    if (response.isSuccessful) {
                        _uploadedThumbnailUrl.value = response.body()?.string()
                        _lastOperationSucceeded.value = true
                    } else {
                        _error.value = "Failed to upload thumbnail (${response.code()})"
                        _lastOperationSucceeded.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to upload thumbnail (${e.message})"
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
