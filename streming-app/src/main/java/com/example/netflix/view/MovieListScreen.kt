package com.example.netflix.view


import android.net.Uri

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.netflix.viewmodel.MovieViewModel



@Composable
fun MovieListScreen(
    navController: NavController,
    viewModel: MovieViewModel = viewModel()
) {
    val movies = viewModel.movies.collectAsState().value

    LaunchedEffect(Unit) {
        viewModel.fetchMovies()
    }

    if (movies.isEmpty()) {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading movies...", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        // Movie list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(movies.size) { index ->
                val movie = movies[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Navigate to player screen with encoded video URL
                            navController.navigate("player/${Uri.encode(movie.videoPath+"/1080")}")

                        }
                        .padding(vertical = 8.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = movie.thumbnailPath),
                        contentDescription = movie.name,
                        modifier = Modifier
                            .size(120.dp)
                            .aspectRatio(16 / 9f),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = movie.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = movie.genre.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = movie.description.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
