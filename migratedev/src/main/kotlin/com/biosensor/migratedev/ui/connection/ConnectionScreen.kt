package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.connection.ConnectionPhase
import com.biosensor.migratedev.translation.connection.ConnectionUiState
import com.biosensor.migratedev.translation.connection.DeviceItemUi

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    onRefresh: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    val refreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing, onRefresh = onRefresh
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "连接 BT24", style = MaterialTheme.typography.headlineSmall
            )
            OutlinedButton(
                enabled = state.phase != ConnectionPhase.EndingSession, onClick = onLogout
            ) {
                Text("退出登录")
            }
        }
        Spacer(Modifier.height(12.dp))
        StatusText(state)
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it, color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            if (state.devices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂未发现符合条件的 BT24")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = state.phase == ConnectionPhase.Scanning || state.phase == ConnectionPhase.Failed,
                        onClick = onRefresh
                    ) {
                        Text("刷新扫描")
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.devices, key = DeviceItemUi::id
                    ) { device ->
                        DeviceCard(
                            device = device,
                            enabled = state.phase == ConnectionPhase.Scanning || state.phase == ConnectionPhase.Failed,
                            onClick = {
                                onDeviceSelected(device.id)
                            })
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = state.isRefreshing, state = refreshState, modifier = Modifier.align(
                    Alignment.TopCenter
                )
            )
        }
    }
}

@Composable
private fun StatusText(state: ConnectionUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.phase == ConnectionPhase.Scanning || state.phase == ConnectionPhase.Connecting || state.phase == ConnectionPhase.EndingSession) {
            CircularProgressIndicator()
        }
        Text(
            when (state.phase) {
                ConnectionPhase.AwaitingBluetoothAccess -> "正在准备蓝牙权限与系统蓝牙"

                ConnectionPhase.Scanning -> "持续扫描中，下拉可刷新当前轮次"

                ConnectionPhase.Connecting -> "正在连接所选设备"

                ConnectionPhase.Connected -> "设备已连接"

                ConnectionPhase.Failed -> "连接失败，可刷新或重新选择"

                ConnectionPhase.EndingSession -> "正在释放蓝牙资源"

                ConnectionPhase.LogoutReady -> "正在退出"
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: DeviceItemUi, enabled: Boolean, onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                device.name, style = MaterialTheme.typography.titleMedium
            )
            Text(
                buildString {
                    append(device.id)
                    device.rssi?.let {
                        append("  RSSI ")
                        append(it)
                    }
                }, style = MaterialTheme.typography.bodySmall
            )
            if (device.isRemembered) {
                Text(
                    "已记忆设备", color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
