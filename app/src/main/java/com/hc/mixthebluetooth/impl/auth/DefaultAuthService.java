package com.hc.mixthebluetooth.impl.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.remote.ServerResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultAuthService implements AuthService {
    private final ServerEndpoints endpoints;
    private final SessionStore sessionStore;

    public DefaultAuthService(@NonNull ServerEndpoints endpoints, @NonNull SessionStore sessionStore) {
        this.endpoints = endpoints;
        this.sessionStore = sessionStore;
    }

    @Override
    public void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback) {
        endpoints.register(new ServerModels.RegisterReq(username, password, phone, null))
                .enqueue(accountCallback(callback));
    }

    @Override
    public void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback) {
        endpoints.login(new ServerModels.LoginReq(phoneOrAccount, password))
                .enqueue(accountCallback(callback));
    }

    @Override
    public void detail(ApiCallback<CallResult<AuthUser>> callback) {
        endpoints.detail().enqueue(accountCallback(callback));
    }

    @Override
    public AuthUser currentUser() {
        return sessionStore.currentUser();
    }

    @Override
    public void clearSession() {
        sessionStore.clear();
    }

    private Callback<ServerResponse<ServerModels.AccountResp>> accountCallback(
            @NonNull ApiCallback<CallResult<AuthUser>> callback) {
        return new Callback<ServerResponse<ServerModels.AccountResp>>() {
            @Override
            public void onResponse(@NonNull Call<ServerResponse<ServerModels.AccountResp>> call,
                                   @NonNull Response<ServerResponse<ServerModels.AccountResp>> response) {
                CallResult<AuthUser> result = mapAccountResponse(response.body());
                if (result.isOk()) {
                    sessionStore.save(result.data);
                }
                callback.onResult(result);
            }

            @Override
            public void onFailure(@NonNull Call<ServerResponse<ServerModels.AccountResp>> call,
                                  @NonNull Throwable t) {
                callback.onResult(CallResult.error(CallResult.NETWORK, "网络错误", t));
            }
        };
    }

    @NonNull
    private static CallResult<AuthUser> mapAccountResponse(@Nullable ServerResponse<ServerModels.AccountResp> response) {
        if (response == null) {
            return CallResult.error(CallResult.EMPTY_RESPONSE, "服务端响应为空", null);
        }
        if (!response.success) {
            return CallResult.error(response.code, response.msg != null ? response.msg : "请求失败", null);
        }
        if (response.data == null) {
            return CallResult.error(CallResult.EMPTY_DATA, "账号数据为空", null);
        }
        return CallResult.ok(toUser(response.data));
    }

    @NonNull
    private static AuthUser toUser(@NonNull ServerModels.AccountResp resp) {
        AuthUser user = new AuthUser();
        user.accountId = resp.accountId;
        user.username = resp.username;
        user.phone = resp.phone;
        user.token = resp.token;
        return user;
    }
}
