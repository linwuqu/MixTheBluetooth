package com.biosensor.migratedev.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.biosensor.migratedev.translation.auth.AuthUiState

/** 状态区:登录/注册流程的阶段文案 + 进度圈 + 错误重置。 */
@Composable
fun AuthStatusArea(
    state: AuthUiState,
    registerMode: Boolean,
    onRetrySession: () -> Unit
) {
    Column {
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
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthStatusAreaRegisteredPreview() {
    MaterialTheme {
        AuthStatusArea(
            state = AuthUiState.Registered("注册成功,请使用新账号登录"),
            registerMode = false,
            onRetrySession = {}
        )
    }
}
