package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.translation.cgm.CgmReadPhase
import com.biosensor.migratedev.translation.cgm.CgmShortResult
import com.biosensor.migratedev.translation.cgm.CgmUiState

/** 纯呈现:标题 + 命令栏 + 状态区 + 数据看板(可收起)。零业务。无停止/复位按钮(1.7 ③)。 */
@Composable
fun CgmScreen(
    state: CgmUiState,
    onRead: () -> Unit,
    onSyncTime: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("缓存明文流", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        CgmCommandBar(
            onRead = onRead,
            onSyncTime = onSyncTime,
            onDelete = onDelete
        )
        Spacer(Modifier.height(12.dp))
        CgmStatusArea(state)
        Spacer(Modifier.height(12.dp))
        CgmDataBoard(
            modifier = Modifier.weight(1f),   // 看板吃剩余高度,列表滚动在自身内
            progressPoints = state.progressPoints,
            roundCount = state.roundCount,
            recentLines = state.recentLines
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CgmScreenPreview() {
    MaterialTheme {
        CgmScreen(
            state = CgmUiState(
                readPhase = CgmReadPhase.Receiving,
                readMessage = "接收中(第 1 次尝试)",
                progressPoints = 285,
                roundCount = 3,
                recentLines = listOf(
                    "EIS:95,1000,1,2,3,4",
                    "CA:1,100",
                    "summarize:2026-07-15 08:00:00"
                ),
                shortResult = CgmShortResult(
                    CgmCommandPurpose.SYNC_TIME, ok = true, message = "对时成功")
            ),
            onRead = {}, onSyncTime = {}, onDelete = {}
        )
    }
}
