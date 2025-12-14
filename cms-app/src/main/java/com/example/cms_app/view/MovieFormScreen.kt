package com.example.cms_app.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val genres = listOf(
        "Action",
        "Adventure",
        "Animation",
        "Comedy",
        "Crime",
        "Drama",
        "Family",
        "Fantasy",
        "Historical",
        "Horror",
        "Musical",
        "Mystery",
        "Romance",
        "Science Fiction",
        "Sports",
        "Thriller / Suspense",
        "War",
        "Western"
    )
    fun decodeGenres(mask: Int): Set<Int> {
        val selected = mutableSetOf<Int>()
        genres.indices.forEach { idx ->
            if (mask and (1 shl idx) != 0) selected.add(idx)
        }
        return selected
    }
    val genreSelection = remember { mutableStateOf(decodeGenres(existing?.genre ?: 0)) }
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
            genreSelection.value = decodeGenres(existing.genre)
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
                .verticalScroll(rememberScrollState())
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
            Text("Genres (select one or more)")
            Spacer(Modifier.size(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEachIndexed { index, label ->
                    val selected = genreSelection.value.contains(index)
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = genreSelection.value.toMutableSet()
                            if (selected) updated.remove(index) else updated.add(index)
                            genreSelection.value = updated.toSet()
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = thumbnailState.value,
                onValueChange = { thumbnailState.value = it },
                label = { Text("Thumbnail path (leave empty for default)") },
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
                        val genreValue = genreSelection.value.fold(0) { acc, idx -> acc or (1 shl idx) }
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
                        val videoPath = videoState.value.text
                        val thumbnailPath = thumbnailState.value.text
                        val genreValue = genreSelection.value.fold(0) { acc, idx -> acc or (1 shl idx) }
                        
                        if (nameState.value.text.isBlank()) {
                            viewModel.setError("Title cannot be empty")
                            return@Button
                        }
                        if (videoPath.isBlank()) {
                            viewModel.setError("Video path cannot be empty")
                            return@Button
                        }
                        
                        scope.launch {
                            didSubmit.value = true
                            
                            val movie = Movie(
                                name = nameState.value.text.trim(),
                                description = descriptionState.value.text.trim(),
                                genre = genreValue,
                                thumbnailPath = "", // Will be set in uploadMovieWithContent
                                videoPath = "" // Will be set in uploadMovieWithContent
                            )
                            
                            viewModel.uploadMovieWithContent(movie, videoPath, thumbnailPath)
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
            
            // Large spacer to allow scrolling past the keyboard
            Spacer(Modifier.size(200.dp))
        }
    }
}
