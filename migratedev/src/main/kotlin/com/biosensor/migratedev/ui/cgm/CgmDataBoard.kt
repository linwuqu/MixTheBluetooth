package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.translation.cgm.CgmReadPhase
import com.biosensor.migratedev.translation.cgm.CgmUiState
import kotlinx.coroutines.launch

/** 所有记录和报告在同一列表中滚动；接收新数据时不强制跳到底部，便于检查前面的数据。 */
@Composable
fun CgmDataBoard(state: CgmUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val checkRows = state.validationChecks.size.coerceAtLeast(1)
    val dataStartIndex = 4 + checkRows
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("本次数据和报告", style = MaterialTheme.typography.titleMedium)
            if (state.dataLines.isNotEmpty()) {
                Text("数据开头", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { scope.launch { listState.scrollToItem(dataStartIndex) } })
                Text("数据末尾", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        scope.launch { listState.scrollToItem(dataStartIndex + state.dataLines.lastIndex) }
                    })
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Text("基本信息", style = MaterialTheme.typography.titleMedium)
                    Text("设备：${state.deviceId ?: "—"}")
                    Text("会话：${state.sessionId ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("已接收 ${state.summary.recordCount} 条记录 · ${state.summary.segments} 段")
                    Text("数据点 ${state.progressPoints} · EIS ${state.summary.eisPoints} · CA ${state.summary.caPoints}")
                    Text("首段时间：${state.summary.firstTime ?: "—"}")
                    Text("末段时间：${state.summary.lastTime ?: "—"}")
                }
            }
            item { Text("校验过程", style = MaterialTheme.typography.titleMedium) }
            if (state.validationChecks.isEmpty()) {
                item {
                    Text(if (state.readPhase == CgmReadPhase.Receiving) "接收中，等待 END 后校验"
                        else "尚未执行校验")
                }
            } else {
                items(state.validationChecks) { check ->
                    Column {
                        Text("${if (check.passed) "通过" else "失败"} · ${check.name}",
                            color = if (check.passed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error)
                        Text(check.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column {
                    Text("文件保存", style = MaterialTheme.typography.titleMedium)
                    val saved = state.storageResult
                    if (saved != null) {
                        Text("新增 ${saved.addedSegments} 段 / ${saved.addedPoints} 点")
                        Text("文件已有，跳过 ${saved.duplicateSegments} 段 / ${saved.duplicatePoints} 点")
                        Text("文件累计 ${saved.totalPoints} 点")
                        SelectionContainer { Text(saved.path, style = MaterialTheme.typography.bodySmall) }
                    } else {
                        Text(when (state.readPhase) {
                            CgmReadPhase.Completed -> "正在与文件比较并追加"
                            CgmReadPhase.Error -> "保存失败，请查看错误和校验报告"
                            else -> "本次数据尚未保存"
                        })
                    }
                }
            }
            item { Text("完整数据 · ${state.dataLines.size} 条", style = MaterialTheme.typography.titleMedium) }
            if (state.dataLines.isEmpty()) {
                item { Text("暂无数据，点击“读缓存”开始接收") }
            } else {
                itemsIndexed(state.dataLines) { index, line ->
                    SelectionContainer {
                        Text("${index + 1}. $line", fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
