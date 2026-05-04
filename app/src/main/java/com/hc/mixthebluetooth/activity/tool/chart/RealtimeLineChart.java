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
 * Small wrapper around MPAndroidChart for one realtime line chart.
 */
public class RealtimeLineChart {

    private final LineChart chart;
    private final LineDataSet dataSet;
    private final Config config;

    private long startTimeMs;

    public RealtimeLineChart(@NonNull LineChart chart, @NonNull Config config) {
        this.chart = chart;
        this.config = config;
        this.startTimeMs = System.currentTimeMillis();

        setupChartBase(chart);
        this.dataSet = createSet(config.label, config.color);
        chart.setData(new LineData(dataSet));
    }

    public void append(float value) {
        LineData data = chart.getData();
        if (data == null) return;

        float x = (System.currentTimeMillis() - startTimeMs) / 1000f;
        data.addEntry(new Entry(x, value), 0);

        if (dataSet.getEntryCount() > config.maxPoints) {
            dataSet.removeFirst();
        }

        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(config.visibleWindowSeconds);

        if (dataSet.getEntryCount() > 0) {
            float lastX = dataSet.getEntryForIndex(dataSet.getEntryCount() - 1).getX();
            chart.moveViewToX(lastX);
        }

        chart.invalidate();
    }

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

    private void setupChartBase(LineChart chart) {
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
        x.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat fmt = new SimpleDateFormat(config.xAxisTimeFormat, Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                long t = startTimeMs + (long) (value * 1000);
                return fmt.format(new Date(t));
            }
        });

        YAxis y = chart.getAxisLeft();
        y.setDrawGridLines(false);

        if (config.yMin != null) {
            y.setAxisMinimum(config.yMin);
        }

        if (config.yMax != null) {
            y.setAxisMaximum(config.yMax);
        }
    }

    private LineDataSet createSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setLineWidth(config.lineWidth);
        set.setColor(color);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        return set;
    }

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
            private Float yMin = null;
            private Float yMax = null;

            public Builder label(String label) {
                this.label = label;
                return this;
            }

            public Builder color(int color) {
                this.color = color;
                return this;
            }

            public Builder maxPoints(int maxPoints) {
                this.maxPoints = maxPoints;
                return this;
            }

            public Builder visibleWindowSeconds(float visibleWindowSeconds) {
                this.visibleWindowSeconds = visibleWindowSeconds;
                return this;
            }

            public Builder lineWidth(float lineWidth) {
                this.lineWidth = lineWidth;
                return this;
            }

            public Builder xAxisTimeFormat(String xAxisTimeFormat) {
                this.xAxisTimeFormat = xAxisTimeFormat;
                return this;
            }

            public Builder yRange(float min, float max) {
                this.yMin = min;
                this.yMax = max;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }
}
