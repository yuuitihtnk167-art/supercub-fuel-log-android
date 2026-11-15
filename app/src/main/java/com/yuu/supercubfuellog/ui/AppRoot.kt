package com.yuu.supercubfuellog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yuu.supercubfuellog.data.ApprovalGate
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuu.supercubfuellog.MainViewModel

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val acquirePhoto = rememberPhotoAcquisition(viewModel)
    val user by viewModel.user.collectAsStateWithLifecycle()
    val ready by viewModel.isDataReady.collectAsStateWithLifecycle()
    val approvalStatus by viewModel.approvalStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    // Discard navigation and remembered UI when the signed-in account changes.
    key(user?.uid, approvalStatus == ApprovalGate.Status.APPROVED) {
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(viewModel) {
            viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    user == null -> LoginScreen(viewModel)
                    approvalStatus != ApprovalGate.Status.APPROVED -> ApprovalCheckScreen(viewModel)
                    !ready -> CloudLoadingScreen(viewModel)
                    else -> AuthenticatedNavigation(viewModel, acquirePhoto)
                }
                if (loading && user != null) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedNavigation(viewModel: MainViewModel, acquirePhoto: AcquirePhoto) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "record"
    val navigate: (String) -> Unit = { destination ->
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }
    NavHost(navController = navController, startDestination = "record") {
        composable("record") { RecordScreen(viewModel, route, navigate, acquirePhoto) }
        composable("history") {
            HistoryScreen(viewModel, route, navigate) { record ->
                viewModel.startEditing(record)
                navigate("record")
            }
        }
        composable("monthly") { MonthlyScreen(viewModel, route, navigate) }
        composable("settings") { SettingsScreen(viewModel, route, navigate) }
    }
}
