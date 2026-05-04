package com.hc.mixthebluetooth.schema.eis;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed EIS sample.
 */
public final class EisSample implements BluetoothSample {

    public static final String TYPE = "eis";
    public static final String METRIC_OHM = "ohm";
    public static final String METRIC_US = "us";

    public final float ohm;
    public final float us;
    public final String raw;

    public EisSample(float ohm, float us, String raw) {
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
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(METRIC_OHM, ohm);
        values.put(METRIC_US, us);
        return values;
    }
}
