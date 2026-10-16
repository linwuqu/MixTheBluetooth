package com.biosensor.migratedev.ui.cgm

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.biosensor.migratedev.decisioncore.patch.PatchBinding

/**
 * 单元标题右侧的贴片说明。
 *
 * ⚠️ 本期还没有「单元 ⟷ 贴片」的划分,所以每一行单元说的是**同一件事**:这台设备绑了哪几枚。
 * 看着像重复不是 bug,等做了划分,这里换成「该单元所属贴片」,短号换成完整编号(架构 13 §D4)。
 *
 * 一枚都没有时什么都不显示:不占位、也不写"未绑定"。
 */
@Composable
fun PatchBadge(bindings: List<PatchBinding>, modifier: Modifier = Modifier) {
    val text = patchBadgeText(bindings) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

/** 一枚 = `贴片 550e8400`;多枚补一个总数:`贴片 550e8400 · 共 2 枚`。 */
internal fun patchBadgeText(bindings: List<PatchBinding>): String? {
    val newest = bindings.firstOrNull() ?: return null
    val head = "贴片 ${newest.shortId}"
    return if (bindings.size == 1) head else "$head · 共 ${bindings.size} 枚"
}
