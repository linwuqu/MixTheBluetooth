package com.biosensor.migratedev.decisioncore.cgm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmReadDecisionTest {

    private val session = CgmSession(id = "s1", deviceId = "AA:01")

    /** 完整单段缓存:EIS 段 95 点 + LOG 声明 + START/END 边界。 */
    private val fullCache = buildList {
        add(CgmRecord.Marker(MarkerKind.START, "Start Playback"))
        add(CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"))
        for (i in 1..95) {
            add(CgmRecord.Eis(
                i, 60 + (i - 1) * 10, "a", "b", "c", "d", "EIS:$i,${60 + (i - 1) * 10},a,b,c,d"))
        }
        add(CgmRecord.Marker(MarkerKind.END, "Playback all done"))
    }

    /** 端口在收到 END 时做收集校验,结论随这批记录送回。 */
    private fun conclusionOf(records: List<CgmRecord>) = CacheValidators.validate(records)

    private fun reduce(state: CgmReadState, event: CgmReadEvent) =
        CgmReadDecision.reduce(state, event)

    private fun receiving(
        records: List<CgmRecord> = emptyList(), retryCount: Int = 0
    ) = CgmReadState(
        phase = CgmReadPhase.RECEIVING, session = session,
        retryCount = retryCount, records = records
    )

    // ── 发起读:Idle/终态可发起,活跃忽略 ──

    @Test
    fun `read request from idle starts read with StartRead effect`() {
        val result = reduce(
            CgmReadState(),
            CgmReadEvent.ReadRequested(session.id, session.deviceId)
        )
        assertEquals(
            CgmReadState(phase = CgmReadPhase.SENDING, session = session), result.newState
        )
        assertEquals(listOf(CgmReadEffect.StartRead(session)), result.effects)
    }

    @Test
    fun `read request from terminal states restarts`() {
        for (terminal in listOf(
            CgmReadState(phase = CgmReadPhase.STOPPED, session = session),
            CgmReadState(phase = CgmReadPhase.FAILED, session = session, message = "校验未通过"),
            CgmReadState(phase = CgmReadPhase.ERROR, message = "写入失败")
        )) {
            val result = reduce(terminal, CgmReadEvent.ReadRequested(session.id, session.deviceId))
            assertEquals("从 $terminal 应可重启",
                CgmReadState(phase = CgmReadPhase.SENDING, session = session), result.newState)
            assertEquals(listOf(CgmReadEffect.StartRead(session)), result.effects)
        }
    }

    @Test
    fun `read request while active is ignored`() {
        for (active in listOf(
            CgmReadState(phase = CgmReadPhase.SENDING, session = session),
            receiving(),
            CgmReadState(phase = CgmReadPhase.WRITING, session = session),
            CgmReadState(phase = CgmReadPhase.RECONNECTING, session = session)  // 等待重连中:拒绝新命令
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
            CgmReadState(phase = CgmReadPhase.SENDING, session = session),
            CgmReadEvent.CommandAccepted(session.id)
        )
        assertEquals(receiving(), result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `command accepted from failed retry restarts accumulation`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState(
                phase = CgmReadPhase.FAILED, session = session,
                retryCount = 1, records = accumulated, message = "校验未通过"
            ),
            CgmReadEvent.CommandAccepted(session.id)
        )
        // 重读 = 设备全量重放缓存:从头累积而非追加——
        // 追加会与全量重放拼接成"段截断"必败;旧数据由新回放重新提供
        assertEquals(receiving(retryCount = 1), result.newState)
    }

    // ── 数据累积与校验结论 ──

    @Test
    fun `records without conclusion accumulate silently`() {
        val eis = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val result = reduce(
            receiving(),
            CgmReadEvent.RecordsProduced(session.id, listOf(eis))
        )
        assertEquals(receiving(listOf(eis)), result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `passing conclusion writes the validated records`() {
        val conclusion = conclusionOf(fullCache)
        assertTrue("测试数据本身应通过收集校验", conclusion.complete)
        val result = reduce(
            receiving(),
            CgmReadEvent.RecordsProduced(session.id, fullCache, conclusion)
        )
        val state = result.newState
        assertEquals(CgmReadPhase.WRITING, state.phase)
        assertEquals(fullCache, state.records)
        assertEquals(fullCache, state.report?.receivedRecords)
        // 写盘只写校验后的数据(已去重、已去边界标记)
        assertEquals(listOf(CgmReadEffect.WriteFile(session, conclusion.records)), result.effects)
    }

    @Test
    fun `failing conclusion retries the read`() {
        // 声明 95 点只到 2 点:结构不完整 → 重读(累积保留)
        val incomplete = listOf(
            CgmRecord.Marker(MarkerKind.START, "Start Playback"),
            CgmRecord.Log("LOG:1,1,95,2026-07-21 12:10:45"),
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d"),
            CgmRecord.Eis(2, 70, "a", "b", "c", "d", "EIS:2,70,a,b,c,d"),
            CgmRecord.Marker(MarkerKind.END, "Playback all done")
        )
        val conclusion = conclusionOf(incomplete)
        val result = reduce(
            receiving(),
            CgmReadEvent.RecordsProduced(session.id, incomplete, conclusion)
        )
        val state = result.newState
        assertEquals(CgmReadPhase.FAILED, state.phase)
        assertEquals(1, state.retryCount)
        assertEquals(conclusion.reason, state.message)
        assertEquals(incomplete, state.records)
        assertEquals(
            listOf(CgmReadEffect.RetryRead(session, requireNotNull(conclusion.reason))),
            result.effects
        )
    }

    @Test
    fun `stale records from finished session are ignored`() {
        val finished = CgmReadState(phase = CgmReadPhase.WRITING, session = session)
        val result = reduce(
            finished,
            CgmReadEvent.RecordsProduced(session.id, fullCache, conclusionOf(fullCache))
        )
        assertEquals(finished, result.newState)
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    // ── 入库校验:写盘成功即收工,失败转错误态 ──

    @Test
    fun `file written closes the session`() {
        val saved = CgmFileAppendResult("cgm/s1.txt", 1, 0, 95, 0, 95)
        val writing = CgmReadState(
            phase = CgmReadPhase.WRITING, session = session, records = fullCache,
            report = CgmReadReport(fullCache, checks = emptyList())
        )
        val result = CgmReadDecision.reduce(
            writing, CgmReadEvent.FileWritten(session.id, saved.path, saved)
        )
        val state = result.newState
        assertEquals(CgmReadPhase.STOPPED, state.phase)
        assertEquals(StopReason.COMPLETED, state.stopReason)
        // 入库校验结果留在报告里,界面可看
        assertEquals(saved, state.report?.storage)
        assertTrue(state.report!!.checks.last().passed)
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.COMPLETED)), result.effects
        )
    }

    @Test
    fun `file write failure moves to error without stopping runtime`() {
        val result = CgmReadDecision.reduce(
            CgmReadState(phase = CgmReadPhase.WRITING, session = session),
            CgmReadEvent.FileWriteFailed(session.id, "磁盘满")
        )
        assertEquals(
            CgmReadState(phase = CgmReadPhase.ERROR, session = session, message = "磁盘满"),
            result.newState
        )
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    // ── 超时与重试 ──

    @Test
    fun `timeout while sending fails without retry`() {
        val result = reduce(
            CgmReadState(phase = CgmReadPhase.SENDING, session = session),
            CgmReadEvent.CommandTimeout(session.id)
        )
        assertEquals(
            CgmReadState(phase = CgmReadPhase.FAILED, session = session, message = "发送超时"),
            result.newState
        )
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.TIMEOUT)), result.effects
        )
    }

    @Test
    fun `timeout while receiving retries until limit`() {
        // 模拟完整重试循环:Receiving → 超时 Failed → RetryRead 确认(CommandAccepted)回到 Receiving
        // 上限语义:retryCount = 3 时再超时 (3+1 > 3) 才停止,即 0→1→2→3 四次超时
        var state: CgmReadState = receiving()
        for (retry in 0 until 3) {
            val result = reduce(state, CgmReadEvent.CommandTimeout(session.id))
            assertEquals(retry + 1, result.newState.retryCount)
            assertEquals(CgmReadPhase.FAILED, result.newState.phase)
            assertEquals(
                listOf(CgmReadEffect.RetryRead(session, "接收超时")), result.effects
            )
            // 重读确认:回到 Receiving,累积与 retryCount 保留
            state = reduce(result.newState, CgmReadEvent.CommandAccepted(session.id)).newState
            assertEquals(CgmReadPhase.RECEIVING, state.phase)
            assertEquals(retry + 1, state.retryCount)
        }
        // 第 4 次超时(重试已满 3 次)达到上限:停止而非重读
        val last = reduce(state, CgmReadEvent.CommandTimeout(session.id))
        assertEquals(4, last.newState.retryCount)
        assertEquals("接收超时", last.newState.message)
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
            receiving(accumulated),
            CgmReadEvent.DeviceDisconnected(session.id)
        )
        // 断线不判失败:保留已收数据等待重连,无命令 effect(重连是连接域职责)
        assertEquals(
            CgmReadState(
                phase = CgmReadPhase.RECONNECTING, session = session, records = accumulated),
            result.newState
        )
        assertEquals(emptyList<CgmReadEffect>(), result.effects)
    }

    @Test
    fun `reconnected resumes reading with retry read from scratch`() {
        val accumulated = listOf(
            CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        )
        val result = reduce(
            CgmReadState(
                phase = CgmReadPhase.RECONNECTING, session = session, records = accumulated),
            CgmReadEvent.DeviceReconnected(session.id)
        )
        // 自动重读继续:回 Receiving 发 RetryRead;累积从头(全量重放覆盖旧数据)
        assertEquals(receiving(), result.newState)
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
            CgmReadState(
                phase = CgmReadPhase.RECONNECTING, session = session, records = accumulated),
            CgmReadEvent.DeviceReconnectFailed(session.id, "重连失败")
        )
        assertEquals(
            CgmReadState(
                phase = CgmReadPhase.FAILED, session = session,
                retryCount = 1, records = accumulated, message = "重连失败"),
            result.newState
        )
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.RECONNECT_FAILED)), result.effects
        )
    }

    @Test
    fun `reconnect events on non-reconnecting states are ignored`() {
        for (state in listOf(
            CgmReadState(),
            receiving(),
            CgmReadState(phase = CgmReadPhase.WRITING, session = session)
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
        val state = CgmReadState(phase = CgmReadPhase.RECONNECTING, session = session)
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
            receiving(accumulated),
            CgmReadEvent.StopCurrent(session.deviceId)
        )
        // Stopped 保留已收数据快照:会话关闭,UI 仍展示
        assertEquals(
            CgmReadState(
                phase = CgmReadPhase.STOPPED, session = session,
                records = accumulated, stopReason = StopReason.USER_CANCEL),
            result.newState
        )
        assertEquals(
            listOf(CgmReadEffect.StopRuntime(session, StopReason.USER_CANCEL)), result.effects
        )
    }

    @Test
    fun `reset error returns to idle`() {
        val result = reduce(CgmReadState(phase = CgmReadPhase.ERROR, message = "写入失败"),
            CgmReadEvent.ResetError)
        assertEquals(CgmReadState(), result.newState)
    }

    @Test
    fun `reset error outside error state is noop`() {
        val result = reduce(CgmReadState(), CgmReadEvent.ResetError)
        assertEquals(CgmReadState(), result.newState)
    }

    // ── 转移表完备性:任意状态 × 事件不崩溃(幂等或转移) ──

    @Test
    fun `every state event pair reduces without exception`() {
        val states = listOf(
            CgmReadState(),
            CgmReadState(phase = CgmReadPhase.SENDING, session = session),
            receiving(),
            CgmReadState(phase = CgmReadPhase.WRITING, session = session),
            CgmReadState(phase = CgmReadPhase.RECONNECTING, session = session),
            CgmReadState(phase = CgmReadPhase.FAILED, session = session, message = "x"),
            CgmReadState(phase = CgmReadPhase.ERROR, message = "x"),
            CgmReadState(phase = CgmReadPhase.STOPPED, session = session,
                stopReason = StopReason.COMPLETED)
        )
        val events = listOf(
            CgmReadEvent.ReadRequested(session.id, session.deviceId),
            CgmReadEvent.CommandAccepted(session.id),
            CgmReadEvent.RecordsProduced(session.id, emptyList()),
            CgmReadEvent.RecordsProduced(session.id, fullCache, conclusionOf(fullCache)),
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
