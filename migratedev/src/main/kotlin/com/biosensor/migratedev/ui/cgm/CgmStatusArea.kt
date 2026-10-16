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
import com.biosensor.migratedev.decisioncore.cgm.CgmReadPhase
import com.biosensor.migratedev.decisioncore.cgm.StopReason
import com.biosensor.migratedev.decisioncore.upload.UploadPhase
import com.biosensor.migratedev.translation.cgm.CgmShortResult
import com.biosensor.migratedev.translation.cgm.CgmUiState

/** 状态区:阶段标签(带进度圈)+ 只在有细节时的第二行 + 短命令结果 + 冲突提示。
 *  失败原因经 readMessage 展示;冲突提示(hint)等价 toast,下次操作自动消失。 */
@Composable
fun CgmStatusArea(state: CgmUiState) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.readPhase.running) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
            Text(state.readPhase.text(state.stopReason))
        }
        state.readMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = if (state.readPhase == CgmReadPhase.FAILED ||
                    state.readPhase == CgmReadPhase.ERROR
                ) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        // 没上传过就不占地方:两件事共用一个状态区,谁没话说谁不出现
        if (state.uploadPhase != UploadPhase.IDLE) {
            Spacer(Modifier.height(8.dp))
            UploadStatus(state.uploadPhase, state.uploadMessage)
        }
    }
}

/** 上传与分析共用一个阶段标签:推进到哪一步说清楚,失败时下面再补一行原因。 */
@Composable
private fun UploadStatus(phase: UploadPhase, message: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (phase.running) CircularProgressIndicator(Modifier.size(20.dp))
        Text(
            phase.text(),
            color = if (phase == UploadPhase.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    message?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}

private fun UploadPhase.text(): String = when (this) {
    UploadPhase.IDLE -> "空闲"
    UploadPhase.UPLOADING -> "正在上传数据"
    UploadPhase.POLLING -> "后端分析中"
    UploadPhase.FETCHING -> "正在取回结果"
    UploadPhase.DONE -> "分析完成"
    UploadPhase.FAILED -> "分析失败"
}

private fun CgmReadPhase.text(stopReason: StopReason?): String = when (this) {
    CgmReadPhase.IDLE -> "空闲,可发起读取"
    CgmReadPhase.SENDING -> "命令发送中"
    CgmReadPhase.RECEIVING -> "接收缓存数据"
    CgmReadPhase.WRITING -> "正在追加保存"
    CgmReadPhase.FAILED -> "读取失败"
    CgmReadPhase.RECONNECTING -> "连接中断,正在重连"
    CgmReadPhase.STOPPED -> if (stopReason == StopReason.USER_CANCEL) "读取已停止" else "读取完成"
    CgmReadPhase.ERROR -> "发生错误"
}

@Preview(showBackground = true)
@Composable
private fun CgmStatusAreaPreview() {
    MaterialTheme {
        CgmStatusArea(
            CgmUiState(
                readPhase = CgmReadPhase.RECEIVING,
                readMessage = "接收中(第 1 次尝试)",
                progressPoints = 0, roundCount = 0, dataLines = emptyList(),
                shortResult = CgmShortResult(CgmCommandPurpose.SYNC_TIME, ok = true, message = "对时成功"))
        )
    }
}
