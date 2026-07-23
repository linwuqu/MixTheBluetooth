package com.biosensor.migratedev.ui.auth

import androidx.compose.runtime.Composable
import com.biosensor.migratedev.translation.auth.AuthIntent
import com.biosensor.migratedev.translation.auth.AuthLifecycleEvent
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.auth.AuthUiState

@Composable
fun AuthRoute(state: AuthUiState, translation: AuthTranslation) {
    AuthScreen(
        state = state,
        onLogin = { account, password ->
            translation.submit(AuthIntent.SubmitLogin(account, password))
        },
        onRegister = { account, password, phone ->
            translation.submit(AuthIntent.SubmitRegister(account, password, phone))
        },
        onRetrySession = {
            translation.onLifecycle(AuthLifecycleEvent.AppStarted)
        }
    )
}
