package com.biosensor.scanner.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * 盖在预览上画码框的一层。框坐标已经是视图坐标([CoordinateMapper.mapToView] 算完的)。
 *
 * 用 View 而不是往预览上叠一堆子 View:一屏最多几枚码,每帧重画几个圆角矩形比重建视图便宜得多,
 * 也不会有"框子视图跟不跟得上预览"的问题。
 */
internal class ScanBoxOverlayView(context: Context) : View(context) {

    private var boxes: List<BoxPx> = emptyList()
    private val rect = RectF()
    private val strokeWidth = 3f * resources.displayMetrics.density
    private val cornerRadius = 10f * resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 255, 255, 255)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = this@ScanBoxOverlayView.strokeWidth
        strokeJoin = Paint.Join.ROUND
    }

    fun setBoxes(newBoxes: List<BoxPx>) {
        if (newBoxes == boxes) return
        boxes = newBoxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) {
            rect.set(box.left, box.top, box.right, box.bottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fill)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, stroke)
        }
    }
}
