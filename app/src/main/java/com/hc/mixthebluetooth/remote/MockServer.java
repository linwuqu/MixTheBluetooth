package com.hc.mixthebluetooth.remote;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class MockServer implements ServerEndpoints {
    @Override
    public Call<ServerResponse<ServerModels.AccountResp>> login(ServerModels.LoginReq req) {
        return new FakeCall<>(ServerResponse.success(account("mock-user", req.phone)));
    }

    @Override
    public Call<ServerResponse<ServerModels.AccountResp>> register(ServerModels.RegisterReq req) {
        return new FakeCall<>(ServerResponse.success(account(req.username, req.phone)));
    }

    @Override
    public Call<ServerResponse<ServerModels.AccountResp>> detail() {
        return new FakeCall<>(ServerResponse.success(account("mock-user", "13800138000")));
    }

    @Override
    public Call<ServerResponse<ServerModels.FileResp>> upload(RequestBody fileName,
                                                              RequestBody identify,
                                                              RequestBody parentId,
                                                              RequestBody fileSize,
                                                              MultipartBody.Part file) {
        ServerModels.FileResp resp = new ServerModels.FileResp();
        resp.fileId = 1L;
        resp.fileName = "CGM_Cache_data.txt";
        resp.path = "/mock/CGM_Cache_data.txt";
        return new FakeCall<>(ServerResponse.success(resp));
    }

    private static ServerModels.AccountResp account(String username, String phone) {
        ServerModels.AccountResp account = new ServerModels.AccountResp();
        account.accountId = 1001L;
        account.username = username;
        account.phone = phone;
        account.token = "mock-token";
        return account;
    }

    public static final class FakeCall<T> implements Call<T> {
        private final T body;
        private boolean executed;
        private boolean canceled;

        public FakeCall(T body) {
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
