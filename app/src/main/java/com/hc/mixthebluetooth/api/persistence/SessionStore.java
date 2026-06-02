package com.hc.mixthebluetooth.api.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.auth.AuthUser;

public interface SessionStore {
    void save(@Nullable AuthUser user);

    @NonNull
    AuthUser currentUser();

    @Nullable
    String token();

    void clear();

    interface Store {
        void putString(@NonNull String key, @NonNull String value);

        void putLong(@NonNull String key, long value);

        @Nullable
        String getString(@NonNull String key);

        long getLong(@NonNull String key, long defaultValue);

        void remove(@NonNull String key);

        void clear();
    }
}
