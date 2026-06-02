package com.hc.mixthebluetooth.driver.implementation.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;

public final class AndroidAppLogger implements AppLogger {
    @Override
    public void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message) {
        ApiTraceLogger.text(owner, api, stage, message);
    }

    @Override
    public void json(@NonNull String owner, @NonNull String api, @NonNull String stage, @Nullable Object value) {
        ApiTraceLogger.json(owner, api, stage, value);
    }
}
