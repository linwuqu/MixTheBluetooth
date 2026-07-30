package com.biosensor.migratedev.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.biosensor.migratedev.AppGraph
import com.biosensor.migratedev.decisioncore.root.RootScreen
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.decisioncore.root.screen
import com.biosensor.migratedev.ui.auth.AuthRoute
import com.biosensor.migratedev.ui.connection.ConnectionRoute

private const val SESSION_GRAPH = "session"
private const val AUTH_ROUTE = "auth"
private const val CONNECTION_ROUTE = "connection/{userId}"

@Composable
fun AppMain(graph: AppGraph) {
    val navController = rememberNavController()
    val root = graph.rootWorkflow
    val rootState by root.state.collectAsStateWithLifecycle()

    RootNavigationEffect(navController, rootState)

    NavHost(
        navController = navController, startDestination = SESSION_GRAPH
    ) {
        navigation(
            route = SESSION_GRAPH, startDestination = AUTH_ROUTE
        ) {
            composable(AUTH_ROUTE) {
                val auth = root.authOrNull()
                if (auth == null) {
                    AppLoading()
                } else {
                    val state by auth.uiState.collectAsStateWithLifecycle()
                    AuthRoute(state, auth)
                }
            }

            composable(CONNECTION_ROUTE) {
                val connection = root.connectionOrNull()
                if (connection == null) {
                    AppLoading()
                } else {
                    ConnectionRoute(connection)
                }
            }
        }
    }
}

@Composable
private fun RootNavigationEffect(
    navController: NavHostController, rootState: RootState
) {
    val screen = rootState.screen()
    LaunchedEffect(screen) {
        when (screen) {
            RootScreen.Auth -> {
                navController.navigate(AUTH_ROUTE) {
                    popUpTo(SESSION_GRAPH) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }

            is RootScreen.Connection -> {
                val route = "connection/${Uri.encode(screen.userId)}"
                navController.navigate(route) {
                    popUpTo(AUTH_ROUTE) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }
}

@Composable
private fun AppLoading() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
