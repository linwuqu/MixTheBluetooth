package com.hc.mixthebluetooth.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.driver.capability.PreferencesStore;

import java.io.IOException;
import java.security.GeneralSecurityException;

public final class EncryptedPreferencesStore implements PreferencesStore, SessionStore.Store {
    private final SharedPreferences preferences;

    public EncryptedPreferencesStore(@NonNull Context context, @NonNull String name) {
        this.preferences = createPreferences(context.getApplicationContext(), name);
    }

    private static SharedPreferences createPreferences(@NonNull Context context, @NonNull String name) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    name,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to create encrypted preferences: " + name, e);
        }
    }

    @Override
    public void putString(@NonNull String key, @Nullable String value) {
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    @Nullable
    @Override
    public String getString(@NonNull String key) {
        return getString(key, null);
    }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    @Override
    public void putLong(@NonNull String key, long value) {
        preferences.edit().putLong(key, value).apply();
    }

    @Override
    public long getLong(@NonNull String key, long defaultValue) {
        return preferences.getLong(key, defaultValue);
    }

    @Override
    public void putInt(@NonNull String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    @Override
    public void remove(@NonNull String key) {
        preferences.edit().remove(key).apply();
    }

    @Override
    public void clear() {
        preferences.edit().clear().apply();
    }
}
