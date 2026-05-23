package com.hc.mixthebluetooth.activity;

import android.view.View;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.ApiClient;
import com.hc.mixthebluetooth.api.ApiEnvironment;
import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;
import com.hc.mixthebluetooth.api.AuthSessionStore;
import com.hc.mixthebluetooth.api.FileUploadUseCase;
import com.hc.mixthebluetooth.databinding.ActivityApiDebugBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiDebugActivity extends BaseActivity<ActivityApiDebugBinding> {
    private AuthSessionStore sessionStore;

    @Override
    public void initAll() {
        sessionStore = new AuthSessionStore(this);
        new DefaultNavigationBar.Builder(this, findViewById(R.id.api_debug_activity))
                .setTitle("API 调试")
                .hideLeftText()
                .hideRightText()
                .builer();
        bindClickListener(
                viewBinding.apiDebugRegister,
                viewBinding.apiDebugLogin,
                viewBinding.apiDebugDetail,
                viewBinding.apiDebugUpload
        );
        renderEnv();
        renderDocs();
        append("点击按钮可以直接走当前构建环境的 ApiClient。mock 构建不会访问网络。");
    }

    @Override
    protected ActivityApiDebugBinding getViewBinding() {
        return ActivityApiDebugBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onClickView(View view) {
        if (isCheck(viewBinding.apiDebugRegister)) {
            register();
        } else if (isCheck(viewBinding.apiDebugLogin)) {
            login();
        } else if (isCheck(viewBinding.apiDebugDetail)) {
            detail();
        } else if (isCheck(viewBinding.apiDebugUpload)) {
            upload();
        }
    }

    private void renderEnv() {
        viewBinding.apiDebugEnv.setText(
                "env: " + ApiEnvironment.env()
                        + "\nbaseUrl: " + ApiEnvironment.baseUrl()
                        + "\nuseMock: " + ApiEnvironment.useMock()
                        + "\nsession.accountId: " + sessionStore.accountId()
                        + "\nsession.phone: " + nullToDash(sessionStore.phone())
                        + "\nsession.token: " + nullToDash(sessionStore.token())
        );
    }

    private void renderDocs() {
        viewBinding.apiDebugDocs.setText(
                "接口路径\n"
                        + "POST /api/account/v1/register\n"
                        + "POST /api/account/v1/login\n"
                        + "GET  /api/account/v1/detail\n"
                        + "POST /api/file/v1/upload\n\n"
                        + "模拟器访问本机后端使用 10.0.2.2，例如 -PapiEnv=dev。"
        );
    }

    private void register() {
        append("register -> request");
        AccountRegisterReq req = new AccountRegisterReq("debug-user", "123456", "13800138000", null);
        ApiClient.get(this).authApi().register(req).enqueue(accountCallback("register"));
    }

    private void login() {
        append("login -> request");
        AccountLoginReq req = new AccountLoginReq("13800138000", "123456");
        ApiClient.get(this).authApi().login(req).enqueue(accountCallback("login"));
    }

    private void detail() {
        append("detail -> request");
        ApiClient.get(this).authApi().detail().enqueue(accountCallback("detail"));
    }

    private Callback<JsonData<AccountInfo>> accountCallback(String tag) {
        return new Callback<JsonData<AccountInfo>>() {
            @Override
            public void onResponse(@NonNull Call<JsonData<AccountInfo>> call,
                                   @NonNull Response<JsonData<AccountInfo>> response) {
                JsonData<AccountInfo> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    sessionStore.save(body.data);
                    append(tag + " <- success " + describeAccount(body.data));
                } else {
                    append(tag + " <- failed " + (body != null && body.msg != null ? body.msg : response.code()));
                }
                renderEnv();
            }

            @Override
            public void onFailure(@NonNull Call<JsonData<AccountInfo>> call, @NonNull Throwable t) {
                append(tag + " <- error " + (t.getMessage() != null ? t.getMessage() : "network error"));
            }
        };
    }

    private void upload() {
        File file;
        try {
            file = createDebugCacheFile();
        } catch (IOException e) {
            append("upload <- create file error " + e.getMessage());
            return;
        }

        append("upload -> " + file.getAbsolutePath() + " (" + file.length() + " B)");
        new FileUploadUseCase(this).uploadRootFile(file, new FileUploadUseCase.ResultCallback() {
            @Override
            public void onSuccess(@NonNull FileUploadResp resp) {
                append("upload <- success fileId=" + resp.fileId
                        + ", fileName=" + resp.fileName
                        + ", path=" + nullToDash(resp.path)
                        + ", url=" + nullToDash(resp.url));
            }

            @Override
            public void onError(@NonNull String message) {
                append("upload <- failed " + message);
            }
        });
    }

    private File createDebugCacheFile() throws IOException {
        File file = new File(getCacheDir(), "CGM_Cache_data.txt");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write("Start Playback\n");
            writer.write("EIS:1,1000,0.12\n");
            writer.write("CA:1,0.08\n");
        }
        return file;
    }

    private void append(String line) {
        CharSequence old = viewBinding.apiDebugOutput.getText();
        String prefix = old == null || old.length() == 0 ? "" : old + "\n";
        viewBinding.apiDebugOutput.setText(prefix + line);
    }

    private static String describeAccount(AccountInfo info) {
        if (info == null) return "empty account";
        return "accountId=" + info.accountId
                + ", username=" + nullToDash(info.username)
                + ", phone=" + nullToDash(info.phone)
                + ", token=" + nullToDash(info.token);
    }

    private static String nullToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
