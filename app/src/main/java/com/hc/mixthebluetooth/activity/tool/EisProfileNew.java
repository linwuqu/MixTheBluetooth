package com.hc.mixthebluetooth.activity.tool;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class EisProfileNew implements DeviceProfile<FragmentMessageNewBinding> {
    public static final String CHART_EIS_OHM = "ohm";
    public static final String CHART_EIS_US = "us";

    @Override public List<BluetoothSampleParser> parsers() {
        List<BluetoothSampleParser> l = new ArrayList<>();
        l.add(new EisProfile.EisProfileParser());
        return l;
    }

    @Override public void registerCharts(@NonNull FragmentMessageNewBinding binding, @NonNull HashMap<String, RealtimeLineChart> charts) {
        charts.put(CHART_EIS_OHM, new RealtimeLineChart(binding.chartOhm,
                new RealtimeLineChart.Config.Builder().label("EIS Ohm").color(Color.RED).maxPoints(500).visibleWindowSeconds(60f).build()));
        charts.put(CHART_EIS_US, new RealtimeLineChart(binding.chartUs,
                new RealtimeLineChart.Config.Builder().label("EIS uS").color(Color.BLUE).maxPoints(500).visibleWindowSeconds(60f).build()));
    }

    @Override public List<SampleConsumer> consumers(@NonNull FragmentMessageNewBinding binding, @NonNull SampleRecorder recorder) {
        List<SampleConsumer> l = new ArrayList<>();
        l.add(new EisProfile.EisProfileRecorderConsumer(recorder));
        return l;
    }

    @Override public void registerControls(@NonNull FragmentMessageNewBinding binding, @NonNull HashMap<Integer, Runnable> controls) {
    }
}
