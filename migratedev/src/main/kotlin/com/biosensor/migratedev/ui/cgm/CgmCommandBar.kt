package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 命令栏:读缓存 / 对时 / 删除。按钮常亮可点(1.7 ①)——冲突点击由 Translation
 *  拒绝并 report Blocked → 页面提示等待(1.7 ②),不做视觉禁用:
 *  禁用按钮无法给反馈,常亮 + 提示让用户明确知道"正在忙,命令被拒"。 */
@Composable
fun CgmCommandBar(
    onRead: () -> Unit,
    onSyncTime: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onRead) { Text("读缓存") }
        OutlinedButton(onClick = onSyncTime) { Text("对时") }
        OutlinedButton(onClick = onDelete) { Text("删除") }
    }
}

@Preview(showBackground = true)
@Composable
private fun CgmCommandBarPreview() {
    CgmCommandBar(onRead = {}, onSyncTime = {}, onDelete = {})
}
