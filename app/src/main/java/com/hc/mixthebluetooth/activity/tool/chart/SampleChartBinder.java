package com.hc.mixthebluetooth.activity.tool.chart;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds parsed sample metric keys to registered realtime charts.
 */
public final class SampleChartBinder {

    private final ChartRegistry chartRegistry;
    private final Map<String, String> metricToChart = new LinkedHashMap<>();

    public SampleChartBinder(@NonNull ChartRegistry chartRegistry) {
        this.chartRegistry = chartRegistry;
    }

    @NonNull
    public SampleChartBinder bind(@NonNull String metricKey, @NonNull String chartKey) {
        metricToChart.put(metricKey, chartKey);
        return this;
    }

    public void append(@NonNull BluetoothSample sample) {
        Map<String, Float> metrics = sample.metrics();
        for (Map.Entry<String, String> entry : metricToChart.entrySet()) {
            Float value = metrics.get(entry.getKey());
            if (value != null) {
                chartRegistry.append(entry.getValue(), value);
            }
        }
    }
}
