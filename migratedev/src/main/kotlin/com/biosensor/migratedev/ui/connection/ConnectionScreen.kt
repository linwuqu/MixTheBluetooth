package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.connection.ConnectionPhase
import com.biosensor.migratedev.translation.connection.ConnectionUiState
import com.biosensor.migratedev.translation.connection.DeviceItemUi

/**
 * 纯呈现:只描述页面结构,不涉及任何业务。
 * 结构 = 标题栏 + 状态区 + 设备列表。
 */
@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    onRefresh: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        ConnectionTitleBar(
            logoutEnabled = state.phase != ConnectionPhase.EndingSession,
            onLogout = onLogout
        )
        Spacer(Modifier.height(12.dp))
        ConnectionStatus(state)
        Spacer(Modifier.height(12.dp))
        DeviceList(
            devices = state.devices,
            canInteract = state.phase == ConnectionPhase.Scanning ||
                state.phase == ConnectionPhase.Failed,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onDeviceSelected = onDeviceSelected
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionScreenPreview() {
    MaterialTheme {
        ConnectionScreen(
            state = ConnectionUiState(
                phase = ConnectionPhase.Scanning,
                devices = listOf(
                    DeviceItemUi("AA:01", "BT24-S", -40, isRemembered = true),
                    DeviceItemUi("BB:02", "BT24-M", -55, isRemembered = false)
                ),
                rememberedDeviceId = "AA:01",
                isRefreshing = false,
                message = null
            ),
            onRefresh = {},
            onDeviceSelected = {},
            onLogout = {}
        )
    }
}
