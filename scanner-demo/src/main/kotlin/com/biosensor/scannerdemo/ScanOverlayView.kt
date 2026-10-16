package com.biosensor.scannerdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

internal class ScanOverlayView(context: Context) : View(context) {
    private val shadePaint = Paint().apply { color = 0x66000000 }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 215, 163)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val horizontalMargin = width * 0.10f
        val frameWidth = width - horizontalMargin * 2
        val frameHeight = frameWidth * 0.68f
        val top = (height - frameHeight) * 0.42f
        val frame = RectF(horizontalMargin, top, width - horizontalMargin, top + frameHeight)

        canvas.drawRect(0f, 0f, width.toFloat(), frame.top, shadePaint)
        canvas.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), shadePaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, shadePaint)
        canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, shadePaint)
        canvas.drawRoundRect(frame, 18f, 18f, framePaint)
    }
}
