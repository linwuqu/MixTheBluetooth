package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PreferencesStore {
    void putString(@NonNull String key, @Nullable String value);

    @Nullable
    String getString(@NonNull String key, @Nullable String defaultValue);

    void putBoolean(@NonNull String key, boolean value);

    boolean getBoolean(@NonNull String key, boolean defaultValue);

    void putLong(@NonNull String key, long value);

    long getLong(@NonNull String key, long defaultValue);

    void putInt(@NonNull String key, int value);

    int getInt(@NonNull String key, int defaultValue);

    void remove(@NonNull String key);

    void clear();
}
