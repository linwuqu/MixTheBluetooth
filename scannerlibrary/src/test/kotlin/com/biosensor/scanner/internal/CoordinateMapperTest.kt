package com.biosensor.scanner.internal

import com.biosensor.scanner.api.ScanBounds
import com.biosensor.scanner.api.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateMapperTest {

    @Test
    fun `upright size swaps on a quarter turn`() {
        assertEquals(SizePx(1920, 1080), CoordinateMapper.uprightSize(1920, 1080, 0))
        assertEquals(SizePx(1080, 1920), CoordinateMapper.uprightSize(1920, 1080, 90))
        assertEquals(SizePx(1920, 1080), CoordinateMapper.uprightSize(1920, 1080, 180))
        assertEquals(SizePx(1080, 1920), CoordinateMapper.uprightSize(1920, 1080, 270))
    }

    @Test
    fun `rotation beyond one turn still normalises`() {
        assertEquals(SizePx(1080, 1920), CoordinateMapper.uprightSize(1920, 1080, 450))
        assertEquals(SizePx(1080, 1920), CoordinateMapper.uprightSize(1920, 1080, -90))
    }

    @Test
    fun `bounding box wraps a tilted code`() {
        val bounds = ScanBounds(
            topLeft = ScanPoint(10, 0),
            topRight = ScanPoint(20, 5),
            bottomRight = ScanPoint(10, 10),
            bottomLeft = ScanPoint(0, 5),
        )

        assertEquals(BoxPx(0f, 0f, 20f, 10f), CoordinateMapper.boundingBox(bounds))
    }

    @Test
    fun `same aspect ratio just scales`() {
        val mapped = CoordinateMapper.mapToView(
            rect = BoxPx(10f, 10f, 20f, 20f),
            source = SizePx(100, 100),
            view = SizePx(200, 200),
        )

        assertEquals(BoxPx(20f, 20f, 40f, 40f), mapped)
    }

    @Test
    fun `analysis and preview of different size just rescale`() {
        val mapped = CoordinateMapper.mapToView(
            rect = BoxPx(0f, 0f, 300f, 150f),
            source = SizePx(1920, 1080),
            view = SizePx(960, 540),
        )

        assertEquals(BoxPx(0f, 0f, 150f, 75f), mapped)
    }

    @Test
    fun `a wider view centre crops the overflow`() {
        // 100x100 的画铺进 200x100:放大 2 倍,上下各裁掉 50。
        val mapped = CoordinateMapper.mapToView(
            rect = BoxPx(0f, 0f, 10f, 10f),
            source = SizePx(100, 100),
            view = SizePx(200, 100),
        )

        assertEquals(BoxPx(0f, -50f, 20f, -30f), mapped)
    }

    @Test
    fun `a zero sized view maps to nothing instead of dividing by zero`() {
        assertEquals(
            BoxPx(0f, 0f, 0f, 0f),
            CoordinateMapper.mapToView(BoxPx(1f, 1f, 2f, 2f), SizePx(100, 100), SizePx(0, 0)),
        )
    }

    @Test
    fun `contains honours the touch slop`() {
        val box = BoxPx(10f, 10f, 20f, 20f)

        assertTrue(box.contains(15f, 15f))
        assertFalse("差得远就不算点中", box.contains(5f, 15f))
        assertTrue("差一点点算点中,手指没那么准", box.contains(5f, 15f, slop = 6f))
    }
}
