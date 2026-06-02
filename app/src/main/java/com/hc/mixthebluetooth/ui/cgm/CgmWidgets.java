package com.hc.mixthebluetooth.ui.cgm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.ui.shared.view.CircleProgressView;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.hc.mixthebluetooth.ui.cgm.CgmController.Region;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CgmWidgets {
    private CgmWidgets() {
    }

    public enum WidgetKind {
        LINE,
        GAUGE,
        VALUE,
        STATS,
        CGM_RESULT
    }

    public enum WidgetStyle {
        CARD,
        HERO,
        COMPACT
    }

    public interface MetricWidget {
        @NonNull
        View view();

        void onSample(@NonNull BluetoothSample sample);

        default void onCgmResult(@NonNull CgmResult result) {
        }

        void reset();
    }

    @NonNull
    public static MetricWidget create(@NonNull Context context, @NonNull WidgetSpec spec) {
        if (spec.kind == WidgetKind.LINE) return new LineMetricWidget(context, spec);
        if (spec.kind == WidgetKind.GAUGE) return new GaugeMetricWidget(context, spec);
        if (spec.kind == WidgetKind.VALUE) return new ValueMetricWidget(context, spec);
        if (spec.kind == WidgetKind.STATS) return new StatsMetricWidget(context, spec);
        if (spec.kind == WidgetKind.CGM_RESULT) return new CgmResultMetricWidget(context, spec);
        return new ValueMetricWidget(context, spec);
    }

    @NonNull
    public static ArrayList<WidgetSpec> orderedForDisplay(@NonNull List<WidgetSpec> widgets) {
        ArrayList<WidgetSpec> orderedWidgets = new ArrayList<>(widgets);
        Collections.sort(orderedWidgets, (a, b) -> {
            int regionCompare = Integer.compare(a.region.ordinal(), b.region.ordinal());
            if (regionCompare != 0) return regionCompare;
            return Integer.compare(a.order, b.order);
        });
        return orderedWidgets;
    }

    public static final class WidgetSpec {
        @NonNull
        public final String id;
        @NonNull
        public final WidgetKind kind;
        @NonNull
        public final String title;
        @NonNull
        public final String metricKey;
        @NonNull
        public final String unit;
        @NonNull
        public final Region region;
        @NonNull
        public final WidgetStyle style;
        public final int order;
        public final int color;
        public final int maxPoints;
        public final float visibleWindowSeconds;
        @Nullable
        public final Float yMin;
        @Nullable
        public final Float yMax;
        @Nullable
        public final Float gaugeMax;

        private WidgetSpec(@NonNull Builder b) {
            id = b.id;
            kind = b.kind;
            title = b.title;
            metricKey = b.metricKey;
            unit = b.unit;
            region = b.region;
            style = b.style;
            order = b.order;
            color = b.color;
            maxPoints = b.maxPoints;
            visibleWindowSeconds = b.visibleWindowSeconds;
            yMin = b.yMin;
            yMax = b.yMax;
            gaugeMax = b.gaugeMax;
        }

        @NonNull
        public static Builder line(@NonNull String id) {
            return new Builder(id, WidgetKind.LINE).region(Region.MAIN).style(WidgetStyle.CARD);
        }

        @NonNull
        public static Builder gauge(@NonNull String id) {
            return new Builder(id, WidgetKind.GAUGE).region(Region.SUMMARY).style(WidgetStyle.HERO);
        }

        @NonNull
        public static Builder value(@NonNull String id) {
            return new Builder(id, WidgetKind.VALUE).region(Region.SUMMARY).style(WidgetStyle.COMPACT);
        }

        @NonNull
        public static Builder stats(@NonNull String id) {
            return new Builder(id, WidgetKind.STATS).region(Region.MAIN).style(WidgetStyle.COMPACT);
        }

        @NonNull
        public static Builder cgmResult(@NonNull String id) {
            return new Builder(id, WidgetKind.CGM_RESULT).region(Region.MAIN).style(WidgetStyle.CARD);
        }
    }

    public static final class Builder {
        @NonNull
        private final String id;
        @NonNull
        private final WidgetKind kind;
        @NonNull
        private String title = "";
        @NonNull
        private String metricKey = "";
        @NonNull
        private String unit = "";
        @NonNull
        private Region region;
        @NonNull
        private WidgetStyle style = WidgetStyle.CARD;
        private int order = 0;
        private int color = 0xFF4EE097;
        private int maxPoints = 500;
        private float visibleWindowSeconds = 60f;
        @Nullable
        private Float yMin = null;
        @Nullable
        private Float yMax = null;
        @Nullable
        private Float gaugeMax = null;

        private Builder(@NonNull String id, @NonNull WidgetKind kind) {
            this.id = id;
            this.kind = kind;
            this.region = kind == WidgetKind.GAUGE || kind == WidgetKind.VALUE ? Region.SUMMARY : Region.MAIN;
        }

        @NonNull
        public Builder title(@NonNull String title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder metric(@NonNull String metricKey) {
            this.metricKey = metricKey;
            return this;
        }

        @NonNull
        public Builder unit(@NonNull String unit) {
            this.unit = unit;
            return this;
        }

        @NonNull
        public Builder region(@NonNull Region region) {
            this.region = region;
            return this;
        }

        @NonNull
        public Builder style(@NonNull WidgetStyle style) {
            this.style = style;
            return this;
        }

        @NonNull
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        @NonNull
        public Builder lineColor(int color) {
            this.color = color;
            return this;
        }

        @NonNull
        public Builder maxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
            return this;
        }

        @NonNull
        public Builder visibleWindowSeconds(float seconds) {
            this.visibleWindowSeconds = seconds;
            return this;
        }

        @NonNull
        public Builder yRange(float min, float max) {
            this.yMin = min;
            this.yMax = max;
            return this;
        }

        @NonNull
        public Builder gaugeMax(float max) {
            this.gaugeMax = max;
            return this;
        }

        @NonNull
        public WidgetSpec build() {
            return new WidgetSpec(this);
        }
    }

    private static final class LineMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final LineChartRuntime chart;

        LineMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.VERTICAL);
            root.addView(title(context, spec.title), matchWrap());
            LineChart chartView = new LineChart(context);
            root.addView(chartView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 210)
            ));
            LineChartRuntime.Config.Builder builder = new LineChartRuntime.Config.Builder()
                    .label(spec.title)
                    .color(spec.color)
                    .maxPoints(spec.maxPoints)
                    .visibleWindowSeconds(spec.visibleWindowSeconds);
            if (spec.yMin != null && spec.yMax != null) builder.yRange(spec.yMin, spec.yMax);
            chart = new LineChartRuntime(chartView, builder.build());
        }

        @NonNull
        @Override
        public View view() {
            return root;
        }

        @Override
        public void onSample(@NonNull BluetoothSample sample) {
            Float value = sample.metrics().get(spec.metricKey);
            if (value != null) chart.append(value);
        }

        @Override
        public void reset() {
            chart.reset();
        }
    }

    private static final class LineChartRuntime {
        private final LineChart chart;
        private final LineDataSet dataSet;
        private final Config config;
        private long startTimeMs;

        LineChartRuntime(@NonNull LineChart chart, @NonNull Config config) {
            this.chart = chart;
            this.config = config;
            this.startTimeMs = System.currentTimeMillis();
            setupChartBase(chart);
            this.dataSet = createSet(config.label, config.color);
            chart.setData(new LineData(dataSet));
        }

        void append(float value) {
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

        void reset() {
            startTimeMs = System.currentTimeMillis();
            dataSet.clear();
            LineData data = chart.getData();
            if (data != null) data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();
        }

        private void setupChartBase(@NonNull LineChart chart) {
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
            if (config.yMin != null) y.setAxisMinimum(config.yMin);
            if (config.yMax != null) y.setAxisMaximum(config.yMax);
        }

        private LineDataSet createSet(String label, int color) {
            LineDataSet set = new LineDataSet(new ArrayList<>(), label);
            set.setLineWidth(config.lineWidth);
            set.setColor(color);
            set.setDrawCircles(false);
            set.setDrawValues(false);
            return set;
        }

        static final class Config {
            final String label;
            final int color;
            final int maxPoints;
            final float visibleWindowSeconds;
            final float lineWidth;
            final String xAxisTimeFormat;
            @Nullable
            final Float yMin;
            @Nullable
            final Float yMax;

            private Config(Builder b) {
                label = b.label;
                color = b.color;
                maxPoints = b.maxPoints;
                visibleWindowSeconds = b.visibleWindowSeconds;
                lineWidth = b.lineWidth;
                xAxisTimeFormat = b.xAxisTimeFormat;
                yMin = b.yMin;
                yMax = b.yMax;
            }

            static final class Builder {
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

                Builder label(String label) {
                    this.label = label;
                    return this;
                }

                Builder color(int color) {
                    this.color = color;
                    return this;
                }

                Builder maxPoints(int maxPoints) {
                    this.maxPoints = maxPoints;
                    return this;
                }

                Builder visibleWindowSeconds(float v) {
                    this.visibleWindowSeconds = v;
                    return this;
                }

                Builder yRange(float min, float max) {
                    yMin = min;
                    yMax = max;
                    return this;
                }

                Config build() {
                    return new Config(this);
                }
            }
        }
    }

    private static final class GaugeMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final CircleProgressView gauge;

        GaugeMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.addView(title(context, spec.title), matchWrap());
            gauge = new CircleProgressView(context);
            gauge.setUnitText(spec.unit);
            gauge.setProgressColor(spec.color);
            gauge.setValueText("--");
            gauge.setProgress(0f);
            root.addView(gauge, new LinearLayout.LayoutParams(dp(context, 128), dp(context, 128)));
        }

        @NonNull
        @Override
        public View view() {
            return root;
        }

        @Override
        public void onSample(@NonNull BluetoothSample sample) {
            Float value = sample.metrics().get(spec.metricKey);
            if (value == null) return;
            gauge.setValueText(format(value));
            float max = spec.gaugeMax == null || spec.gaugeMax <= 0f ? 1f : spec.gaugeMax;
            gauge.setProgress(Math.max(0f, Math.min(1f, value / max)));
        }

        @Override
        public void reset() {
            gauge.setValueText("--");
            gauge.setProgress(0f);
        }
    }

    private static final class ValueMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final TextView value;

        @SuppressLint("SetTextI18n")
        ValueMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(title(context, spec.title), matchWrap());
            value = new TextView(context);
            value.setText("-- " + spec.unit);
            value.setTextColor(Color.rgb(30, 30, 30));
            value.setTextSize(22f);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(value, matchWrap());
        }

        @NonNull
        @Override
        public View view() {
            return root;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onSample(@NonNull BluetoothSample sample) {
            Float v = sample.metrics().get(spec.metricKey);
            if (v != null) value.setText(format(v) + " " + spec.unit);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void reset() {
            value.setText("-- " + spec.unit);
        }
    }

    private static final class StatsMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final WidgetSpec spec;
        private final TextView maxView;
        private final TextView minView;
        private final TextView ampView;
        @Nullable
        private Float max = null;
        @Nullable
        private Float min = null;

        StatsMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.spec = spec;
            root = card(context, LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            if (!spec.title.isEmpty()) {
                TextView label = statText(context, spec.title);
                root.addView(label, weight());
            }
            maxView = statText(context, "Max: --");
            minView = statText(context, "Min: --");
            ampView = statText(context, "Amp: --");
            root.addView(maxView, weight());
            root.addView(minView, weight());
            root.addView(ampView, weight());
        }

        @NonNull
        @Override
        public View view() {
            return root;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onSample(@NonNull BluetoothSample sample) {
            Float v = sample.metrics().get(spec.metricKey);
            if (v == null) return;
            max = max == null ? v : Math.max(max, v);
            min = min == null ? v : Math.min(min, v);
            float amp = max - min;
            maxView.setText("Max: " + format(max) + " " + spec.unit);
            minView.setText("Min: " + format(min) + " " + spec.unit);
            ampView.setText("Amp: " + format(amp) + " " + spec.unit);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void reset() {
            max = null;
            min = null;
            maxView.setText("Max: --");
            minView.setText("Min: --");
            ampView.setText("Amp: --");
        }
    }

    private static final class CgmResultMetricWidget implements MetricWidget {
        private final LinearLayout root;
        private final LineChart chart;
        private final TextView summary;
        private final TextView units;

        CgmResultMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            root = card(context, LinearLayout.VERTICAL);
            root.addView(title(context, spec.title), matchWrap());

            chart = new LineChart(context);
            setupCgmChart(chart);
            root.addView(chart, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 230)
            ));

            summary = statBlock(context);
            units = statBlock(context);
            summary.setText("等待 CGM 结果");
            units.setText("units=[]");
            root.addView(summary, matchWrap());
            root.addView(units, matchWrap());
        }

        @NonNull
        @Override
        public View view() {
            return root;
        }

        @Override
        public void onSample(@NonNull BluetoothSample sample) {
        }

        @Override
        public void onCgmResult(@NonNull CgmResult result) {
            ArrayList<Entry> predicted = new ArrayList<>();
            ArrayList<Entry> actual = new ArrayList<>();
            for (CgmResult.Point point : allPoints(result)) {
                predicted.add(new Entry(point.time, (float) point.predicted));
                if (point.actual != null) {
                    actual.add(new Entry(point.time, point.actual.floatValue()));
                }
            }

            ApiTraceLogger.text("CgmWidgetBinder", "RENDER", "result",
                    "jobId=" + result.jobId
                            + "\nstatus=" + result.status
                            + "\npoints=" + predicted.size()
                            + "\npredicted=" + predicted.size()
                            + "\nactual=" + actual.size()
                            + "\nunitCount=" + result.unitCount);

            LineData data = new LineData();
            data.addDataSet(cgmSet(predicted, "Predicted mmol/L", Color.rgb(45, 99, 155)));
            if (!actual.isEmpty()) {
                data.addDataSet(cgmSet(actual, "Actual mmol/L", Color.rgb(245, 158, 11)));
            }
            chart.setData(data);
            chart.invalidate();

            summary.setText(String.format(Locale.getDefault(),
                    "status=%s  points=%d  units=%d\nprediction[min=%s max=%s mean=%s std=%s]  avgMard=%s  mardStd=%s\nids[result=%d job=%d dataset=%d]  created=%s",
                    textOrDash(result.status),
                    result.pointCount,
                    result.unitCount,
                    formatDouble(result.predictionMin),
                    formatDouble(result.predictionMax),
                    formatDouble(result.predictionMean),
                    formatDouble(result.predictionStd),
                    formatDouble(result.avgMard),
                    result.mardStd == null ? "--" : formatDouble(result.mardStd),
                    result.resultId,
                    result.jobId,
                    result.datasetId,
                    textOrDash(result.gmtCreate)));
            units.setText(formatUnits(result.summaryJson == null ? null : result.summaryJson.units));
        }

        @Override
        public void reset() {
            chart.clear();
            summary.setText("等待 CGM 结果");
            units.setText("units=[]");
        }

        private void setupCgmChart(@NonNull LineChart chart) {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setDrawGridBackground(false);
            chart.getAxisRight().setEnabled(false);

            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.0f", value);
                }
            });

            YAxis yAxis = chart.getAxisLeft();
            yAxis.setAxisMinimum(0f);
            yAxis.setAxisMaximum(15f);
            yAxis.setGranularity(3f);
            yAxis.setDrawGridLines(true);
        }

        @NonNull
        private LineDataSet cgmSet(@NonNull List<Entry> entries, @NonNull String label, int color) {
            LineDataSet set = new LineDataSet(entries, label);
            set.setColor(color);
            set.setCircleColor(color);
            set.setLineWidth(1.4f);
            set.setCircleRadius(3f);
            set.setDrawCircles(true);
            set.setDrawValues(false);
            return set;
        }

        @NonNull
        private List<CgmResult.Point> allPoints(@NonNull CgmResult data) {
            ArrayList<CgmResult.Point> points = new ArrayList<>();
            if (data.summaryJson == null || data.summaryJson.units == null) {
                return points;
            }
            for (CgmResult.Unit unit : data.summaryJson.units) {
                if (unit.points != null) {
                    points.addAll(unit.points);
                }
            }
            return points;
        }

        @NonNull
        private String formatUnits(@Nullable List<CgmResult.Unit> unitList) {
            if (unitList == null || unitList.isEmpty()) {
                return "units=[]";
            }
            StringBuilder builder = new StringBuilder();
            for (CgmResult.Unit unit : unitList) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(String.format(Locale.getDefault(),
                        "unit=%d  title=%s  points=%d  mard=%s",
                        unit.unit,
                        textOrDash(unit.unitTitle),
                        unit.pointCount,
                        formatDouble(unit.mard)));
            }
            return builder.toString();
        }
    }

    @NonNull
    private static LinearLayout card(@NonNull Context context, int orientation) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(orientation);
        v.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 10));
        v.setBackground(bg);
        v.setElevation(dp(context, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 8));
        v.setLayoutParams(lp);
        return v;
    }

    @NonNull
    private static TextView title(@NonNull Context context, @NonNull String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(Color.rgb(20, 20, 20));
        v.setTextSize(17f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    @NonNull
    private static TextView statText(@NonNull Context context, @NonNull String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.rgb(96, 96, 96));
        v.setTextSize(13f);
        return v;
    }

    @NonNull
    private static TextView statBlock(@NonNull Context context) {
        TextView v = new TextView(context);
        v.setTextColor(Color.rgb(71, 85, 105));
        v.setTextSize(12f);
        v.setPadding(0, dp(context, 6), 0, 0);
        return v;
    }

    @NonNull
    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    @NonNull
    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static String format(float value) {
        if (Math.abs(value) >= 100f) return String.format(Locale.getDefault(), "%.1f", value);
        return String.format(Locale.getDefault(), "%.3f", value);
    }

    @NonNull
    private static String formatDouble(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    @NonNull
    private static String textOrDash(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? "--" : value;
    }
}

