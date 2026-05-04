package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered parser registry. The first parser that understands a line wins.
 */
public final class BluetoothSampleRegistry {

    private final List<BluetoothSampleParser> parsers = new ArrayList<>();

    @NonNull
    public BluetoothSampleRegistry register(@NonNull BluetoothSampleParser parser) {
        parsers.add(parser);
        return this;
    }

    @Nullable
    public BluetoothSample parse(@Nullable String line) {
        for (BluetoothSampleParser parser : parsers) {
            BluetoothSample sample = parser.parse(line);
            if (sample != null) return sample;
        }
        return null;
    }
}
