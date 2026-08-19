package com.biosensor.migratedev.ui.cgm

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
import com.biosensor.migratedev.decisioncore.cgm.CgmCommandPurpose
import com.biosensor.migratedev.translation.cgm.CgmReadPhase
import com.biosensor.migratedev.translation.cgm.CgmShortResult
import com.biosensor.migratedev.translation.cgm.CgmUiState

/** 状态区:读流程阶段文案(带进度圈)+ 过程消息 + 短命令结果 + 冲突提示。
 *  失败原因经 readMessage 展示;冲突提示(hint)等价 toast,下次操作自动消失。 */
@Composable
fun CgmStatusArea(state: CgmUiState) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.readPhase.isBusy()) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
            Text(state.readPhase.text())
        }
        state.readMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.shortResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                result.message,
                color = if (result.ok) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun CgmReadPhase.isBusy(): Boolean =
    this == CgmReadPhase.Sending || this == CgmReadPhase.Receiving ||
        this == CgmReadPhase.Completed || this == CgmReadPhase.Reconnecting

private fun CgmReadPhase.text(): String = when (this) {
    CgmReadPhase.Idle -> "空闲,可发起读取"
    CgmReadPhase.Sending -> "命令发送中"
    CgmReadPhase.Receiving -> "接收缓存数据"
    CgmReadPhase.Completed -> "校验通过,正在落盘"
    CgmReadPhase.Failed -> "读取失败"
    CgmReadPhase.Reconnecting -> "连接中断,正在重连"
    CgmReadPhase.Stopped -> "读取完成(会话已关闭,数据保留)"
    CgmReadPhase.Error -> "发生错误"
}

@Preview(showBackground = true)
@Composable
private fun CgmStatusAreaPreview() {
    MaterialTheme {
        CgmStatusArea(
            CgmUiState(
                readPhase = CgmReadPhase.Receiving,
                readMessage = "接收中(第 1 次尝试)",
                progressPoints = 0, roundCount = 0, recentLines = emptyList(),
                shortResult = CgmShortResult(CgmCommandPurpose.SYNC_TIME, ok = true, message = "对时成功"))
        )
    }
}
