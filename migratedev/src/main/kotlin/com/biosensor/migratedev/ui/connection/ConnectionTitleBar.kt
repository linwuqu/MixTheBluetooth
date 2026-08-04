package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** 标题栏:页面标题 + 退出登录。 */
@Composable
fun ConnectionTitleBar(
    logoutEnabled: Boolean, onLogout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("连接 BT24", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(enabled = logoutEnabled, onClick = onLogout) {
            Text("退出登录")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionTitleBarPreview() {
    MaterialTheme {
        ConnectionTitleBar(logoutEnabled = true, onLogout = {})
    }
}
