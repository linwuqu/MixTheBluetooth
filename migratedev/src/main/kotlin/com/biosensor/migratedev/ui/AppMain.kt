package com.biosensor.migratedev.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biosensor.migratedev.translation.auth.AuthIntent
import com.biosensor.migratedev.translation.auth.AuthLifecycleEvent
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.auth.AuthUiState
import com.biosensor.migratedev.ui.auth.AuthRoute
import com.biosensor.migratedev.ui.auth.DebugHomeScreen

@Composable
fun AppMain(authTranslation: AuthTranslation) {
    val uiState by authTranslation.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(authTranslation) {
        authTranslation.onLifecycle(AuthLifecycleEvent.AppStarted)
    }
    when (val state = uiState) {
        is AuthUiState.Authenticated -> DebugHomeScreen(state.user) {
            authTranslation.submit(AuthIntent.Logout)
        }

        else -> AuthRoute(state, authTranslation)
    }
}
