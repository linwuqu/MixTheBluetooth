package com.biosensor.migratedev.decisioncore.cgm

// ── 共享词汇 ──

enum class CgmCommandPurpose { SYNC_TIME, READ_ALL, DELETE }

enum class StopReason { COMPLETED, TIMEOUT, USER_CANCEL, RETRY_EXHAUSTED, RECONNECT_FAILED }

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

data class CgmFileAppendResult(
    val path: String, val addedSegments: Int, val duplicateSegments: Int,
    val addedPoints: Int, val duplicatePoints: Int, val totalPoints: Int
)

/** 收集校验与入库校验的结论,供界面查看。 */
data class CgmReadReport(
    val receivedRecords: List<CgmRecord>,
    val checks: List<ValidationCheck>,
    val storage: CgmFileAppendResult? = null
)

// ── 读会话:确定超时 → 收数据 → 解码/拼行/解析 → 收集校验 → 入库校验 ──

/** 读会话推进到哪一步。 */
enum class CgmReadPhase {
    IDLE, SENDING, RECEIVING, WRITING, RECONNECTING, STOPPED, FAILED, ERROR;

    /** 会话在手上:此时拒绝新命令。FAILED 不在内——它等重读或用户操作。 */
    val running: Boolean
        get() = this == SENDING || this == RECEIVING || this == WRITING || this == RECONNECTING

    /** 断线后需要等重连:重读途中也算,否则会话会丢。 */
    val holdingSession: Boolean
        get() = running || this == FAILED
}

/**
 * 读会话的全部状态。
 * 状态机不做 IO,也不做数据校验——校验由端口在收到 END 时完成,结论经事件送回。
 */
data class CgmReadState(
    val phase: CgmReadPhase = CgmReadPhase.IDLE,
    val session: CgmSession? = null,
    /** 已重读次数。 */
    val retryCount: Int = 0,
    /** 本次已收到的记录(含边界标记):接收过程中实时显示。 */
    val records: List<CgmRecord> = emptyList(),
    /** 收集校验与入库校验的结论。 */
    val report: CgmReadReport? = null,
    /** FAILED / ERROR 的原因。 */
    val message: String? = null,
    /** STOPPED 的原因。 */
    val stopReason: StopReason? = null,
)

sealed interface CgmReadEvent {
    data class ReadRequested(val sessionId: String, val deviceId: String) : CgmReadEvent
    data class CommandAccepted(val sessionId: String) : CgmReadEvent
    /**
     * 端口解析出的一批记录。
     * conclusion 只在收到 Playback all done 时附上——即收集校验的结论(null = 本次还没收完)。
     */
    data class RecordsProduced(
        val sessionId: String,
        val records: List<CgmRecord>,
        val conclusion: ValidationConclusion? = null
    ) : CgmReadEvent
    data class CommandTimeout(val sessionId: String) : CgmReadEvent
    data class DeviceDisconnected(val sessionId: String) : CgmReadEvent
    /** 断线重连成功(Connection 域经 Root 转发):自动重读继续。 */
    data class DeviceReconnected(val sessionId: String) : CgmReadEvent
    /** 断线重连失败(Connection 域经 Root 转发):闭环终止,不再悬挂。 */
    data class DeviceReconnectFailed(val sessionId: String, val reason: String) : CgmReadEvent
    data class FileWritten(
        val sessionId: String, val path: String, val result: CgmFileAppendResult? = null
    ) : CgmReadEvent
    data class FileWriteFailed(val sessionId: String, val message: String) : CgmReadEvent
    /** 内部使用(用户长按强制停止),UI 不暴露(1.7)。 */
    data class StopCurrent(val deviceId: String) : CgmReadEvent
    /** 内部使用(Error 后自动复位),UI 不暴露(1.7)。 */
    data object ResetError : CgmReadEvent
}

sealed interface CgmReadEffect {
    data class StartRead(val session: CgmSession) : CgmReadEffect
    data class RetryRead(val session: CgmSession, val reason: String) : CgmReadEffect
    data class WriteFile(val session: CgmSession, val records: List<CgmRecord>) : CgmReadEffect
    data class StopRuntime(val session: CgmSession?, val reason: StopReason) : CgmReadEffect
}

// ── 短命令状态机词汇(对时/删除:发 → 等确认 → Done)──

sealed interface CgmShortState {
    data object Idle : CgmShortState

    /** 命令在途,等设备确认。 */
    sealed interface Pending : CgmShortState {
        val session: CgmSession
        val purpose: CgmCommandPurpose
    }

    data class Sending(
        override val session: CgmSession, override val purpose: CgmCommandPurpose
    ) : Pending

    data class WaitingAck(
        override val session: CgmSession, override val purpose: CgmCommandPurpose
    ) : Pending

    /** 终态:对时成功 / 删除成功。Done 就是结果,没有"停止"语义。 */
    data class Done(val session: CgmSession, val purpose: CgmCommandPurpose) : CgmShortState
    data class Failed(
        val session: CgmSession, val reason: String, val purpose: CgmCommandPurpose
    ) : CgmShortState
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
