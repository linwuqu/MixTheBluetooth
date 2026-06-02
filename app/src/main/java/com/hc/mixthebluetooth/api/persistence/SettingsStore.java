package com.hc.mixthebluetooth.api.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface SettingsStore {
    @NonNull
    String textEncoding();

    void setTextEncoding(@NonNull String encoding);

    boolean firstLaunch();

    void setFirstLaunch(boolean firstLaunch);

    boolean deviceFilterEnabled();

    void setDeviceFilterEnabled(boolean enabled);

    void putBoolean(@NonNull String key, boolean value);

    boolean getBoolean(@NonNull String key, boolean defaultValue);

    void putString(@NonNull String key, @Nullable String value);

    @Nullable
    String getString(@NonNull String key, @Nullable String defaultValue);

    void putInt(@NonNull String key, int value);

    int getInt(@NonNull String key, int defaultValue);
}
