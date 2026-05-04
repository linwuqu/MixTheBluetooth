package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for multiple realtime line charts in one Fragment.
 */
public class ChartRegistry {

    private final Map<String, RealtimeLineChart> charts = new LinkedHashMap<>();

    @NonNull
    public ChartRegistry register(@NonNull String key, @NonNull LineChart chart, @NonNull RealtimeLineChart.Config config) {
        charts.put(key, new RealtimeLineChart(chart, config));
        return this;
    }

    public void append(@NonNull String key, float value) {
        RealtimeLineChart chart = charts.get(key);
        if (chart == null) return;

        chart.append(value);
    }

    public void reset(@NonNull String key) {
        RealtimeLineChart chart = charts.get(key);
        if (chart == null) return;

        chart.reset();
    }

    public void resetAll() {
        for (RealtimeLineChart chart : charts.values()) {
            chart.reset();
        }
    }

    public boolean contains(@NonNull String key) {
        return charts.containsKey(key);
    }

    @Nullable
    public RealtimeLineChart get(@NonNull String key) {
        return charts.get(key);
    }
}
