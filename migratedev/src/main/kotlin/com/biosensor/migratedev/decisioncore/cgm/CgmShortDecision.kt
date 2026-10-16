package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

/**
 * 短命令状态机(对时/删除):发一条 → 等确认 → Done,四态足够。
 * Done 就是终态(对时成功/删除成功),没有"停止"概念;超时/断开直接 Failed,不重试(重试 = 用户再点一次)。
 */
object CgmShortDecision : DecisionCore<CgmShortState, CgmShortEvent, CgmShortEffect> {
    override fun reduce(
        current: CgmShortState, event: CgmShortEvent
    ): Transition<CgmShortState, CgmShortEffect> = when (event) {
        is CgmShortEvent.ShortRequested -> when (current) {
            CgmShortState.Idle, is CgmShortState.Done, is CgmShortState.Failed -> {
                val session = CgmSession(event.sessionId, event.deviceId)
                Transition(
                    CgmShortState.Sending(session, event.purpose),
                    listOf(CgmShortEffect.SendCommand(session, event.purpose)))
            }
            // 兜底忽略;正常路径由 Translation 先复位再开始(架构 07 §1.7 ②)
            else -> Transition(current)
        }

        is CgmShortEvent.CommandAccepted -> when (current) {
            is CgmShortState.Sending -> if (current.session.id == event.sessionId) {
                Transition(CgmShortState.WaitingAck(current.session, current.purpose))
            } else Transition(current)
            else -> Transition(current)
        }

        is CgmShortEvent.AckReceived -> when (current) {
            // purpose 不匹配:串行下不会发生,防御性忽略
            is CgmShortState.WaitingAck ->
                if (current.session.id == event.sessionId && current.purpose == event.purpose) {
                    Transition(CgmShortState.Done(current.session, current.purpose))
                } else Transition(current)
            else -> Transition(current)
        }

        is CgmShortEvent.CommandTimeout -> current.fail(event.sessionId, "命令超时")
        is CgmShortEvent.DeviceDisconnected -> current.fail(event.sessionId, "设备断开")
    }

    /** 命令在途时失败;其他状态忽略(迟到的终止事件)。 */
    private fun CgmShortState.fail(
        sessionId: String, reason: String
    ): Transition<CgmShortState, CgmShortEffect> =
        if (this is CgmShortState.Pending && session.id == sessionId) {
            Transition(CgmShortState.Failed(session, reason, purpose))
        } else Transition(this)
}
