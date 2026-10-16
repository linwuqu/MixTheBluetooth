package com.biosensor.scanner.internal

import com.biosensor.scanner.api.BarcodeFormat
import com.biosensor.scanner.api.ScanBounds
import com.biosensor.scanner.api.ScanContentType
import com.biosensor.scanner.api.ScanPoint
import com.biosensor.scanner.api.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultStabilizerTest {
    private var now = 1_000L
    private val stabilizer = ResultStabilizer { now }

    @Test
    fun `emits only after required consecutive frames`() {
        val result = result("ABC")

        assertNull(stabilizer.accept(result, requiredFrames = 2, duplicateCooldownMillis = 1_500))
        assertEquals(result, stabilizer.accept(result, requiredFrames = 2, duplicateCooldownMillis = 1_500))
    }

    @Test
    fun `different value restarts confirmation`() {
        assertNull(stabilizer.accept(result("A"), requiredFrames = 2, duplicateCooldownMillis = 1_500))
        assertNull(stabilizer.accept(result("B"), requiredFrames = 2, duplicateCooldownMillis = 1_500))
        assertEquals("B", stabilizer.accept(result("B"), 2, 1_500)?.text)
    }

    @Test
    fun `same value is suppressed during cooldown`() {
        val result = result("ABC")
        assertEquals(result, stabilizer.accept(result, requiredFrames = 1, duplicateCooldownMillis = 1_500))

        now += 1_000
        assertNull(stabilizer.accept(result, requiredFrames = 1, duplicateCooldownMillis = 1_500))

        now += 500
        assertEquals(result, stabilizer.accept(result, requiredFrames = 1, duplicateCooldownMillis = 1_500))
    }

    @Test
    fun `empty frame breaks a confirmation streak`() {
        val result = result("ABC")
        assertNull(stabilizer.accept(result, requiredFrames = 2, duplicateCooldownMillis = 0))
        assertNull(stabilizer.accept(null, requiredFrames = 2, duplicateCooldownMillis = 0))
        assertNull(stabilizer.accept(result, requiredFrames = 2, duplicateCooldownMillis = 0))
    }

    private fun result(text: String) = ScanResult(
        text = text,
        rawBytes = text.encodeToByteArray(),
        format = BarcodeFormat.QR_CODE,
        contentType = ScanContentType.TEXT,
        bounds = ScanBounds(
            ScanPoint(0, 0),
            ScanPoint(1, 0),
            ScanPoint(1, 1),
            ScanPoint(0, 1),
        ),
        rotationDegrees = 0,
        errorCorrectionLevel = null,
        symbologyIdentifier = null,
        decodeTimeMillis = 10,
        timestampMillis = now,
    )
}
