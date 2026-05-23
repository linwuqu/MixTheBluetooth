package com.hc.mixthebluetooth.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiClient;
import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;
import com.hc.mixthebluetooth.api.AuthApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class AuthRepository {
    public interface ResultCallback {
        void onSuccess(@NonNull AccountInfo info);

        void onError(@NonNull String message);
    }

    private final AuthApi authApi;
    private final AuthSessionStore sessionStore;

    public AuthRepository(@NonNull Context context) {
        this(ApiClient.get(context).authApi(), new AuthSessionStore(context));
    }

    public AuthRepository(@NonNull AuthApi authApi, @NonNull AuthSessionStore sessionStore) {
        this.authApi = authApi;
        this.sessionStore = sessionStore;
    }

    public void register(@NonNull String username,
                         @NonNull String password,
                         @NonNull String phone,
                         @NonNull ResultCallback callback) {
        authApi.register(new AccountRegisterReq(username, password, phone, null))
                .enqueue(accountCallback(callback));
    }

    public void login(@NonNull String phone,
                      @NonNull String password,
                      @NonNull ResultCallback callback) {
        authApi.login(new AccountLoginReq(phone, password))
                .enqueue(accountCallback(callback));
    }

    public void detail(@NonNull ResultCallback callback) {
        authApi.detail().enqueue(accountCallback(callback));
    }

    public long accountId() {
        return sessionStore.accountId();
    }

    @Nullable
    public String phone() {
        return sessionStore.phone();
    }

    @Nullable
    public String token() {
        return sessionStore.token();
    }

    public void clearSession() {
        sessionStore.clear();
    }

    private Callback<JsonData<AccountInfo>> accountCallback(@NonNull ResultCallback callback) {
        return new Callback<JsonData<AccountInfo>>() {
            @Override
            public void onResponse(@NonNull Call<JsonData<AccountInfo>> call,
                                   @NonNull Response<JsonData<AccountInfo>> response) {
                JsonData<AccountInfo> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    sessionStore.save(body.data);
                    callback.onSuccess(body.data);
                } else {
                    callback.onError(body != null && body.msg != null ? body.msg : "请求失败");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonData<AccountInfo>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "网络错误");
            }
        };
    }
}
