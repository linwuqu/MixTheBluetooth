package com.hc.mixthebluetooth.api.auth;

import com.hc.mixthebluetooth.api.CallResult;

import java.util.function.Consumer;

public interface AuthService {
    void register(String username, String password, String phone, Consumer<CallResult<AuthUser>> callback);

    void login(String phoneOrAccount, String password, Consumer<CallResult<AuthUser>> callback);

    void detail(Consumer<CallResult<AuthUser>> callback);

    AuthUser currentUser();

    void clearSession();
}
