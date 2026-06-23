package com.hc.mixthebluetooth.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.persistence.SessionStore;

public final class EncryptedSessionStore implements SessionStore {
    private static final String SECURE_PREF = "bioai_auth_session_secure";
    private static final String LEGACY_PREF = "bioai_auth_session";

    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_LAST_LOGIN_AT = "last_login_at";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final Store store;

    public static EncryptedSessionStore create(@NonNull Context context) {
        Context app = context.getApplicationContext();
        EncryptedSessionStore secure = new EncryptedSessionStore(
                new EncryptedPreferencesStore(app, SECURE_PREF)
        );
        secure.migrateLegacyPlaintext(app);
        return secure;
    }

    public EncryptedSessionStore(@NonNull Store store) {
        this.store = store;
    }

    @Override
    public void save(@Nullable AuthUser user) {
        save(user, System.currentTimeMillis() + LOCAL_TOKEN_TTL_MS);
    }

    @Override
    public void save(@Nullable AuthUser user, long expiresAtMillis) {
        if (user == null) return;
        store.putLong(KEY_ACCOUNT_ID, user.accountId);
        putString(KEY_USERNAME, user.username);
        putString(KEY_PHONE, user.phone);
        putString(KEY_TOKEN, user.token);
        store.putLong(KEY_LAST_LOGIN_AT, System.currentTimeMillis());
        store.putLong(KEY_EXPIRES_AT, expiresAtMillis);
    }

    @NonNull
    @Override
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

    @Override
    public boolean isTokenValid() {
        String t = token();
        if (t == null || t.trim().isEmpty()) return false;
        long expiresAt = store.getLong(KEY_EXPIRES_AT, 0L);
        if (expiresAt <= 0L) return false;
        return System.currentTimeMillis() < expiresAt;
    }

    @Override
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

    private void migrateLegacyPlaintext(@NonNull Context context) {
        if (token() != null) return;

        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE);
        String legacyToken = legacy.getString(KEY_TOKEN, null);
        if (legacyToken == null || legacyToken.trim().isEmpty()) return;

        AuthUser user = new AuthUser();
        user.accountId = legacy.getLong(KEY_ACCOUNT_ID, 0L);
        user.username = legacy.getString(KEY_USERNAME, null);
        user.phone = legacy.getString(KEY_PHONE, null);
        user.token = legacyToken;
        save(user);
        legacy.edit().clear().apply();
    }
}
