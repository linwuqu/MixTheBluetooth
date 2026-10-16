package com.biosensor.migratedev.ui.cgm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.translation.cgm.CGM_RANGE_HIGH
import com.biosensor.migratedev.translation.cgm.CGM_RANGE_LOW
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 一条单元的预测曲线:目标区间底纹 + 折线 + 实测点,点一下看某点的值。
 *
 * 刻度文字直接画进画布——只有几张标签,再拆一层布局去对齐反而更绕。
 * 横轴按点序均分:后端给的点本来就是等间隔采样的,按时间画反而会被采集中断拉出假空隙。
 *
 * 点选的回读放在画布外的 Text 里,不画进画布:那样不用在画布里排版,也不会让图跳高。
 */
@Composable
fun CgmGlucoseChart(points: List<ResultPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text(
            "该单元没有曲线点",
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val valueStyle = TextStyle(
        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val textMeasurer = rememberTextMeasurer()
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val actualColor = MaterialTheme.colorScheme.secondary
    val markerColor = MaterialTheme.colorScheme.tertiary

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        selected = indexAt(offset.x, plotRect(size.toSize()), points.size)
                    }
                }
        ) {
            val plot = plotRect(size)
            if (plot.width <= 0f || plot.height <= 0f) return@Canvas

            val dataMin = points.minOf { it.predicted }
            val dataMax = points.maxOf { it.predicted }
            // 目标区间始终留在视野里,否则"全都在范围内"和"全都很高"看起来一模一样
            val yMin = min(dataMin, CGM_RANGE_LOW) - Y_MARGIN
            val yMax = max(dataMax, CGM_RANGE_HIGH) + Y_MARGIN
            val ySpan = (yMax - yMin).toFloat()
            fun yOf(value: Double): Float =
                plot.bottom - plot.height * ((value - yMin) / ySpan).toFloat()

            // 目标区间底纹
            val bandTop = yOf(CGM_RANGE_HIGH)
            val bandBottom = yOf(CGM_RANGE_LOW)
            drawRect(
                color = bandColor,
                topLeft = Offset(plot.left, bandTop),
                size = Size(plot.width, bandBottom - bandTop)
            )

            // 横向刻度:上界 / 下界 / 目标区间两条边
            for (value in listOf(yMax, CGM_RANGE_HIGH, CGM_RANGE_LOW, yMin)) {
                val y = yOf(value)
                drawLine(
                    color = gridColor,
                    start = Offset(plot.left, y),
                    end = Offset(plot.right, y),
                    strokeWidth = 1f
                )
                val label = textMeasurer.measure(formatValue(value), valueStyle)
                drawText(
                    label,
                    topLeft = Offset(
                        plot.left - label.size.width - 4.dp.toPx(),
                        y - label.size.height / 2f
                    )
                )
            }

            // 预测折线
            val line = Path()
            points.forEachIndexed { index, point ->
                val x = xOf(index, plot, points.size)
                val y = yOf(point.predicted)
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            drawPath(line, color = lineColor, style = Stroke(width = 2.dp.toPx()))

            // 实测点:后端给了才画
            points.forEachIndexed { index, point ->
                val actual = point.actual ?: return@forEachIndexed
                drawCircle(
                    color = actualColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xOf(index, plot, points.size), yOf(actual))
                )
            }

            // 选中点:一条竖线指出位置,再给折线上的点套个圈
            selected?.let { index ->
                val point = points.getOrNull(index) ?: return@let
                val x = xOf(index, plot, points.size)
                drawLine(
                    color = markerColor,
                    start = Offset(x, plot.top),
                    end = Offset(x, plot.bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = markerColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, yOf(point.predicted)),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 横轴首末时刻:只标两端,中间靠折线和点选说话
            val first = points.first().rawTime
            val last = points.last().rawTime
            if (first != null && last != null) {
                val bottom = plot.bottom + 4.dp.toPx()
                drawText(textMeasurer.measure(first, valueStyle), topLeft = Offset(plot.left, bottom))
                val lastLabel = textMeasurer.measure(last, valueStyle)
                drawText(lastLabel, topLeft = Offset(plot.right - lastLabel.size.width, bottom))
            }
        }
        Spacer(Modifier.height(8.dp))
        PointReadout(selected?.let(points::getOrNull))
    }
}

@Composable
private fun PointReadout(point: ResultPoint?) {
    val text = if (point == null) {
        "点击曲线可查看某点的值"
    } else {
        val actual = point.actual?.let { " · 实测 ${formatValue(it)}" }.orEmpty()
        "${point.rawTime ?: "第 ${point.index} 点"} · 预测 ${formatValue(point.predicted)}$actual mmol/L"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 画布尺寸 → 绘图区矩形。画和点选都用它,免得两边各算一遍还慢慢走偏。 */
private fun Density.plotRect(canvas: Size): Rect = Rect(
    left = LABEL_WIDTH.toPx(),
    top = TOP_PADDING.toPx(),
    right = canvas.width - END_PADDING.toPx(),
    bottom = canvas.height - LABEL_HEIGHT.toPx()
)

private fun xOf(index: Int, plot: Rect, count: Int): Float =
    if (count <= 1) plot.left else plot.left + plot.width / (count - 1) * index

/** 点在哪就取最近的点;点在绘图区外视为取消选择。 */
private fun indexAt(x: Float, plot: Rect, count: Int): Int? {
    if (count <= 0) return null
    if (count == 1) return if (x in plot.left..plot.right) 0 else null
    if (x < plot.left || x > plot.right) return null
    val step = plot.width / (count - 1)
    return ((x - plot.left) / step).roundToInt().coerceIn(0, count - 1)
}

private fun IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())

private fun formatValue(value: Double): String = String.format(Locale.US, "%.1f", value)

private val CHART_HEIGHT = 200.dp
private val LABEL_WIDTH = 34.dp
private val LABEL_HEIGHT = 16.dp
private val TOP_PADDING = 8.dp
private val END_PADDING = 8.dp

/** 上下各留一点:最低点贴边会被裁掉半个圆。 */
private const val Y_MARGIN = 0.5
