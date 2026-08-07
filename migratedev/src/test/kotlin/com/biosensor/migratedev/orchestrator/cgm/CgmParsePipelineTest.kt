package com.biosensor.migratedev.orchestrator.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmParsePipelineTest {

    // ── ① 解码:多字节字符被块切开 ──

    @Test
    fun `decoder joins a GBK character split across blocks`() {
        val decoder = SessionTextDecoder()
        // GBK 真实字节:你好 = C4E3 BAC3;切成 3+1:第一块末尾是 C3(半个字符)
        val gbk = "你好".toByteArray(Charset.forName("GBK"))
        val out1 = decoder.accept(gbk.copyOfRange(0, 3))
        val out2 = decoder.accept(gbk.copyOfRange(3, 4))

        assertTrue("first block should be accepted as Text", out1 is SessionTextDecoder.Output.Text)
        assertTrue("second block should be accepted as Text", out2 is SessionTextDecoder.Output.Text)
        val joined = (out1 as SessionTextDecoder.Output.Text).text +
            (out2 as SessionTextDecoder.Output.Text).text
        assertEquals("你好", joined)
    }

    @Test
    fun `decoder never silently replaces malformed bytes with replacement char`() {
        val decoder = SessionTextDecoder()
        // REPORT 语义:非法序列(0x81+0x20)被跳过而非替换为 U+FFFD,其余字节正常解码,不阻塞后续数据。
        val out1 = decoder.accept(byteArrayOf(0x81.toByte(), 0x20, 0x41))
        assertTrue("should still produce Text", out1 is SessionTextDecoder.Output.Text)
        val text1 = (out1 as SessionTextDecoder.Output.Text).text
        assertTrue(
            "REPORT 模式不得出现替换字符,实际: ${text1.map { it.code }}",
            !text1.contains('�')
        )
        assertEquals("非法首字节跳过,其余正常解码", " A", text1)

        val out2 = decoder.accept(byteArrayOf(0x42))
        assertTrue(out2 is SessionTextDecoder.Output.Text)
        assertEquals("B", (out2 as SessionTextDecoder.Output.Text).text)
    }

    @Test
    fun `decoder strips null characters`() {
        val decoder = SessionTextDecoder()
        val text = "EIS:1,60,-1.0,2.0,3.0,4.0" + Char(0) + "\n"
        val out = decoder.accept(text.toByteArray(Charsets.ISO_8859_1))
        assertTrue(out is SessionTextDecoder.Output.Text)
        assertEquals("EIS:1,60,-1.0,2.0,3.0,4.0\n", (out as SessionTextDecoder.Output.Text).text)
    }

    // ── ② 切行:行被块切开 ──

    @Test
    fun `assembler joins a line split across blocks`() {
        val assembler = LineAssembler()
        assertEquals(emptyList<String>(), assembler.accept("EIS:1,60,-1.0,"))
        assertEquals(listOf("EIS:1,60,-1.0,2.0,3.0,4.0"), assembler.accept("2.0,3.0,4.0\n"))
    }

    @Test
    fun `assembler trims CR and drops empty boundary lines`() {
        val assembler = LineAssembler()
        val lines = assembler.accept("Start Playback\r\n\r\nEIS:1,60,-1.0,2.0,3.0,4.0\n")
        assertEquals(listOf("Start Playback", "EIS:1,60,-1.0,2.0,3.0,4.0"), lines)
    }

    @Test
    fun `assembler keeps tail without newline`() {
        val assembler = LineAssembler()
        assertEquals(emptyList<String>(), assembler.accept("Playback all done"))
        assertEquals(listOf("Playback all done"), assembler.accept("\n"))
    }

    // ── ③ 记录解析:行 → 结构化记录 ──

    @Test
    fun `parser recognizes all record kinds`() {
        val parser = RecordParser()

        assertEquals(
            CgmRecord.Marker(MarkerKind.START, "Start Playback"),
            parser.parse("Start Playback")
        )
        assertEquals(
            CgmRecord.Marker(MarkerKind.END, "Playback all done"),
            parser.parse("Playback all done")
        )
        assertEquals(
            CgmRecord.Eis(2, 70, "-809339.11", "-329705.55", "873919.65", "-157.84",
                "EIS:2,70,-809339.11,-329705.55,873919.65,-157.84"),
            parser.parse("EIS:2,70,-809339.11,-329705.55,873919.65,-157.84")
        )
        assertEquals(
            CgmRecord.Ca(1, "19.632", "CA:1,19.632"),
            parser.parse("CA:1,19.632")
        )
        assertEquals(
            CgmRecord.Log("LOG:1,1,95,2026-06-23 14:31:27"),
            parser.parse("LOG:1,1,95,2026-06-23 14:31:27")
        )
        assertEquals(
            CgmRecord.Summarize("summarize:2026-07-15 16:15:46 -0.01 42949672.95"),
            parser.parse("summarize:2026-07-15 16:15:46 -0.01 42949672.95")
        )
    }

    @Test
    fun `parser drops unknown lines like RI`() {
        val parser = RecordParser()
        assertEquals(null, parser.parse("RI:0min34s"))
        assertEquals(null, parser.parse(""))
        assertEquals(null, parser.parse("EIS 没有冒号分隔"))
    }

    // ── 管道:分块喂入与真实样本 ──

    @Test
    fun `pipeline assembles records from split blocks`() = runBlocking {
        val pipeline = SequentialCgmParsePipeline()
        val bytes = buildString {
            append("Start Playback\n")
            append("EIS:1,60,-709.59,-2502325.89,2502325.99,-90.02\n")
            append("EIS:2,70,-833716.79,-396757.05,923309.18,-154.55\n")
            append("Playback all done\n")
        }.toByteArray(Charsets.ISO_8859_1)

        // 每个字节一块:极端分块,验证解码/切行/解析全程正确
        val records = mutableListOf<CgmRecord>()
        for (byte in bytes) {
            records += pipeline.parse(byteArrayOf(byte))
        }
        assertEquals(4, records.size)
        assertEquals(CgmRecord.Marker(MarkerKind.START, "Start Playback"), records[0])
        assertTrue(records[1] is CgmRecord.Eis)
        assertTrue(records[2] is CgmRecord.Eis)
        assertEquals(CgmRecord.Marker(MarkerKind.END, "Playback all done"), records[3])
    }

    @Test
    fun `pipeline parses real sample file in GBK`() = runBlocking {
        val sample = File("../docs/数据样例/2026-07-15CGM_Cache_data.txt")
        assertTrue("样本文件应存在: ${sample.absolutePath}", sample.exists())

        val bytes = sample.readBytes()
        val pipeline = SequentialCgmParsePipeline()
        val records = mutableListOf<CgmRecord>()
        // 模拟蓝牙分块:按 100 字节一块(旧库缓冲/静默合块的粒度)
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, minOf(offset + 100, bytes.size))
            records += pipeline.parse(chunk)
            offset += 100
        }

        assertTrue("应产出记录(而非全被丢弃),实际 ${records.size}", records.isNotEmpty())
        val markers = records.filterIsInstance<CgmRecord.Marker>()
        assertTrue("应含 START 与 END marker", markers.any { it.kind == MarkerKind.START })
        assertTrue("应含 END marker", markers.any { it.kind == MarkerKind.END })
        assertTrue("应含 EIS 记录", records.any { it is CgmRecord.Eis })
        assertTrue("应含 CA 记录", records.any { it is CgmRecord.Ca })
        assertTrue("应含 Summarize", records.any { it is CgmRecord.Summarize })
    }

    private fun String.encodeGbk(): ByteArray = toByteArray(Charset.forName("GBK"))
}
