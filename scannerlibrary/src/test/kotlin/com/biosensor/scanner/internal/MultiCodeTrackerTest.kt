package com.biosensor.scanner.internal

import com.biosensor.scanner.api.BarcodeFormat
import com.biosensor.scanner.api.ScanBounds
import com.biosensor.scanner.api.ScanContentType
import com.biosensor.scanner.api.ScanPoint
import com.biosensor.scanner.api.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiCodeTrackerTest {
    private var now = 1_000L
    private val tracker = MultiCodeTracker { now }

    @Test
    fun `a lone code gets a box first and is only confirmed once it holds steady`() {
        assertTrue(feed("A").boxes.isEmpty())

        val second = feed("A")
        assertEquals(listOf("A"), second.boxes.map { it.result.text })
        assertNull("上框比自动确认早,这一帧还没到自动确认的火候", second.autoConfirmed)

        var auto: ScanResult? = null
        repeat(8) { auto = feed("A").autoConfirmed ?: auto }
        assertEquals("A", auto?.text)
    }

    @Test
    fun `two codes are boxed together and neither is auto confirmed`() {
        feed("A", "B")
        var outcome = feed("A", "B")
        repeat(20) { outcome = feed("A", "B") }

        assertEquals(listOf("A", "B"), outcome.boxes.map { it.result.text })
        assertNull(outcome.autoConfirmed)
    }

    @Test
    fun `a second code arriving a few frames late still blocks the automatic bind`() {
        // 检测器找第二枚码往往慢一两帧。这几帧里 A 已经上框、且是"孤单"的,
        // 若自动确认的门槛和上框一样低,这里就会抢跑绑上 A —— 用户根本没机会选。
        feed("A")
        feed("A")

        // 盯着"第二枚刚露头"的这一帧看:此刻 A 已上框且仍是孤单的,
        // 若自动确认门槛降到和上框一样,抢跑就发生在这里 —— 只看最终帧是看不出来的。
        assertNull("第二枚码刚露头,不能抢跑", feed("A", "B").autoConfirmed)

        var outcome = feed("A", "B")
        repeat(20) { outcome = feed("A", "B") }

        assertEquals(listOf("A", "B"), outcome.boxes.map { it.result.text })
        assertNull(outcome.autoConfirmed)
    }

    @Test
    fun `a boxed code survives a single missed frame and drops on the second`() {
        feed("A")
        assertEquals(1, feed("A").boxes.size)

        assertEquals("漏一帧不该让框闪", 1, feed().boxes.size)
        assertTrue("连漏两帧才算走了", feed().boxes.isEmpty())
    }

    @Test
    fun `a code seen in a single frame never gets a box`() {
        feed("A")
        feed("B")
        val outcome = feed("B")

        assertEquals(listOf("B"), outcome.boxes.map { it.result.text })
        assertTrue("A 只露了一帧,不该在框里", outcome.boxes.none { it.result.text == "A" })
    }

    @Test
    fun `a lone code left over from a multi code scene still waits for a tap`() {
        feed("A", "B")
        feed("A", "B")

        var outcome = feed("A")
        repeat(30) { outcome = feed("A") }

        assertEquals(listOf("A"), outcome.boxes.map { it.result.text })
        assertNull("多码出现过,剩下这一枚也得点", outcome.autoConfirmed)
    }

    @Test
    fun `clearing the scene re-arms automatic confirmation`() {
        feed("A", "B")
        feed("A", "B")
        repeat(8) { feed() }

        var auto: ScanResult? = null
        repeat(12) { auto = feed("A").autoConfirmed ?: auto }
        assertEquals("A", auto?.text)
    }

    @Test
    fun `tapping returns exactly the tapped code`() {
        feed("A", "B")
        feed("A", "B")
        val boxes = feed("A", "B").boxes

        val a = boxes.single { it.result.text == "A" }
        val b = boxes.single { it.result.text == "B" }
        assertEquals("A", tracker.select(a.key)?.text)
        assertEquals("B", tracker.select(b.key)?.text)
    }

    @Test
    fun `tapping returns the newest decode of that code`() {
        feedAt("A", 0)
        feedAt("A", 0)

        val box = feedAt("A", 7).boxes.single()
        assertEquals(7, tracker.select(box.key)?.bounds?.topLeft?.x)
    }

    @Test
    fun `tapping a code that has not been confirmed yet does nothing`() {
        val outcome = feed("A")

        assertTrue(outcome.boxes.isEmpty())
        assertNull(tracker.select(ResultKey.from(result("A"))))
    }

    @Test
    fun `a lone code confirms again only after the cooldown`() {
        var auto: ScanResult? = null
        repeat(10) { auto = feed("A").autoConfirmed ?: auto }
        assertEquals("A", auto?.text)

        repeat(5) { assertNull(feed("A").autoConfirmed) }

        now += 1_500
        assertEquals("A", feed("A").autoConfirmed?.text)
    }

    @Test
    fun `reset forgets boxes and the multi code latch`() {
        feed("A", "B")
        feed("A", "B")
        tracker.reset()

        val outcome = feed("A")
        assertTrue("复位后重新从第一帧开始攒", outcome.boxes.isEmpty())
        assertNull(outcome.autoConfirmed)
    }

    private fun feed(vararg texts: String): MultiCodeTracker.Outcome =
        tracker.accept(texts.map { result(it) }, CONFIRMATION_FRAMES, COOLDOWN)

    private fun feedAt(text: String, topLeftX: Int): MultiCodeTracker.Outcome =
        tracker.accept(listOf(result(text, topLeftX)), CONFIRMATION_FRAMES, COOLDOWN)

    private fun result(text: String, topLeftX: Int = 0) = ScanResult(
        text = text,
        rawBytes = text.encodeToByteArray(),
        format = BarcodeFormat.DATA_MATRIX,
        contentType = ScanContentType.TEXT,
        bounds = ScanBounds(
            ScanPoint(topLeftX, 0),
            ScanPoint(topLeftX + 10, 0),
            ScanPoint(topLeftX + 10, 10),
            ScanPoint(topLeftX, 10),
        ),
        rotationDegrees = 0,
        errorCorrectionLevel = null,
        symbologyIdentifier = null,
        decodeTimeMillis = 10,
        timestampMillis = now,
    )

    private companion object {
        const val CONFIRMATION_FRAMES = 2
        const val COOLDOWN = 1_500L
    }
}
