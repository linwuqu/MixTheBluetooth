package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * Builds JSONL records for EIS samples.
 */
public final class EisJsonLineBuilder {

    private EisJsonLineBuilder() {
    }

    @NonNull
    public static String build(@Nullable DeviceModule module, @NonNull EisSample sample) {
        String mac = module != null ? module.getMac() : "";
        String name = module != null ? module.getName() : "";

        return "{"
                + "\"tMs\":" + System.currentTimeMillis()
                + ",\"mac\":\"" + escapeJson(mac) + "\""
                + ",\"name\":\"" + escapeJson(name) + "\""
                + ",\"ohm\":" + sample.ohm
                + ",\"us\":" + sample.us
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
