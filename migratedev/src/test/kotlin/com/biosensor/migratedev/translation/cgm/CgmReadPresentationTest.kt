package com.biosensor.migratedev.translation.cgm

import com.biosensor.migratedev.decisioncore.cgm.*
import org.junit.Assert.*
import org.junit.Test

class CgmReadPresentationTest {
    private val session = CgmSession("s1", "AA:01")
    private val raw = listOf(
        CgmRecord.Marker(MarkerKind.START, "Start Playback"),
        CgmRecord.Log("LOG:1,1,1,2026-06-23 14:31:27"),
        CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d"),
        CgmRecord.Log("LOG:2,2,2,2026-06-23 14:32:02"),
        CgmRecord.Ca(1, "10", "CA:1,10"),
        CgmRecord.Ca(2, "20", "CA:2,20"),
        CgmRecord.Marker(MarkerKind.END, "Playback all done")
    )

    /** 端口在收到 END 时做收集校验,结论随这批记录送回状态机。 */
    private fun concluded(records: List<CgmRecord>) =
        CgmReadDecision.reduce(
            CgmReadState(phase = CgmReadPhase.RECEIVING, session = session),
            CgmReadEvent.RecordsProduced(session.id, records, CacheValidators.validate(records))
        ).newState

    @Test
    fun `completed save retains raw data and detailed reports for display`() {
        val writing = concluded(raw)
        assertEquals(CgmReadPhase.WRITING, writing.phase)
        // 看板显示本次收到的原始记录(含边界标记),不去重不裁剪
        assertEquals(raw, writing.displayRecords)
        assertTrue(writing.report!!.checks.any { it.name.contains("seq") && it.passed })
        assertTrue(writing.report!!.checks.all { it.name.startsWith("第 1 次") })

        val saved = CgmFileAppendResult("cgm/AA_01.txt", 2, 0, 3, 0, 3)
        val stopped = CgmReadDecision.reduce(
            writing, CgmReadEvent.FileWritten(session.id, saved.path, saved)
        ).newState

        assertEquals(CgmReadPhase.STOPPED, stopped.phase)
        assertEquals(raw, stopped.displayRecords)
        assertEquals(saved, stopped.report!!.storage)
        assertTrue(stopped.report!!.checks.last().passed)
        val summary = summarizeRecords(stopped.displayRecords)
        assertEquals(7, summary.recordCount)
        assertEquals(1, summary.eisPoints)
        assertEquals(2, summary.caPoints)
        assertEquals(2, summary.segments)
        assertEquals("2026-06-23 14:31:27", summary.firstTime)
        assertEquals("2026-06-23 14:32:02", summary.lastTime)
    }

    @Test
    fun `save failure keeps received data instead of clearing the board`() {
        val writing = concluded(raw)
        val error = CgmReadDecision.reduce(writing,
            CgmReadEvent.FileWriteFailed(session.id, "磁盘满")).newState

        assertEquals(CgmReadPhase.ERROR, error.phase)
        assertEquals(raw, error.displayRecords)
        assertEquals(session, error.session)
        assertFalse(error.report!!.checks.last().passed)
    }

    @Test
    fun `retry preserves earlier failure checks and adds new passing checks`() {
        val incomplete = raw.filterNot { it is CgmRecord.Ca && it.seq == 2 }
        val failed = concluded(incomplete)
        assertEquals(CgmReadPhase.FAILED, failed.phase)

        val receiving = CgmReadDecision.reduce(
            failed, CgmReadEvent.CommandAccepted(session.id)).newState
        val writing = CgmReadDecision.reduce(
            receiving,
            CgmReadEvent.RecordsProduced(session.id, raw, CacheValidators.validate(raw))
        ).newState

        assertTrue(writing.report!!.checks.any { it.name.startsWith("第 1 次") && !it.passed })
        assertTrue(writing.report!!.checks.any { it.name.startsWith("第 2 次") && it.passed })
        assertEquals(raw, writing.displayRecords)
    }

    @Test
    fun `receiving exposes all records rather than only the final ten`() {
        val points = (1..95).map { CgmRecord.Ca(it, "10", "CA:$it,10") }
        val state = CgmReadState(
            phase = CgmReadPhase.RECEIVING, session = session, records = points)
        assertEquals(95, state.displayRecords.size)
        assertEquals(95, summarizeRecords(state.displayRecords).caPoints)
    }
}
