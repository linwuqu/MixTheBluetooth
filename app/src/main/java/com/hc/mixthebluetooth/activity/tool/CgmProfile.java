package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CgmProfile implements DeviceProfile<FragmentMessageBinding> {
    public static final String CHART_PRIMARY = "cgm_primary";
    public static final String CHART_CURRENT = "cgm_current";

    @Override public List<BluetoothSampleParser> parsers() {
        List<BluetoothSampleParser> l = new ArrayList<>();
        l.add(new CgmProfileParser());
        return l;
    }

    @Override public void registerCharts(@NonNull FragmentMessageBinding binding, @NonNull HashMap<String, RealtimeLineChart> charts) {
        charts.put(CHART_PRIMARY, new RealtimeLineChart(binding.chartPrimary,
                new RealtimeLineChart.Config.Builder().label("CGM").color(Color.RED).maxPoints(500).visibleWindowSeconds(60f).build()));
        charts.put(CHART_CURRENT, new RealtimeLineChart(binding.chartCurrent,
                new RealtimeLineChart.Config.Builder().label("Current").color(Color.BLUE).maxPoints(500).visibleWindowSeconds(60f).build()));
    }

    @Override public List<SampleConsumer> consumers(@NonNull FragmentMessageBinding binding, @NonNull SampleRecorder recorder) {
        List<SampleConsumer> l = new ArrayList<>();
        l.add(new CgmProfileStatusConsumer(binding.tvStatus));
        l.add(new CgmProfileCurrentValueConsumer(binding.tvCurrentValue));
        l.add(new CgmProfileFileConsumer(binding.getRoot().getContext()));
        l.add(new CgmProfileRecorderConsumer(recorder));
        return l;
    }

    @Override public void registerControls(@NonNull FragmentMessageBinding binding, @NonNull HashMap<Integer, Runnable> controls) {
        controls.put(binding.btnStartMeasure.getId(), () -> {});
        controls.put(binding.btnReadCache.getId(), () -> {});
        controls.put(binding.btnDeleteCache.getId(), () -> {});
        controls.put(binding.btnParams.getId(), () -> {});
    }

    static final class CgmProfileParser implements BluetoothSampleParser {
        private static final Pattern P = Pattern.compile("(.+?)\\s*=\\s*([+-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

        @Nullable @Override public BluetoothSample parse(@Nullable String line) {
            String c = clean(line);
            if (c == null) return null;
            String[] v = vals(c);
            if (c.contains("BG")) {
                if (v.length > 1) { Float f = pf(v[1]); if (f != null) return CgmSample.metric(CgmSample.EVENT_BG, c, CgmSample.METRIC_PRIMARY, f); }
            }
            if (c.contains("TR")) {
                if (v.length > 1) { Float f = pf(v[1]); if (f != null) return CgmSample.metric(CgmSample.EVENT_TR, c, CgmSample.METRIC_PRIMARY, f); }
            }
            if (c.contains("CA")) {
                String[] v2 = vals(c);
                if (v2.length > 1) { Float f = pf(v2[1]); if (f != null) return c.contains("CA:266") ? CgmSample.current(c, f) : CgmSample.metric(CgmSample.EVENT_CA, c, CgmSample.METRIC_PRIMARY, f); }
            }
            return null;
        }

        @Nullable private static String clean(@Nullable String line) {
            if (line == null) return null;
            String c = line.replace("\u0000", "").trim();
            return c.isEmpty() ? null : c;
        }

        private static String[] vals(String s) {
            String[] p = s.split(":");
            return p.length < 2 ? new String[0] : p[1].split(",");
        }

        @Nullable private static Float pf(String t) {
            try { return Float.parseFloat(t.trim()); } catch (NumberFormatException e) { return null; }
        }
    }

    static final class CgmProfileStatusConsumer implements SampleConsumer {
        private final TextView v;
        CgmProfileStatusConsumer(TextView v) { this.v = v; }
        @Override public void consume(@NonNull BluetoothSample s) {
            if (!(s instanceof CgmSample)) return;
            CgmSample c = (CgmSample) s;
            v.setText(c.status != null && !c.status.isEmpty() ? c.status : c.event);
        }
    }

    static final class CgmProfileCurrentValueConsumer implements SampleConsumer {
        private final TextView v;
        CgmProfileCurrentValueConsumer(TextView v) { this.v = v; }
        @Override public void consume(@NonNull BluetoothSample s) {
            if (!(s instanceof CgmSample)) return;
            Float val = s.metrics().get(CgmSample.METRIC_CURRENT);
            if (val != null) v.setText(String.valueOf(val));
        }
    }

    static final class CgmProfileFileConsumer implements SampleConsumer {
        private final Context ctx;
        CgmProfileFileConsumer(Context c) { this.ctx = c; }
        @Override public void consume(@NonNull BluetoothSample s) {
            if (!(s instanceof CgmSample)) return;
            CgmSample c = (CgmSample) s;
            if (CgmSample.EVENT_CACHE_START.equals(c.event) || CgmSample.EVENT_CACHE_LINE.equals(c.event) || CgmSample.EVENT_CACHE_DONE.equals(c.event)) {
                String dir = String.valueOf(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
                Analysis.IO_input_data(c.raw, dir, "CGM_Cache_data.txt");
            }
        }
    }

    static final class CgmProfileRecorderConsumer implements SampleConsumer {
        private final SampleRecorder r;
        CgmProfileRecorderConsumer(SampleRecorder r) { this.r = r; }
        @Override public void consume(@NonNull BluetoothSample s) {
            if (!r.isRecording() || !(s instanceof CgmSample)) return;
            CgmSample c = (CgmSample) s;
            Float primary = c.metrics().get(CgmSample.METRIC_PRIMARY);
            Float current = c.metrics().get(CgmSample.METRIC_CURRENT);
            r.appendLine("{\"tMs\":" + System.currentTimeMillis()
                    + ",\"event\":\"" + esc(c.event) + "\""
                    + ",\"status\":\"" + esc(c.status) + "\""
                    + ",\"primary\":" + (primary == null ? "null" : primary)
                    + ",\"current\":" + (current == null ? "null" : current)
                    + ",\"raw\":\"" + esc(c.raw) + "\"}");
        }
        private static String esc(@Nullable String t) {
            if (t == null) return "";
            return t.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
        }
    }
}
