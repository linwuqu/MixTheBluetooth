package com.hc.mixthebluetooth.impl.auth;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

public final class StaticAuthService implements AuthService {
    private static final String OWNER = "StaticAuthService";
    private static final String API_REGISTER = "POST /api/account/v1/register";
    private static final String API_LOGIN = "POST /api/account/v1/login";
    private static final String API_DETAIL = "GET /api/account/v1/detail";

    private final SessionStore sessionStore;

    public StaticAuthService(@NonNull SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_REGISTER, "request",
                ApiTraceLogger.maskedAuthBody(username, phone, password));
        AuthUser user = StaticBioAiFixtures.authUser();
        ApiTraceLogger.json(OWNER, API_REGISTER, "response", user);
        callback.onResult(CallResult.ok(user));
    }

    @Override
    public void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_LOGIN, "request",
                ApiTraceLogger.maskedAuthBody(null, phoneOrAccount, password));
        AuthUser user = StaticBioAiFixtures.authUser();
        sessionStore.save(user);
        ApiTraceLogger.json(OWNER, API_LOGIN, "response", user);
        callback.onResult(CallResult.ok(user));
    }

    @Override
    public void detail(ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.text(OWNER, API_DETAIL, "request", "{}");
        AuthUser user = sessionStore.currentUser();
        if (user.token == null || user.token.trim().isEmpty()) {
            user = StaticBioAiFixtures.authUser();
            sessionStore.save(user);
        }
        ApiTraceLogger.json(OWNER, API_DETAIL, "response", user);
        callback.onResult(CallResult.ok(user));
    }

    @Override
    public AuthUser currentUser() {
        return sessionStore.currentUser();
    }

    @Override
    public void clearSession() {
        sessionStore.clear();
    }
}
