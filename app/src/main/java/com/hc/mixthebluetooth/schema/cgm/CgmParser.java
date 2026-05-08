package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleParser;

public final class CgmParser implements BluetoothSampleParser {

    private boolean readingCache;

    @Nullable
    @Override
    public BluetoothSample parse(@Nullable String line) {
        String clean = clean(line);
        if (clean == null) return null;

        CgmSample sample = parseCleanLine(clean);
        if (sample != null) {
            if (CgmSample.EVENT_CACHE_START.equals(sample.event)) {
                readingCache = true;
            } else if (CgmSample.EVENT_CACHE_DONE.equals(sample.event)) {
                readingCache = false;
            }
            return sample;
        }

        if (readingCache) {
            return CgmSample.event(CgmSample.EVENT_CACHE_LINE, clean);
        }

        return null;
    }

    @Nullable
    public static CgmSample parseLine(@Nullable String line) {
        String clean = clean(line);
        if (clean == null) return null;
        return parseCleanLine(clean);
    }

    @Nullable
    private static CgmSample parseCleanLine(String clean) {
        if (clean.contains("Start Playback")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_START, clean);
        }

        if (clean.contains("Playback all done")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_DONE, clean);
        }

        if (clean.contains("RI")) {
            return CgmSample.status(CgmSample.EVENT_RI, clean, clean);
        }

        if (clean.contains("EIS")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 2) {
                Float value = parseFloat(values[2]);
                if (value != null) {
                    return CgmSample.metric(CgmSample.EVENT_EIS, clean, CgmSample.METRIC_PRIMARY, value);
                }
            }
            return null;
        }

        if (clean.contains("CA")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 1) {
                Float value = parseFloat(values[1]);
                if (value != null) {
                    if (clean.contains("CA:266")) {
                        return CgmSample.current(clean, value);
                    }
                    return CgmSample.metric(CgmSample.EVENT_CA, clean, CgmSample.METRIC_PRIMARY, value);
                }
            }
        }

        return null;
    }

    @Nullable
    private static String clean(@Nullable String line) {
        if (line == null) return null;
        String clean = line.replace("\u0000", "").trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String[] valuesAfterColon(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return new String[0];
        return parts[1].split(",");
    }

    @Nullable
    private static Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
