package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.cgm.CgmResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CGM-style glucose chart View.
 *
 * <p>Visual language follows the dominant CGM app conventions (Dexcom Clarity / LibreView):
 * <ul>
 *   <li>Warm-white card background with subtle border.</li>
 *   <li>Horizontal Y-axis labels: Low / Normal / High range bands (3.9–7.8 mmol/L by default).</li>
 *   <li>Color-coded predicted line: orange-red above target, green in range, amber below.</li>
 *   <li>Smooth Catmull-Rom-style cubic bezier curve with translucent area fill.</li>
 *   <li>Actual-data markers as small filled circles.</li>
 *   <li>Time-of-day X-axis (HH:mm) with day separator and cross-day tick.</li>
 *   <li>Tap-to-pin vertical highlight with floating value pill.</li>
 * </ul>
 */
public class CgmGlucoseChart extends View {

    // --- Color palette -----------------------------------------------------
    private static final int COLOR_BG             = Color.parseColor("#FFFAF6EE"); // warm cream
    private static final int COLOR_BORDER          = Color.parseColor("#FFE6DED0");
    private static final int COLOR_GRID            = Color.parseColor("#1A000000");
    private static final int COLOR_AXIS_TEXT       = Color.parseColor("#FF7A6B58");
    private static final int COLOR_TIME_TEXT       = Color.parseColor("#FF9C8B73");
    private static final int COLOR_HIGH_BAND       = Color.parseColor("#22E5614D"); // red tint
    private static final int COLOR_LOW_BAND        = Color.parseColor("#22F2A33A"); // amber tint
    private static final int COLOR_TARGET_BAND     = Color.parseColor("#222BB673"); // green tint
    private static final int COLOR_LINE_HIGH       = Color.parseColor("#FFE5614D");
    private static final int COLOR_LINE_NORMAL     = Color.parseColor("#FF2BB673");
    private static final int COLOR_LINE_LOW        = Color.parseColor("#FFF2A33A");
    private static final int COLOR_FILL_HIGH       = Color.parseColor("#33E5614D");
    private static final int COLOR_FILL_NORMAL     = Color.parseColor("#332BB673");
    private static final int COLOR_FILL_LOW        = Color.parseColor("#33F2A33A");
    private static final int COLOR_DAY_LINE        = Color.parseColor("#FFB39A7A");
    private static final int COLOR_PILL_BG         = Color.parseColor("#EE1E1A14");
    private static final int COLOR_PILL_TEXT       = Color.parseColor("#FFFFFAF0");
    private static final int COLOR_HIGHLIGHT       = Color.parseColor("#FF8A7A60");
    private static final int COLOR_ACTUAL_POINT    = Color.parseColor("#FFFFFFFF");
    private static final int COLOR_ACTUAL_RING     = Color.parseColor("#FFE5614D");
    private static final int COLOR_EXTREME_HIGH    = Color.parseColor("#FFC0352B"); // deep red
    private static final int COLOR_EXTREME_LOW     = Color.parseColor("#FFB07A1F"); // deep amber
    private static final int COLOR_EXTREME_LABEL_BG = Color.parseColor("#EE1E1A14");
    private static final int COLOR_EXTREME_LABEL_TEXT = Color.parseColor("#FFFFFAF0");

    // Glucose range bands (mmol/L). Reasonable defaults; can be made configurable later.
    private static final float RANGE_VERY_LOW = 2.8f;
    private static final float RANGE_LOW      = 3.9f;
    private static final float RANGE_HIGH     = 7.8f;
    private static final float RANGE_VERY_HIGH = 11.1f;

    // --- Paints ------------------------------------------------------------
    private final Paint bgPaint         = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rangePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayLinePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint axisPaint   = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint   = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillText    = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayLabel    = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint extremeLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // --- Layout metrics ----------------------------------------------------
    private final float density = getResources().getDisplayMetrics().density;
    private float padLeft  = 44f * density;
    private float padRight = 16f * density;
    private float padTop   = 18f * density;
    private float padBottom = 28f * density;

    // --- Data --------------------------------------------------------------
    @NonNull private final ArrayList<CgmResult.Point> sortedPoints = new ArrayList<>();
    @NonNull private final ArrayList<Long> dayBoundaries = new ArrayList<>();
    private long minTs = 0L;
    private long maxTs = 0L;
    private float yMin = 2f;
    private float yMax = 14f;

    // Cached extrema indices into sortedPoints (predicted value). -1 means "not computed yet".
    private int maxIndex = -1;
    private int minIndex = -1;

    @Nullable private Integer pinnedIndex = null;
    private float pinnedX = 0f;

    private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dayFmt =
            new SimpleDateFormat("MM/dd", Locale.getDefault());

    public CgmGlucoseChart(Context context) {
        this(context, null);
    }

    public CgmGlucoseChart(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CgmGlucoseChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(COLOR_BG);
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(COLOR_BORDER);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1f));

        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.7f));

        rangePaint.setStyle(Paint.Style.FILL);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(2.2f));

        fillPaint.setStyle(Paint.Style.FILL);

        actualFillPaint.setColor(COLOR_ACTUAL_POINT);
        actualFillPaint.setStyle(Paint.Style.FILL);
        actualRingPaint.setColor(COLOR_ACTUAL_RING);
        actualRingPaint.setStyle(Paint.Style.STROKE);
        actualRingPaint.setStrokeWidth(dp(1.5f));

        dayLinePaint.setColor(COLOR_DAY_LINE);
        dayLinePaint.setStyle(Paint.Style.STROKE);
        dayLinePaint.setStrokeWidth(dp(1f));
        dayLinePaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(4f), dp(3f)}, 0f));

        highlightPaint.setColor(COLOR_HIGHLIGHT);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(1.2f));
        highlightPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(3f), dp(3f)}, 0f));

        pillFillPaint.setColor(COLOR_PILL_BG);
        pillFillPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(COLOR_AXIS_TEXT);
        axisPaint.setTextSize(sp(10f));

        timePaint.setColor(COLOR_TIME_TEXT);
        timePaint.setTextSize(sp(9.5f));

        pillText.setColor(COLOR_PILL_TEXT);
        pillText.setTextSize(sp(11f));
        pillText.setFakeBoldText(true);

        dayLabel.setColor(COLOR_DAY_LINE);
        dayLabel.setTextSize(sp(9.5f));
        dayLabel.setFakeBoldText(true);

        extremeFillPaint.setStyle(Paint.Style.FILL);
        extremeStrokePaint.setStyle(Paint.Style.STROKE);
        extremeStrokePaint.setStrokeWidth(dp(1.8f));
        extremeLabel.setColor(COLOR_EXTREME_LABEL_TEXT);
        extremeLabel.setTextSize(sp(10f));
        extremeLabel.setFakeBoldText(true);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // dashed lines need software layer
    }

    public void setData(@NonNull List<CgmResult.Point> rawPoints) {
        sortedPoints.clear();
        dayBoundaries.clear();

        ArrayList<CgmResult.Point> tmp = new ArrayList<>(rawPoints);
        Collections.sort(tmp, (a, b) -> {
            long ta = parseTs(a);
            long tb = parseTs(b);
            if (ta == 0L && tb == 0L) return 0;
            if (ta == 0L) return 1;
            if (tb == 0L) return -1;
            return Long.compare(ta, tb);
        });

        long prevDay = 0L;
        long lo = Long.MAX_VALUE, hi = 0L;
        for (CgmResult.Point p : tmp) {
            long ts = parseTs(p);
            if (ts <= 0L) continue;
            sortedPoints.add(p);
            if (ts < lo) lo = ts;
            if (ts > hi) hi = ts;

            long dayKey = ts / 86400000L;
            if (prevDay != 0L && dayKey != prevDay) {
                dayBoundaries.add(ts);
            }
            prevDay = dayKey;
        }

        if (lo != Long.MAX_VALUE && hi > lo) {
            minTs = lo;
            maxTs = hi;
        } else {
            minTs = 0L;
            maxTs = 0L;
        }

        recomputeExtremes();

        pinnedIndex = null;
        invalidate();
    }

    public void clear() {
        sortedPoints.clear();
        dayBoundaries.clear();
        minTs = 0L;
        maxTs = 0L;
        maxIndex = -1;
        minIndex = -1;
        pinnedIndex = null;
        invalidate();
    }

    private void recomputeExtremes() {
        maxIndex = -1;
        minIndex = -1;
        if (sortedPoints.isEmpty()) return;
        double maxV = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        for (int i = 0; i < sortedPoints.size(); i++) {
            double v = sortedPoints.get(i).predicted;
            if (v > maxV) { maxV = v; maxIndex = i; }
            if (v < minV) { minV = v; minIndex = i; }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        // Card background
        RectF card = new RectF(0, 0, w, h);
        canvas.drawRoundRect(card, dp(12f), dp(12f), bgPaint);
        canvas.drawRoundRect(card, dp(12f), dp(12f), borderPaint);

        // Plot rect
        float left = padLeft;
        float right = w - padRight;
        float top = padTop;
        float bottom = h - padBottom;
        RectF plot = new RectF(left, top, right, bottom);

        // Y range
        if (sortedPoints.isEmpty()) {
            yMin = RANGE_VERY_LOW;
            yMax = RANGE_VERY_HIGH;
        } else {
            float dataMin = Float.MAX_VALUE, dataMax = -Float.MAX_VALUE;
            for (CgmResult.Point p : sortedPoints) {
                float y = (float) p.predicted;
                if (y < dataMin) dataMin = y;
                if (y > dataMax) dataMax = y;
            }
            yMin = Math.max(2f, dataMin - 1.2f);
            yMax = Math.min(20f, dataMax + 1.2f);
            if (yMax - yMin < 4f) yMax = yMin + 4f;
        }

        // Range bands
        drawRangeBand(canvas, plot, RANGE_LOW, RANGE_HIGH, COLOR_TARGET_BAND);
        drawRangeBand(canvas, plot, RANGE_VERY_LOW, RANGE_LOW, COLOR_LOW_BAND);
        drawRangeBand(canvas, plot, RANGE_HIGH, RANGE_VERY_HIGH, COLOR_HIGH_BAND);

        // Horizontal grid lines + Y axis labels
        drawYGrid(canvas, plot);

        // Day boundary dashed lines
        for (long dayTs : dayBoundaries) {
            float x = xForTs(dayTs, plot);
            canvas.drawLine(x, top, x, bottom, dayLinePaint);
        }

        if (sortedPoints.isEmpty()) {
            drawCenteredText(canvas, "No data", plot);
            return;
        }

        // Build predicted path with smooth cubic curves
        Path linePath = new Path();
        Path fillPath = new Path();
        ArrayList<PointF> pts = new ArrayList<>(sortedPoints.size());
        for (CgmResult.Point p : sortedPoints) {
            long ts = parseTs(p);
            if (ts <= 0L) continue;
            float x = xForTs(ts, plot);
            float y = yForVal((float) p.predicted, plot);
            pts.add(new PointF(x, y));
        }
        if (pts.isEmpty()) {
            drawCenteredText(canvas, "No data", plot);
            return;
        }

        buildSmoothPath(pts, linePath);
        buildSmoothPath(pts, fillPath);
        fillPath.lineTo(pts.get(pts.size() - 1).x, bottom);
        fillPath.lineTo(pts.get(0).x, bottom);
        fillPath.close();

        // Fill under curve (gradient)
        Paint linePaintLocal = new Paint(linePaint);
        if (!pts.isEmpty()) {
            Shader fillShader = new LinearGradient(
                    0, top, 0, bottom,
                    new int[]{colorForY(pts.get(0).y, plot), colorWithAlpha(colorForY(pts.get(0).y, plot), 0)},
                    null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
        }

        canvas.drawPath(fillPath, fillPaint);
        fillPaint.setShader(null);

        // Stroke with segmented color (above target = red, in range = green, below = amber).
        // For simplicity we use a single line color: pick the dominant band color.
        int dominant = COLOR_LINE_NORMAL;
        int highCount = 0, lowCount = 0, normalCount = 0;
        for (CgmResult.Point p : sortedPoints) {
            float v = (float) p.predicted;
            if (v >= RANGE_HIGH) highCount++;
            else if (v <= RANGE_LOW) lowCount++;
            else normalCount++;
        }
        if (highCount >= normalCount && highCount >= lowCount) dominant = COLOR_LINE_HIGH;
        else if (lowCount >= normalCount && lowCount >= highCount) dominant = COLOR_LINE_LOW;
        linePaintLocal.setColor(dominant);
        canvas.drawPath(linePath, linePaintLocal);

        // Actual data point markers
        for (CgmResult.Point p : sortedPoints) {
            if (p.actual == null) continue;
            long ts = parseTs(p);
            if (ts <= 0L) continue;
            float x = xForTs(ts, plot);
            float y = yForVal(p.actual.floatValue(), plot);
            canvas.drawCircle(x, y, dp(3.2f), actualFillPaint);
            canvas.drawCircle(x, y, dp(3.2f), actualRingPaint);
        }

        // Max / Min highlights (hollow ring + value label)
        if (sortedPoints.size() >= 2 && maxIndex >= 0 && minIndex >= 0 && maxIndex != minIndex) {
            drawExtreme(canvas, plot, maxIndex, COLOR_EXTREME_HIGH, '\u25B2', true);
            drawExtreme(canvas, plot, minIndex, COLOR_EXTREME_LOW, '\u25BC', false);
        }

        // X axis: time labels at left, middle, right
        drawTimeAxis(canvas, plot);

        // Day labels along the top
        drawDayLabels(canvas, plot);

        // Pinned highlight
        if (pinnedIndex != null && pinnedIndex >= 0 && pinnedIndex < sortedPoints.size()) {
            CgmResult.Point pin = sortedPoints.get(pinnedIndex);
            long ts = parseTs(pin);
            if (ts > 0L) {
                float px = xForTs(ts, plot);
                canvas.drawLine(px, top, px, bottom, highlightPaint);
                drawPill(canvas, px,
                        yForVal((float) pin.predicted, plot),
                        pin);
            }
        }
    }

    private void drawPill(Canvas canvas, float x, float y, CgmResult.Point p) {
        long ts = parseTs(p);
        String value = String.format(Locale.getDefault(), "%.2f", p.predicted);
        String time = timeFmt.format(new Date(ts));
        String text = time + "  ·  " + value;

        Rect bounds = new Rect();
        pillText.getTextBounds(text, 0, text.length(), bounds);
        float padH = dp(10f);
        float padV = dp(5f);
        float pw = bounds.width() + padH * 2;
        float ph = bounds.height() + padV * 2;
        float pillX = x - pw / 2f;
        float pillY = y - ph - dp(10f);
        if (pillX < padLeft) pillX = padLeft;
        if (pillX + pw > getWidth() - padRight) pillX = getWidth() - padRight - pw;
        if (pillY < padTop) pillY = y + dp(10f);

        RectF pill = new RectF(pillX, pillY, pillX + pw, pillY + ph);
        canvas.drawRoundRect(pill, dp(8f), dp(8f), pillFillPaint);
        canvas.drawText(text,
                pillX + padH,
                pillY + padV - bounds.top,
                pillText);
    }

    /**
     * Draw an extreme-point marker on the predicted curve.
     *
     * @param isMax true for max (label above the point), false for min (label below).
     */
    private void drawExtreme(Canvas canvas, RectF plot, int idx,
                             int color, char arrow, boolean isMax) {
        CgmResult.Point p = sortedPoints.get(idx);
        long ts = parseTs(p);
        if (ts <= 0L) return;
        float x = xForTs(ts, plot);
        float y = yForVal((float) p.predicted, plot);

        // Hollow ring: filled with chart background, stroked with band color.
        float outerR = dp(5.5f);
        extremeFillPaint.setColor(COLOR_BG);
        extremeStrokePaint.setColor(color);
        canvas.drawCircle(x, y, outerR, extremeFillPaint);
        canvas.drawCircle(x, y, outerR, extremeStrokePaint);
        canvas.drawCircle(x, y, dp(2.2f), extremeStrokePaint);

        // Value label: e.g. "▲ 7.92" inside a small dark pill, auto-flip across edges.
        String value = String.format(Locale.getDefault(), "%.2f", p.predicted);
        String text = arrow + " " + value;
        Rect b = new Rect();
        extremeLabel.getTextBounds(text, 0, text.length(), b);
        float padH = dp(7f);
        float padV = dp(3.5f);
        float pw = b.width() + padH * 2;
        float ph = b.height() + padV * 2;
        float gap = dp(6f);
        float labelX = x - pw / 2f;
        if (labelX < plot.left) labelX = plot.left;
        if (labelX + pw > plot.right) labelX = plot.right - pw;
        // Default: max label above, min label below
        float labelY = isMax ? (y - outerR - gap - ph) : (y + outerR + gap);
        // Flip if it would clip the top
        if (isMax && labelY < plot.top) labelY = y + outerR + gap;
        // Flip if it would clip the bottom
        if (!isMax && labelY + ph > plot.bottom) labelY = y - outerR - gap - ph;

        RectF pill = new RectF(labelX, labelY, labelX + pw, labelY + ph);
        canvas.drawRoundRect(pill, dp(6f), dp(6f), pillFillPaint);
        canvas.drawText(text,
                labelX + padH,
                labelY + padV - b.top,
                extremeLabel);
    }

    private void drawTimeAxis(Canvas canvas, RectF plot) {
        if (minTs == 0L) return;
        long span = Math.max(1L, maxTs - minTs);
        // Up to 5 ticks
        int tickCount = 5;
        for (int i = 0; i < tickCount; i++) {
            float frac = (float) i / (float) (tickCount - 1);
            long ts = minTs + (long) (span * frac);
            float x = xForTs(ts, plot);
            String label = timeFmt.format(new Date(ts));
            Rect b = new Rect();
            timePaint.getTextBounds(label, 0, label.length(), b);
            float tx = x - b.width() / 2f;
            if (tx < plot.left) tx = plot.left;
            if (tx + b.width() > plot.right) tx = plot.right - b.width();
            canvas.drawText(label, tx, plot.bottom + dp(16f), timePaint);
            // Light tick
            canvas.drawLine(x, plot.bottom, x, plot.bottom + dp(4f), gridPaint);
        }
    }

    private void drawDayLabels(Canvas canvas, RectF plot) {
        if (minTs == 0L) return;
        // Start of first day
        long firstDay = (minTs / 86400000L) * 86400000L;
        long lastTs = maxTs;
        long cur = firstDay;
        int count = 0;
        while (cur <= lastTs && count < 4) {
            // Render the label at the very start of the day, but offset a bit right so it's
            // visible even if it sits at plot.left.
            float x = xForTs(cur + 60_000L, plot); // 1 min after midnight
            if (x < plot.left) x = plot.left;
            if (x > plot.right) break;
            String text = dayFmt.format(new Date(cur));
            Rect b = new Rect();
            dayLabel.getTextBounds(text, 0, text.length(), b);
            canvas.drawText(text, x, plot.top - dp(4f), dayLabel);
            cur += 86400000L;
            count++;
        }
    }

    private void drawCenteredText(Canvas canvas, String text, RectF plot) {
        Rect b = new Rect();
        axisPaint.getTextBounds(text, 0, text.length(), b);
        canvas.drawText(text,
                plot.centerX() - b.width() / 2f,
                plot.centerY() + b.height() / 2f,
                axisPaint);
    }

    private void drawYGrid(Canvas canvas, RectF plot) {
        float[] gridVals = {RANGE_VERY_LOW, RANGE_LOW, RANGE_HIGH, RANGE_VERY_HIGH};
        for (float v : gridVals) {
            if (v < yMin || v > yMax) continue;
            float y = yForVal(v, plot);
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
        }

        // Y axis labels
        String[] labels = {"Low", "Normal", "High"};
        float[] labelVals = {(RANGE_VERY_LOW + RANGE_LOW) / 2f,
                (RANGE_LOW + RANGE_HIGH) / 2f,
                (RANGE_HIGH + RANGE_VERY_HIGH) / 2f};
        for (int i = 0; i < labels.length; i++) {
            float v = labelVals[i];
            if (v < yMin || v > yMax) continue;
            float y = yForVal(v, plot);
            Rect b = new Rect();
            axisPaint.getTextBounds(labels[i], 0, labels[i].length(), b);
            canvas.drawText(labels[i],
                    plot.left - b.width() - dp(6f),
                    y + b.height() / 2f,
                    axisPaint);
        }
    }

    private void drawRangeBand(Canvas canvas, RectF plot, float vLo, float vHi, int color) {
        float yLo = yForVal(vLo, plot);
        float yHi = yForVal(vHi, plot);
        if (yLo > plot.bottom || yHi < plot.top) return;
        yLo = Math.max(yLo, plot.top);
        yHi = Math.min(yHi, plot.bottom);
        if (yHi - yLo < 0.5f) return;
        rangePaint.setColor(color);
        canvas.drawRect(plot.left, yHi, plot.right, yLo, rangePaint);
    }

    private float xForTs(long ts, RectF plot) {
        if (maxTs == minTs) return plot.centerX();
        return plot.left + (plot.width() * (ts - minTs)) / (float) (maxTs - minTs);
    }

    private float yForVal(float v, RectF plot) {
        return plot.bottom - (plot.height() * (v - yMin)) / (yMax - yMin);
    }

    private int colorForY(float yPx, RectF plot) {
        float v = yMin + (yMax - yMin) * (plot.bottom - yPx) / plot.height();
        if (v >= RANGE_HIGH) return COLOR_FILL_HIGH;
        if (v <= RANGE_LOW) return COLOR_FILL_LOW;
        return COLOR_FILL_NORMAL;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Build a smooth path through the given points using a simple Catmull-Rom -> Bezier
     * conversion. Produces the soft curves typical of CGM apps without the heavy handed
     * bulges that MPAndroidChart's CUBIC_BEZIER can produce.
     */
    private void buildSmoothPath(List<PointF> pts, Path path) {
        if (pts.isEmpty()) return;
        path.moveTo(pts.get(0).x, pts.get(0).y);
        if (pts.size() == 1) return;
        if (pts.size() == 2) {
            path.lineTo(pts.get(1).x, pts.get(1).y);
            return;
        }
        float tension = 0.18f;
        for (int i = 0; i < pts.size() - 1; i++) {
            PointF p0 = pts.get(Math.max(0, i - 1));
            PointF p1 = pts.get(i);
            PointF p2 = pts.get(i + 1);
            PointF p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            float c1x = p1.x + (p2.x - p0.x) * tension;
            float c1y = p1.y + (p2.y - p0.y) * tension;
            float c2x = p2.x - (p3.x - p1.x) * tension;
            float c2y = p2.y - (p3.y - p1.y) * tension;
            path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y);
        }
    }

    // --- Touch handling ----------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sortedPoints.isEmpty()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int idx = findClosestIndex(event.getX());
                if (idx >= 0) {
                    pinnedIndex = idx;
                    pinnedX = event.getX();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private int findClosestIndex(float touchX) {
        float plotLeft = padLeft;
        float plotRight = getWidth() - padRight;
        if (touchX < plotLeft) touchX = plotLeft;
        if (touchX > plotRight) touchX = plotRight;

        // First, project to timestamp then binary search
        long span = Math.max(1L, maxTs - minTs);
        long touchedTs = minTs + (long) ((touchX - plotLeft) * span / (plotRight - plotLeft));

        int lo = 0, hi = sortedPoints.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            long ts = parseTs(sortedPoints.get(mid));
            if (ts < touchedTs) lo = mid + 1;
            else hi = mid;
        }
        // Check neighbour
        if (lo > 0) {
            long a = parseTs(sortedPoints.get(lo - 1));
            long b = parseTs(sortedPoints.get(lo));
            if (Math.abs(a - touchedTs) < Math.abs(b - touchedTs)) {
                lo = lo - 1;
            }
        }
        return lo;
    }

    private static long parseTs(CgmResult.Point p) {
        if (p == null || p.rawTime == null || p.rawTime.isEmpty()) return 0L;
        try {
            if (p.rawTime.length() == 19) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .parse(p.rawTime).getTime();
            }
            if (p.rawTime.length() == 16) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .parse(p.rawTime).getTime();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    // --- Unit conversion ---------------------------------------------------
    private float dp(float v) { return v * density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
