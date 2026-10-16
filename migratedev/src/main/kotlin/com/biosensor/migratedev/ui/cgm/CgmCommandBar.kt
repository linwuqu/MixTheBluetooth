package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** 命令栏:读缓存 / 上传分析 / 对时 / 删除。按钮常亮可点(1.7 ①)——冲突点击由 Translation
 *  拒绝并 report Blocked → 页面提示等待(1.7 ②),不做视觉禁用:
 *  禁用按钮无法给反馈,常亮 + 提示让用户明确知道"正在忙,命令被拒"。
 *  分两行:四个按钮挤一行时中文标签会被压到换行。 */
@Composable
fun CgmCommandBar(
    onRead: () -> Unit,
    onForceStopRead: () -> Unit,
    onUpload: () -> Unit,
    onSyncTime: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var suppressClickAfterForceStop by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (suppressClickAfterForceStop) suppressClickAfterForceStop = false
                    else onRead()
                },
                modifier = Modifier.weight(1f).pointerInput(onForceStopRead) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val released = withTimeoutOrNull(FORCE_STOP_HOLD_MILLIS) {
                            // Button 自己会消费 pointer change；不能用 waitForUpOrCancellation，
                            // 否则普通点击也会返回 null，被误判为“长按超时”。这里只看物理按压态。
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) break
                            }
                            true
                        }
                        if (released == null) {
                            suppressClickAfterForceStop = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForceStopRead()
                            // 强停只触发一次；消费阈值后的事件，避免 Button 在松手时补发普通点击。
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                }
            ) { Text("读缓存") }
            Button(onClick = onUpload, modifier = Modifier.weight(1f)) { Text("上传分析") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onSyncTime, modifier = Modifier.weight(1f)) { Text("对时") }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CgmCommandBarPreview() {
    CgmCommandBar(
        onRead = {}, onForceStopRead = {}, onUpload = {}, onSyncTime = {}, onDelete = {}
    )
}

private const val FORCE_STOP_HOLD_MILLIS = 3_000L
