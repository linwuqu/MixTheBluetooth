package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

// State / Event / Effect 词汇在 CgmContracts.kt(文件 04)

/**
 * 读状态机:发 ALL → 收数据 → 校验(消费侧)→ 落盘 + 自动删缓存。
 * 只做转移计算,不执行 IO;删除确认与文件落盘构成 Completed 双闸门,都到才 Stopped。
 */
object CgmReadDecision : DecisionCore<CgmReadState, CgmReadEvent, CgmReadEffect> {
    private const val RETRY_LIMIT = 3

    override fun reduce(
        currentState: CgmReadState, event: CgmReadEvent
    ): Transition<CgmReadState, CgmReadEffect> = when (event) {
        is CgmReadEvent.ReadRequested -> onRequested(currentState, event)
        is CgmReadEvent.CommandAccepted -> onAccepted(currentState, event)
        is CgmReadEvent.AckReceived -> onDeleteAck(currentState, event)
        is CgmReadEvent.RecordsProduced -> onRecords(currentState, event)
        is CgmReadEvent.FileWritten -> onFileWritten(currentState, event)
        is CgmReadEvent.FileWriteFailed -> Transition(CgmReadState.Error(event.message))
        is CgmReadEvent.CommandTimeout -> onTimeout(currentState, event)
        is CgmReadEvent.DeviceDisconnected -> {
            val session = currentState.session ?: return Transition(currentState)
            // 无活跃会话:迟到的断开事件幂等忽略
            Transition(
                CgmReadState.Failed(session, emptyList(), "设备断开", 0),
                listOf(CgmReadEffect.StopRuntime(session, StopReason.DISCONNECTED)))
        }
        is CgmReadEvent.StopCurrent -> {
            val session = currentState.session ?: return Transition(currentState)
            Transition(
                CgmReadState.Stopped(session),
                listOf(CgmReadEffect.StopRuntime(session, StopReason.USER_CANCEL)))
        }
        CgmReadEvent.ResetError -> when (currentState) {
            is CgmReadState.Error -> Transition(CgmReadState.Idle)
            else -> Transition(currentState)
        }
    }

    private fun onRequested(
        current: CgmReadState, event: CgmReadEvent.ReadRequested
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        CgmReadState.Idle, is CgmReadState.Completed, is CgmReadState.Failed,
        is CgmReadState.Stopped, is CgmReadState.Error -> {
            val session = CgmSession(event.sessionId, event.deviceId)
            Transition(
                CgmReadState.Sending(session),
                listOf(CgmReadEffect.StartRead(session)))
        }
        else -> Transition(current)   // 串行会话:活跃中忽略(UI 主防,状态机兜底)
    }

    private fun onAccepted(
        current: CgmReadState, event: CgmReadEvent.CommandAccepted
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Sending -> Transition(
            CgmReadState.Receiving(current.session, emptyList(), retryCount = 0))
        // 重读路径:RetryRead 重发 ALL 后,确认回到 Receiving,保留已累积记录
        is CgmReadState.Failed -> Transition(
            CgmReadState.Receiving(current.session, current.accumulated, current.retryCount))
        else -> Transition(current)
    }

    /** 删除确认:Completed 双闸门之一(filePath 与 deleteAcked 都到才 Stopped)。 */
    private fun onDeleteAck(
        current: CgmReadState, event: CgmReadEvent.AckReceived
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Completed -> if (event.purpose == CgmCommandPurpose.DELETE) {
            val done = current.filePath != null
            Transition(
                if (done) CgmReadState.Stopped(current.session) else current.copy(deleteAcked = true),
                if (done) listOf(CgmReadEffect.StopRuntime(current.session, StopReason.COMPLETED))
                else emptyList())
        } else Transition(current)
        else -> Transition(current)
    }

    /** 读取数据:累积 + 校验触发点(消费侧,见架构 07 §1.2)。首个 END 到达即触发全量校验。 */
    private fun onRecords(
        current: CgmReadState, event: CgmReadEvent.RecordsProduced
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current !is CgmReadState.Receiving || current.session.id != event.sessionId) {
            return Transition(current)   // 会话已结束/不匹配:迟到的记录忽略(幂等)
        }
        val all = current.accumulated + event.records
        val endReached = event.records.any {
            it is CgmRecord.Marker && it.kind == MarkerKind.END
        }
        if (!endReached) {
            return Transition(current.copy(accumulated = all))   // 继续累积,无 effect
        }
        val conclusion = CacheValidators.validate(all)   // 纯函数
        if (conclusion.complete) {
            return Transition(
                CgmReadState.Completed(current.session, filePath = null, deleteAcked = false),
                listOf(
                    CgmReadEffect.WriteFile(current.session, conclusion.records),
                    CgmReadEffect.SendDelete(current.session)))
        }
        val retry = current.retryCount
        val exhausted = retry + 1 > RETRY_LIMIT
        return Transition(
            CgmReadState.Failed(current.session, all, conclusion.reason ?: "校验未通过", retry + 1),
            listOf(
                if (exhausted) CgmReadEffect.StopRuntime(current.session, StopReason.RETRY_EXHAUSTED)
                else CgmReadEffect.RetryRead(current.session, conclusion.reason ?: "校验未通过")))
    }

    private fun onFileWritten(
        current: CgmReadState, event: CgmReadEvent.FileWritten
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Completed -> {
            val done = current.deleteAcked
            Transition(
                if (done) CgmReadState.Stopped(current.session) else current.copy(filePath = event.path),
                if (done) listOf(CgmReadEffect.StopRuntime(current.session, StopReason.COMPLETED))
                else emptyList())
        }
        else -> Transition(current)
    }

    private fun onTimeout(
        current: CgmReadState, event: CgmReadEvent.CommandTimeout
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Sending -> Transition(
            CgmReadState.Failed(current.session, emptyList(), "发送超时", 0),
            listOf(CgmReadEffect.StopRuntime(current.session, StopReason.TIMEOUT)))
        // 读取超时:重读(累积保留);到上限则结束
        is CgmReadState.Receiving -> {
            val retry = current.retryCount
            val exhausted = retry + 1 > RETRY_LIMIT
            Transition(
                CgmReadState.Failed(current.session, current.accumulated, "接收超时", retry + 1),
                listOf(
                    if (exhausted) CgmReadEffect.StopRuntime(current.session, StopReason.RETRY_EXHAUSTED)
                    else CgmReadEffect.RetryRead(current.session, "接收超时")))
        }
        else -> Transition(current)
    }

    private val CgmReadState.session: CgmSession?
        get() = when (this) {
            // 逐类型分支 + this. 前缀:避免递归解析到本扩展属性自身
            is CgmReadState.Sending -> this.session
            is CgmReadState.Receiving -> this.session
            is CgmReadState.Completed -> this.session
            is CgmReadState.Failed -> this.session
            is CgmReadState.Stopped -> this.session
            else -> null
        }
}
