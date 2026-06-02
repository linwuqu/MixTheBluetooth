package com.hc.mixthebluetooth.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.driver.capability.PreferencesStore;

import java.util.HashMap;
import java.util.Map;

public final class MemoryPreferencesStore implements PreferencesStore, SessionStore.Store {
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public void putString(@NonNull String key, @Nullable String value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @Nullable
    @Override
    public String getString(@NonNull String key) {
        return getString(key, null);
    }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    @Override
    public void putLong(@NonNull String key, long value) {
        values.put(key, value);
    }

    @Override
    public long getLong(@NonNull String key, long defaultValue) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : defaultValue;
    }

    @Override
    public void putInt(@NonNull String key, int value) {
        values.put(key, value);
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : defaultValue;
    }

    @Override
    public void remove(@NonNull String key) {
        values.remove(key);
    }

    @Override
    public void clear() {
        values.clear();
    }
}
