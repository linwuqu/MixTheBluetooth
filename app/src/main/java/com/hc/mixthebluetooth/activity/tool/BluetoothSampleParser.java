package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.Nullable;

public interface BluetoothSampleParser {
    @Nullable BluetoothSample parse(@Nullable String line);
}
