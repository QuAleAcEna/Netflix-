package com.example.cms_app.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.cms_app.model.Movie
import com.example.cms_app.viewmodel.MovieViewModel
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MovieFormScreen(
    viewModel: MovieViewModel,
    movieId: Int,
    onBack: () -> Unit
) {
    val movies = viewModel.movies.collectAsState()
    val lastOperationSucceeded = viewModel.lastOperationSucceeded.collectAsState()
    val existing = movies.value.find { it.id == movieId }
    val nameState = remember { mutableStateOf(TextFieldValue(existing?.name.orEmpty())) }
    val descriptionState = remember { mutableStateOf(TextFieldValue(existing?.description.orEmpty())) }
    val genreState = remember { mutableStateOf(TextFieldValue(existing?.genre?.toString().orEmpty())) }
    val thumbnailState = remember { mutableStateOf(TextFieldValue(existing?.thumbnailPath.orEmpty())) }
    val videoState = remember { mutableStateOf(TextFieldValue(existing?.videoPath.orEmpty())) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(movieId) {
        if (movies.value.isEmpty()) {
            viewModel.loadMovies()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (movieId == -1) "Upload movie" else "Edit movie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = nameState.value,
                onValueChange = { nameState.value = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = descriptionState.value,
                onValueChange = { descriptionState.value = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = genreState.value,
                onValueChange = { genreState.value = it },
                label = { Text("Genre (number)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = thumbnailState.value,
                onValueChange = { thumbnailState.value = it },
                label = { Text("Thumbnail path") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = videoState.value,
                onValueChange = { videoState.value = it },
                label = { Text("Video path") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(24.dp))
            Button(
                onClick = {
                    val movie = Movie(
                        id = if (movieId == -1) 0 else movieId,
                        name = nameState.value.text,
                        description = descriptionState.value.text,
                        genre = genreState.value.text.toIntOrNull() ?: 0,
                        thumbnailPath = thumbnailState.value.text,
                        videoPath = videoState.value.text
                    )
                    scope.launch {
                        viewModel.uploadMovie(movie)
                        if (lastOperationSucceeded.value) {
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (movieId == -1) "Upload" else "Save")
            }
        }
    }
}
