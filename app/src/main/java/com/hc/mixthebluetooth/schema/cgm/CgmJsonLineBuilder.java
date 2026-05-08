package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;

public final class CgmJsonLineBuilder {

    private CgmJsonLineBuilder() {
    }

    @NonNull
    public static String build(@Nullable DeviceModule module, @NonNull CgmSample sample) {
        String mac = module != null ? module.getMac() : "";
        String name = module != null ? module.getName() : "";
        Float primary = sample.metrics().get(CgmSample.METRIC_PRIMARY);
        Float current = sample.metrics().get(CgmSample.METRIC_CURRENT);

        return "{"
                + "\"tMs\":" + System.currentTimeMillis()
                + ",\"mac\":\"" + escapeJson(mac) + "\""
                + ",\"name\":\"" + escapeJson(name) + "\""
                + ",\"event\":\"" + escapeJson(sample.event) + "\""
                + ",\"status\":\"" + escapeJson(sample.status) + "\""
                + ",\"primary\":" + (primary == null ? "null" : primary)
                + ",\"current\":" + (current == null ? "null" : current)
                + ",\"raw\":\"" + escapeJson(sample.raw) + "\""
                + "}";
    }

    @NonNull
    public static String escapeJson(@Nullable String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
