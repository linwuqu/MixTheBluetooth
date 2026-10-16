package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biosensor.migratedev.decisioncore.cgm.CgmReadPhase
import com.biosensor.migratedev.decisioncore.patch.PatchBinding
import com.biosensor.migratedev.decisioncore.upload.CgmResult
import com.biosensor.migratedev.decisioncore.upload.PredictionStats
import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import com.biosensor.migratedev.decisioncore.upload.UploadPhase
import com.biosensor.migratedev.translation.cgm.CGM_RANGE_HIGH
import com.biosensor.migratedev.translation.cgm.CGM_RANGE_LOW
import com.biosensor.migratedev.translation.cgm.CgmUiState
import com.biosensor.migratedev.translation.cgm.CgmUnitSummary
import com.biosensor.migratedev.translation.cgm.toSummary
import java.util.Locale

/**
 * 分析结果面板:一个单元一张页签,一张曲线一组数字。
 *
 * 数字全部由曲线点算出(平均 / 高值占比 / 低值占比 / 时长),后端自己的那组统计单独一行标出来——
 * 两者口径不同(一个是这段曲线的,一个是整个数据集的),混在一起会让人以为对不上。
 */
@Composable
fun CgmResultPanel(
    state: CgmUiState,
    bindings: List<PatchBinding>,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = state.result
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 标题由页签给,这里只留一行概览
        if (result != null) {
            // 点数据实算:后端的 point_count 实测给过 0,和下面画出来的曲线对不上会很难看
            val drawn = remember(result) { result.units.sumOf { it.points.size } }
            Text(
                "共 ${result.unitCount} 个单元 · $drawn 个点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        if (result == null) {
            EmptyResult(state.uploadPhase, onUpload)
            return@Column
        }
        BackendStats(result.stats)
        val summaries = remember(result) { result.units.map { it.toSummary() } }
        if (summaries.isEmpty()) {
            Text("结果里没有曲线数据")
            return@Column
        }
        var selected by remember(result) { mutableIntStateOf(0) }
        val index = selected.coerceIn(0, summaries.lastIndex)
        UnitPicker(summaries, index) { selected = it }
        Spacer(Modifier.height(8.dp))
        UnitNumbers(summaries[index], bindings)
        Spacer(Modifier.height(12.dp))
        CgmGlucoseChart(points = result.units[index].points)
        Spacer(Modifier.height(8.dp))
        Text(
            "底纹区间为目标范围 ${formatValue(CGM_RANGE_LOW)}–${formatValue(CGM_RANGE_HIGH)} mmol/L",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyResult(phase: UploadPhase, onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when (phase) {
                UploadPhase.FAILED -> "上次分析失败,可重新上传"
                UploadPhase.IDLE -> "还没有分析结果"
                else -> "正在分析,结果稍后到这里"
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!phase.running) {
            Button(onClick = onUpload) { Text("上传分析") }
        }
    }
}

/** 后端自己那组统计:整个数据集的,不是当前这条曲线的。拉不到 info 时整段不出现。 */
@Composable
private fun BackendStats(stats: PredictionStats?) {
    if (stats == null) return
    Text(
        "后端统计 · 最低 ${formatValue(stats.min)} · 最高 ${formatValue(stats.max)}" +
            " · 平均 ${formatValue(stats.mean)} · 标准差 ${formatValue(stats.std)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun UnitPicker(
    summaries: List<CgmUnitSummary>, selected: Int, onSelect: (Int) -> Unit
) {
    if (summaries.size == 1) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(summaries) { index, summary ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(summary.title ?: "单元 ${summary.unit}") }
            )
        }
    }
}

@Composable
private fun UnitNumbers(summary: CgmUnitSummary, bindings: List<PatchBinding>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary.title ?: "单元 ${summary.unit}",
                style = MaterialTheme.typography.titleSmall
            )
            PatchBadge(bindings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Figure("平均", summary.mean?.let(::formatValue) ?: "—")
            Figure("高值", formatPercent(summary.highRatio))
            Figure("低值", formatPercent(summary.lowRatio))
        }
        // MARD 拿不到就是"—":后端算不出来的值比不显示更糟,见 CgmResultPresentation 的说明
        val mard = summary.mard?.let { "MARD ${formatValue(it)}" } ?: "MARD —"
        Text(
            "时长 ${formatDuration(summary.durationMillis)} · ${summary.pointCount} 个点 · $mard",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatValue(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatPercent(ratio: Double): String =
    String.format(Locale.US, "%.0f%%", ratio * 100)

private fun formatDuration(millis: Long?): String {
    if (millis == null) return "—"
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours 小时 $minutes 分"
        totalMinutes > 0 -> "$totalMinutes 分钟"
        else -> "${millis / 1000} 秒"
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun CgmResultPanelPreview() {
    MaterialTheme {
        CgmResultPanel(
            state = CgmUiState(
                readPhase = CgmReadPhase.STOPPED,
                readMessage = null, progressPoints = 4, roundCount = 1,
                dataLines = emptyList(), shortResult = null,
                uploadPhase = UploadPhase.DONE,
                result = CgmResult(
                    resultId = 99, jobId = 42, datasetId = 7,
                    unitCount = 1, pointCount = 4,
                    stats = PredictionStats(3.0, 9.0, 6.0, 1.0),
                    units = listOf(
                        ResultUnit(
                            unit = 1, title = "第一单元", pointCount = 4, mard = 0.12,
                            points = listOf(
                                ResultPoint(1, 60, "2026-07-21 12:00:00", 4.0, 4.2),
                                ResultPoint(2, 70, "2026-07-21 12:10:00", 5.5, 5.1),
                                ResultPoint(3, 80, "2026-07-21 12:20:00", 8.2, 8.0),
                                ResultPoint(4, 90, "2026-07-21 12:30:00", 6.4, 6.6)
                            )
                        )
                    )
                )
            ),
            bindings = listOf(
                PatchBinding(
                    patchId = "550e8400-e29b-41d4-a716-446655440000",
                    modelId = 1, lotNo = "DEMO-260701",
                    manufacturedOn = "2026-07-01", expiresOn = "2027-07-01",
                    maxUseMinutes = 120, calibrationRef = "DEMO-CAL",
                    rawCode = "", boundAtMillis = 0
                )
            ),
            onUpload = {}
        )
    }
}
