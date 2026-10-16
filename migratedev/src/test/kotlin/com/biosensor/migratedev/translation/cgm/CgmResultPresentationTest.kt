package com.biosensor.migratedev.translation.cgm

import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CgmResultPresentationTest {

    private fun unit(
        vararg points: ResultPoint, mard: Double = 0.1, claimedPointCount: Int = points.size
    ) = ResultUnit(
        unit = 1, title = "第一单元", pointCount = claimedPointCount, mard = mard,
        points = points.toList()
    )

    private fun point(
        index: Int, value: Double, rawTime: String? = null, time: Int = index * 10
    ) = ResultPoint(index = index, time = time, rawTime = rawTime, predicted = value)

    // ── 平均 ──

    @Test
    fun `mean averages the predicted values`() {
        val summary = unit(point(1, 4.0), point(2, 6.0), point(3, 8.0)).toSummary()

        assertEquals(6.0, summary.mean!!, 1e-9)
    }

    @Test
    fun `non finite values stay out of the mean`() {
        val summary = unit(point(1, 5.0), point(2, Double.NaN)).toSummary()

        assertEquals(5.0, summary.mean!!, 1e-9)
    }

    @Test
    fun `a unit without points has no mean`() {
        val summary = unit().toSummary()

        assertNull(summary.mean)
        assertEquals(0.0, summary.highRatio, 1e-9)
        assertEquals(0.0, summary.lowRatio, 1e-9)
        assertNull(summary.durationMillis)
    }

    // ── 高值 / 低值占比 ──

    @Test
    fun `high ratio counts points above the target range`() {
        // 4 个点里 1 个高于 7.8
        val summary = unit(
            point(1, 3.0), point(2, 6.0), point(3, 7.8), point(4, 9.0)
        ).toSummary()

        assertEquals(0.25, summary.highRatio, 1e-9)
        assertEquals(0.25, summary.lowRatio, 1e-9)
    }

    @Test
    fun `the target range boundaries count as in range`() {
        val summary = unit(point(1, CGM_RANGE_LOW), point(2, CGM_RANGE_HIGH)).toSummary()

        assertEquals(0.0, summary.highRatio, 1e-9)
        assertEquals(0.0, summary.lowRatio, 1e-9)
    }

    @Test
    fun `ratios are shares of the whole unit`() {
        val summary = unit(
            point(1, 2.0), point(2, 2.5), point(3, 5.0), point(4, 5.5)
        ).toSummary()

        assertEquals(0.5, summary.lowRatio, 1e-9)
        assertEquals(0.0, summary.highRatio, 1e-9)
    }

    // ── 时长 ──

    @Test
    fun `duration comes from the recorded times`() {
        val summary = unit(
            point(1, 5.0, rawTime = "2026-07-21 12:00:00"),
            point(2, 5.5, rawTime = "2026-07-21 14:30:00")
        ).toSummary()

        assertEquals(150L * 60 * 1000, summary.durationMillis)
        assertEquals("2026-07-21 12:00:00", summary.firstTime)
        assertEquals("2026-07-21 14:30:00", summary.lastTime)
    }

    @Test
    fun `duration crosses days`() {
        val summary = unit(
            point(1, 5.0, rawTime = "2026-07-21 23:30:00"),
            point(2, 5.5, rawTime = "2026-07-22 01:00:00")
        ).toSummary()

        assertEquals(90L * 60 * 1000, summary.durationMillis)
    }

    @Test
    fun `duration falls back to the relative seconds`() {
        // 没有时间字符串,只有相对秒数
        val summary = unit(point(1, 5.0, time = 60), point(2, 5.5, time = 360)).toSummary()

        assertEquals(300_000L, summary.durationMillis)
    }

    @Test
    fun `a single point has no duration`() {
        val summary = unit(point(1, 5.0, rawTime = "2026-07-21 12:00:00")).toSummary()

        assertNull(summary.durationMillis)
    }

    // ── MARD:只在它可能是个百分比时才显示 ──

    @Test
    fun `a plausible mard survives`() {
        // 旧 app 的解析测试用的就是这个量级
        assertEquals(16.3, unit(point(1, 5.0), mard = 16.3).toSummary().mard!!, 1e-9)
        assertEquals(0.23, unit(point(1, 5.0), mard = 0.23).toSummary().mard!!, 1e-9)
        assertEquals(100.0, unit(point(1, 5.0), mard = 100.0).toSummary().mard!!, 1e-9)
    }

    @Test
    fun `the bogus mard the backend actually sent is dropped`() {
        // 真机上拿到的原值:同一份数据里每个点的 actual 都是 0.0,多半是拿 actual 做分母除出来的
        val summary = unit(point(1, 5.0), mard = 6.5017617036301575E10).toSummary()

        assertNull(summary.mard)
    }

    @Test
    fun `out of range mard is dropped`() {
        assertNull("负数不可能是百分比", unit(point(1, 5.0), mard = -1.0).toSummary().mard)
        assertNull("超过 100% 等于比瞎猜还差一倍", unit(point(1, 5.0), mard = 100.1).toSummary().mard)
        assertNull("NaN", unit(point(1, 5.0), mard = Double.NaN).toSummary().mard)
        assertNull("无穷", unit(point(1, 5.0), mard = Double.POSITIVE_INFINITY).toSummary().mard)
        // 0 更像"没算"的哨兵值,不是"算得完美"
        assertNull(unit(point(1, 5.0), mard = 0.0).toSummary().mard)
    }

    // ── 点数:数画出来的,不信后端报的 ──

    @Test
    fun `point count comes from the points not the backend claim`() {
        // 真机上后端报 point_count=0,而 points 里有一整条曲线
        val summary = unit(point(1, 5.0), point(2, 6.0), claimedPointCount = 0).toSummary()

        assertEquals(2, summary.pointCount)
    }

    // ── 透传 ──

    @Test
    fun `unit identity passes through`() {
        val summary = unit(point(1, 5.0)).toSummary()

        assertEquals(1, summary.unit)
        assertEquals("第一单元", summary.title)
        assertEquals(1, summary.pointCount)
    }
}
