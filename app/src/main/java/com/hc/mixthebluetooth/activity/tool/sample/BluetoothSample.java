package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * Common shape for one parsed bluetooth data sample.
 */
public interface BluetoothSample {

    @NonNull
    String type();

    @NonNull
    String raw();

    @NonNull
    Map<String, Float> metrics();
}
