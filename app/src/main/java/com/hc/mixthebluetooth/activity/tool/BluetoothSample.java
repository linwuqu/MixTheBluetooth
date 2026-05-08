package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public interface BluetoothSample {
    @NonNull String type();
    @NonNull String raw();
    @NonNull Map<String, Float> metrics();
}
