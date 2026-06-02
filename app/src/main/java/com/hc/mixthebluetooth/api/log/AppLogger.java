package com.hc.mixthebluetooth.api.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface AppLogger {
    void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message);

    void json(@NonNull String owner, @NonNull String api, @NonNull String stage, @Nullable Object value);
}
