package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.connection.DeviceItemUi

/** 设备列表:空态 / 列表 + 下拉刷新。局部状态(刷新)只在这里。 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DeviceList(
    devices: List<DeviceItemUi>,
    canInteract: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDeviceSelected: (String) -> Unit
) {
    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing, onRefresh = onRefresh
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        if (devices.isEmpty()) {
            EmptyDevices(
                canInteract = canInteract && !isRefreshing,
                onRefresh = onRefresh
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = devices, key = DeviceItemUi::id) { device ->
                    DeviceCard(
                        device = device,
                        enabled = canInteract,
                        onClick = { onDeviceSelected(device.id) })
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun BoxScope.EmptyDevices(canInteract: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("暂未发现符合条件的 BT24")
        Spacer(Modifier.height(12.dp))
        Button(enabled = canInteract, onClick = onRefresh) {
            Text("刷新扫描")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceListEmptyPreview() {
    MaterialTheme {
        DeviceList(
            devices = emptyList(),
            canInteract = true,
            isRefreshing = false,
            onRefresh = {},
            onDeviceSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceListWithDevicesPreview() {
    MaterialTheme {
        DeviceList(
            devices = listOf(
            DeviceItemUi("AA:01", "BT24-S", -40, isRemembered = true),
            DeviceItemUi("BB:02", "BT24-M", null, isRemembered = false)
        ), canInteract = true, isRefreshing = false, onRefresh = {}, onDeviceSelected = {})
    }
}
