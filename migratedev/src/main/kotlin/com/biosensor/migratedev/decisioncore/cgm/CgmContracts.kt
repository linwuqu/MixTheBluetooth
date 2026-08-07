package com.biosensor.migratedev.decisioncore.cgm

// ── 共享词汇 ──

enum class CgmCommandPurpose { SYNC_TIME, READ_ALL, DELETE }

enum class StopReason { COMPLETED, TIMEOUT, USER_CANCEL, DISCONNECTED, RETRY_EXHAUSTED }

data class CgmSession(val id: String, val deviceId: String)

/** 解析管道的产出:结构化记录(保留序号/时间索引元数据供校验)。 */
sealed interface CgmRecord {
    val text: String                      // 原始行文本(去重判据)

    data class Eis(
        val seq: Int, val ts: Int,
        val v1: String, val v2: String, val v3: String, val v4: String,
        override val text: String
    ) : CgmRecord

    data class Ca(val seq: Int, val value: String, override val text: String) : CgmRecord
    data class Log(override val text: String) : CgmRecord
    data class Summarize(override val text: String) : CgmRecord
    data class Marker(val kind: MarkerKind, override val text: String) : CgmRecord
}

enum class MarkerKind { START, END }

// ── 读状态机词汇(发 ALL → 收数据 → 校验 → 落盘 → 删缓存)──

sealed interface CgmReadState {
    data object Idle : CgmReadState
    data class Sending(val session: CgmSession) : CgmReadState
    data class Receiving(
        val session: CgmSession, val accumulated: List<CgmRecord>, val retryCount: Int
    ) : CgmReadState
    data class Completed(
        val session: CgmSession, val filePath: String?, val deleteAcked: Boolean
    ) : CgmReadState
    data class Failed(
        val session: CgmSession, val accumulated: List<CgmRecord>,
        val reason: String, val retryCount: Int
    ) : CgmReadState
    data class Error(val message: String) : CgmReadState
    data class Stopped(val session: CgmSession?) : CgmReadState
}

sealed interface CgmReadEvent {
    data class ReadRequested(val sessionId: String, val deviceId: String) : CgmReadEvent
    data class CommandAccepted(val sessionId: String) : CgmReadEvent
    /** 仅删除确认(读流程自动删)。短命令的确认在 CgmShortEvent,不混。 */
    data class AckReceived(val sessionId: String, val purpose: CgmCommandPurpose) : CgmReadEvent
    data class RecordsProduced(val sessionId: String, val records: List<CgmRecord>) : CgmReadEvent
    data class CommandTimeout(val sessionId: String) : CgmReadEvent
    data class DeviceDisconnected(val sessionId: String) : CgmReadEvent
    data class FileWritten(val sessionId: String, val path: String) : CgmReadEvent
    data class FileWriteFailed(val sessionId: String, val message: String) : CgmReadEvent
    /** 内部使用(隐式切换/用户换命令时由 Translation 发出),UI 不暴露(1.7)。 */
    data class StopCurrent(val deviceId: String) : CgmReadEvent
    /** 内部使用(Error 后自动复位),UI 不暴露(1.7)。 */
    data object ResetError : CgmReadEvent
}

sealed interface CgmReadEffect {
    data class StartRead(val session: CgmSession) : CgmReadEffect
    data class WriteFile(val session: CgmSession, val records: List<CgmRecord>) : CgmReadEffect
    data class SendDelete(val session: CgmSession) : CgmReadEffect
    data class RetryRead(val session: CgmSession, val reason: String) : CgmReadEffect
    data class StopRuntime(val session: CgmSession?, val reason: StopReason) : CgmReadEffect
}

// ── 短命令状态机词汇(对时/删除:发 → 等确认 → Done)──

sealed interface CgmShortState {
    data object Idle : CgmShortState
    data class Sending(
        val session: CgmSession, val purpose: CgmCommandPurpose
    ) : CgmShortState
    data class WaitingAck(
        val session: CgmSession, val purpose: CgmCommandPurpose
    ) : CgmShortState
    /** 终态:对时成功 / 删除成功。Done 就是结果,没有"停止"语义。 */
    data class Done(val session: CgmSession, val purpose: CgmCommandPurpose) : CgmShortState
    data class Failed(val session: CgmSession, val reason: String) : CgmShortState
}

sealed interface CgmShortEvent {
    data class ShortRequested(
        val sessionId: String, val deviceId: String, val purpose: CgmCommandPurpose
    ) : CgmShortEvent
    data class CommandAccepted(val sessionId: String) : CgmShortEvent
    data class AckReceived(val sessionId: String, val purpose: CgmCommandPurpose) : CgmShortEvent
    data class CommandTimeout(val sessionId: String) : CgmShortEvent
    data class DeviceDisconnected(val sessionId: String) : CgmShortEvent
}

sealed interface CgmShortEffect {
    data class SendCommand(val session: CgmSession, val purpose: CgmCommandPurpose) : CgmShortEffect
}
