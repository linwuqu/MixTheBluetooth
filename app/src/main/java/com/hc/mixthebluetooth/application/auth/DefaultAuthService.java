package com.hc.mixthebluetooth.application.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerDtos;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultAuthService implements AuthService {
    private static final String OWNER = "DefaultAuthService";
    private static final String API_REGISTER = "POST /api/account/v1/register";
    private static final String API_LOGIN = "POST /api/account/v1/login";
    private static final String API_DETAIL = "GET /api/account/v1/detail";

    private final BioAiEndpoints endpoints;
    private final SessionStore sessionStore;

    public DefaultAuthService(@NonNull BioAiEndpoints endpoints, @NonNull SessionStore sessionStore) {
        this.endpoints = endpoints;
        this.sessionStore = sessionStore;
    }

    @Override
    public void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_REGISTER, "request",
                ApiTraceLogger.maskedAuthBody(username, phone, password));
        endpoints.register(new ServerDtos.RegisterReq(username, password, phone, null))
                .enqueue(accountCallback(API_REGISTER, callback));
    }

    @Override
    public void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_LOGIN, "request",
                ApiTraceLogger.maskedAuthBody(null, phoneOrAccount, password));
        endpoints.login(new ServerDtos.LoginReq(phoneOrAccount, password))
                .enqueue(accountCallback(API_LOGIN, callback));
    }

    @Override
    public void detail(ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.text(OWNER, API_DETAIL, "request", "{}");
        endpoints.detail().enqueue(accountCallback(API_DETAIL, callback));
    }

    @Override
    public AuthUser currentUser() {
        return sessionStore.currentUser();
    }

    @Override
    public void clearSession() {
        sessionStore.clear();
    }

    private Callback<ServerResponse<ServerDtos.AccountResp>> accountCallback(
            @NonNull String api,
            @NonNull ApiCallback<CallResult<AuthUser>> callback) {
        return new Callback<ServerResponse<ServerDtos.AccountResp>>() {
            @Override
            public void onResponse(@NonNull Call<ServerResponse<ServerDtos.AccountResp>> call,
                                   @NonNull Response<ServerResponse<ServerDtos.AccountResp>> response) {
                ApiTraceLogger.json(OWNER, api, "response", response.body());
                CallResult<AuthUser> result = mapAccountResponse(response.body());
                if (result.isOk()) {
                    sessionStore.save(result.data);
                }
                callback.onResult(result);
            }

            @Override
            public void onFailure(@NonNull Call<ServerResponse<ServerDtos.AccountResp>> call,
                                  @NonNull Throwable t) {
                ApiTraceLogger.text(OWNER, api, "failure", failureText(t));
                callback.onResult(CallResult.error(CallResult.NETWORK, "网络错误", t));
            }
        };
    }

    @NonNull
    private static String failureText(@NonNull Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    @NonNull
    private static CallResult<AuthUser> mapAccountResponse(@Nullable ServerResponse<ServerDtos.AccountResp> response) {
        if (response == null) {
            return CallResult.error(CallResult.EMPTY_RESPONSE, "Empty server response", null);
        }
        if (!response.success) {
            return CallResult.error(response.code, response.msg != null ? response.msg : "Request failed", null);
        }
        if (response.data == null) {
            return CallResult.error(CallResult.EMPTY_DATA, "Empty account data", null);
        }
        return CallResult.ok(toUser(response.data));
    }

    @NonNull
    private static AuthUser toUser(@NonNull ServerDtos.AccountResp resp) {
        AuthUser user = new AuthUser();
        user.accountId = resp.accountId;
        user.username = resp.username;
        user.phone = resp.phone;
        user.token = resp.token;
        return user;
    }
}
