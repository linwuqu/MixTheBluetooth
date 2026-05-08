package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CgmSample implements BluetoothSample {
    public static final String TYPE = "cgm";
    public static final String METRIC_PRIMARY = "primary";
    public static final String METRIC_CURRENT = "current";
    public static final String EVENT_CACHE_START = "cache_start";
    public static final String EVENT_CACHE_DONE = "cache_done";
    public static final String EVENT_CACHE_LINE = "cache_line";
    public static final String EVENT_CA = "ca";
    public static final String EVENT_EIS = "eis";
    public static final String EVENT_RI = "ri";
    public static final String EVENT_CURRENT = "current";
    public static final String EVENT_BG = "bg";
    public static final String EVENT_TR = "tr";

    public final String event;
    @NonNull public final String raw;
    @Nullable public final String status;
    private final Map<String, Float> metrics;

    CgmSample(@NonNull String event, @NonNull String raw, @Nullable String status, Map<String, Float> metrics) {
        this.event = event;
        this.raw = raw;
        this.status = status;
        this.metrics = new LinkedHashMap<>(metrics);
    }

    public static CgmSample event(@NonNull String event, @NonNull String raw) {
        return new CgmSample(event, raw, null, Collections.emptyMap());
    }

    public static CgmSample status(@NonNull String event, @NonNull String raw, @NonNull String status) {
        return new CgmSample(event, raw, status, Collections.emptyMap());
    }

    public static CgmSample metric(@NonNull String event, @NonNull String raw, @NonNull String key, float value) {
        Map<String, Float> m = new LinkedHashMap<>();
        m.put(key, value);
        return new CgmSample(event, raw, event, m);
    }

    public static CgmSample current(@NonNull String raw, float value) {
        Map<String, Float> m = new LinkedHashMap<>();
        m.put(METRIC_PRIMARY, value);
        m.put(METRIC_CURRENT, value);
        return new CgmSample(EVENT_CURRENT, raw, EVENT_CURRENT, m);
    }

    @NonNull @Override public String type() { return TYPE; }
    @NonNull @Override public String raw() { return raw; }
    @NonNull @Override public Map<String, Float> metrics() { return Collections.unmodifiableMap(metrics); }
}
