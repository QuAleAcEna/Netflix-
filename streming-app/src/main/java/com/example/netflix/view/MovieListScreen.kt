package com.example.netflix.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.netflix.viewmodel.MovieViewModel

@Composable
fun MovieListScreen(
    viewModel: MovieViewModel = viewModel(),
    onMovieClick: (String) -> Unit
) {
    val movies = viewModel.movies.collectAsState().value

    LaunchedEffect(Unit) { viewModel.fetchMovies() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(movies.size) { index ->
            val movie = movies[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMovieClick(movie.videoUrl1080p) }
                    .padding(8.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(movie.thumbnailUrl),
                    contentDescription = movie.title,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(text = movie.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = movie.genre, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}