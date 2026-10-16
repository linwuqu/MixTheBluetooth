package com.biosensor.migratedev.decisioncore.patch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 贴片码解析:认得出的照收,认不出的说得出为什么。 */
class PatchCodeTest {

    private val sampleA = "CGPATCH/1|patchId=550e8400-e29b-41d4-a716-446655440000" +
        "|model=1|lot=DEMO-260701|mfg=2026-07-01|exp=2027-07-01" +
        "|maxUseMin=120|calRef=DEMO-CAL"

    private fun ok(text: String): PatchCode =
        (parsePatchCode(text) as PatchCodeResult.Ok).code

    private fun rejected(text: String?): String =
        (parsePatchCode(text) as PatchCodeResult.Rejected).reason

    @Test
    fun `parses all fields of a well formed code`() {
        val code = ok(sampleA)

        assertEquals(1, code.schemaVersion)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", code.patchId)
        assertEquals(1, code.modelId)
        assertEquals("DEMO-260701", code.lotNo)
        assertEquals("2026-07-01", code.manufacturedOn)
        assertEquals("2027-07-01", code.expiresOn)
        assertEquals(120, code.maxUseMinutes)
        assertEquals("DEMO-CAL", code.calibrationRef)
        assertEquals(sampleA, code.raw)
    }

    @Test
    fun `field order does not matter`() {
        val shuffled = "CGPATCH/1|calRef=DEMO-CAL|maxUseMin=120|lot=DEMO-260701" +
            "|patchId=550e8400-e29b-41d4-a716-446655440000|exp=2027-07-01" +
            "|model=1|mfg=2026-07-01"

        val code = ok(shuffled)

        assertEquals("550e8400-e29b-41d4-a716-446655440000", code.patchId)
        assertEquals(120, code.maxUseMinutes)
        assertEquals("DEMO-CAL", code.calibrationRef)
    }

    @Test
    fun `unknown extension fields stay in raw`() {
        val withExtension = "$sampleA|futureField=xyz"

        val code = ok(withExtension)

        assertEquals("550e8400-e29b-41d4-a716-446655440000", code.patchId)
        assertTrue("扩展字段要留在原文里,不能丢", code.raw.contains("futureField=xyz"))
    }

    @Test
    fun `optional fields absent become null not empty`() {
        val minimal = "CGPATCH/1|patchId=550e8400"

        val code = ok(minimal)

        assertEquals("550e8400", code.patchId)
        assertNull(code.modelId)
        assertNull(code.lotNo)
        assertNull(code.manufacturedOn)
        assertNull(code.expiresOn)
        assertNull(code.maxUseMinutes)
        assertNull(code.calibrationRef)
    }

    @Test
    fun `empty optional value counts as absent`() {
        val code = ok("CGPATCH/1|patchId=550e8400|lot=")

        assertNull("空串与没给是一回事,界面上不该出现空的批号", code.lotNo)
    }

    @Test
    fun `missing patchId is rejected`() {
        assertEquals("贴片码里没有编号", rejected("CGPATCH/1|model=1|lot=DEMO-260701"))
    }

    @Test
    fun `unknown version is rejected with the version in the reason`() {
        val reason = rejected("CGPATCH/2|patchId=550e8400")

        assertTrue(reason, reason.contains("2"))
        assertTrue(reason, reason.contains("暂不支持"))
    }

    @Test
    fun `other barcodes are rejected as not a patch code`() {
        assertEquals("这不是贴片码", rejected("https://example.com/whatever"))
        assertEquals("这不是贴片码", rejected("PRODUCT-12345"))
    }

    @Test
    fun `blank input is rejected`() {
        assertEquals("没有扫到内容", rejected(null))
        assertEquals("没有扫到内容", rejected(""))
        assertEquals("没有扫到内容", rejected("   "))
    }

    @Test
    fun `malformed number is rejected rather than silently dropped`() {
        assertEquals("贴片型号不是数字", rejected("CGPATCH/1|patchId=550e8400|model=abc"))
        assertEquals(
            "可用时长不是数字",
            rejected("CGPATCH/1|patchId=550e8400|maxUseMin=两小时")
        )
    }

    @Test
    fun `segments without a separator are ignored`() {
        val code = ok("CGPATCH/1|garbage|patchId=550e8400|lot=DEMO-260701")

        assertEquals("550e8400", code.patchId)
        assertEquals("DEMO-260701", code.lotNo)
    }

    @Test
    fun `falls back to raw bytes when the decoder gives no text`() {
        val text = patchCodeTextFrom(null, sampleA.toByteArray(Charsets.UTF_8))

        assertEquals(sampleA, text)
        assertEquals(1, ok(requireNotNull(text)).schemaVersion)
    }

    @Test
    fun `text wins over raw bytes when both are present`() {
        val text = patchCodeTextFrom(sampleA, byteArrayOf(0x01, 0x02))

        assertEquals(sampleA, text)
    }
}
