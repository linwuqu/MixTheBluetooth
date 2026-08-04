package com.biosensor.migratedev.ui.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.connection.DeviceItemUi

/** 设备卡片:名称 + 地址/RSSI + 记忆标记。 */
@Composable
fun DeviceCard(
    device: DeviceItemUi, enabled: Boolean, onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
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
                Text("已记忆设备", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceCardPreview() {
    MaterialTheme {
        DeviceCard(
            device = DeviceItemUi("AA:01", "BT24-S", -40, isRemembered = true),
            enabled = true,
            onClick = {})
    }
}
