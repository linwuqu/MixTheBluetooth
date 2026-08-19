package com.biosensor.migratedev.decisioncore.cgm

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.biosensor.migratedev.orchestrator.cgm.SequentialCgmParsePipeline

class CacheValidatorsTest {

    // ── ① 边界规整 ──

    @Test
    fun `trimmer keeps first START to first END and drops surrounding stray`() {
        val stray = CgmRecord.Eis(2, 70, "-809339.11", "-329705.55", "873919.65", "-157.84",
            "EIS:2,70,-809339.11,-329705.55,873919.65,-157.84")
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val eis1 = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")
        val secondStart = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val secondEnd = CgmRecord.Marker(MarkerKind.END, "Playback all done")
        val interPushStray = CgmRecord.Eis(1, 60, "x", "y", "z", "w", "EIS:1,60,x,y,z,w")  // 推间杂散

        val trimmed = CacheBoundaryTrimmer.trim(
            listOf(stray, start, eis1, end, secondStart, secondEnd, interPushStray)
        )
        // 校验输入 = 首份推送(首个 START..首个 END);后续推送与推间杂散在 Completed 后被幂等忽略
        assertEquals(listOf(start, eis1, end), trimmed)
    }

    @Test
    fun `trimmer returns empty when markers missing or unordered`() {
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val eis1 = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")

        assertEquals(emptyList<CgmRecord>(), CacheBoundaryTrimmer.trim(listOf(eis1, end)))
        assertEquals(emptyList<CgmRecord>(), CacheBoundaryTrimmer.trim(listOf(start, eis1)))
        // END 在 START 前:无有效区间
        assertEquals(emptyList<CgmRecord>(), CacheBoundaryTrimmer.trim(listOf(end, eis1, start)))
        assertEquals(emptyList<CgmRecord>(), CacheBoundaryTrimmer.trim(emptyList()))
    }

    @Test
    fun `trimmer keeps first occurrence in repeated pushes as state machine does`() {
        // 状态机首个 END 即触发校验:trim 输出 = 首份推送(重复推送会被 Completed 后的幂等忽略)
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val eis1 = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")
        val records = listOf(start, eis1, end) + listOf(start, eis1, end)   // 两遍完整推送

        assertEquals(listOf(start, eis1, end), CacheBoundaryTrimmer.trim(records))
    }

    // ── ② marker 校验 ──

    @Test
    fun `marker validator requires START then END`() {
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")
        val eis1 = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")

        assertTrue(CacheMarkerValidator.validate(listOf(start, eis1, end)).ok)
        assertFalse(CacheMarkerValidator.validate(listOf(eis1, end)).ok)
        assertFalse(CacheMarkerValidator.validate(listOf(start, eis1)).ok)
        assertFalse(CacheMarkerValidator.validate(listOf(end, eis1, start)).ok)
        assertFalse(CacheMarkerValidator.validate(emptyList()).ok)
    }

    // ── ③ 去重 ──

    @Test
    fun `deduper drops adjacent duplicates and markers`() {
        val eis = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val eisDup = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")  // 相邻重发(字节块重发)
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")

        val deduped = CacheRecordDeduper.dedupe(listOf(start, eis, eisDup, end))
        assertEquals(listOf(eis), deduped)   // marker 剔除,相邻重复收拢为一条
    }

    @Test
    fun `deduper keeps non-adjacent identical text across segments`() {
        // 实测特征:不同快照段中某点的值可能恰巧相同(EIS:18 同时出现在 LOG:1 与 LOG:3 段)
        // 不相邻的相同行是合法数据,不能被去重误删
        val eisA = CgmRecord.Eis(17, 220, "a", "b", "c", "d", "EIS:17,220,a,b,c,d")
        val shared = CgmRecord.Eis(18, 230, "x", "y", "z", "w", "EIS:18,230,x,y,z,w")
        val eisB = CgmRecord.Eis(19, 240, "m", "n", "o", "p", "EIS:19,240,m,n,o,p")

        val deduped = CacheRecordDeduper.dedupe(listOf(eisA, shared, eisB, shared))
        assertEquals(listOf(eisA, shared, eisB, shared), deduped)
    }

    // ── ④ 结构校验 ──

    @Test
    fun `structure validator accepts EIS and CA segments with declared counts`() {
        val records = buildString {
            appendLine("LOG:1,1,95,2026-06-23 14:31:27")
            for (i in 1..95) appendLine("EIS:$i,${60 + (i - 1) * 10},-1.0,2.0,3.0,4.0")
            appendLine("LOG:2,2,266,2026-06-23 14:32:02")
            for (i in 1..266) appendLine("CA:$i,15.5")
            appendLine("summarize:2026-06-23 14:32:02 15.16 11505196.18")
        }
        val parsed = parseLines(buildString {
            appendLine("Start Playback")
            append(records)
            appendLine("Playback all done")
        })

        val result = CacheStructureValidator.validate(parsed)
        assertTrue("结构应通过,实际: ${result.message}", result.ok)
    }

    @Test
    fun `structure validator rejects CA segment with wrong point count`() {
        val records = buildString {
            appendLine("LOG:1,2,266,2026-06-23 14:32:02")
            for (i in 1..200) appendLine("CA:$i,15.5")   // 声明 266,实际 200
        }
        val parsed = parseLines(buildString {
            appendLine("Start Playback")
            append(records)
            appendLine("Playback all done")
        })

        val result = CacheStructureValidator.validate(parsed)
        assertFalse(result.ok)
        assertNotNull(result.message)
    }

    @Test
    fun `structure validator rejects non-contiguous EIS seq`() {
        val records = buildString {
            appendLine("LOG:1,1,95,2026-06-23 14:31:27")
            appendLine("EIS:1,60,-1.0,2.0,3.0,4.0")
            appendLine("EIS:3,80,-1.0,2.0,3.0,4.0")   // 2 缺失
        }
        val parsed = parseLines(buildString {
            appendLine("Start Playback")
            append(records)
            appendLine("Playback all done")
        })

        val result = CacheStructureValidator.validate(parsed)
        assertFalse(result.ok)
        assertTrue("应提示 seq 不连续: ${result.message}", result.message!!.contains("seq"))
    }

    @Test
    fun `structure validator rejects data before any LOG declaration`() {
        val parsed = parseLines(buildString {
            appendLine("Start Playback")
            appendLine("EIS:1,60,-1.0,2.0,3.0,4.0")
            appendLine("Playback all done")
        })
        val result = CacheStructureValidator.validate(parsed)
        assertFalse(result.ok)
    }

    // ── ⑤ 链式校验(全流程) ──

    @Test
    fun `validate rejects when no markers present`() {
        val eis1 = CgmRecord.Eis(1, 60, "a", "b", "c", "d", "EIS:1,60,a,b,c,d")
        val conclusion = CacheValidators.validate(listOf(eis1))
        assertFalse(conclusion.complete)
        assertTrue(conclusion.reason!!.contains("边界"))
    }

    @Test
    fun `validate accepts empty playback after stray-first payload`() {
        // 模拟实测样本:首个 START 前的杂散行会先被规整剔除,规整后只剩 marker。
        // 空回放 = 缓存已消费后的合法状态(实测 2026-08-07 第二轮:Start Playback + Playback all done,零数据),
        // 判完成不判失败——否则空缓存读会触发 3 次无意义重传
        val stray = CgmRecord.Eis(2, 70, "-809339.11", "-329705.55", "873919.65", "-157.84",
            "EIS:2,70,-809339.11,-329705.55,873919.65,-157.84")
        val start = CgmRecord.Marker(MarkerKind.START, "Start Playback")
        val end = CgmRecord.Marker(MarkerKind.END, "Playback all done")

        val conclusion = CacheValidators.validate(listOf(stray, start, end))
        assertTrue(conclusion.complete)
        assertTrue(conclusion.records.isEmpty())
    }

    // ── ⑥ 真实样本集成(与解析管道对接) ──

    @Test
    fun `validators accept all three real samples end to end`() = runBlocking {
        val samples = listOf(
            "../docs/数据样例/2026-07-15CGM_Cache_data.txt",
            "../docs/数据样例/2026-07-20CGM_Cache_data.txt",
            "../docs/数据样例/2026-07-21CGM_Cache_data.txt"
        )
        for (sample in samples) {
            val file = File(sample)
            assertTrue("样本文件应存在: ${file.absolutePath}", file.exists())

            val bytes = file.readBytes()
            val pipeline = SequentialCgmParsePipeline()
            val records = mutableListOf<CgmRecord>()
            var offset = 0
            while (offset < bytes.size) {
                val chunk = bytes.copyOfRange(offset, minOf(offset + 100, bytes.size))
                records += pipeline.parse(chunk)
                offset += 100
            }

            // 完整链路:规整(样本首行杂散 EIS 先于 START)→ marker → 去重 → 结构
            val conclusion = CacheValidators.validate(records)
            assertTrue("样本 ${file.name} 应校验通过,实际: ${conclusion.reason}", conclusion.complete)
            assertTrue("结论记录应含 EIS", conclusion.records.any { it is CgmRecord.Eis })
            assertTrue("结论记录应含 CA", conclusion.records.any { it is CgmRecord.Ca })
            assertTrue("结论记录应无 marker(render 补齐边界)", conclusion.records.none { it is CgmRecord.Marker })
        }
    }

    @Test
    fun `real samples trim captures first push and dedupe keeps segment skeleton`() = runBlocking {
        val file = File("../docs/数据样例/2026-07-21CGM_Cache_data.txt")
        val bytes = file.readBytes()
        val pipeline = SequentialCgmParsePipeline()
        val records = mutableListOf<CgmRecord>()
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, minOf(offset + 100, bytes.size))
            records += pipeline.parse(chunk)
            offset += 100
        }

        // 状态机语义:首个 END 触发校验,trim 输出 = 首份推送(缓存 3 段: EIS95 + CA268 + EIS95)
        val bounded = CacheBoundaryTrimmer.trim(records)
        val dataRows = bounded.count { it is CgmRecord.Eis || it is CgmRecord.Ca }
        assertEquals("trim 应只含首份推送的完整缓存", 95 + 268 + 95, dataRows)

        val deduped = CacheRecordDeduper.dedupe(bounded)
        assertEquals("首份推送内无重复快照,去重后点数不变", dataRows,
            deduped.count { it is CgmRecord.Eis || it is CgmRecord.Ca })
    }

    // ── 工具 ──

    private fun parseLines(text: String): List<CgmRecord> = runBlocking {
        val pipeline = SequentialCgmParsePipeline()
        pipeline.parse(text.toByteArray(Charsets.ISO_8859_1))
    }
}
