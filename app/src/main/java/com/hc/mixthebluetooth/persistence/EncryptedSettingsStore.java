package com.hc.mixthebluetooth.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.driver.capability.PreferencesStore;

import java.util.Map;

public final class EncryptedSettingsStore implements SettingsStore {
    private static final String SECURE_PREF = "bioai_settings_secure";
    private static final String LEGACY_PREF = "storage";

    private static final String KEY_TEXT_ENCODING = "settings.text_encoding";
    private static final String KEY_FIRST_LAUNCH = "settings.first_launch";
    private static final String KEY_DEVICE_FILTER_ENABLED = "settings.device_filter_enabled";
    private static final String KEY_MIGRATED = "settings.migrated_from_plaintext";

    private static final String LEGACY_TEXT_ENCODING = "codedFormatKey";
    private static final String LEGACY_FIRST_LAUNCH = "firstTimeStartKey";

    private final PreferencesStore store;

    public static EncryptedSettingsStore create(@NonNull Context context) {
        Context app = context.getApplicationContext();
        EncryptedSettingsStore secure = new EncryptedSettingsStore(
                new EncryptedPreferencesStore(app, SECURE_PREF)
        );
        secure.migrateLegacyPlaintext(app);
        return secure;
    }

    public EncryptedSettingsStore(@NonNull PreferencesStore store) {
        this.store = store;
    }

    @NonNull
    @Override
    public String textEncoding() {
        String encoding = store.getString(KEY_TEXT_ENCODING, "GBK");
        return encoding == null || encoding.trim().isEmpty() ? "GBK" : encoding;
    }

    @Override
    public void setTextEncoding(@NonNull String encoding) {
        store.putString(KEY_TEXT_ENCODING, encoding);
    }

    @Override
    public boolean firstLaunch() {
        return store.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    @Override
    public void setFirstLaunch(boolean firstLaunch) {
        store.putBoolean(KEY_FIRST_LAUNCH, firstLaunch);
    }

    @Override
    public boolean deviceFilterEnabled() {
        return store.getBoolean(KEY_DEVICE_FILTER_ENABLED, true);
    }

    @Override
    public void setDeviceFilterEnabled(boolean enabled) {
        store.putBoolean(KEY_DEVICE_FILTER_ENABLED, enabled);
    }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        store.putBoolean(key, value);
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        return store.getBoolean(key, defaultValue);
    }

    @Override
    public void putString(@NonNull String key, @Nullable String value) {
        store.putString(key, value);
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        return store.getString(key, defaultValue);
    }

    @Override
    public void putInt(@NonNull String key, int value) {
        store.putInt(key, value);
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        return store.getInt(key, defaultValue);
    }

    private void migrateLegacyPlaintext(@NonNull Context context) {
        if (store.getBoolean(KEY_MIGRATED, false)) return;

        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE);
        Map<String, ?> legacyValues = legacy.getAll();
        for (Map.Entry<String, ?> entry : legacyValues.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                store.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                store.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                store.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                store.putLong(entry.getKey(), (Long) value);
            }
        }

        if (legacy.contains(LEGACY_TEXT_ENCODING)) {
            String encoding = legacy.getString(LEGACY_TEXT_ENCODING, "GBK");
            if (encoding != null) setTextEncoding(encoding);
        }
        if (legacy.contains(LEGACY_FIRST_LAUNCH)) {
            setFirstLaunch(legacy.getBoolean(LEGACY_FIRST_LAUNCH, true));
        }

        store.putBoolean(KEY_MIGRATED, true);
        legacy.edit().clear().apply();
    }
}
