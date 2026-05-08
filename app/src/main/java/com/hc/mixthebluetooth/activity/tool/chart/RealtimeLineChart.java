package com.hc.mixthebluetooth.activity.tool.chart;

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
import java.util.Locale;

/**
 * RealtimeLineChart — 单张实时折线图的封装
 *
 * 作用：
 *   对第三方库 MPAndroidChart（com.github.mikephil.charting）的薄封装，
 *   让"往实时图表追加数据点"这件事变得简单。
 *
 * 核心逻辑：
 *   每追加一个数据点：
 *     ① 计算 X 坐标 = 从图表创建到现在经过的秒数（相对时间）
 *     ② 计算 Y 坐标 = 传入的 float 值（血糖浓度）
 *     ③ 追加到数据集
 *     ④ 如果超过 maxPoints（500个），删除最早的点
 *     ⑤ 重绘图表，X 轴自动滚动到最新数据
 *
 * X 轴时间显示：
 *   X 轴显示的是"从图表创建（或重置）到现在"的相对时间，格式化为 HH:mm:ss。
 *   不是绝对时间（如 14:30:00），而是相对时间（0:00:05 = 图表创建后第5秒）。
 *   重置图表时 startTimeMs 更新，时间轴从 0 重新开始。
 *
 * 为什么 X 轴用相对时间而不是绝对时间？
 *   因为实时监测时，相邻两个数据点的时间差才是最重要的。
 *   相对时间可以直接看出"这5秒内血糖变化了多少"，更直观。
 *
 * Config（配置）：
 *   所有外观参数都通过 Config.Builder 链式传入：
 *     label              — 图例文字（"CGM" / "Current"）
 *     color              — 折线颜色（RED / BLUE）
 *     maxPoints          — 最多保留 500 个数据点
 *     visibleWindowSeconds — X 轴最多显示 60 秒
 *     lineWidth          — 折线粗细
 *     xAxisTimeFormat    — X 轴时间格式（默认 "HH:mm:ss"）
 *     yMin / yMax        — Y 轴范围（可空）
 */
public class RealtimeLineChart {

    /** 实际的图表控件（来自 XML 绑定的 LineChart view） */
    private final LineChart chart;

    /** 数据集（折线的实际数据存放处） */
    private final LineDataSet dataSet;

    /** 图表配置（颜色/标签/点数/窗口等） */
    private final Config config;

    /** 图表创建时的时间戳（毫秒），用于计算相对 X 坐标 */
    private long startTimeMs;

    public RealtimeLineChart(@NonNull LineChart chart, @NonNull Config config) {
        this.chart = chart;
        this.config = config;
        this.startTimeMs = System.currentTimeMillis();

        // 配置图表基础外观（坐标轴/网格/交互等）
        setupChartBase(chart);

        // 创建数据集（一条折线）
        this.dataSet = createSet(config.label, config.color);

        // 把数据集绑定到图表
        chart.setData(new LineData(dataSet));
    }

    /**
     * 追加一个数据点到图表。
     *
     * @param value 血糖浓度值（Y 坐标）
     *
     * 完整流程：
     *   float x = (System.currentTimeMillis() - startTimeMs) / 1000f;
     *     → 计算从图表创建到现在经过的秒数 = X 坐标
     *   data.addEntry(new Entry(x, value), 0);
     *     → 在数据集末尾追加一个新点 (x, value)
     *   if (dataSet.getEntryCount() > maxPoints)
     *     dataSet.removeFirst();
     *     → 超过 500 个点就删掉最早的点，保持内存占用稳定
     *   chart.setVisibleXRangeMaximum(60f);
     *     → X 轴最多显示 60 秒（超出范围自动滚动）
     *   chart.moveViewToX(lastX);
     *     → 自动把视图滚动到最新数据的位置（始终看到最新数据）
     *   chart.invalidate();
     *     → 重绘图表
     */
    public void append(float value) {
        LineData data = chart.getData();
        if (data == null) return;

        // X 坐标 = 从 startTimeMs 到现在的相对秒数
        float x = (System.currentTimeMillis() - startTimeMs) / 1000f;
        data.addEntry(new Entry(x, value), 0);

        // 超过最大点数就删最早的
        if (dataSet.getEntryCount() > config.maxPoints) {
            dataSet.removeFirst();
        }

        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(config.visibleWindowSeconds);

        // 自动滚动到最新数据
        if (dataSet.getEntryCount() > 0) {
            float lastX = dataSet.getEntryForIndex(dataSet.getEntryCount() - 1).getX();
            chart.moveViewToX(lastX);
        }

        chart.invalidate();
    }

    /**
     * 重置图表：清空所有数据点，重置时间基准。
     * 开始录波前会调用 chartRegistry.resetAll()，清空两个图表，重新计时。
     */
    public void reset() {
        startTimeMs = System.currentTimeMillis();
        dataSet.clear();

        LineData data = chart.getData();
        if (data != null) {
            data.notifyDataChanged();
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    /** 配置图表基础外观 */
    private void setupChartBase(LineChart chart) {
        // 禁用默认描述文字
        chart.getDescription().setEnabled(false);

        // 启用触摸交互（拖动/缩放）
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        // 只显示左 Y 轴，禁用右 Y 轴
        chart.getAxisRight().setEnabled(false);

        // X 轴配置
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);  // X 轴最小间隔 1 秒
        x.setDrawGridLines(false);

        // X 轴标签格式化：把相对秒数转成 HH:mm:ss 绝对时间
        x.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat fmt = new SimpleDateFormat(config.xAxisTimeFormat, Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                // value 是相对秒数，加上 startTimeMs 得到绝对时间戳
                long t = startTimeMs + (long) (value * 1000);
                return fmt.format(new Date(t));
            }
        });

        // Y 轴配置
        YAxis y = chart.getAxisLeft();
        y.setDrawGridLines(false);

        // 可选的 Y 轴范围
        if (config.yMin != null) {
            y.setAxisMinimum(config.yMin);
        }
        if (config.yMax != null) {
            y.setAxisMaximum(config.yMax);
        }
    }

    /** 创建一条折线的数据集 */
    private LineDataSet createSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setLineWidth(config.lineWidth);
        set.setColor(color);
        set.setDrawCircles(false);  // 不显示数据点（小圆圈），只显示线条
        set.setDrawValues(false);   // 不显示数值标签
        return set;
    }

    // ── Config ─────────────────────────────────────────────

    /**
     * 图表配置（不可变）。
     *
     * 使用 Builder 模式，所有字段都有默认值，支持链式调用：
     *   new RealtimeLineChart.Config.Builder()
     *       .label("CGM")
     *       .color(Color.RED)
     *       .maxPoints(500)
     *       .visibleWindowSeconds(60f)
     *       .build()
     */
    public static class Config {

        public final String label;
        public final int color;
        public final int maxPoints;
        public final float visibleWindowSeconds;
        public final float lineWidth;
        public final String xAxisTimeFormat;
        @Nullable
        public final Float yMin;
        @Nullable
        public final Float yMax;

        private Config(Builder builder) {
            this.label = builder.label;
            this.color = builder.color;
            this.maxPoints = builder.maxPoints;
            this.visibleWindowSeconds = builder.visibleWindowSeconds;
            this.lineWidth = builder.lineWidth;
            this.xAxisTimeFormat = builder.xAxisTimeFormat;
            this.yMin = builder.yMin;
            this.yMax = builder.yMax;
        }

        public static class Builder {
            private String label = "data";
            private int color = Color.BLUE;
            private int maxPoints = 500;
            private float visibleWindowSeconds = 60f;
            private float lineWidth = 1.2f;
            private String xAxisTimeFormat = "HH:mm:ss";
            @Nullable
            private Float yMin = null;
            @Nullable
            private Float yMax = null;

            public Builder label(String label) { this.label = label; return this; }
            public Builder color(int color) { this.color = color; return this; }
            public Builder maxPoints(int maxPoints) { this.maxPoints = maxPoints; return this; }
            public Builder visibleWindowSeconds(float visibleWindowSeconds) { this.visibleWindowSeconds = visibleWindowSeconds; return this; }
            public Builder lineWidth(float lineWidth) { this.lineWidth = lineWidth; return this; }
            public Builder xAxisTimeFormat(String xAxisTimeFormat) { this.xAxisTimeFormat = xAxisTimeFormat; return this; }

            /**
             * 设置 Y 轴固定范围。
             * 如果不设置，Y 轴会根据数据的最小/最大值自动调整。
             */
            public Builder yRange(float min, float max) { this.yMin = min; this.yMax = max; return this; }

            public Config build() { return new Config(this); }
        }
    }
}
