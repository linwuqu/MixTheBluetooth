package com.hc.mixthebluetooth.debug;

import android.view.View;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.ApiEnvironment;
import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.auth.AuthRepository;
import com.hc.mixthebluetooth.auth.AuthSessionStore;
import com.hc.mixthebluetooth.cgm.CgmReplayUploadUseCase;
import com.hc.mixthebluetooth.databinding.ActivityApiDebugBinding;

import java.io.File;

public class DiagnosticsActivity extends BaseActivity<ActivityApiDebugBinding> {
    private AuthSessionStore sessionStore;
    private AuthRepository authRepository;

    @Override
    public void initAll() {
        sessionStore = new AuthSessionStore(this);
        authRepository = new AuthRepository(this);
        new DefaultNavigationBar.Builder(this, findViewById(R.id.api_debug_activity))
                .setTitle("Diagnostics")
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
        append("点击按钮会走正式 Repository/UseCase。mock 构建不会访问网络。");
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
        authRepository.register("debug-user", "123456", "13800138000", accountCallback("register"));
    }

    private void login() {
        append("login -> request");
        authRepository.login("13800138000", "123456", accountCallback("login"));
    }

    private void detail() {
        append("detail -> request");
        authRepository.detail(accountCallback("detail"));
    }

    private AuthRepository.ResultCallback accountCallback(String tag) {
        return new AuthRepository.ResultCallback() {
            @Override
            public void onSuccess(@NonNull AccountInfo info) {
                append(tag + " <- success " + describeAccount(info));
                renderEnv();
            }

            @Override
            public void onError(@NonNull String message) {
                append(tag + " <- failed " + message);
                renderEnv();
            }
        };
    }

    private void upload() {
        append("replay fixture -> cgm/cgm_playback_sample.txt");
        new CgmReplayUploadUseCase(this).replayAssetAndUpload(
                "cgm/cgm_playback_sample.txt",
                new CgmReplayUploadUseCase.ResultCallback() {
            @Override
            public void onSuccess(@NonNull File file, @NonNull FileUploadResp resp) {
                append("upload <- success fileId=" + resp.fileId
                        + ", fileName=" + resp.fileName
                        + ", local=" + file.getAbsolutePath()
                        + ", path=" + nullToDash(resp.path)
                        + ", url=" + nullToDash(resp.url));
            }

            @Override
            public void onError(@NonNull String message) {
                append("upload <- failed " + message);
            }
        });
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
