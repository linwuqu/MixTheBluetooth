package com.hc.mixthebluetooth.api;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;

public final class AuthSessionStore {
    private static final String PREF = "bioai_auth_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_LAST_LOGIN_AT = "last_login_at";

    private final Store store;

    public AuthSessionStore(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        this.store = new SharedPreferencesStore(prefs);
    }

    AuthSessionStore(@NonNull Store store) {
        this.store = store;
    }

    public void save(@Nullable AccountInfo info) {
        if (info == null) return;
        store.putLong(KEY_ACCOUNT_ID, info.accountId);
        putString(KEY_USERNAME, info.username);
        putString(KEY_PHONE, info.phone);
        putString(KEY_AVATAR_URL, info.avatarUrl);
        putString(KEY_TOKEN, info.token);
        store.putLong(KEY_LAST_LOGIN_AT, System.currentTimeMillis());
    }

    @Nullable
    public String token() {
        return store.getString(KEY_TOKEN);
    }

    public long accountId() {
        return store.getLong(KEY_ACCOUNT_ID, 0L);
    }

    @Nullable
    public String phone() {
        return store.getString(KEY_PHONE);
    }

    public void clear() {
        store.clear();
    }

    private void putString(@NonNull String key, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            store.remove(key);
        } else {
            store.putString(key, value);
        }
    }

    interface Store {
        void putString(@NonNull String key, @NonNull String value);

        void putLong(@NonNull String key, long value);

        @Nullable
        String getString(@NonNull String key);

        long getLong(@NonNull String key, long defaultValue);

        void remove(@NonNull String key);

        void clear();
    }

    private static final class SharedPreferencesStore implements Store {
        private final SharedPreferences prefs;

        SharedPreferencesStore(@NonNull SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            prefs.edit().putString(key, value).apply();
        }

        @Override
        public void putLong(@NonNull String key, long value) {
            prefs.edit().putLong(key, value).apply();
        }

        @Nullable
        @Override
        public String getString(@NonNull String key) {
            return prefs.getString(key, null);
        }

        @Override
        public long getLong(@NonNull String key, long defaultValue) {
            return prefs.getLong(key, defaultValue);
        }

        @Override
        public void remove(@NonNull String key) {
            prefs.edit().remove(key).apply();
        }

        @Override
        public void clear() {
            prefs.edit().clear().apply();
        }
    }
}
