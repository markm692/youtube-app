package com.youtubeapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youtubeapp.ui.home.HomeScreen
import com.youtubeapp.ui.player.PlayerScreen
import com.youtubeapp.ui.search.SearchScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onVideoClick = { videoId ->
                    navController.navigate("player/$videoId")
                },
                onSearchClick = {
                    navController.navigate("search")
                }
            )
        }

        composable("search") {
            SearchScreen(
                onVideoClick = { videoId ->
                    navController.navigate("player/$videoId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "player/{videoId}",
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            PlayerScreen(
                videoId = videoId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
