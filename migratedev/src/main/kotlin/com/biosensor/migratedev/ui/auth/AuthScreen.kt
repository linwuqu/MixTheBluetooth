package com.biosensor.migratedev.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.translation.auth.AuthUiState

/** UI 占位；工作流合约完成接线后再补充具体界面。 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onRetrySession: () -> Unit
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }
    val canSubmit =
        state is AuthUiState.Idle || state is AuthUiState.Error || state is AuthUiState.Registered

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
        when (state) {
            AuthUiState.RestoringSession -> {
                CircularProgressIndicator()
                Text("正在自动登录…")
            }

            AuthUiState.Loading -> {
                CircularProgressIndicator()
                Text(if (registerMode) "正在注册…" else "正在登录…")
            }

            AuthUiState.SavingSession -> {
                CircularProgressIndicator()
                Text("正在安全保存会话…")
            }

            is AuthUiState.Registered -> Text(state.message)
            is AuthUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetrySession) {
                    Text("遇到未知问题，点击此处重置")
                }
            }

            else -> Unit
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        if (registerMode) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账户名") },
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = canSubmit, onClick = {
                    if (registerMode) {
                        onRegister(phone.trim(), password, nickname.trim())
                    } else {
                        onLogin(phone.trim(), password)
                    }
                }) {
                Text(if (registerMode) "注册" else "登录")
            }
            OutlinedButton(
                enabled = canSubmit, onClick = { registerMode = !registerMode }) {
                Text(if (registerMode) "切换登录" else "切换注册")
            }
        }
    }
}

@Composable
fun DebugHomeScreen(user: User, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), verticalArrangement = Arrangement.Center
    ) {
        Text("已登录：${user.userName}")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogout) {
            Text("退出登录")
        }
    }
}
