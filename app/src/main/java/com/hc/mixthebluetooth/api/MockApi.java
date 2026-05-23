package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import java.io.IOException;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class MockApi {
    private MockApi() {
    }

    public static AuthApi authApi() {
        return new AuthApi() {
            @Override
            public Call<JsonData<AccountInfo>> register(AccountRegisterReq req) {
                return new FakeCall<>(success(mockAccount(req.username, req.phone)));
            }

            @Override
            public Call<JsonData<AccountInfo>> login(AccountLoginReq req) {
                return new FakeCall<>(success(mockAccount("mock-user", req.phone)));
            }

            @Override
            public Call<JsonData<AccountInfo>> detail() {
                return new FakeCall<>(success(mockAccount("mock-user", "13800138000")));
            }
        };
    }

    public static FileApi fileApi() {
        return (fileName, identify, parentId, fileSize, file) -> {
            FileUploadResp resp = new FileUploadResp();
            resp.fileId = 1L;
            resp.fileName = "CGM_Cache_data.txt";
            resp.path = "/mock/CGM_Cache_data.txt";
            return new FakeCall<>(success(resp));
        };
    }

    private static AccountInfo mockAccount(String username, String phone) {
        AccountInfo info = new AccountInfo();
        info.accountId = 1001L;
        info.username = username;
        info.phone = phone;
        info.token = "mock-token";
        return info;
    }

    private static <T> JsonData<T> success(T data) {
        JsonData<T> json = new JsonData<>();
        json.code = 0;
        json.data = data;
        json.msg = "";
        json.success = true;
        return json;
    }

    static final class FakeCall<T> implements Call<T> {
        private final T body;
        private boolean executed;
        private boolean canceled;

        FakeCall(T body) {
            this.body = body;
        }

        @Override
        public Response<T> execute() throws IOException {
            executed = true;
            return Response.success(body);
        }

        @Override
        public void enqueue(@NonNull Callback<T> callback) {
            executed = true;
            callback.onResponse(this, Response.success(body));
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @NonNull
        @Override
        public Call<T> clone() {
            return new FakeCall<>(body);
        }

        @NonNull
        @Override
        public Request request() {
            return new Request.Builder().url("http://mock.local/").build();
        }

        @NonNull
        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
