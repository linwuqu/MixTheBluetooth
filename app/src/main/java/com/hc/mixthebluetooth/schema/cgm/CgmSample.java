package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CgmSample implements BluetoothSample {

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

    @NonNull
    public final String event;
    @NonNull
    public final String raw;
    @Nullable
    public final String status;
    @NonNull
    private final Map<String, Float> metrics;

    public CgmSample(
            @NonNull String event,
            @NonNull String raw,
            @Nullable String status,
            @NonNull Map<String, Float> metrics
    ) {
        this.event = event;
        this.raw = raw;
        this.status = status;
        this.metrics = new LinkedHashMap<>(metrics);
    }

    @NonNull
    public static CgmSample event(@NonNull String event, @NonNull String raw) {
        return new CgmSample(event, raw, null, Collections.emptyMap());
    }

    @NonNull
    public static CgmSample status(@NonNull String event, @NonNull String raw, @NonNull String status) {
        return new CgmSample(event, raw, status, Collections.emptyMap());
    }

    @NonNull
    public static CgmSample metric(@NonNull String event, @NonNull String raw, @NonNull String key, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(key, value);
        return new CgmSample(event, raw, event, values);
    }

    @NonNull
    public static CgmSample current(@NonNull String raw, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(METRIC_PRIMARY, value);
        values.put(METRIC_CURRENT, value);
        return new CgmSample(EVENT_CURRENT, raw, EVENT_CURRENT, values);
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
        return Collections.unmodifiableMap(metrics);
    }
}
