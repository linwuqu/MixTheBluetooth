package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.WidgetSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ActionSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.BuiltIn;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ProfileSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static ProfileSpec eis() {
        return ProfileSpec.builder("eis")
                .parser(new EisParser())
                .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
                .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
                .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
                .widget(WidgetSpec.gauge("eis_conductance_gauge")
                        .title("电导率")
                        .metric(EisSample.METRIC_US)
                        .unit("uS")
                        .region(Region.SUMMARY)
                        .order(10)
                        .gaugeMax(10f)
                        .lineColor(0xFF4EE097)
                        .build())
                .widget(WidgetSpec.value("eis_ohm_value")
                        .title("阻抗")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Region.SUMMARY)
                        .order(20)
                        .build())
                .widget(WidgetSpec.line("eis_ohm_line")
                        .title("电化学交流阻抗（EIS）")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Region.MAIN)
                        .order(10)
                        .lineColor(0xFF4285F4)
                        .build())
                .widget(WidgetSpec.line("eis_us_line")
                        .title("电导率（uS）")
                        .metric(EisSample.METRIC_US)
                        .unit("uS")
                        .region(Region.MAIN)
                        .order(20)
                        .lineColor(0xFFFBBC05)
                        .build())
                .widget(WidgetSpec.stats("eis_ohm_stats")
                        .title("阻抗统计")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Region.MAIN)
                        .order(30)
                        .build())
                .recordJson(Profiles::eisJson)
                .build();
    }

    static final class EisParser implements BluetoothSampleParser {
        private static final Pattern P = Pattern.compile(
                "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*\\u03A9\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|\\u03BCS|\\u78C1S)\\s*",
                Pattern.CASE_INSENSITIVE
        );

        @Nullable
        @Override
        public BluetoothSample parse(@Nullable String line) {
            if (line == null) return null;
            String clean = line;
            int idx = clean.lastIndexOf("dataString:");
            if (idx >= 0) clean = clean.substring(idx + "dataString:".length());
            clean = clean.replace("\u0000", "").trim();
            Matcher m = P.matcher(clean);
            if (!m.find()) return null;
            try {
                float ohm = Float.parseFloat(m.group(1));
                float us = Float.parseFloat(m.group(2));
                return new EisSample(ohm, us, clean);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static class EisSample implements BluetoothSample {
        public static final String TYPE = "eis";
        public static final String METRIC_OHM = "ohm";
        public static final String METRIC_US = "us";

        public final float ohm;
        public final float us;
        @NonNull public final String raw;

        public EisSample(float ohm, float us, @NonNull String raw) {
            this.ohm = ohm;
            this.us = us;
            this.raw = raw;
        }

        @NonNull
        @Override
        public String type() {
            return TYPE;
        }

        @NonNull
        @Override
        public String raw() {
            return raw;
        }

        @NonNull
        @Override
        public Map<String, Float> metrics() {
            Map<String, Float> m = new LinkedHashMap<>();
            m.put(METRIC_OHM, ohm);
            m.put(METRIC_US, us);
            return m;
        }
    }

    @NonNull
    static String eisJson(@NonNull BluetoothSample sample) {
        if (!(sample instanceof EisSample)) {
            return "{\"tMs\":" + System.currentTimeMillis() + ",\"raw\":\"" + esc(sample.raw()) + "\"}";
        }
        EisSample e = (EisSample) sample;
        return "{\"tMs\":" + System.currentTimeMillis()
                + ",\"ohm\":" + e.ohm
                + ",\"us\":" + e.us
                + ",\"raw\":\"" + esc(e.raw) + "\"}";
    }

    @NonNull
    private static String esc(@Nullable String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
