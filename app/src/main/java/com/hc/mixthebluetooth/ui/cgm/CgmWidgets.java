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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.ui.shared.view.CircleProgressView;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.hc.mixthebluetooth.ui.cgm.CgmController.Region;

import java.text.ParseException;
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
        CGM_RESULT,
        POINT_LIST
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
        if (spec.kind == WidgetKind.POINT_LIST) return new PointListMetricWidget(context, spec);
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

        @NonNull
        public static Builder pointList(@NonNull String id) {
            return new Builder(id, WidgetKind.POINT_LIST).region(Region.MAIN).style(WidgetStyle.CARD);
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
                private String xAxisTimeFormat = "HH:mm";
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
        private final Context context;
        private final LinearLayout root;
        private final LinearLayout tabContainer;
        private final CgmGlucoseChart chart;

        // Hero header fields
        private final TextView heroValue;
        private final TextView heroUnit;
        private final TextView heroBadge;
        private final TextView heroSubtitle;
        private final TextView heroStatus;

        // Stat cells
        private final TextView statAverage;
        private final TextView statHigh;
        private final TextView statLow;
        private final TextView statSpan;
        private final TextView statHighSub;
        private final TextView statLowSub;
        private final TextView statSpanSub;
        private final TextView statAverageSub;

        // Bottom info
        private final TextView info;

        @Nullable private CgmResult result;
        private int selectedUnitIndex = 0;

        CgmResultMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.context = context;

            // Outer container with warm cream background to host the hero card
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.parseColor("#FFF5EFE6"));
            int pad = dp(context, 12);
            root.setPadding(pad, pad, pad, pad);

            // ---- Hero header card (dark, rounded) ----
            LinearLayout hero = new LinearLayout(context);
            hero.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor("#FF2C2620"), Color.parseColor("#FF3B2E24")});
            heroBg.setCornerRadius(dp(context, 18));
            hero.setBackground(heroBg);
            int hp = dp(context, 18);
            hero.setPadding(hp, dp(context, 16), hp, dp(context, 16));

            // Top row: badge + subtitle + status
            LinearLayout heroTop = new LinearLayout(context);
            heroTop.setOrientation(LinearLayout.HORIZONTAL);
            heroTop.setGravity(Gravity.CENTER_VERTICAL);

            heroBadge = new TextView(context);
            heroBadge.setTextSize(11f);
            heroBadge.setTextColor(Color.parseColor("#FFB7E8C2"));
            heroBadge.setPadding(dp(context, 8), dp(context, 3),
                    dp(context, 8), dp(context, 3));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#33FFFFFF"));
            badgeBg.setCornerRadius(dp(context, 8));
            heroBadge.setBackground(badgeBg);
            heroBadge.setTypeface(Typeface.DEFAULT_BOLD);
            heroBadge.setText("NORMAL");
            heroTop.addView(heroBadge);

            heroSubtitle = new TextView(context);
            heroSubtitle.setTextSize(12f);
            heroSubtitle.setTextColor(Color.parseColor("#CCCBBBA8"));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            sp.setMarginStart(dp(context, 8));
            heroSubtitle.setLayoutParams(sp);
            heroSubtitle.setText("--");
            heroTop.addView(heroSubtitle);

            heroStatus = new TextView(context);
            heroStatus.setTextSize(11f);
            heroStatus.setTextColor(Color.parseColor("#FFB39A7A"));
            heroStatus.setText("");
            heroTop.addView(heroStatus);

            hero.addView(heroTop);

            // Big value row
            LinearLayout valueRow = new LinearLayout(context);
            valueRow.setOrientation(LinearLayout.HORIZONTAL);
            valueRow.setGravity(Gravity.BOTTOM);
            valueRow.setPadding(0, dp(context, 14), 0, 0);

            heroValue = new TextView(context);
            heroValue.setTextSize(56f);
            heroValue.setTextColor(Color.parseColor("#FFFFFAF0"));
            heroValue.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            heroValue.setText("--");
            valueRow.addView(heroValue);

            heroUnit = new TextView(context);
            heroUnit.setTextSize(14f);
            heroUnit.setTextColor(Color.parseColor("#FFB39A7A"));
            heroUnit.setPadding(dp(context, 6), 0, 0, dp(context, 14));
            heroUnit.setText("mmol/L");
            valueRow.addView(heroUnit);

            hero.addView(valueRow);

            LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            heroLp.setMargins(0, 0, 0, dp(context, 12));
            root.addView(hero, heroLp);

            // ---- Time range tabs (1H / 3H / 6H / 12H / 24H) ----
            tabContainer = new LinearLayout(context);
            tabContainer.setOrientation(LinearLayout.HORIZONTAL);
            tabContainer.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tabsLp.setMargins(0, 0, 0, dp(context, 8));
            tabContainer.setLayoutParams(tabsLp);
            root.addView(tabContainer);

            // ---- Custom CGM-style chart ----
            chart = new CgmGlucoseChart(context);
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 220));
            chartLp.setMargins(0, 0, 0, dp(context, 12));
            chart.setLayoutParams(chartLp);
            root.addView(chart);

            // ---- Stats grid (2x2) ----
            LinearLayout row1 = new LinearLayout(context);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            android.util.Pair<TextView, TextView> avgCard =
                    makeStatCard(row1, "Average", "--", "mmol/L");
            android.util.Pair<TextView, TextView> highCard =
                    makeStatCard(row1, "Time High", "--", "above 7.8");
            statAverage = avgCard.first;
            statAverageSub = avgCard.second;
            statHigh = highCard.first;
            statHighSub = highCard.second;

            LinearLayout row2 = new LinearLayout(context);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            android.util.Pair<TextView, TextView> lowCard =
                    makeStatCard(row2, "Time Low", "--", "below 3.9");
            android.util.Pair<TextView, TextView> spanCard =
                    makeStatCard(row2, "Range", "--", "duration");
            statLow = lowCard.first;
            statLowSub = lowCard.second;
            statSpan = spanCard.first;
            statSpanSub = spanCard.second;

            LinearLayout statsGrid = new LinearLayout(context);
            statsGrid.setOrientation(LinearLayout.VERTICAL);
            statsGrid.addView(row1);
            statsGrid.addView(row2);
            root.addView(statsGrid);

            // ---- Footer info ----
            info = new TextView(context);
            info.setTextSize(11f);
            info.setTextColor(Color.parseColor("#FF8A7A60"));
            info.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), 0);
            info.setText("等待 CGM 结果");
            root.addView(info);
        }

        @NonNull
        private android.util.Pair<TextView, TextView> makeStatCard(@NonNull LinearLayout row,
                                                                   @NonNull String label,
                                                                   @NonNull String value,
                                                                   @NonNull String sub) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dp(context, 14));
            cardBg.setStroke(dp(context, 1), Color.parseColor("#FFE0D6C8"));
            card.setBackground(cardBg);

            int cp = dp(context, 14);
            card.setPadding(cp, cp, cp, cp);
            card.setElevation(dp(context, 1));

            TextView titleView = new TextView(context);
            titleView.setText(label);
            titleView.setTextSize(11f);
            titleView.setTextColor(Color.parseColor("#FF8A7060"));
            card.addView(titleView);

            TextView valueView = new TextView(context);
            valueView.setText(value);
            valueView.setTextSize(22f);
            valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            valueView.setTextColor(Color.parseColor("#FF1E1A14"));
            valueView.setPadding(0, dp(context, 4), 0, dp(context, 2));
            card.addView(valueView);

            TextView subView = new TextView(context);
            subView.setText(sub);
            subView.setTextSize(10f);
            subView.setTextColor(Color.parseColor("#FFB39A7A"));
            card.addView(subView);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(context, 5), 0, dp(context, 5), dp(context, 8));
            card.setLayoutParams(lp);
            row.addView(card);
            return new android.util.Pair<>(valueView, subView);
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
            this.result = result;
            this.selectedUnitIndex = 0;

            ApiTraceLogger.text("CgmWidgetBinder", "RENDER", "result",
                    "jobId=" + result.jobId
                            + "\nstatus=" + result.status
                            + "\npoints=" + result.pointCount
                            + "\nunitCount=" + result.unitCount);

            buildTabs(result);
            drawSelectedUnit();

            info.setText(String.format(Locale.getDefault(),
                    "Result #%d · Job %d · Dataset %d",
                    result.resultId, result.jobId, result.datasetId));
        }

        private void buildTabs(@NonNull CgmResult result) {
            tabContainer.removeAllViews();
            List<CgmResult.Unit> units = result.summaryJson == null
                    ? Collections.emptyList()
                    : (result.summaryJson.units == null
                        ? Collections.emptyList()
                        : result.summaryJson.units);

            if (units.isEmpty()) {
                tabContainer.addView(makeRangeTab(context, "无数据", 0, true));
                return;
            }

            for (int i = 0; i < units.size(); i++) {
                CgmResult.Unit u = units.get(i);
                String label = u.unitTitle != null && !u.unitTitle.isEmpty()
                        ? u.unitTitle
                        : ("Unit " + u.unit);
                boolean selected = (i == selectedUnitIndex);
                tabContainer.addView(makeRangeTab(context, label, i, selected));
            }
        }

        @NonNull
        private TextView makeRangeTab(@NonNull Context ctx, @NonNull String label,
                                      int index, boolean selected) {
            TextView tab = new TextView(ctx);
            tab.setText(label);
            tab.setTextSize(13f);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            int padH = dp(ctx, 16);
            int padV = dp(ctx, 8);
            tab.setPadding(padH, padV, padH, padV);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(ctx, 10));
            if (selected) {
                bg.setColor(Color.parseColor("#FF1E1A14"));
                bg.setStroke(0, Color.TRANSPARENT);
                tab.setTextColor(Color.parseColor("#FFFFFAF0"));
            } else {
                bg.setColor(Color.parseColor("#FFFFFFFF"));
                bg.setStroke(dp(ctx, 1), Color.parseColor("#FFE6DED0"));
                tab.setTextColor(Color.parseColor("#FF6B5C4A"));
            }
            tab.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f);
            lp.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0);
            tab.setLayoutParams(lp);

            tab.setOnClickListener(v -> {
                if (index == selectedUnitIndex) return;
                selectedUnitIndex = index;
                if (result != null) {
                    buildTabs(result);
                    drawSelectedUnit();
                }
            });
            return tab;
        }

        private void drawSelectedUnit() {
            if (result == null) return;
            List<CgmResult.Unit> units = result.summaryJson == null
                    ? Collections.emptyList()
                    : (result.summaryJson.units == null
                        ? Collections.emptyList()
                        : result.summaryJson.units);
            if (selectedUnitIndex < 0 || selectedUnitIndex >= units.size()) return;

            CgmResult.Unit unit = units.get(selectedUnitIndex);
            if (unit.points == null || unit.points.isEmpty()) {
                chart.clear();
                return;
            }

            chart.setData(unit.points);

            // Hero: show last predicted value (most recent)
            CgmResult.Point first = null;
            CgmResult.Point last = null;
            for (CgmResult.Point p : unit.points) {
                if (first == null) first = p;
                last = p;
            }
            if (last != null) {
                float v = (float) last.predicted;
                heroValue.setText(String.format(Locale.getDefault(), "%.2f", v));
                if (v >= 7.8f) {
                    heroBadge.setText("HIGH");
                    heroBadge.setTextColor(Color.parseColor("#FFFFC1A6"));
                } else if (v <= 3.9f) {
                    heroBadge.setText("LOW");
                    heroBadge.setTextColor(Color.parseColor("#FFFFE7B0"));
                } else {
                    heroBadge.setText("NORMAL");
                    heroBadge.setTextColor(Color.parseColor("#FFB7E8C2"));
                }
                heroSubtitle.setText(unit.unitTitle == null ? "Unit" : unit.unitTitle);
            }

            // Range / duration
            if (first != null && last != null) {
                long spanMs = parseTimestamp(last.rawTime) - parseTimestamp(first.rawTime);
                if (spanMs > 0L) {
                    long mins = spanMs / 60_000L;
                    long hrs = mins / 60;
                    long remMins = mins % 60;
                    statSpan.setText(String.format(Locale.getDefault(),
                            "%dh %02dm", hrs, remMins));
                } else {
                    statSpan.setText("--");
                }
            }

            // Average + Time High + Time Low
            double sum = 0;
            int n = 0;
            int highCount = 0, lowCount = 0;
            for (CgmResult.Point p : unit.points) {
                double v = p.predicted;
                sum += v;
                n++;
                if (v >= 7.8f) highCount++;
                else if (v <= 3.9f) lowCount++;
            }
            if (n > 0) {
                statAverage.setText(String.format(Locale.getDefault(), "%.2f", sum / n));
                int total = unit.points.size();
                statHigh.setText(String.format(Locale.getDefault(), "%d%%",
                        (int) Math.round(100.0 * highCount / total)));
                statLow.setText(String.format(Locale.getDefault(), "%d%%",
                        (int) Math.round(100.0 * lowCount / total)));
            }
        }

        private long parseTimestamp(@Nullable String rawTime) {
            if (rawTime == null || rawTime.isEmpty()) return 0L;
            try {
                if (rawTime.length() == 19) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .parse(rawTime).getTime();
                }
                if (rawTime.length() == 16) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            .parse(rawTime).getTime();
                }
            } catch (ParseException ignored) {
            }
            return 0L;
        }

        @Override
        public void reset() {
            chart.clear();
            tabContainer.removeAllViews();
            heroValue.setText("--");
            heroBadge.setText("NORMAL");
            heroBadge.setTextColor(Color.parseColor("#FFB7E8C2"));
            heroSubtitle.setText("--");
            statAverage.setText("--");
            statHigh.setText("--");
            statLow.setText("--");
            statSpan.setText("--");
            info.setText("等待 CGM 结果");
            result = null;
            selectedUnitIndex = 0;
        }
    }

    /**
     * Collapsible card listing every predicted glucose point as "rawTime · value".
     * Sourced from the same {@link CgmResult.Unit#points} as {@link CgmResultMetricWidget}'s
     * chart, so values stay in sync regardless of which time-range tab is active.
     */
    private static final class PointListMetricWidget implements MetricWidget {
        // Same band edges as CgmGlucoseChart (mmol/L).
        private static final float RANGE_LOW  = 3.9f;
        private static final float RANGE_HIGH = 7.8f;

        private static final int COLOR_BG          = Color.parseColor("#FFFAF6EE");
        private static final int COLOR_BORDER      = Color.parseColor("#FFE0D6C8");
        private static final int COLOR_HEADER_BG   = Color.parseColor("#FFF8F2EC");
        private static final int COLOR_TITLE       = Color.parseColor("#FF3D2E24");
        private static final int COLOR_SUBTITLE    = Color.parseColor("#FF8A7060");
        private static final int COLOR_ARROW       = Color.parseColor("#FFA0A8B0");
        private static final int COLOR_BAR         = Color.parseColor("#FFA0A8B0");
        private static final int COLOR_DIVIDER     = Color.parseColor("#FFF0E8DC");
        private static final int COLOR_TIME        = Color.parseColor("#FF6B5C4A");
        private static final int COLOR_VALUE_NORMAL = Color.parseColor("#FF1E1A14");
        private static final int COLOR_VALUE_HIGH   = Color.parseColor("#FFC0352B");
        private static final int COLOR_VALUE_LOW    = Color.parseColor("#FFB07A1F");
        private static final int COLOR_EMPTY        = Color.parseColor("#FFA09080");

        private final Context context;
        private final LinearLayout root;
        private final LinearLayout headerRow;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView arrowView;
        private final LinearLayout body;
        private final TextView emptyView;
        private final PointAdapter adapter;

        @Nullable private CgmResult result;
        private int selectedUnitIndex = 0;
        private boolean expanded = false;

        PointListMetricWidget(@NonNull Context context, @NonNull WidgetSpec spec) {
            this.context = context;

            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.TRANSPARENT);

            // Margin between this card and the previous one in the same region.
            LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rootLp.setMargins(dp(context, 10), dp(context, 6),
                    dp(context, 10), dp(context, 6));
            root.setLayoutParams(rootLp);
            root.setTag("cgm_point_list_card");

            // ---- Header (clickable, mirrors stream-card style: cream bg, 14dp radius,
            //      1dp #E0D6C8 border). The rounded background also covers the body
            //      by being applied to root, but body paints over it later. ----
            headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(COLOR_HEADER_BG);
            headerBg.setCornerRadius(dp(context, 14));
            headerBg.setStroke(dp(context, 1), COLOR_BORDER);
            headerRow.setBackground(headerBg);
            int hp = dp(context, 14);
            headerRow.setPadding(hp, dp(context, 10), hp, dp(context, 10));

            View bar = new View(context);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    dp(context, 3), dp(context, 14));
            bar.setBackgroundColor(COLOR_BAR);
            bar.setLayoutParams(barLp);
            headerRow.addView(bar);

            titleView = new TextView(context);
            titleView.setText(spec.title == null || spec.title.isEmpty()
                    ? "血糖数据明细" : spec.title);
            titleView.setTextColor(COLOR_TITLE);
            titleView.setTextSize(14f);
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.setMarginStart(dp(context, 8));
            titleView.setLayoutParams(titleLp);
            headerRow.addView(titleView);

            subtitleView = new TextView(context);
            subtitleView.setTextColor(COLOR_SUBTITLE);
            subtitleView.setTextSize(11f);
            headerRow.addView(subtitleView);

            arrowView = new TextView(context);
            arrowView.setText("›");
            arrowView.setTextColor(COLOR_ARROW);
            arrowView.setTextSize(16f);
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            arrowLp.setMarginStart(dp(context, 6));
            arrowView.setLayoutParams(arrowLp);
            arrowView.setRotation(0f);
            headerRow.addView(arrowView);

            root.addView(headerRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // ---- Body (RecyclerView with bounded height) ----
            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            // Match the fragment background so the expanded list visually "flows"
            // out of the header the same way the stream card does.
            body.setBackgroundColor(Color.parseColor("#FFF5EFE6"));
            int bp = dp(context, 10);
            body.setPadding(bp, dp(context, 8), bp, dp(context, 8));
            // Bounded height so the parent ScrollView scrolls the page, not just this list.
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 280));
            body.setLayoutParams(bodyLp);
            body.setVisibility(View.GONE);
            root.addView(body);

            adapter = new PointAdapter();
            RecyclerView rv = new RecyclerView(context);
            rv.setLayoutManager(new LinearLayoutManager(context));
            rv.setAdapter(adapter);
            rv.setNestedScrollingEnabled(true);
            body.addView(rv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            emptyView = new TextView(context);
            emptyView.setText("暂无数据");
            emptyView.setTextColor(COLOR_EMPTY);
            emptyView.setTextSize(12f);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, dp(context, 24), 0, dp(context, 24));
            emptyView.setVisibility(View.GONE);
            body.addView(emptyView);

            // Toggle expand/collapse. Always clickable: empty body shows "暂无数据".
            headerRow.setOnClickListener(v -> {
                expanded = !expanded;
                applyExpandedState();
            });

            // Initial placeholder state: header visible, subtitle reads "等待 CGM 结果",
            // body hidden. Once onCgmResult() runs with real data it updates these.
            subtitleView.setText("等待 CGM 结果");
            arrowView.setTextColor(COLOR_ARROW);
            headerRow.setClickable(true);
            emptyView.setVisibility(View.GONE);
            root.setVisibility(View.VISIBLE);
            applyExpandedState();
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
            this.result = result;
            this.selectedUnitIndex = 0;
            renderForSelectedUnit();
        }

        private void renderForSelectedUnit() {
            if (result == null) return;
            List<CgmResult.Unit> units = unitsOf(result);
            if (units.isEmpty()) {
                adapter.submit(new ArrayList<>());
                subtitleView.setText("0 条");
                emptyView.setVisibility(View.VISIBLE);
                arrowView.setTextColor(COLOR_ARROW);
                headerRow.setClickable(true);
                return;
            }
            if (selectedUnitIndex < 0 || selectedUnitIndex >= units.size()) {
                selectedUnitIndex = 0;
            }
            CgmResult.Unit unit = units.get(selectedUnitIndex);
            String label = unit.unitTitle != null && !unit.unitTitle.isEmpty()
                    ? unit.unitTitle : ("Unit " + unit.unit);
            List<CgmResult.Point> src = unit.points;
            if (src == null) src = Collections.emptyList();

            // Sort ascending by time so the list reads earliest → latest.
            ArrayList<CgmResult.Point> sorted = new ArrayList<>(src.size());
            for (CgmResult.Point p : src) sorted.add(p);
            Collections.sort(sorted, (a, b) -> {
                long ta = parseTimestamp(a.rawTime);
                long tb = parseTimestamp(b.rawTime);
                if (ta == 0L && tb == 0L) return 0;
                if (ta == 0L) return 1;
                if (tb == 0L) return -1;
                return Long.compare(ta, tb);
            });

            adapter.submit(sorted);
            subtitleView.setText(label + " · " + sorted.size() + " 条");
            emptyView.setVisibility(View.GONE);
            arrowView.setTextColor(COLOR_ARROW);
            headerRow.setClickable(true);
            // If data is now available and we previously had no header action, leave
            // current expanded state alone — preserves the user's preference.
        }

        private void applyExpandedState() {
            List<CgmResult.Unit> units = result == null ? null : unitsOf(result);
            // body contains either the point list or the emptyView placeholder; both
            // are valid in expanded state. We just need to know whether a unit was
            // resolved so we don't try to index into an empty list.
            boolean resolved = units != null && !units.isEmpty();
            if (!resolved) {
                // No data yet (or no units). Still allow expanding — emptyView shows "暂无数据".
                body.setVisibility(expanded ? View.VISIBLE : View.GONE);
                arrowView.setRotation(expanded ? 90f : 0f);
                emptyView.setVisibility(View.VISIBLE);
                return;
            }
            CgmResult.Unit unit = units.get(selectedUnitIndex < units.size() ? selectedUnitIndex : 0);
            boolean hasData = unit.points != null && !unit.points.isEmpty();
            emptyView.setVisibility(hasData ? View.GONE : View.VISIBLE);
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            arrowView.setRotation(expanded ? 90f : 0f);
        }

        private static List<CgmResult.Unit> unitsOf(@NonNull CgmResult result) {
            if (result.summaryJson == null || result.summaryJson.units == null) {
                return Collections.emptyList();
            }
            return result.summaryJson.units;
        }

        private static int colorForValue(double v) {
            if (v >= RANGE_HIGH) return COLOR_VALUE_HIGH;
            if (v <= RANGE_LOW)  return COLOR_VALUE_LOW;
            return COLOR_VALUE_NORMAL;
        }

        @Override
        public void reset() {
            result = null;
            selectedUnitIndex = 0;
            expanded = false;
            adapter.submit(new ArrayList<>());
            subtitleView.setText("");
            emptyView.setVisibility(View.GONE);
            applyExpandedState();
        }

        private long parseTimestamp(@Nullable String rawTime) {
            if (rawTime == null || rawTime.isEmpty()) return 0L;
            try {
                if (rawTime.length() == 19) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .parse(rawTime).getTime();
                }
                if (rawTime.length() == 16) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            .parse(rawTime).getTime();
                }
            } catch (ParseException ignored) {
            }
            return 0L;
        }

        private static int dp(@NonNull Context context, int dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }

        // ---- Adapter ----
        private static final class PointAdapter
                extends RecyclerView.Adapter<PointAdapter.VH> {

            private final ArrayList<CgmResult.Point> data = new ArrayList<>();

            void submit(@NonNull List<CgmResult.Point> next) {
                data.clear();
                data.addAll(next);
                notifyDataSetChanged();
            }

            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                Context ctx = parent.getContext();
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));

                TextView time = new TextView(ctx);
                time.setTextSize(11f);
                time.setTextColor(COLOR_TIME);
                row.addView(time, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView value = new TextView(ctx);
                value.setTextSize(13f);
                value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                value.setGravity(Gravity.END);
                value.setMinWidth(dp(ctx, 64));
                row.addView(value, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                // Thin divider.
                View divider = new View(ctx);
                divider.setBackgroundColor(COLOR_DIVIDER);
                LinearLayout container = new LinearLayout(ctx);
                container.setOrientation(LinearLayout.VERTICAL);
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                container.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
                return new VH(container, time, value);
            }

            @Override
            public void onBindViewHolder(@NonNull VH h, int position) {
                CgmResult.Point p = data.get(position);
                h.time.setText(p.rawTime == null || p.rawTime.isEmpty() ? "--" : p.rawTime);
                String v = String.format(Locale.getDefault(), "%.2f", p.predicted);
                h.value.setText(v);
                h.value.setTextColor(colorForValue(p.predicted));
            }

            @Override
            public int getItemCount() {
                return data.size();
            }

            static final class VH extends RecyclerView.ViewHolder {
                final TextView time;
                final TextView value;
                VH(@NonNull View itemView, @NonNull TextView time, @NonNull TextView value) {
                    super(itemView);
                    this.time = time;
                    this.value = value;
                }
            }
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

