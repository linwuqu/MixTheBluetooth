package com.hc.mixthebluetooth.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.remote.ServerClient;

public final class SessionStore implements ServerClient.TokenProvider {
    private static final String PREF = "bioai_auth_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_LAST_LOGIN_AT = "last_login_at";

    private final Store store;

    public SessionStore(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        this.store = new SharedPreferencesStore(prefs);
    }

    public SessionStore(@NonNull Store store) {
        this.store = store;
    }

    public void save(@Nullable AuthUser user) {
        if (user == null) return;
        store.putLong(KEY_ACCOUNT_ID, user.accountId);
        putString(KEY_USERNAME, user.username);
        putString(KEY_PHONE, user.phone);
        putString(KEY_TOKEN, user.token);
        store.putLong(KEY_LAST_LOGIN_AT, System.currentTimeMillis());
    }

    @NonNull
    public AuthUser currentUser() {
        AuthUser user = new AuthUser();
        user.accountId = store.getLong(KEY_ACCOUNT_ID, 0L);
        user.username = store.getString(KEY_USERNAME);
        user.phone = store.getString(KEY_PHONE);
        user.token = store.getString(KEY_TOKEN);
        return user;
    }

    @Nullable
    @Override
    public String token() {
        return store.getString(KEY_TOKEN);
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

    public interface Store {
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
