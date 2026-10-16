package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.translation.cgm.CgmReadPhase
import com.biosensor.migratedev.translation.cgm.CgmShortResult
import com.biosensor.migratedev.translation.cgm.CgmUiState
import com.biosensor.migratedev.translation.cgm.CgmConnectionStatus
import com.biosensor.migratedev.translation.cgm.CgmDataSummary
import com.biosensor.migratedev.decisioncore.cgm.ValidationCheck
import com.biosensor.migratedev.decisioncore.cgm.CgmFileAppendResult

/** 纯呈现:标题/连接态 + 命令栏 + 状态区 + 数据看板。长按读缓存提供强停入口。 */
@Composable
fun CgmScreen(
    state: CgmUiState,
    onRead: () -> Unit,
    onForceStopRead: () -> Unit,
    onSyncTime: () -> Unit,
    onDelete: () -> Unit
) {
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
            Text("缓存明文流", style = MaterialTheme.typography.headlineSmall)
            CgmConnectionIndicator(state.connectionStatus)
        }
        Spacer(Modifier.height(12.dp))
        CgmCommandBar(
            onRead = onRead,
            onForceStopRead = onForceStopRead,
            onSyncTime = onSyncTime,
            onDelete = onDelete
        )
        Spacer(Modifier.height(12.dp))
        CgmStatusArea(state)
        Spacer(Modifier.height(12.dp))
        CgmDataBoard(
            modifier = Modifier.weight(1f),   // 看板吃剩余高度,列表滚动在自身内
            state = state
        )
    }
}

@Composable
private fun CgmConnectionIndicator(status: CgmConnectionStatus) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == CgmConnectionStatus.Reconnecting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            text = when (status) {
                CgmConnectionStatus.Connected -> "已连接"
                CgmConnectionStatus.Reconnecting -> "重连中"
                CgmConnectionStatus.Disconnected -> "已掉线"
            },
            color = when (status) {
                CgmConnectionStatus.Connected -> MaterialTheme.colorScheme.primary
                CgmConnectionStatus.Reconnecting -> MaterialTheme.colorScheme.tertiary
                CgmConnectionStatus.Disconnected -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CgmScreenPreview() {
    MaterialTheme {
        CgmScreen(
            state = CgmUiState(
                readPhase = CgmReadPhase.Stopped,
                readMessage = "读取完成",
                progressPoints = 3,
                roundCount = 2,
                dataLines = listOf(
                    "Start Playback",
                    "LOG:1,1,1,2026-07-15 08:00:00",
                    "EIS:1,60,1,2,3,4",
                    "LOG:2,2,2,2026-07-15 08:01:00",
                    "CA:1,100", "CA:2,101",
                    "Playback all done"
                ),
                shortResult = null,
                summary = CgmDataSummary(7, 1, 2, 2, "2026-07-15 08:00:00", "2026-07-15 08:01:00"),
                validationChecks = listOf(
                    ValidationCheck("回放边界", true, "START 和 END 齐全"),
                    ValidationCheck("LOG:1 EIS / 点数", true, "声明 1 点，实际 1 点"),
                    ValidationCheck("LOG:2 CA / seq", true, "seq 从 1 连续到 2")
                ),
                storageResult = CgmFileAppendResult("cgm/AA_01.txt", 1, 1, 2, 1, 3),
                sessionId = "preview", deviceId = "AA:01"
            ),
            onRead = {}, onForceStopRead = {}, onSyncTime = {}, onDelete = {}
        )
    }
}
