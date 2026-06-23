package com.hc.mixthebluetooth.api.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.auth.AuthUser;

public interface SessionStore {
    /**
     * 本地 token 有效期：7 天，镜像后端 JwtUtil.EXPIRED。
     * 如果服务端改了 EXPIRED，本常量也需要同步修改。
     */
    long LOCAL_TOKEN_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * 保存当前用户及 token，同时写入本地过期时间 = now + LOCAL_TOKEN_TTL_MS。
     */
    void save(@Nullable AuthUser user);

    /**
     * 保存当前用户并显式指定过期时间戳（毫秒）。传 0 表示立即过期。
     */
    void save(@Nullable AuthUser user, long expiresAtMillis);

    @NonNull
    AuthUser currentUser();

    @Nullable
    String token();

    /**
     * token 非空 且 未到过期时间。
     */
    boolean isTokenValid();

    /**
     * 清除 token 和用户信息。
     */
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
