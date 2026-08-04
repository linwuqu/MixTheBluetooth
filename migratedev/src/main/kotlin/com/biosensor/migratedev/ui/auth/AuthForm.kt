package com.biosensor.migratedev.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.auth.AuthUiState

/**
 * 表单区:手机号/密码/账户名输入 + 登录/注册切换。
 * 输入值(phone/password/nickname)是纯 UI 状态,留在这里;
 * registerMode 影响状态区文案,由 AuthScreen 以受控状态传入。
 */
@Composable
fun AuthForm(
    state: AuthUiState,
    registerMode: Boolean,
    onRegisterModeChange: (Boolean) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val canSubmit =
        state is AuthUiState.Idle || state is AuthUiState.Error || state is AuthUiState.Registered

    Column {
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
                enabled = canSubmit, onClick = { onRegisterModeChange(!registerMode) }) {
                Text(if (registerMode) "切换登录" else "切换注册")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthFormPreview() {
    MaterialTheme {
        AuthForm(
            state = AuthUiState.Idle,
            registerMode = false,
            onRegisterModeChange = {},
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> }
        )
    }
}
