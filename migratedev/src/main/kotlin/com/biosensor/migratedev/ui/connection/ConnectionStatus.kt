package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.connection.ConnectionPhase
import com.biosensor.migratedev.translation.connection.ConnectionUiState

/** 状态区:当前阶段文案(带进度圈)+ 错误消息。 */
@Composable
fun ConnectionStatus(state: ConnectionUiState) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.phase.isBusy) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
            Text(state.phase.text())
        }
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private val ConnectionPhase.isBusy: Boolean
    get() = this == ConnectionPhase.Scanning || this == ConnectionPhase.Connecting || this == ConnectionPhase.EndingSession

private fun ConnectionPhase.text(): String = when (this) {
    ConnectionPhase.AwaitingBluetoothAccess -> "正在准备蓝牙权限与系统蓝牙"
    ConnectionPhase.Scanning -> "持续扫描中,下拉可刷新列表"
    ConnectionPhase.Connecting -> "正在连接所选设备"
    ConnectionPhase.Connected -> "设备已连接"
    ConnectionPhase.Failed -> "连接失败,可刷新或重新选择"
    ConnectionPhase.EndingSession -> "正在释放蓝牙资源"
    ConnectionPhase.LogoutReady -> "正在退出"
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStatusPreview() {
    MaterialTheme {
        ConnectionStatus(
            ConnectionUiState(
                phase = ConnectionPhase.Connecting,
                devices = emptyList(),
                rememberedDeviceId = null,
                isRefreshing = false,
                message = null
            )
        )
    }
}
