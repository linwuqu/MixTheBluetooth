package com.hc.mixthebluetooth.activity.tool;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ActionSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.BuiltIn;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ChartSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ProfileSpec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static ProfileSpec eis() {
        return ProfileSpec.builder("eis")
                .parser(new EisParser())
                .chart(ChartSpec.line(EisSample.METRIC_OHM, "EIS Ohm", EisSample.METRIC_OHM, Color.RED))
                .chart(ChartSpec.line(EisSample.METRIC_US, "EIS uS", EisSample.METRIC_US, Color.BLUE))
                .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
                .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
                .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
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
