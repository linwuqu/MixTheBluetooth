package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

/**
 * 读状态机:只管会话推进,不碰数据。
 *
 * 数据流(收字节 → 解码/拼行/解析 → 收集校验)全在端口完成,结论由
 * [CgmReadEvent.RecordsProduced.conclusion] 送回。这里只回答两个问题:
 * 校验过了没有(过 → 写盘,不过 → 重读或收工),以及会话该停在哪个终态。
 */
object CgmReadDecision : DecisionCore<CgmReadState, CgmReadEvent, CgmReadEffect> {
    private const val RETRY_LIMIT = 3

    override fun reduce(
        current: CgmReadState, event: CgmReadEvent
    ): Transition<CgmReadState, CgmReadEffect> = when (event) {
        is CgmReadEvent.ReadRequested -> onRequested(current, event)
        is CgmReadEvent.CommandAccepted -> onAccepted(current, event)
        is CgmReadEvent.RecordsProduced -> onRecords(current, event)
        is CgmReadEvent.CommandTimeout -> onTimeout(current, event)
        is CgmReadEvent.DeviceDisconnected -> onDisconnected(current, event)
        is CgmReadEvent.DeviceReconnected -> onReconnected(current, event)
        is CgmReadEvent.DeviceReconnectFailed -> onReconnectFailed(current, event)
        is CgmReadEvent.FileWritten -> onFileWritten(current, event)
        is CgmReadEvent.FileWriteFailed -> onFileWriteFailed(current, event)
        is CgmReadEvent.StopCurrent -> onStop(current, event)
        CgmReadEvent.ResetError ->
            if (current.phase == CgmReadPhase.ERROR) Transition(CgmReadState()) else Transition(current)
    }

    /** 发起读:终端态可重启,活跃中忽略(UI 主防,状态机兜底)。 */
    private fun onRequested(
        current: CgmReadState, event: CgmReadEvent.ReadRequested
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase.running) return Transition(current)
        val session = CgmSession(event.sessionId, event.deviceId)
        return Transition(
            CgmReadState(phase = CgmReadPhase.SENDING, session = session),
            listOf(CgmReadEffect.StartRead(session)))
    }

    /**
     * 命令送达 → 开始接收。
     * FAILED 也收:重读走同一路径。重读 = 设备全量重放缓存(删除指令在完成后才发),
     * 所以记录从头累积,不与上一轮拼接。
     */
    private fun onAccepted(
        current: CgmReadState, event: CgmReadEvent.CommandAccepted
    ): Transition<CgmReadState, CgmReadEffect> {
        val from = current.phase
        if ((from != CgmReadPhase.SENDING && from != CgmReadPhase.FAILED) ||
            !current.isFor(event.sessionId)
        ) return Transition(current)
        return Transition(
            current.copy(phase = CgmReadPhase.RECEIVING, records = emptyList(), message = null))
    }

    /** 收数据:未收到 END 只累积;收到 END 则按端口的收集校验结论决定写盘还是重读。 */
    private fun onRecords(
        current: CgmReadState, event: CgmReadEvent.RecordsProduced
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase != CgmReadPhase.RECEIVING || !current.isFor(event.sessionId)) {
            return Transition(current)   // 会话已结束/不匹配:迟到的记录忽略(幂等)
        }
        val accumulated = current.records + event.records
        val conclusion = event.conclusion
            ?: return Transition(current.copy(records = accumulated))   // 还没收完,无 effect

        val session = requireNotNull(current.session)
        val attempt = current.retryCount + 1
        val report = CgmReadReport(
            receivedRecords = CacheBoundaryTrimmer.trim(accumulated).ifEmpty { accumulated },
            checks = current.report?.checks.orEmpty() +
                conclusion.checks.map { it.copy(name = "第 $attempt 次 · ${it.name}") })

        if (conclusion.complete) {
            return Transition(
                current.copy(
                    phase = CgmReadPhase.WRITING, records = accumulated, report = report),
                listOf(CgmReadEffect.WriteFile(session, conclusion.records)))
        }
        return failAndRetry(
            current, conclusion.reason ?: "校验未通过", report = report, records = accumulated)
    }

    private fun onTimeout(
        current: CgmReadState, event: CgmReadEvent.CommandTimeout
    ): Transition<CgmReadState, CgmReadEffect> {
        if (!current.isFor(event.sessionId)) return Transition(current)
        val session = requireNotNull(current.session)
        return when (current.phase) {
            // 命令没送出去:重试无意义,直接收工
            CgmReadPhase.SENDING -> Transition(
                current.copy(phase = CgmReadPhase.FAILED, message = "发送超时"),
                listOf(CgmReadEffect.StopRuntime(session, StopReason.TIMEOUT)))
            CgmReadPhase.RECEIVING -> failAndRetry(current, "接收超时")
            else -> Transition(current)
        }
    }

    /**
     * 断线 → 等重连。保留已收数据,不发命令 effect(重连是连接域职责,
     * Translation 在 onTransition 检测 RECONNECTING 上报 ConnectionLost,由 Root 转 Connection 域)。
     * 重试计数归零:重连成功后是全量重读,重试次数重新算。
     */
    private fun onDisconnected(
        current: CgmReadState, event: CgmReadEvent.DeviceDisconnected
    ): Transition<CgmReadState, CgmReadEffect> {
        if (!current.phase.holdingSession || !current.isFor(event.sessionId)) {
            return Transition(current)
        }
        return Transition(
            current.copy(phase = CgmReadPhase.RECONNECTING, retryCount = 0))
    }

    /** 重连成功:自动重读继续。记录从头开始——重读是全量重放。 */
    private fun onReconnected(
        current: CgmReadState, event: CgmReadEvent.DeviceReconnected
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase != CgmReadPhase.RECONNECTING || !current.isFor(event.sessionId)) {
            return Transition(current)
        }
        val session = requireNotNull(current.session)
        return Transition(
            current.copy(phase = CgmReadPhase.RECEIVING, records = emptyList()),
            listOf(CgmReadEffect.RetryRead(session, "重连后重读")))
    }

    /** 重连失败:闭环终止,不悬挂。 */
    private fun onReconnectFailed(
        current: CgmReadState, event: CgmReadEvent.DeviceReconnectFailed
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase != CgmReadPhase.RECONNECTING || !current.isFor(event.sessionId)) {
            return Transition(current)
        }
        val session = requireNotNull(current.session)
        return Transition(
            current.copy(
                phase = CgmReadPhase.FAILED,
                retryCount = current.retryCount + 1,
                message = event.reason),
            listOf(CgmReadEffect.StopRuntime(session, StopReason.RECONNECT_FAILED)))
    }

    /** 入库校验完成:记录结果,关闭会话。 */
    private fun onFileWritten(
        current: CgmReadState, event: CgmReadEvent.FileWritten
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase != CgmReadPhase.WRITING || !current.isFor(event.sessionId)) {
            return Transition(current)
        }
        val session = requireNotNull(current.session)
        val previous = current.report
        val detail = event.result
            ?.let { "新增 ${it.addedPoints} 点，文件重复 ${it.duplicatePoints} 点" }
            ?: "保存完成: ${event.path}"
        return Transition(
            current.copy(
                phase = CgmReadPhase.STOPPED,
                stopReason = StopReason.COMPLETED,
                report = previous?.copy(
                    storage = event.result,
                    checks = previous.checks + ValidationCheck("文件保存", true, detail))),
            listOf(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)))
    }

    /** 入库校验失败:数据留住给界面看,会话转错误态(下次命令自动复位)。 */
    private fun onFileWriteFailed(
        current: CgmReadState, event: CgmReadEvent.FileWriteFailed
    ): Transition<CgmReadState, CgmReadEffect> {
        if (current.phase != CgmReadPhase.WRITING || !current.isFor(event.sessionId)) {
            return Transition(current)
        }
        val previous = current.report
        return Transition(
            current.copy(
                phase = CgmReadPhase.ERROR,
                message = event.message,
                report = previous?.copy(
                    checks = previous.checks + ValidationCheck("文件追加", false, event.message))))
    }

    /** 用户强制停止:把已收数据留在会话快照里(信道已关,界面仍可见)。 */
    private fun onStop(
        current: CgmReadState, event: CgmReadEvent.StopCurrent
    ): Transition<CgmReadState, CgmReadEffect> {
        val session = current.session
        if (session == null || session.deviceId != event.deviceId) return Transition(current)
        return Transition(
            current.copy(
                phase = CgmReadPhase.STOPPED,
                stopReason = StopReason.USER_CANCEL,
                report = current.report?.copy(receivedRecords = current.records)),
            listOf(CgmReadEffect.StopRuntime(session, StopReason.USER_CANCEL)))
    }

    /** 失败 → 重读;重试次数用尽则收工。 */
    private fun failAndRetry(
        current: CgmReadState,
        reason: String,
        report: CgmReadReport? = current.report,
        records: List<CgmRecord> = current.records,
    ): Transition<CgmReadState, CgmReadEffect> {
        val session = requireNotNull(current.session)
        val attempt = current.retryCount + 1
        return Transition(
            current.copy(
                phase = CgmReadPhase.FAILED, retryCount = attempt,
                records = records, report = report, message = reason),
            listOf(
                if (attempt > RETRY_LIMIT) CgmReadEffect.StopRuntime(session, StopReason.RETRY_EXHAUSTED)
                else CgmReadEffect.RetryRead(session, reason)))
    }

    private fun CgmReadState.isFor(sessionId: String): Boolean = session?.id == sessionId
}
