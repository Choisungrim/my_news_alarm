package com.example.news_alarm.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.news_alarm.ui.screen.DetailScreen
import com.example.news_alarm.ui.screen.RssScreen
import com.example.news_alarm.ui.screen.SettingsScreen
import com.example.news_alarm.viewmodel.NewsViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: NewsViewModel = viewModel()

    NavHost(navController, startDestination = "rss") {
        composable("rss") {
            RssScreen(navController, viewModel)
        }
        composable("detail") {
            DetailScreen(viewModel = viewModel) {
                viewModel.clearSelection()
                navController.popBackStack()
            }
        }
        composable("settings") {
            SettingsScreen(context = LocalContext.current) {
                viewModel.clearSelection()
                navController.popBackStack()
            }
        }
    }
}