package com.example.netflix.view

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.netflix.R
import com.example.netflix.repository.ProgressRepository
import com.example.netflix.util.WatchProgressManager
import com.example.netflix.viewmodel.MovieViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    navController: NavController,
    userId: Int,
    accountName: String,
    profileId: Int,
    movieId: Int,
    movieViewModel: MovieViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val watchProgressManager = remember { WatchProgressManager(context) }
    val progressRepository = remember { ProgressRepository() }
    val movies by movieViewModel.movies.collectAsState()
    var selectedQuality by remember { mutableStateOf("1080") }

    LaunchedEffect(movies) {
        if (movies.isEmpty()) {
            movieViewModel.fetchMovies()
        }
    }

    val movie = movies.find { it.id == movieId }
    val progressMs = watchProgressManager.getProgress(profileId, movieId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movie?.name ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (movie == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { Text("Loading movie...") }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Image(
                painter = painterResource(id = R.drawable.background_wavy),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                if (movie.thumbnailPath.isNotBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = movie.thumbnailPath),
                        contentDescription = "Thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Transparent)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(movie.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Genres: ${detailGenreName(movie.genre)}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Year: N/A", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(movie.description.ifBlank { "No description available." }, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(modifier = Modifier.height(24.dp))
                if (progressMs > 0L) {
                    Text("Progress: ${detailFormatPlaybackPosition(progressMs)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text("Quality", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QualityChip(label = "1080p", selected = selectedQuality == "1080") {
                        selectedQuality = "1080"
                    }
                    QualityChip(label = "360p", selected = selectedQuality == "360") {
                        selectedQuality = "360"
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val fileName = "${movie.name}-${selectedQuality}p.mp4"
                        val videoUrl = "${movie.videoPath}/${selectedQuality}"
                        val encodedUrl = Uri.encode(videoUrl)
                        val encodedTitle = Uri.encode(fileName)
                        navController.navigate("player/$profileId/${movie.id}/$encodedUrl/$encodedTitle")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val continueLabel = if (progressMs > 0) "Continue • ${detailFormatPlaybackPosition(progressMs)}" else "Watch"
                    Text(continueLabel)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        watchProgressManager.clearProgress(profileId, movie.id)
                        scope.launch(Dispatchers.IO) {
                            try {
                                progressRepository.clearProgress(profileId, movie.id)
                            } catch (_: Exception) {
                            }
                        }
                        val fileName = "${movie.name}-${selectedQuality}p.mp4"
                        val videoUrl = "${movie.videoPath}/${selectedQuality}"
                        val encodedUrl = Uri.encode(videoUrl)
                        val encodedTitle = Uri.encode(fileName)
                        navController.navigate("player/$profileId/${movie.id}/$encodedUrl/$encodedTitle")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restart") }
            }
        }
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
    val contentColor = if (selected) Color.White else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun detailGenreName(genreId: Int): String {
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
        "Thriller",
        "War",
        "Western"
    )
    val isPowerOfTwo = genreId > 0 && (genreId and (genreId - 1)) == 0
    if (isPowerOfTwo) {
        val idx = Integer.numberOfTrailingZeros(genreId)
        return genres.getOrElse(idx) { "Unknown" }
    }
    if (genreId <= 0) return "Unknown"
    val selected = genres.indices.filter { genreId and (1 shl it) != 0 }.map { genres[it] }
    return if (selected.isNotEmpty()) selected.joinToString(", ") else "Unknown"
}

private fun detailFormatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
