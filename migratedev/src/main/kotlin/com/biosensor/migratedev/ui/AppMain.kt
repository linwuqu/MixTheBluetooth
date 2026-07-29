package com.biosensor.migratedev.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biosensor.migratedev.AppGraph
import com.biosensor.migratedev.translation.auth.AuthIntent
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.auth.AuthUiState
import com.biosensor.migratedev.translation.connection.ConnectionTranslation
import com.biosensor.migratedev.ui.auth.AuthRoute
import com.biosensor.migratedev.ui.connection.ConnectionRoute
import kotlinx.coroutines.flow.first

private const val SESSION_GRAPH = "session"
private const val AUTH_ROUTE = "auth"
private const val CONNECTION_ROUTE = "connection/{userId}"

@Composable
fun AppMain(graph: AppGraph) {
    val navController = rememberNavController()

    NavHost(
        navController = navController, startDestination = SESSION_GRAPH
    ) {
        navigation(
            route = SESSION_GRAPH, startDestination = AUTH_ROUTE
        ) {
            composable(AUTH_ROUTE) { entry ->
                val sessionEntry = remember(entry) {
                    navController.getBackStackEntry(
                        SESSION_GRAPH
                    )
                }
                val auth: AuthTranslation = viewModel(
                    viewModelStoreOwner = sessionEntry, factory = graph.authTranslationFactory
                )
                val state by auth.uiState.collectAsStateWithLifecycle()

                if (state is AuthUiState.Authenticated) {
                    val user = (state as AuthUiState.Authenticated).user
                    LaunchedEffect(user.id) {
                        navController.navigate("connection/${Uri.encode(user.id)}") {
                            popUpTo(AUTH_ROUTE) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
                AuthRoute(state, auth)
            }

            composable(
                route = CONNECTION_ROUTE, arguments = listOf(
                    navArgument("userId") {
                        type = NavType.StringType
                    })
            ) { entry ->
                val userId = requireNotNull(
                    entry.arguments?.getString("userId")
                )
                val sessionEntry = remember(entry) {
                    navController.getBackStackEntry(
                        SESSION_GRAPH
                    )
                }
                val auth: AuthTranslation = viewModel(
                    viewModelStoreOwner = sessionEntry, factory = graph.authTranslationFactory
                )
                val connection: ConnectionTranslation = viewModel(
                    key = "connection:$userId", factory = graph.connectionTranslationFactory(
                        userId
                    )
                )

                ConnectionRoute(
                    translation = connection, onLogoutReady = {
                        auth.submit(AuthIntent.Logout)
                        auth.uiState.first {
                            it is AuthUiState.Idle
                        }
                        navController.navigate(AUTH_ROUTE) {
                            popUpTo(SESSION_GRAPH) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    })
            }
        }
    }
}
