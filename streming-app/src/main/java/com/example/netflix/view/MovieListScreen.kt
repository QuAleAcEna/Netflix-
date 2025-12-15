package com.example.netflix.view


import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.netflix.R
import com.example.netflix.model.Movie
import com.example.netflix.repository.ProgressRepository
import com.example.netflix.util.WatchProgressManager
import com.example.netflix.viewmodel.MovieViewModel
import com.example.netflix.data.AuthStore
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


// --- Pull to Refresh Implementation --- //

private val RefreshDistance = 100.dp
private const val DragMultiplier = 1.2f

@Composable
private fun rememberPullToRefreshState(onRefresh: () -> Unit, isRefreshing: Boolean): PullToRefreshState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(onRefresh) {
        PullToRefreshState(onRefresh, coroutineScope)
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            state.finishRefresh()
        }
    }
    return state
}

private class PullToRefreshState(
    private val onRefresh: () -> Unit,
    private val coroutineScope: CoroutineScope
) {
    private val _position = mutableStateOf(0f)
    val position: Float get() = _position.value

    private val maxPullDistance = RefreshDistance.value * 10.0f

    private var _isRefreshing by mutableStateOf(false)
    val isRefreshing: Boolean get() = _isRefreshing

    fun onPull(delta: Float): Float {
        if (_isRefreshing) return 0f

        val newPosition = (position + delta).coerceIn(0f, maxPullDistance)
        val consumed = newPosition - position
        _position.value = newPosition
        return consumed
    }

    fun onRelease() {
        if (_isRefreshing) return

        if (position > RefreshDistance.value * 1.5f) { // Threshold to trigger refresh
            _isRefreshing = true
            onRefresh()
        }
        animateTo(0f)
    }

    fun finishRefresh() {
        _isRefreshing = false
        animateTo(0f)
    }

    private fun animateTo(target: Float) {
        coroutineScope.launch {
            animate(initialValue = position, targetValue = target) { value, _ ->
                _position.value = value
            }
        }
    }
}

private class PullToRefreshNestedScrollConnection(private val state: PullToRefreshState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = when {
        source == UserInput && available.y < 0 && state.position > 0 -> Offset(0f, state.onPull(available.y))
        else -> Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = when {
        source == UserInput && available.y > 0 -> Offset(0f, state.onPull(available.y * DragMultiplier))
        else -> Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        state.onRelease()
        return Velocity.Zero
    }
}

@Composable
fun PullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState(onRefresh, isRefreshing)
    val nestedScrollConnection = remember { PullToRefreshNestedScrollConnection(state) }
    val density = LocalDensity.current
    val refreshDistancePx = with(density) { RefreshDistance.toPx() }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        content()

        val indicatorSize = 40.dp
        val indicatorAlpha = (state.position / refreshDistancePx).coerceIn(0f, 1f)

        Surface(
            modifier = Modifier
                .size(indicatorSize)
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (state.position * 0.5f).toDp() } - (indicatorSize / 2))
                .alpha(indicatorAlpha),
            shape = CircleShape,
            shadowElevation = 6.dp
        ) {
            if (state.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Pull to refresh",
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

// --- End of Pull to Refresh Implementation --- //

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    navController: NavController,
    userId: Int,
    accountName: String,
    profileId: Int,
    profileName: String? = null,
    viewModel: MovieViewModel = viewModel()
) {
    val movies by viewModel.movies.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val watchProgressManager = remember { WatchProgressManager(context) }
    val progressRepository = remember { ProgressRepository() }
    val progressRefreshScope = rememberCoroutineScope()
    val logoutScope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context.applicationContext) }
    var progressMap by remember(profileId) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var expandedMovie by remember { mutableStateOf<Movie?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    fun readLocalProgress(): Map<Int, Long> {
        return movies.associate { movie ->
            movie.id to watchProgressManager.getProgress(profileId, movie.id)
        }
    }

    suspend fun syncProgressWithBackend() {
            try {
                val response = progressRepository.getProfileProgress(profileId)
                if (response.isSuccessful) {
                    response.body().orEmpty().forEach { progress ->
                        watchProgressManager.saveProgress(profileId, progress.movieId, progress.positionMs)
                    }
                    progressMap = readLocalProgress()
                    return // Success
                } else {
                    throw Exception("API Error ${response.code()}")
                }
            } catch (e: Exception) {

        }
    }

    val onQualitySelected = { movie: Movie, quality: String ->
        val fileName = "${movie.name}-${quality}.mp4"
        val movieFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)

        val videoUrl = if (movieFile.exists()) {
            Uri.fromFile(movieFile).toString()
        } else {
            if (quality == "1080p") movie.videoPath + "/1080" else movie.videoPath + "/360"
        }

        val encodedUrl = Uri.encode(videoUrl)
        val encodedTitle = Uri.encode(fileName)
        navController.navigate("player/$profileId/${movie.id}/$encodedUrl/$encodedTitle")
        selectedMovie = null // Close the dialog
    }

    LaunchedEffect(profileId) {
        viewModel.fetchMovies()
        syncProgressWithBackend()
    }

    LaunchedEffect(profileId, movies) {
        progressMap = readLocalProgress()
    }

    DisposableEffect(lifecycleOwner, profileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                progressRefreshScope.launch {
                    syncProgressWithBackend()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (selectedMovie != null) {
        AlertDialog(
            onDismissRequest = { selectedMovie = null },
            title = { Text("Select Quality") },
            text = { Text("Choose the video quality for ${selectedMovie!!.name}") },
            confirmButton = {
                Button(onClick = { onQualitySelected(selectedMovie!!, "1080p") }) {
                    Text("1080p")
                }
            },
            dismissButton = {
                Button(onClick = { onQualitySelected(selectedMovie!!, "360p") }) {
                    Text("360p")
                }
            }
        )
    }

    if (expandedMovie != null) {
        AlertDialog(
            onDismissRequest = { expandedMovie = null },
            title = { Text("Name:"+expandedMovie!!.name) },
            text = {
                Column(){
                    Text("Description:"+expandedMovie!!.description)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Genre:"+ getGenreName(expandedMovie!!.genre))
                }
            },
            confirmButton = {
                Button(onClick = { expandedMovie = null }) {
                    Text("Close")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_wavy),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        SearchAppBar(
                            text = searchQuery,
                            onTextChange = { searchQuery = it },
                        )
                    },
                    actions = {
                        IconButton(onClick = { accountMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Account options"
                            )
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Switch profile") },
                                onClick = {
                                    accountMenuExpanded = false
                                    val navigatedBack = navController.popBackStack()
                                    if (!navigatedBack) {
                                        val encodedAccount = Uri.encode(accountName.ifBlank { "_" })
                                        navController.navigate("profiles/$userId/$encodedAccount")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    accountMenuExpanded = false
                                    // Clear saved credentials for this device
                                    logoutScope.launch {
                                        authStore.clear()
                                    }
                                    navController.navigate("signin") {
                                        popUpTo("splash") {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->

            val filteredMovies = if (searchQuery.isNotBlank()) {
                movies.filter { movie ->
                    movie.name.contains(searchQuery, ignoreCase = true) ||
                            getGenreName(movie.genre).contains(searchQuery, ignoreCase = true)
                }
            } else {
                movies
            }

            PullToRefresh(isRefreshing = isRefreshing, onRefresh = { viewModel.fetchMovies() }) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (!profileName.isNullOrBlank()) {
                        Text(
                            text = "Welcome, ${profileName.trim()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    if (filteredMovies.isEmpty() && !isRefreshing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            contentAlignment = Alignment.Center
                        ) {
                            if (movies.isEmpty()) {
                                CircularProgressIndicator()
                            } else {
                                Text("No movies found", color = Color.White)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredMovies) { movie ->
                                MovieCard(
                                    movie = movie,
                                    progressMs = progressMap[movie.id] ?: 0L,
                                    onClick = {
                                        val encodedAccount = Uri.encode(accountName.ifBlank { "_" })
                                        navController.navigate("movieDetail/$userId/$encodedAccount/$profileId/${movie.id}")
                                    },
                                    onLongClick = { expandedMovie = movie }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchAppBar(
    text: String,
    onTextChange: (String) -> Unit
) {
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)),
        value = text,
        onValueChange = { onTextChange(it) },
        placeholder = { Text(text = "Search by name or genre...", color = Color.White.copy(alpha = 0.7f)) },
        textStyle = TextStyle(color = Color.White),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search Icon",
                tint = Color.White.copy(alpha = 0.7f)
            )
        },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Icon",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(movie: Movie, progressMs: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Image(
                painter = rememberAsyncImagePainter(model = movie.thumbnailPath),
                contentDescription = movie.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 2.dp, 
                        color = Color.White, 
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (progressMs > 0L) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Continue • ${formatPlaybackPosition(progressMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = movie.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = getGenreName(movie.genre),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = movie.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

fun getGenreName(genreId: Int): String {
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
    // Support both legacy single-id values and bitmask values
    val isPowerOfTwo = genreId > 0 && (genreId and (genreId - 1)) == 0
    if (isPowerOfTwo) {
        val idx = Integer.numberOfTrailingZeros(genreId)
        return genres.getOrElse(idx) { "Unknown" }
    }
    if (genreId <= 0) return "Unknown"
    val selected = genres.indices.filter { genreId and (1 shl it) != 0 }.map { genres[it] }
    return if (selected.isNotEmpty()) selected.joinToString(", ") else "Unknown"
}

private fun formatPlaybackPosition(positionMs: Long): String {
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

@Preview(showBackground = true)
@Composable
fun MovieCardPreview() {
    val mockMovies = List(6) { index ->
        Movie(
            id = index + 1,
            name = "Sample Movie ${index + 1}",
            description = "This is a sample movie description for the preview.",
            genre = (index % 3) + 1, // Cycle through genres 1, 2, 3
            thumbnailPath = "", // No image in preview
            videoPath = ""
        )
    }

    Box {
        Image(
            painter = painterResource(id = R.drawable.background_wavy),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(mockMovies) { movie ->
                MovieCard(
                    movie = movie,
                    progressMs = if (movie.id % 2 == 0) 120_000L else 0L,
                    onClick = {},
                    onLongClick = {}
                )
            }
        }
    }
}
