package com.biosensor.migratedev.translation.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmReadState
import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.CgmSession

data class CgmDataSummary(
    val recordCount: Int = 0, val eisPoints: Int = 0, val caPoints: Int = 0,
    val segments: Int = 0, val firstTime: String? = null, val lastTime: String? = null
)

/** 接收时显示全部累积记录；结束或保存失败后仍保留本次原始记录及边界标记。 */
internal fun CgmReadState.displayRecords(): List<CgmRecord> = when (this) {
    is CgmReadState.Receiving -> accumulated
    is CgmReadState.Failed -> accumulated
    is CgmReadState.Reconnecting -> accumulated
    is CgmReadState.Completed -> report?.receivedRecords ?: records
    is CgmReadState.Stopped -> report?.receivedRecords ?: lastRecords
    is CgmReadState.Error -> report?.receivedRecords.orEmpty()
    else -> emptyList()
}

internal fun CgmReadState.displaySession(): CgmSession? = when (this) {
    is CgmReadState.Sending -> session
    is CgmReadState.Receiving -> session
    is CgmReadState.Completed -> session
    is CgmReadState.Failed -> session
    is CgmReadState.Reconnecting -> session
    is CgmReadState.Stopped -> session
    is CgmReadState.Error -> report?.session
    else -> null
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
