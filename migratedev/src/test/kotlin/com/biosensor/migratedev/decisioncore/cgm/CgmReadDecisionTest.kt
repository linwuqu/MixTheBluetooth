package com.biosensor.migratedev.decisioncore.cgm

import com.biosensor.migratedev.decisioncore.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmReadDecisionTest {

    private val session = CgmSession(id = "s1", deviceId = "AA:01")

    private fun reduce(state: CgmReadState, event: CgmReadEvent) =
        CgmReadDecision.reduce(state, event)

    // ── 发起读:Idle/终态可发起,活跃忽略 ──

    @Test
    fun `read request from idle starts read with StartRead effect`() {
        val result = reduce(
            CgmReadState.Idle,
            CgmReadEvent.ReadRequested(session.id, session.deviceId)
        )
        assertEquals(CgmReadState.Sending(session), result.newState)
        assertEquals(listOf(CgmReadEffect.StartRead(session)), result.effects)
    }

    @Test
    fun `read request from terminal states restarts`() {
        for (terminal in listOf(
            CgmReadState.Completed(session, "f.txt", deleteAcked = true, records = emptyList()),
            CgmReadState.Failed(session, emptyList(), "校验未通过", 1),
            CgmReadState.Stopped(session, emptyList()),
            CgmReadState.Error("写入失败")
        )) {
            val result = reduce(terminal, CgmReadEvent.ReadRequested(session.id, session.deviceId))
            assertEquals("从 $terminal 应可重启", CgmReadState.Sending(session), result.newState)
            assertEquals(listOf(CgmReadEffect.StartRead(session)), result.effects)
        }
    }

    @Test
    fun `read request while active is ignored`() {
        for (active in listOf(
            CgmReadState.Sending(session),
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadState.Reconnecting(session, emptyList(), 0)   // 等待重连中:拒绝新命令
        )) {
            val result = reduce(active, CgmReadEvent.ReadRequested(session.id, session.deviceId))
            assertEquals(active, result.newState)   // 幂等忽略,无 effect
            assertEquals(emptyList<CgmReadEffect>(), result.effects)
        }
    }

    // ── 命令确认:进入 Receiving ──

    @Test
    fun `command accepted moves sending to receiving`() {
        val result = reduce(
            CgmReadState.Sending(session),
            CgmReadEvent.CommandAccepted(session.id)
        )
        assertEquals(CgmReadState.Receiving(session, emptyList(), 0), result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `command accepted from failed retry restarts accumulation`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState.Failed(session, accumulated, "校验未通过", 1),
            CgmReadEvent.CommandAccepted(session.id)
        )
        // 重读 = 设备全量重放缓存(删除在完成后才发):从头累积而非追加——
        // 追加会与全量重放拼接成"段截断"必败;旧数据由新回放重新提供
        assertEquals(CgmReadState.Receiving(session, emptyList(), 1), result.newState)
    }

    // ── 数据累积与校验触发 ──

    @Test
    fun `records without END accumulate silently`() {
        val eis = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val result = reduce(
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, listOf(eis))
        )
        assertEquals(CgmReadState.Receiving(session, listOf(eis), 0), result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `records with END trigger validation and complete read`() {
        // 完整的单段缓存:EIS 段 95 点 + LOG 声明 + START/END 边界
        val records = buildList {
            add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
            add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
            for (i in 1..95) {
                add(CgmRecord.Eis(
                    i, 60 + (i - 1) * 10, "a", "b", "c", "d", "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"))
            }
            add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
        }
        val result = reduce(
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, records)
        )
        val completed = result.newState as? CgmReadState.Completed
        assertTrue("应完成而非重读,实际: ${result.newState}", completed != null)
        assertEquals(2, result.effects.size)
        assertTrue("应先写盘", result.effects[0] is CgmReadEffect.WriteFile)
        assertTrue("后发删除", result.effects[1] is CgmReadEffect.SendDelete)
        // 完成时双闸门未到(落盘/删除确认都未回)
        assertEquals(false, completed!!.deleteAcked)
        assertEquals(null, completed.filePath)
    }

    @Test
    fun `records with END but incomplete structure trigger retry read`() {
        // 声明 95 点只到 2 点:结构不完整 → 重读(累积保留)
        val incomplete = listOf(
            CgmRecord.Marker(MarkerKind.START, "Start Playback"),
            CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"),
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d"),
            CgmRecord.Eis(2, 70, "a", "b", "c", "d", "EIS:2,70,a,b,c,d"),
            CgmRecord.Marker(MarkerKind.END, "Playback all done")
        )
        val result = reduce(
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, incomplete)
        )
        val failed = result.newState as? CgmReadState.Failed
        assertTrue("应重读而非完成,实际: ${result.newState}", failed != null)
        assertEquals(1, failed!!.retryCount)
        assertTrue(result.effects.any { it is CgmReadEffect.RetryRead })
    }

    @Test
    fun `stale records from finished session are ignored`() {
        val result = reduce(
            CgmReadState.Completed(session, "f.txt", deleteAcked = true, records = emptyList()),
            CgmReadEvent.RecordsProduced(session.id, listOf(
                CgmRecord.Marker(MarkerKind.START, "Start Playback")
            ))
        )
        assertEquals(
            CgmReadState.Completed(session, "f.txt", deleteAcked = true, records = emptyList()),
            result.newState
        )
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    // ── Completed 双闸门:filePath + deleteAcked ──

    @Test
    fun `completed read needs both write and delete ack before stopping`() {
        val snapshot = listOf(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
        var state: CgmReadState =
            CgmReadState.Completed(session, null, deleteAcked = false, records = snapshot)

        val written = CgmReadDecision.reduce(
            state, CgmReadEvent.FileWritten(session.id, "cgm/s1.txt")
        )
        state = written.newState
        assertEquals(
            CgmReadState.Completed(session, "cgm/s1.txt", deleteAcked = false, records = snapshot),
            state
        )
        assertTrue("仅写盘不停止", written.effects.isEmpty())

        val acked = CgmReadDecision.reduce(
            state, CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
        )
        // Stopped 携带只读快照:会话关闭后 UI 数据看板保留
        assertEquals(CgmReadState.Stopped(session, snapshot), acked.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)), acked.effects
        )
    }

    @Test
    fun `delete ack first then file written also stops`() {
        var state: CgmReadState =
            CgmReadState.Completed(session, null, deleteAcked = false, records = emptyList())

        val acked = CgmReadDecision.reduce(
            state, CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE)
        )
        state = acked.newState
        assertEquals(CgmReadState.Completed(session, null, true, emptyList()), state)

        val written = CgmReadDecision.reduce(
            state, CgmReadEvent.FileWritten(session.id, "cgm/s1.txt")
        )
        assertEquals(CgmReadState.Stopped(session, emptyList()), written.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)), written.effects
        )
    }

    @Test
    fun `delete ack with wrong purpose is ignored`() {
        val state = CgmReadState.Completed(session, "f.txt", deleteAcked = false, records = emptyList())
        val result = CgmReadDecision.reduce(
            state, CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.SYNC_TIME)
        )
        assertEquals(state, result.newState)
    }

    @Test
    fun `file write failure moves to error`() {
        val result = CgmReadDecision.reduce(
            CgmReadState.Completed(session, null, false, emptyList()),
            CgmReadEvent.FileWriteFailed(session.id, "磁盘满")
        )
        assertEquals(CgmReadState.Error("磁盘满"), result.newState)
    }

    // ── 超时与重试 ──

    @Test
    fun `timeout while sending fails without retry`() {
        val result = reduce(
            CgmReadState.Sending(session),
            CgmReadEvent.CommandTimeout(session.id)
        )
        assertEquals(CgmReadState.Failed(session, emptyList(), "发送超时", 0), result.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.TIMEOUT)), result.effects
        )
    }

    @Test
    fun `timeout while receiving retries until limit`() {
        // 模拟完整重试循环:Receiving → 超时 Failed → RetryRead 确认(CommandAccepted)回到 Receiving
        // 上限语义:retryCount = 3 时再超时 (3+1 > 3) 才停止,即 0→1→2→3 四次超时
        var state: CgmReadState = CgmReadState.Receiving(session, emptyList(), 0)
        for (retry in 0 until 3) {
            val result = reduce(state, CgmReadEvent.CommandTimeout(session.id))
            val failed = result.newState as CgmReadState.Failed
            assertEquals(retry + 1, failed.retryCount)
            assertTrue("第 ${retry + 1} 次应重读", result.effects.any { it is CgmReadEffect.RetryRead })
            // 重读确认:回到 Receiving,累积与 retryCount 保留
            val accepted = reduce(failed, CgmReadEvent.CommandAccepted(session.id))
            state = accepted.newState
            assertTrue(state is CgmReadState.Receiving)
            assertEquals(retry + 1, (state as CgmReadState.Receiving).retryCount)
        }
        // 第 4 次超时(重试已满 3 次)达到上限:停止而非重读
        val last = reduce(state, CgmReadEvent.CommandTimeout(session.id))
        assertEquals(CgmReadState.Failed(session, emptyList(), "接收超时", 4), last.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.RETRY_EXHAUSTED)), last.effects
        )
    }

    // ── 断开 → 重连(默认重连,不静默失败)──

    @Test
    fun `disconnect moves to reconnecting keeping accumulated records`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState.Receiving(session, accumulated, 0),
            CgmReadEvent.DeviceDisconnected(session.id)
        )
        // 断线不判失败:保留已收数据等待重连,无命令 effect(重连是连接域职责)
        assertEquals(CgmReadState.Reconnecting(session, accumulated, 0), result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `reconnected resumes reading with retry read from scratch`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState.Reconnecting(session, accumulated, 0),
            CgmReadEvent.DeviceReconnected(session.id)
        )
        // 自动重读继续:回 Receiving 发 RetryRead;累积从头(全量重放覆盖旧数据,去重器兜底)
        assertEquals(CgmReadState.Receiving(session, emptyList(), 0), result.newState)
        assertEquals(
            listOf(CgmReadEffect.RetryRead(session, "重连后重读")), result.effects
        )
    }

    @Test
    fun `reconnect failed closes loop with stop runtime`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState.Reconnecting(session, accumulated, 0),
            CgmReadEvent.DeviceReconnectFailed(session.id, "重连失败")
        )
        assertEquals(CgmReadState.Failed(session, accumulated, "重连失败", 1), result.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.RECONNECT_FAILED)), result.effects
        )
    }

    @Test
    fun `reconnect events on non-reconnecting states are ignored`() {
        for (state in listOf(
            CgmReadState.Idle,
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadState.Completed(session, null, false, emptyList())
        )) {
            val reconnected = reduce(state, CgmReadEvent.DeviceReconnected(session.id))
            assertEquals(state, reconnected.newState)
            assertEquals(emptyList<CgmReadEffect>(), reconnected.effects)

            val failed = reduce(state, CgmReadEvent.DeviceReconnectFailed(session.id, "x"))
            assertEquals(state, failed.newState)
            assertEquals(emptyList<CgmReadEffect>(), failed.effects)
        }
    }

    @Test
    fun `reconnect events for other session are ignored`() {
        val other = CgmSession("s2", "BB:02")
        val state = CgmReadState.Reconnecting(session, emptyList(), 0)
        val result = reduce(state, CgmReadEvent.DeviceReconnected(other.id))
        assertEquals(state, result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    // ── 停止 / 复位 ──

    @Test
    fun `stop current moves to stopped keeping snapshot`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState.Receiving(session, accumulated, 0),
            CgmReadEvent.StopCurrent(session.deviceId)
        )
        // Stopped 携带已收数据快照:会话关闭,UI 仍展示
        assertEquals(CgmReadState.Stopped(session, accumulated), result.newState)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.USER_CANCEL)), result.effects
        )
    }

    @Test
    fun `reset error returns to idle`() {
        val result = reduce(CgmReadState.Error("写入失败"), CgmReadEvent.ResetError)
        assertEquals(CgmReadState.Idle, result.newState)
    }

    @Test
    fun `reset error outside error state is noop`() {
        val result = reduce(CgmReadState.Idle, CgmReadEvent.ResetError)
        assertEquals(CgmReadState.Idle, result.newState)
    }

    // ── 转移表完备性:任意状态 × 事件不崩溃(幂等或转移) ──

    @Test
    fun `every state event pair reduces without exception`() {
        val states = listOf<CgmReadState>(
            CgmReadState.Idle,
            CgmReadState.Sending(session),
            CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadState.Completed(session, "f.txt", deleteAcked = false, records = emptyList()),
            CgmReadState.Completed(session, "f.txt", deleteAcked = true, records = emptyList()),
            CgmReadState.Failed(session, emptyList(), "x", 1),
            CgmReadState.Reconnecting(session, emptyList(), 0),
            CgmReadState.Error("x"),
            CgmReadState.Stopped(session, emptyList())
        )
        val events = listOf<CgmReadEvent>(
            CgmReadEvent.ReadRequested(session.id, session.deviceId),
            CgmReadEvent.CommandAccepted(session.id),
            CgmReadEvent.AckReceived(session.id, CgmCommandPurpose.DELETE),
            CgmReadEvent.RecordsProduced(session.id, emptyList()),
            CgmReadEvent.CommandTimeout(session.id),
            CgmReadEvent.DeviceDisconnected(session.id),
            CgmReadEvent.DeviceReconnected(session.id),
            CgmReadEvent.DeviceReconnectFailed(session.id, "x"),
            CgmReadEvent.FileWritten(session.id, "f.txt"),
            CgmReadEvent.FileWriteFailed(session.id, "x"),
            CgmReadEvent.StopCurrent(session.deviceId),
            CgmReadEvent.ResetError
        )
        for (state in states) {
            for (event in events) {
                // 任何 (状态, 事件) 都必须是合法转移(可能幂等,但不得抛异常)
                CgmReadDecision.reduce(state, event)
            }
        }
    }
}
