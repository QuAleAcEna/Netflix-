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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.cms_app.model.Movie
import com.example.cms_app.model.UpdateMovieRequest
import com.example.cms_app.viewmodel.MovieViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.InputStream

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MovieFormScreen(
    viewModel: MovieViewModel,
    movieId: Int,
    onBack: () -> Unit
) {
    val movies = viewModel.movies.collectAsState()
    val error = viewModel.error.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()
    val lastOperationSucceeded = viewModel.lastOperationSucceeded.collectAsState()
    val uploadedThumbnailUrl = viewModel.uploadedThumbnailUrl.collectAsState()
    val isEdit = movieId != -1
    val didSubmit = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val existing = movies.value.find { it.id == movieId }
    val nameState = remember { mutableStateOf(TextFieldValue(existing?.name.orEmpty())) }
    val descriptionState = remember { mutableStateOf(TextFieldValue(existing?.description.orEmpty())) }
    val genreState = remember { mutableStateOf(TextFieldValue(existing?.genre?.toString().orEmpty())) }
    val thumbnailState = remember { mutableStateOf(TextFieldValue(existing?.thumbnailPath.orEmpty())) }
    val videoState = remember { mutableStateOf(TextFieldValue(existing?.videoPath.orEmpty())) }
    val scope = rememberCoroutineScope()

    val pickThumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File.createTempFile("thumb_", ".png", context.cacheDir)
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        val movieName = nameState.value.text.ifBlank { "thumbnail" }
                        viewModel.uploadThumbnailFile(tempFile.absolutePath, movieName)
                    } else {
                        viewModel.setError("Unable to read selected thumbnail")
                    }
                } catch (e: Exception) {
                    viewModel.setError("Failed to process thumbnail (${e.message})")
                }
            }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File.createTempFile("upload_", ".mp4", context.cacheDir)
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        videoState.value = TextFieldValue(tempFile.absolutePath)
                    } else {
                        viewModel.setError("Unable to read selected file")
                    }
                } catch (e: Exception) {
                    viewModel.setError("Failed to process selected file (${e.message})")
                }
            }
        }
    }

    LaunchedEffect(movieId) {
        if (movies.value.isEmpty()) {
            viewModel.loadMovies()
        }
    }
    LaunchedEffect(existing) {
        if (existing != null) {
            nameState.value = TextFieldValue(existing.name)
            descriptionState.value = TextFieldValue(existing.description)
            genreState.value = TextFieldValue(existing.genre.toString())
            thumbnailState.value = TextFieldValue(existing.thumbnailPath)
            videoState.value = TextFieldValue(existing.videoPath)
        }
    }
    LaunchedEffect(uploadedThumbnailUrl.value) {
        uploadedThumbnailUrl.value?.let { url ->
            thumbnailState.value = TextFieldValue(url)
        }
    }
    LaunchedEffect(lastOperationSucceeded.value, isLoading.value, didSubmit.value) {
        if (didSubmit.value && lastOperationSucceeded.value && !isLoading.value) {
            onBack()
            didSubmit.value = false
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
            Button(
                onClick = { pickThumbnailLauncher.launch("image/*") },
                enabled = !isLoading.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick thumbnail from device")
            }
            Spacer(Modifier.size(12.dp))
            if (!isEdit) {
                OutlinedTextField(
                    value = videoState.value,
                    onValueChange = { videoState.value = it },
                    label = { Text("Video file path (local)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(16.dp))
                Button(
                    onClick = { pickVideoLauncher.launch("video/*") },
                    enabled = !isLoading.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick video from device")
                }
                Spacer(Modifier.size(12.dp))
            }
            error.value?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
            }
            if (!isLoading.value && lastOperationSucceeded.value) {
                Text(
                    if (isEdit) "Changes saved" else "Upload completed",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
            }
            Spacer(Modifier.size(8.dp))
            if (isEdit) {
                Button(
                    onClick = {
                        val genreValue = genreState.value.text.toIntOrNull()
                        if (nameState.value.text.isBlank()) {
                            viewModel.setError("Title cannot be empty")
                            return@Button
                        }
                        if (thumbnailState.value.text.isBlank()) {
                            viewModel.setError("Thumbnail path cannot be empty")
                            return@Button
                        }
                        scope.launch {
                            didSubmit.value = true
                            viewModel.updateMovie(
                                movieId,
                                UpdateMovieRequest(
                                    name = nameState.value.text.trim(),
                                    description = descriptionState.value.text.trim(),
                                    genre = genreValue,
                                    thumbnailPath = thumbnailState.value.text.trim()
                                )
                            )
                        }
                    },
                    enabled = !isLoading.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Save changes")
                    }
                }
            } else {
                Button(
                    onClick = {
                        val path = videoState.value.text
                        val genreValue = genreState.value.text.toIntOrNull()
                        if (nameState.value.text.isBlank()) {
                            viewModel.setError("Title cannot be empty")
                            return@Button
                        }
                        if (path.isBlank()) {
                            viewModel.setError("Video path cannot be empty")
                            return@Button
                        }
                        // Sanitize movie name for server-side filenames/paths
                        val safeName = nameState.value.text.trim().replace("[^A-Za-z0-9._-]".toRegex(), "_")
                        scope.launch {
                            didSubmit.value = true
                            // Save metadata using server-friendly video/thumbnail paths
                            viewModel.uploadMovie(
                                Movie(
                                    name = nameState.value.text.trim(),
                                    description = descriptionState.value.text.trim(),
                                    genre = genreValue ?: 0,
                                    thumbnailPath = thumbnailState.value.text.trim(),
                                    videoPath = "movie/$safeName"
                                )
                            )
                            // Upload the actual video file with a consistent filename
                            viewModel.uploadMovieFile(path, safeName)
                        }
                    },
                    enabled = !isLoading.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Save & upload film")
                    }
                }
            }
        }
    }
}
