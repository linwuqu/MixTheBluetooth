package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CgmCommandSet {

    private CgmCommandSet() {
    }

    @NonNull
    public static String startNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
        return startWithTime(sdf.format(new Date()));
    }

    @NonNull
    public static String startWithTime(@NonNull String formattedTime) {
        return "TIME," + formattedTime + "\n\r";
    }

    @NonNull
    public static String readCache() {
        return "ALL\n\r";
    }

    @NonNull
    public static String deleteCache() {
        return "DELETE\n\r";
    }

    @NonNull
    public static String buildParameters(@NonNull CgmParameters params) {
        return padLeft(params.controlRatio, "000")
                + "0010" + params.extractionTime
                + padLeft(params.highLevelTime, "010")
                + "011" + params.voltage
                + padLeft(params.detectionTime, "100");
    }

    @NonNull
    static String padLeft(@NonNull String value, @NonNull String prefix) {
        String clean = value.trim();
        if (clean.length() >= prefix.length()) return clean;
        return prefix.substring(0, prefix.length() - clean.length()) + clean;
    }
}
