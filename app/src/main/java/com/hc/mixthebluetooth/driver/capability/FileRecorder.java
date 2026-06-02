package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;

import java.io.File;

public interface FileRecorder {
    void start();

    void appendLine(@NonNull String line);

    @NonNull
    File finish();

    void reset();
}
