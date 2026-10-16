package com.biosensor.migratedev.translation.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmReadPhase
import com.biosensor.migratedev.decisioncore.cgm.CgmReadState
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord

data class CgmDataSummary(
    val recordCount: Int = 0, val eisPoints: Int = 0, val caPoints: Int = 0,
    val segments: Int = 0, val firstTime: String? = null, val lastTime: String? = null
)

/**
 * 看板要显示的记录。
 * 收集校验前显示实时累积;校验后显示本次结论里的原始记录(含边界标记)。
 * FAILED 用累积:超时失败时还没有校验结论。
 */
internal val CgmReadState.displayRecords: List<CgmRecord>
    get() = when (phase) {
        CgmReadPhase.WRITING, CgmReadPhase.STOPPED, CgmReadPhase.ERROR ->
            report?.receivedRecords ?: records
        else -> records
    }

internal fun summarizeRecords(records: List<CgmRecord>): CgmDataSummary {
    val logs = records.filterIsInstance<CgmRecord.Log>()
    val times = logs.mapNotNull { it.text.split(',', limit = 4).getOrNull(3) }
    return CgmDataSummary(
        recordCount = records.size,
        eisPoints = records.count { it is CgmRecord.Eis },
        caPoints = records.count { it is CgmRecord.Ca },
        segments = logs.size,
        firstTime = times.firstOrNull(), lastTime = times.lastOrNull()
    )
}
