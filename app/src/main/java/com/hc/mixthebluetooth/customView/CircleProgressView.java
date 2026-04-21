package com.hc.mixthebluetooth.customView;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hc.mixthebluetooth.R;

public class CircleProgressView extends View {
    private float progress = 0.7f; // 0~1
    private int progressColor = Color.parseColor("#18B071");
    private int backgroundColor = Color.parseColor("#E6F7F1");
    private Drawable arrowDrawable;
    private String valueText = "9.3";
    private String unitText = "mmol/L";
    private float unitTextSize = 12f; // 添加单位文字大小属性，默认12sp
    private Paint bgPaint, progressPaint, textPaint, unitPaint;
    private RectF oval = new RectF();

    public CircleProgressView(Context context) {
        this(context, null);
    }
    public CircleProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public CircleProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressView);
        progress = a.getFloat(R.styleable.CircleProgressView_progress, progress);
        progressColor = a.getColor(R.styleable.CircleProgressView_progressColor, progressColor);
        backgroundColor = a.getColor(R.styleable.CircleProgressView_backgroundColor, backgroundColor);
        valueText = a.getString(R.styleable.CircleProgressView_valueText);
        unitText = a.getString(R.styleable.CircleProgressView_unitText);
        unitTextSize = a.getDimension(R.styleable.CircleProgressView_unitTextSize, 12f); // 获取单位文字大小
        arrowDrawable = a.getDrawable(R.styleable.CircleProgressView_arrowDrawable);
        a.recycle();
        init();
    }
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(14f);
        bgPaint.setColor(backgroundColor);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(14f);
        progressPaint.setColor(progressColor);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#222222"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(54f);
        unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitPaint.setColor(Color.parseColor("#888888"));
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTextSize(26f);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = Math.min(w, h) / 2f - 18f;
        float cx = w / 2f;
        float cy = h / 2f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // 背景圆环
        canvas.drawArc(oval, 135, 270, false, bgPaint);
        // 进度圆环
        canvas.drawArc(oval, 135, 270 * progress, false, progressPaint);
        // 箭头
        if (arrowDrawable != null) {
            int arrowSize = (int) (radius * 0.5f);
            int left = (int) (cx - arrowSize / 2f);
            int top = (int) (cy - radius * 0.7f - arrowSize / 2f);
            int right = left + arrowSize;
            int bottom = top + arrowSize;
            arrowDrawable.setBounds(left, top, right, bottom);
            arrowDrawable.draw(canvas);
        }
        // 数值
        if (valueText != null) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2 - 18f;
            canvas.drawText(valueText, cx, textY, textPaint);
        }
        // 单位
        if (unitText != null && !unitText.isEmpty()) {
            unitPaint.setTextSize(unitTextSize); // 使用设置的文字大小
            float unitY = cy - (unitPaint.ascent() + unitPaint.descent()) / 2 + 38f;
            canvas.drawText(unitText, cx, unitY, unitPaint);
        }
    }
    // 公开方法
    public void setProgress(float p) { this.progress = p; invalidate(); }
    public void setValueText(String t) { this.valueText = t; invalidate(); }
    public void setUnitText(String t) { this.unitText = t; invalidate(); }
    public void setArrowDrawable(Drawable d) { this.arrowDrawable = d; invalidate(); }
    public void setProgressColor(int c) { this.progressColor = c; invalidate(); }
    public void setBackgroundColor(int c) { this.backgroundColor = c; invalidate(); }
} 