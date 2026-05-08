package com.hc.mixthebluetooth.activity.tool;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EisProfile implements DeviceProfile<FragmentMessageBinding> {
    public static final String CHART_EIS_OHM = "ohm";

    @Override
    public List<BluetoothSampleParser> parsers() {
        List<BluetoothSampleParser> l = new ArrayList<>();
        l.add(new EisProfileParser());
        return l;
    }

    @Override
    public void registerCharts(@NonNull FragmentMessageBinding binding, @NonNull HashMap<String, RealtimeLineChart> charts) {
        charts.put(CHART_EIS_OHM, new RealtimeLineChart(binding.chartPrimary,
                new RealtimeLineChart.Config.Builder().label("EIS Ohm").color(Color.RED).maxPoints(500).visibleWindowSeconds(60f).build()));
    }

    @Override
    public List<SampleConsumer> consumers(@NonNull FragmentMessageBinding binding, @NonNull SampleRecorder recorder) {
        List<SampleConsumer> l = new ArrayList<>();
        l.add(new EisProfileRecorderConsumer(recorder));
        return l;
    }

    @Override
    public void registerControls(@NonNull FragmentMessageBinding binding, @NonNull HashMap<Integer, Runnable> controls) {
    }

    static final class EisProfileParser implements BluetoothSampleParser {
        private static final Pattern P = Pattern.compile(
                "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*\\u03A9\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|\u03BCS|\u78C1S)\\s*",
                Pattern.CASE_INSENSITIVE);

        @Nullable
        @Override
        public BluetoothSample parse(@Nullable String line) {
            if (line == null) return null;
            String c = line;
            int idx = c.lastIndexOf("dataString:");
            if (idx >= 0) c = c.substring(idx + "dataString:".length());
            c = c.replace("\u0000", "").trim();
            Matcher m = P.matcher(c);
            if (!m.find()) return null;
            try {
                float ohm = Float.parseFloat(m.group(1));
                float us = Float.parseFloat(m.group(2));
                return new EisSample(ohm, us, c);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    static final class EisProfileRecorderConsumer implements SampleConsumer {
        private final SampleRecorder r;

        EisProfileRecorderConsumer(SampleRecorder r) {
            this.r = r;
        }

        @Override
        public void consume(@NonNull BluetoothSample s) {
            if (!r.isRecording() || !(s instanceof EisSample)) return;
            EisSample e = (EisSample) s;
            r.appendLine("{\"tMs\":" + System.currentTimeMillis() + ",\"ohm\":" + e.ohm + ",\"us\":" + e.us + ",\"raw\":\"" + esc(e.raw) + "\"}");
        }

        private static String esc(String t) {
            if (t == null) return "";
            return t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
}
