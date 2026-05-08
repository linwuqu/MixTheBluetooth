package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

public interface SampleConsumer {
    void consume(@NonNull BluetoothSample sample);
}
