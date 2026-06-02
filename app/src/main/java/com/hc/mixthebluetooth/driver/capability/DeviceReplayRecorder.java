package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;

import java.io.File;

public interface DeviceReplayRecorder {
    @NonNull
    CallResult<File> consumeLine(@Nullable String line);

    @Nullable
    File currentFile();
}
