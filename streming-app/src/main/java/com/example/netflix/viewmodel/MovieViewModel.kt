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
                for(movie in _movies.value){
                    movie.videoPath = repository.BASE_URL + movie.videoPath;
                    movie.thumbnailPath= repository.BASE_URL + movie.thumbnailPath  ;
                }
            }

//            val mockMovies = listOf(
//                Movie(
//                    id = "1",
//                    title = "Big Buck Bunny",
//                    description = "A fun animated short film",
//                    genre = "Animation",
//                    duration = "10m",
//                    thumbnailUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
//                    videoUrl1080p = "https://archive.org/download/PopeyeAliBaba/PopeyeAliBaba.mp4",
//                    videoUrl360p = "https://archive.org/download/PopeyeAliBaba/PopeyeAliBaba_512kb.mp4"
//                ),
//                Movie(
//                    id = "2",
//                    title = "Night of the Living Dead",
//                    description = "Classic horror film",
//                    genre = "Horror",
//                    duration = "95m",
//                    thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9d/Night_of_the_Living_Dead_%281968%29_poster.jpg",
//                    videoUrl1080p = "https://archive.org/download/night_of_the_living_dead/night_of_the_living_dead.mp4",
//                    videoUrl360p = "https://archive.org/download/night_of_the_living_dead/night_of_the_living_dead_512kb.mp4"
//                )
//            )
           // _movies.value= mockMovies



        }
    }
}