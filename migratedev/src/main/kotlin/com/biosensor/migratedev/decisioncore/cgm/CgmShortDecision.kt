package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.DecisionCore
import com.biosensor.migratedev.decisioncore.Transition

// State / Event / Effect 词汇在 CgmContracts.kt(文件 04)

/**
 * 短命令状态机(对时/删除):发一条 → 等确认 → Done,四态足够。
 * Done 就是终态(对时成功/删除成功),没有"停止"概念;超时/断开直接 Failed,不重试(重试 = 用户再点一次)。
 */
object CgmShortDecision : DecisionCore<CgmShortState, CgmShortEvent, CgmShortEffect> {
    override fun reduce(
        currentState: CgmShortState, event: CgmShortEvent
    ): Transition<CgmShortState, CgmShortEffect> = when (event) {
        is CgmShortEvent.ShortRequested -> when (currentState) {
            CgmShortState.Idle, is CgmShortState.Done, is CgmShortState.Failed -> {
                val session = CgmSession(event.sessionId, event.deviceId)
                Transition(
                    CgmShortState.Sending(session, event.purpose),
                    listOf(CgmShortEffect.SendCommand(session, event.purpose)))
            }
            else -> Transition(currentState)   // 兜底忽略;正常路径由 Translation 先复位再开始(架构 07 §1.7 ②)
        }

        is CgmShortEvent.CommandAccepted -> when (currentState) {
            is CgmShortState.Sending -> Transition(
                CgmShortState.WaitingAck(currentState.session, currentState.purpose))
            else -> Transition(currentState)
        }

        is CgmShortEvent.AckReceived -> when (currentState) {
            is CgmShortState.WaitingAck ->
                if (currentState.purpose == event.purpose) {
                    Transition(CgmShortState.Done(currentState.session, currentState.purpose))
                } else Transition(currentState)   // purpose 不匹配:串行下不会发生,防御性忽略
            else -> Transition(currentState)
        }

        // 逐类型分支:多分支并列时 smart cast 不成立,每个类型单独取 session
        is CgmShortEvent.CommandTimeout -> when (currentState) {
            is CgmShortState.Sending -> Transition(
                CgmShortState.Failed(currentState.session, "命令超时"))
            is CgmShortState.WaitingAck -> Transition(
                CgmShortState.Failed(currentState.session, "命令超时"))
            else -> Transition(currentState)
        }

        is CgmShortEvent.DeviceDisconnected -> when (currentState) {
            is CgmShortState.Sending -> Transition(
                CgmShortState.Failed(currentState.session, "设备断开"))
            is CgmShortState.WaitingAck -> Transition(
                CgmShortState.Failed(currentState.session, "设备断开"))
            else -> Transition(currentState)
        }
    }
}
