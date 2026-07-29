package com.biosensor.migratedev.ui.auth

import androidx.compose.runtime.Composable
import com.biosensor.migratedev.translation.auth.AuthIntent
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.auth.AuthUiState

@Composable
fun AuthRoute(state: AuthUiState, translation: AuthTranslation) {
    AuthScreen(state = state, onLogin = { phone, password ->
        translation.submit(AuthIntent.SubmitLogin(phone, password))
    }, onRegister = { phone, password, nickname ->
        translation.submit(AuthIntent.SubmitRegister(phone, password, nickname))
    }, onRetrySession = {
        translation.submit(AuthIntent.RetrySession)
    })
}
