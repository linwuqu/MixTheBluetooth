package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class EisSample implements BluetoothSample {
    public static final String TYPE = "eis";
    public static final String METRIC_OHM = "ohm";
    public static final String METRIC_US = "us";

    public final float ohm, us;
    @NonNull public final String raw;

    EisSample(float ohm, float us, @NonNull String raw) { this.ohm = ohm; this.us = us; this.raw = raw; }

    @NonNull @Override public String type() { return TYPE; }
    @NonNull @Override public String raw() { return raw; }
    @NonNull @Override public Map<String, Float> metrics() {
        Map<String, Float> m = new LinkedHashMap<>();
        m.put(METRIC_OHM, ohm);
        m.put(METRIC_US, us);
        return m;
    }
}
