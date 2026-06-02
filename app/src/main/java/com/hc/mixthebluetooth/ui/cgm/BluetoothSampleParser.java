package com.hc.mixthebluetooth.ui.cgm;

import androidx.annotation.Nullable;

public interface BluetoothSampleParser {
    @Nullable BluetoothSample parse(@Nullable String line);
}
