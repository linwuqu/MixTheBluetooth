package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmShortDecisionTest {

    private val session = CgmSession(id = "s1", deviceId = "AA:01")

    private fun reduce(state: CgmShortState, event: CgmShortEvent) =
        CgmShortDecision.reduce(state, event)

    // ── 发起短命令:Idle/终态可发起,活跃忽略 ──

    @Test
    fun `sync time from idle sends command`() {
        val result = reduce(
            CgmShortState.Idle,
            CgmShortEvent.ShortRequested(session.id, session.deviceId, CgmCommandPurpose.SYNC_TIME)
        )
        assertEquals(CgmShortState.Sending(session, CgmCommandPurpose.SYNC_TIME), result.newState)
        assertEquals(
            listOf(CgmShortEffect.SendCommand(session, CgmCommandPurpose.SYNC_TIME)), result.effects
        )
    }

    @Test
    fun `short command from done or failed restarts`() {
        for (terminal in listOf(
            CgmShortState.Done(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.Failed(session, "命令超时")
        )) {
            val result = reduce(
                terminal,
                CgmShortEvent.ShortRequested(session.id, session.deviceId, CgmCommandPurpose.DELETE)
            )
            assertEquals(
                CgmShortState.Sending(session, CgmCommandPurpose.DELETE), result.newState
            )
        }
    }

    @Test
    fun `short command while active is ignored`() {
        for (active in listOf(
            CgmShortState.Sending(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME)
        )) {
            val result = reduce(
                active,
                CgmShortEvent.ShortRequested(session.id, session.deviceId, CgmCommandPurpose.DELETE)
            )
            assertEquals(active, result.newState)
            assertEquals(emptyList<CgmShortEffect>(), result.effects)
        }
    }

    // ── 确认路径:CommandAccepted → WaitingAck → AckReceived → Done ──

    @Test
    fun `command accepted moves to waiting ack`() {
        val result = reduce(
            CgmShortState.Sending(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortEvent.CommandAccepted(session.id)
        )
        assertEquals(CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME), result.newState)
        assertEquals(emptyList<CgmShortEffect>(), result.effects)
    }

    @Test
    fun `matching ack moves to done`() {
        val result = reduce(
            CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.SYNC_TIME)
        )
        assertEquals(CgmShortState.Done(session, CgmCommandPurpose.SYNC_TIME), result.newState)
    }

    @Test
    fun `non matching ack is ignored`() {
        val state = CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME)
        val result = reduce(
            state,
            CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
        )
        assertEquals(state, result.newState)
    }

    // ── 失败路径:超时 / 断开,不重试 ──

    @Test
    fun `timeout while sending fails without retry`() {
        val result = reduce(
            CgmShortState.Sending(session, CgmCommandPurpose.DELETE),
            CgmShortEvent.CommandTimeout(session.id)
        )
        assertEquals(CgmShortState.Failed(session, "命令超时"), result.newState)
        assertEquals(emptyList<CgmShortEffect>(), result.effects)
    }

    @Test
    fun `timeout while waiting ack fails without retry`() {
        val result = reduce(
            CgmShortState.WaitingAck(session, CgmCommandPurpose.DELETE),
            CgmShortEvent.CommandTimeout(session.id)
        )
        assertEquals(CgmShortState.Failed(session, "命令超时"), result.newState)
    }

    @Test
    fun `disconnect while active fails`() {
        for (active in listOf(
            CgmShortState.Sending(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME)
        )) {
            val result = reduce(active, CgmShortEvent.DeviceDisconnected(session.id))
            assertEquals(CgmShortState.Failed(session, "设备断开"), result.newState)
        }
    }

    // ── 转移表完备性 ──

    @Test
    fun `every state event pair reduces without exception`() {
        val states = listOf<CgmShortState>(
            CgmShortState.Idle,
            CgmShortState.Sending(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.WaitingAck(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.Done(session, CgmCommandPurpose.SYNC_TIME),
            CgmShortState.Failed(session, "x")
        )
        val events = listOf<CgmShortEvent>(
            CgmShortEvent.ShortRequested(session.id, session.deviceId, CgmCommandPurpose.SYNC_TIME),
            CgmShortEvent.CommandAccepted(session.id),
            CgmShortEvent.AckReceived(session.id, CgmCommandPurpose.SYNC_TIME),
            CgmShortEvent.CommandTimeout(session.id),
            CgmShortEvent.DeviceDisconnected(session.id)
        )
        for (state in states) {
            for (event in events) {
                // 任何 (状态, 事件) 都必须是合法转移(可能幂等,但不得抛异常)
                CgmShortDecision.reduce(state, event)
            }
        }
    }
}
