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
        is CgmReadEvent.DeviceDisconnected -> onDisconnected(currentState, event)
        is CgmReadEvent.DeviceReconnected -> onReconnected(currentState, event)
        is CgmReadEvent.DeviceReconnectFailed -> onReconnectFailed(currentState, event)
        is CgmReadEvent.StopCurrent -> {
            val session = currentState.session ?: return Transition(currentState)
            Transition(
                CgmReadState.Stopped(session, currentState.accumulatedSoFar),
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
        // 重读路径:RetryRead 重发 ALL → 设备全量重放缓存(删除指令在完成后才发,缓存未删),
        // 从头累积而非追加——追加旧累积会与全量重放拼接成"段被截断"(如旧 EIS 40/95 + 新 LOG 开头),
        // 结构校验必然失败。旧数据由新回放重新提供,去重器兜底重复行。
        is CgmReadState.Failed -> Transition(
            CgmReadState.Receiving(current.session, emptyList(), current.retryCount))
        else -> Transition(current)
    }

    /** 删除确认:Completed 双闸门之一(filePath 与 deleteAcked 都到才 Stopped)。 */
    private fun onDeleteAck(
        current: CgmReadState, event: CgmReadEvent.AckReceived
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Completed -> if (event.purpose == CgmCommandPurpose.DELETE) {
            val done = current.filePath != null
            Transition(
                // Stopped 携带结果快照:信道关闭后用户仍可见已读数据(只读)
                if (done) CgmReadState.Stopped(current.session, current.records)
                else current.copy(deleteAcked = true),
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
                CgmReadState.Completed(
                    current.session, filePath = null, deleteAcked = false,
                    records = conclusion.records   // 快照:落盘同款内容,供 UI 只读展示
                ),
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
                if (done) CgmReadState.Stopped(current.session, current.records)
                else current.copy(filePath = event.path),
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

    /**
     * 断线 → 重连等待:保留已收数据(accumulated),发重连信号(无命令 effect——重连是连接域职责,
     * Translation 在 onTransition 检测 Reconnecting 上报 ConnectionLost,由 Root 转 Connection 域)。
     */
    private fun onDisconnected(
        current: CgmReadState, event: CgmReadEvent.DeviceDisconnected
    ): Transition<CgmReadState, CgmReadEffect> {
        val session = currentStateSession(current) ?: return Transition(current)
        if (session.id != event.sessionId) return Transition(current)
        return Transition(
            CgmReadState.Reconnecting(session, current.accumulatedSoFar, attempt = 0))
    }

    /** 重连成功(Root 转发 Connection 域 Connected):自动重读继续。
     * 累积从头开始——重读 = 全量重放(缓存未删,旧数据由新回放重新提供),追加会拼接成"段截断"必败。 */
    private fun onReconnected(
        current: CgmReadState, event: CgmReadEvent.DeviceReconnected
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Reconnecting -> if (current.session.id == event.sessionId) {
            Transition(
                CgmReadState.Receiving(current.session, emptyList(), current.attempt),
                listOf(CgmReadEffect.RetryRead(current.session, "重连后重读")))
        } else Transition(current)
        else -> Transition(current)
    }

    /** 重连失败(Root 转发 Connection 域失败):闭环终止,不悬挂。 */
    private fun onReconnectFailed(
        current: CgmReadState, event: CgmReadEvent.DeviceReconnectFailed
    ): Transition<CgmReadState, CgmReadEffect> = when (current) {
        is CgmReadState.Reconnecting -> if (current.session.id == event.sessionId) {
            Transition(
                CgmReadState.Failed(current.session, current.accumulated, event.reason, current.attempt + 1),
                listOf(CgmReadEffect.StopRuntime(current.session, StopReason.RECONNECT_FAILED)))
        } else Transition(current)
        else -> Transition(current)
    }

    private fun currentStateSession(state: CgmReadState): CgmSession? = when (state) {
        is CgmReadState.Sending -> state.session
        is CgmReadState.Receiving -> state.session
        is CgmReadState.Completed -> state.session
        is CgmReadState.Failed -> state.session
        is CgmReadState.Reconnecting -> state.session
        else -> null
    }

    private val CgmReadState.session: CgmSession?
        get() = when (this) {
            // 逐类型分支 + this. 前缀:避免递归解析到本扩展属性自身
            is CgmReadState.Sending -> this.session
            is CgmReadState.Receiving -> this.session
            is CgmReadState.Completed -> this.session
            is CgmReadState.Failed -> this.session
            is CgmReadState.Reconnecting -> this.session
            is CgmReadState.Stopped -> this.session
            else -> null
        }

    /** 已收数据快照:重连保留 / 停止展示用。 */
    private val CgmReadState.accumulatedSoFar: List<CgmRecord>
        get() = when (this) {
            is CgmReadState.Receiving -> this.accumulated
            is CgmReadState.Completed -> this.records
            is CgmReadState.Failed -> this.accumulated
            is CgmReadState.Reconnecting -> this.accumulated
            else -> emptyList()
        }
}
