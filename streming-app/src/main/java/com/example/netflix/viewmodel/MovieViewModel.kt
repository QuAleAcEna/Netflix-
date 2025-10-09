package com.example.netflix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netflix.model.Movie
import com.example.netflix.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MovieViewModel : ViewModel() {

    private val repository = MovieRepository()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies

    fun fetchMovies() {
        viewModelScope.launch {
            val response = repository.getMovies()
            if (response.isSuccessful) {
                _movies.value = response.body() ?: emptyList()
            }
        }
    }
}