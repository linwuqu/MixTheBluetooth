package com.hc.mixthebluetooth.api.auth;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;

public interface AuthService {
    void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback);

    void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback);

    void detail(ApiCallback<CallResult<AuthUser>> callback);

    AuthUser currentUser();

    void clearSession();
}
