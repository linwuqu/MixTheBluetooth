package com.hc.mixthebluetooth.activity.tool;

import android.annotation.SuppressLint;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 画布注册器 — 统一管理所有 LineChart 的初始化、数据添加和配置。
 *
 * 设计思路：
 * 每个 Fragment 需要什么图，只需要实现 IChartConfig 接口，
 * 调用 {@link #register(LineChart, ChartConfig)} 注册即可。
 * 数据追加、超限裁剪、视图跟随等全部由注册器托管。
 *
 * 使用示例（FragmentMessageNew）：
 *
 * <pre>{@code
 * private final ChartRegistry registry = new ChartRegistry();
 *
 * @Override protected void initCharts() {
 *     registry.register(viewBinding.chartOhm, new LineChartConfig.Builder()
 *         .label("阻抗 (Ω)")
 *         .color(Color.RED)
 *         .maxPoints(500)
 *         .visibleWindowSeconds(60f)
 *         .xAxisFormatter(timestamp -> fmt.format(new Date(timestamp))))
 *         .build());
 *
 *     registry.register(viewBinding.chartUs, new LineChartConfig.Builder()
 *         .label("电导 (uS)")
 *         .color(Color.BLUE)
 *         .maxPoints(500)
 *         .visibleWindowSeconds(60f)
 *         .xAxisFormatter(timestamp -> fmt.format(new Date(timestamp))))
 *         .build());
 * }
 *
 * // 追加数据点（自动裁剪、自动刷新、自动跟随）
 * registry.append("阻抗 (Ω)", timeMs, ohm);
 * registry.append("电导 (uS)", timeMs, us);
 *
 * // 重置（开始新会话时）
 * registry.resetAll();
 * }</pre>
 *
 * 支持的功能：
 * - 按 label 注册多个 LineChart，数据点按 label 路由
 * - MAX_POINTS 超限自动裁剪最早数据
 * - 可见窗口自动跟随最新数据
 * - X轴时间戳格式化
 * - 批量重置
 *
 * 注意：本注册器假设所有 LineChart 使用相同的时间起点 startTimeMs，
 * 调用方负责在 {@link #resetAll(long)} 时传入一致的起点。
 */
public class ChartRegistry {

    private final List<ChartEntry> charts = new ArrayList<>();

    // ─── 注册 ───────────────────────────────────────────────────────────

    /**
     * 注册一个 LineChart。
     * @param chart  要注册的图表
     * @param config 图表配置
     */
    public void register(@NonNull LineChart chart, @NonNull ChartConfig config) {
        setupBase(chart, config);
        LineDataSet set = createSet(config);
        chart.setData(new LineData(set));
        charts.add(new ChartEntry(chart, set, config));
    }

    private void setupBase(@NonNull LineChart chart, @NonNull ChartConfig config) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.getAxisRight().setEnabled(false);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
        if (config.xAxisFormatter != null) {
            x.setValueFormatter(new ValueFormatter() {
                private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                @Override
                public String getFormattedValue(float value) {
                    return config.xAxisFormatter.format((long) value);
                }
            });
        }

        YAxis y = chart.getAxisLeft();
        y.setDrawGridLines(false);
        y.setAxisMinimum(config.yMin);
        y.setAxisMaximum(config.yMax);
    }

    private LineDataSet createSet(@NonNull ChartConfig config) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), config.label);
        set.setLineWidth(config.lineWidth);
        set.setColor(config.color);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        if (config.fillColor != null) {
            set.setDrawFilled(true);
            set.setFillColor(config.fillColor);
            set.setFillAlpha(config.fillAlpha);
        }
        return set;
    }

    // ─── 数据操作 ────────────────────────────────────────────────────────

    /**
     * 向指定 label 的图表追加数据点。
     * @param label     图表 label，与注册时的 config.label 对应
     * @param xMs       X轴值（毫秒时间戳）
     * @param y         Y轴值
     */
    public void append(@NonNull String label, long xMs, float y) {
        for (ChartEntry e : charts) {
            if (e.config.label.equals(label)) {
                LineData data = e.chart.getData();
                if (data == null) continue;
                data.addEntry(new Entry(xMs, y), 0);
                if (e.set.getEntryCount() > e.config.maxPoints) {
                    e.set.removeFirst();
                }
                data.notifyDataChanged();
                e.chart.notifyDataSetChanged();
                e.chart.setVisibleXRangeMaximum(e.config.visibleWindowSeconds * 1000);
                e.chart.invalidate();
                if (e.set.getEntryCount() > 0) {
                    float lastX = e.set.getEntryForIndex(e.set.getEntryCount() - 1).getX();
                    e.chart.moveViewToX(lastX);
                }
                break;
            }
        }
    }

    /**
     * 重置所有图表，清空数据并重置起点。
     * @param startTimeMs 新的时间起点（毫秒）
     */
    public void resetAll(long startTimeMs) {
        for (ChartEntry e : charts) {
            e.set.clear();
            e.chart.notifyDataSetChanged();
            e.chart.invalidate();
        }
    }

    /**
     * 重置所有图表（不改变时间起点，仅清空数据）。
     */
    public void resetAll() {
        for (ChartEntry e : charts) {
            e.set.clear();
            e.chart.notifyDataSetChanged();
            e.chart.invalidate();
        }
    }

    /**
     * 获取指定 label 的 LineDataSet。
     * 用于批量操作。
     */
    @Nullable
    public LineDataSet getDataSet(@NonNull String label) {
        for (ChartEntry e : charts) {
            if (e.config.label.equals(label)) {
                return e.set;
            }
        }
        return null;
    }

    // ─── 内部数据结构 ────────────────────────────────────────────────────

    private static class ChartEntry {
        @NonNull final LineChart chart;
        @NonNull final LineDataSet set;
        @NonNull final ChartConfig config;

        ChartEntry(@NonNull LineChart chart, @NonNull LineDataSet set, @NonNull ChartConfig config) {
            this.chart  = chart;
            this.set    = set;
            this.config = config;
        }
    }

    // ─── ChartConfig 配置类 ─────────────────────────────────────────────

    /**
     * 图表配置 Builder。
     *
     * <pre>{@code
     * new ChartConfig.Builder()
     *     .label("阻抗 (Ω)")
     *     .color(Color.RED)
     *     .maxPoints(500)
     *     .visibleWindowSeconds(60f)
     *     .lineWidth(1.2f)
     *     .yMin(0f)
     *     .yMax(Float.NaN)  // 自动
     *     .xAxisFormatter(timestamp -> fmt.format(new Date(timestamp)))
     *     .fillColor(Color.argb(30, 255, 0, 0))
     *     .fillAlpha(30)
     *     .build();
     * }</pre>
     */
    public static class ChartConfig {

        public @NonNull String label = "data";
        public int color              = Color.BLUE;
        public float lineWidth        = 1.2f;
        public int maxPoints          = 500;
        public float visibleWindowSeconds = 60f;
        public float yMin             = Float.NaN;
        public float yMax             = Float.NaN;
        public @Nullable ValueFormatterX xAxisFormatter = null;
        public @Nullable Integer fillColor  = null;
        public int fillAlpha          = 30;

        public interface ValueFormatterX {
            String format(long timestampMs);
        }

        public static class Builder {
            private final ChartConfig c = new ChartConfig();

            public Builder label(@NonNull String label) {
                c.label = label;
                return this;
            }
            public Builder color(int color) {
                c.color = color;
                return this;
            }
            public Builder lineWidth(float w) {
                c.lineWidth = w;
                return this;
            }
            public Builder maxPoints(int n) {
                c.maxPoints = n;
                return this;
            }
            public Builder visibleWindowSeconds(float s) {
                c.visibleWindowSeconds = s;
                return this;
            }
            public Builder yRange(float min, float max) {
                c.yMin = min;
                c.yMax = max;
                return this;
            }
            public Builder xAxisFormatter(ValueFormatterX f) {
                c.xAxisFormatter = f;
                return this;
            }
            public Builder fill(int color, int alpha) {
                c.fillColor = color;
                c.fillAlpha = alpha;
                return this;
            }
            public ChartConfig build() {
                return c;
            }
        }
    }
}
