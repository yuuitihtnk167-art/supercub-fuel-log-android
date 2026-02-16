package com.yuu.supercubfuellog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "record"
    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
            NavHost(navController = navController, startDestination = "record") {
                composable("record") {
                    RecordScreen(
                        viewModel = viewModel,
                        currentRoute = currentRoute,
                        onNavigate = navigateTo
                    )
                }
                composable("history") {
                    HistoryScreen(
                        viewModel = viewModel,
                        currentRoute = currentRoute,
                        onNavigate = navigateTo,
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
                        onNavigate = navigateTo
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        currentRoute = currentRoute,
                        onNavigate = navigateTo
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
