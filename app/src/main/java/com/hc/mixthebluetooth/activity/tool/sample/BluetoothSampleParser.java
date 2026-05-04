package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.Nullable;

/**
 * Parser for one device/protocol text format.
 */
public interface BluetoothSampleParser {

    @Nullable
    BluetoothSample parse(@Nullable String line);
}
