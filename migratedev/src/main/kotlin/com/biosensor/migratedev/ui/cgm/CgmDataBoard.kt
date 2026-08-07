package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 数据看板:点数 / 轮数 + 可收起的最近记录流(11.8 性能要求)。
 *  滚动跟随仅当展开时:新数据到达自动滚到底部;收起不触发,零开销(11.8 ④)。 */
@Composable
fun CgmDataBoard(
    modifier: Modifier = Modifier,
    progressPoints: Int,
    roundCount: Int,
    recentLines: List<String>
) {
    var expanded by rememberSaveable { mutableStateOf(true) }   // 纯 UI 态,可随旋转保存
    val listState = rememberLazyListState()
    LaunchedEffect(roundCount) {
        if (expanded && recentLines.isNotEmpty()) {
            listState.scrollToItem(recentLines.lastIndex)
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("点数: $progressPoints", style = MaterialTheme.typography.titleMedium)
            Text("轮数: $roundCount", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (recentLines.isEmpty()) {
                Text("暂无数据,点击「读缓存」开始接收", color = MaterialTheme.colorScheme.outline)
            } else {
                // 条件组合(if 而非固定高度):收起/展开都按当前内容重新测量,
                // 不会残留旧高度导致布局错位(11.8 要求 3)
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),   // 由外层 weight 给定高度
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(recentLines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CgmDataBoardPreview() {
    MaterialTheme {
        CgmDataBoard(
            progressPoints = 285,
            roundCount = 3,
            recentLines = listOf("EIS:95,1000,1,2,3,4", "CA:1,100", "summarize:2026-07-15 08:00:00"))
    }
}
