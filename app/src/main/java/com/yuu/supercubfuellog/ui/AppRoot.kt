package com.yuu.supercubfuellog.ui

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuu.supercubfuellog.MainViewModel

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = viewModel.messages

    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "record"

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
        NavHost(navController = navController, startDestination = "record") {
            composable("record") {
                RecordScreen(
                    viewModel = viewModel,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            restoreState = true
                        }
                    }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            restoreState = true
                        }
                    },
                    onEditNavigate = { record ->
                        viewModel.startEditing(record)
                        navController.navigate("record") { launchSingleTop = true }
                    }
                )
            }
            composable("monthly") {
                MonthlyScreen(
                    viewModel = viewModel,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            restoreState = true
                        }
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
