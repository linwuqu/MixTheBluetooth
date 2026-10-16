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

    @Test
    fun `completed save retains raw data and detailed reports for display`() {
        val completed = CgmReadDecision.reduce(CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, raw)).newState as CgmReadState.Completed
        assertEquals(raw, completed.displayRecords())
        assertTrue(completed.records.none { it is CgmRecord.Marker })
        assertTrue(completed.report!!.checks.any { it.name.contains("seq") && it.passed })
        val saved = CgmFileAppendResult("cgm/AA_01.txt", 2, 0, 3, 0, 3)
        val stopped = CgmReadDecision.reduce(completed,
            CgmReadEvent.FileWritten(session.id, saved.path, saved)).newState as CgmReadState.Stopped
        assertEquals(raw, stopped.displayRecords())
        assertEquals(saved, stopped.report!!.storage)
        assertTrue(stopped.report!!.checks.last().passed)
        val summary = summarizeRecords(stopped.displayRecords())
        assertEquals(7, summary.recordCount)
        assertEquals(1, summary.eisPoints)
        assertEquals(2, summary.caPoints)
        assertEquals(2, summary.segments)
        assertEquals("2026-06-23 14:31:27", summary.firstTime)
        assertEquals("2026-06-23 14:32:02", summary.lastTime)
    }

    @Test
    fun `save failure keeps received data instead of clearing the board`() {
        val completed = CgmReadDecision.reduce(CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, raw)).newState as CgmReadState.Completed
        val error = CgmReadDecision.reduce(completed,
            CgmReadEvent.FileWriteFailed(session.id, "磁盘满")).newState as CgmReadState.Error
        assertEquals(raw, error.displayRecords())
        assertEquals(session, error.displaySession())
        assertFalse(error.report!!.checks.last().passed)
    }

    @Test
    fun `retry preserves earlier failure checks and adds new passing checks`() {
        val incomplete = raw.filterNot { it is CgmRecord.Ca && it.seq == 2 }
        val failed = CgmReadDecision.reduce(CgmReadState.Receiving(session, emptyList(), 0),
            CgmReadEvent.RecordsProduced(session.id, incomplete)).newState as CgmReadState.Failed
        val receiving = CgmReadDecision.reduce(failed, CgmReadEvent.CommandAccepted(session.id)).newState
        val completed = CgmReadDecision.reduce(receiving,
            CgmReadEvent.RecordsProduced(session.id, raw)).newState as CgmReadState.Completed
        assertTrue(completed.report!!.checks.any { it.name.startsWith("第 1 次") && !it.passed })
        assertTrue(completed.report!!.checks.any { it.name.startsWith("第 2 次") && it.passed })
        assertEquals(raw, completed.displayRecords())
    }

    @Test
    fun `receiving exposes all records rather than only the final ten`() {
        val points = (1..95).map { CgmRecord.Ca(it, "10", "CA:$it,10") }
        val state = CgmReadState.Receiving(session, points, 0)
        assertEquals(95, state.displayRecords().size)
        assertEquals(95, summarizeRecords(state.displayRecords()).caPoints)
    }
}
