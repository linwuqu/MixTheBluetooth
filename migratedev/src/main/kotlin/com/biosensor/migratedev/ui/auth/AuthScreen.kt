package com.biosensor.migratedev.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.auth.AuthUiState

/**
 * 纯呈现:只描述页面结构,不涉及任何业务。
 * 结构 = 欢迎标题 + 状态区 + 表单区。
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onRetrySession: () -> Unit
) {
    // 登录/注册模式影响状态区文案与表单按钮,由骨架持有并分发给两个子组件
    var registerMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Registered) {
            registerMode = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("欢迎", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        AuthStatusArea(
            state = state,
            registerMode = registerMode,
            onRetrySession = onRetrySession
        )
        Spacer(Modifier.height(12.dp))
        AuthForm(
            state = state,
            registerMode = registerMode,
            onRegisterModeChange = { registerMode = it },
            onLogin = onLogin,
            onRegister = onRegister
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    MaterialTheme {
        AuthScreen(
            state = AuthUiState.Idle,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onRetrySession = {}
        )
    }
}
