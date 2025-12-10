package com.example.cms_app.view

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cms_app.viewmodel.MovieViewModel
import com.example.cms_app.viewmodel.UserViewModel

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    movieViewModel: MovieViewModel = MovieViewModel(),
    userViewModel: UserViewModel = UserViewModel(),
    loadOnStart: Boolean = true
) {
    val navController = rememberNavController()

    LaunchedEffect(loadOnStart) {
        if (loadOnStart) {
            movieViewModel.loadMovies()
            userViewModel.loadUsers()
        }
    }

    val items = listOf("movies", "users")
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    androidx.compose.material3.Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { route ->
                    val label = if (route == "movies") "Movies" else "Users"
                    val icon = if (route == "movies") Icons.Default.Movie else Icons.Default.Person
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                launchSingleTop = true
                                popUpTo(navController.graph.startDestinationId)
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "movies"
        ) {
            composable("movies") {
                MovieListScreen(
                    viewModel = movieViewModel,
                    onCreateMovie = { navController.navigate("movieForm/-1") },
                    onEditMovie = { id -> navController.navigate("movieForm/$id") }
                )
            }
            composable(
                route = "movieForm/{movieId}",
                arguments = listOf(
                    navArgument("movieId") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getInt("movieId") ?: -1
                MovieFormScreen(
                    viewModel = movieViewModel,
                    movieId = movieId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("users") {
                UserListScreen(
                    viewModel = userViewModel
                )
            }
        }
    }
}
