package com.biosensor.migratedev.translation.cgm

import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 目标区间(mmol/L),与旧 app 曲线分段一致:低于它是低值,高于它是高值。 */
const val CGM_RANGE_LOW = 3.9
const val CGM_RANGE_HIGH = 7.8

/**
 * MARD(平均绝对相对误差)是个百分比,落在 (0, 100] 里才可能是真的。
 *
 * 上界:平均相对误差超过 100% 等于说"预测得比瞎猜还差一倍",不成立。
 * 下界取开区间:相对误差的平均恰好为 0 基本不可能,更像是"没算"的哨兵值。
 *
 * 实测后端给过 6.5017617036301575E10——同一份数据里每个点的 `actual` 都是 0.0,
 * 多半是拿 actual 做分母除出来的。照原样显示就是"65017617036.3",还不如不显示。
 */
private const val MARD_MIN = 0.0
private const val MARD_MAX = 100.0

/**
 * 一条曲线的判读数字。
 *
 * 全部由预测点算出,不看信息接口——那个接口拉不到时曲线还在,数字就还得有。
 * MARD 是后端给的,直接透传。
 */
data class CgmUnitSummary(
    val unit: Int,
    val title: String?,
    /** 这条曲线实际有几个点。不取后端的 point_count——实测它给过 0,而点画了一屏。 */
    val pointCount: Int,
    /** MARD 百分比;后端给了不可能的值时为 null(见 [MARD_MIN] / [MARD_MAX])。 */
    val mard: Double?,
    /** 预测值的平均;一个有效点都没有时为 null。 */
    val mean: Double?,
    /** 高于目标区间的点数占比,0..1。 */
    val highRatio: Double,
    /** 低于目标区间的点数占比,0..1。 */
    val lowRatio: Double,
    /** 首末点的时间跨度;时间拿不到时为 null。 */
    val durationMillis: Long?,
    val firstTime: String?,
    val lastTime: String?
)

internal fun ResultUnit.toSummary(): CgmUnitSummary {
    // 后端理论上不给 NaN,但真给了会让平均值和占比一起变成 NaN,不如先挡掉
    val values = points.map { it.predicted }.filter { it.isFinite() }
    return CgmUnitSummary(
        unit = unit,
        title = title,
        pointCount = points.size,
        mard = mard.takeIf { it.isFinite() && it > MARD_MIN && it <= MARD_MAX },
        mean = if (values.isEmpty()) null else values.average(),
        highRatio = values.ratio { it > CGM_RANGE_HIGH },
        lowRatio = values.ratio { it < CGM_RANGE_LOW },
        durationMillis = durationMillis(),
        firstTime = points.firstOrNull()?.rawTime,
        lastTime = points.lastOrNull()?.rawTime
    )
}

private fun List<Double>.ratio(predicate: (Double) -> Boolean): Double =
    if (isEmpty()) 0.0 else count(predicate).toDouble() / size

/**
 * 跨度优先按 rawTime 算:那是设备真实记录的时刻,跨天也算得对。
 * rawTime 缺失时退回相对秒数——它只是个偏移,凑不出绝对时刻,但跨度还能用。
 */
private fun ResultUnit.durationMillis(): Long? {
    val first = points.firstOrNull() ?: return null
    val last = points.lastOrNull() ?: return null
    val start = parseRawTime(first.rawTime)
    val end = parseRawTime(last.rawTime)
    if (start != null && end != null) {
        return Duration.between(start, end).toMillis().takeIf { it > 0 }
    }
    return (last.time - first.time).toLong().times(1000L).takeIf { it > 0 }
}

private val RAW_TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
)

private fun parseRawTime(text: String?): LocalDateTime? {
    if (text.isNullOrBlank()) return null
    for (format in RAW_TIME_FORMATS) {
        try {
            return LocalDateTime.parse(text, format)
        } catch (_: DateTimeParseException) {
            // 换下一个格式再试
        }
    }
    return null
}
