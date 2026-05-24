package com.hc.mixthebluetooth.debug;

import android.view.View;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.EnvConfig;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.databinding.ActivityVerificationBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class VerificationActivity extends BaseActivity<ActivityVerificationBinding> {
    @Override
    public void initAll() {
        new DefaultNavigationBar.Builder(this, findViewById(R.id.verification_activity))
                .setTitle("Service API Verification")
                .hideLeftText()
                .hideRightText()
                .builer();
        bindClickListener(
                viewBinding.verificationRegister,
                viewBinding.verificationLogin,
                viewBinding.verificationDetail,
                viewBinding.verificationReplay,
                viewBinding.verificationUpload
        );
        renderEnv();
        append("通过 AppApi 调用 auth/file/device 服务。mock 环境返回静态 Call，不发出真实网络请求。");
    }

    @Override
    protected ActivityVerificationBinding getViewBinding() {
        return ActivityVerificationBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onClickView(View view) {
        if (isCheck(viewBinding.verificationRegister)) {
            register();
        } else if (isCheck(viewBinding.verificationLogin)) {
            login();
        } else if (isCheck(viewBinding.verificationDetail)) {
            detail();
        } else if (isCheck(viewBinding.verificationReplay)) {
            replaySample();
        } else if (isCheck(viewBinding.verificationUpload)) {
            uploadLastFile();
        }
    }

    private void renderEnv() {
        EnvConfig env = AppApi.env();
        AuthUser user = AppApi.auth().currentUser();
        viewBinding.verificationEnv.setText(
                "env: " + env.env
                        + "\nbaseUrl: " + env.baseUrl
                        + "\nuseMock: " + env.useMock
                        + "\ndebug: " + env.debug
                        + "\nremote: " + env.remoteName
                        + "\nnetworkEnabled: " + env.networkEnabled
                        + "\ncurrentUser: " + describeUser(user)
        );
        viewBinding.verificationHint.setText(env.networkEnabled
                ? "dev/prod 会走 Retrofit。用 Android Studio Network Inspector 观察 /api/account 与 /api/file 请求。"
                : "mock 使用 MockServer 返回固定 Call 响应；这里不会产生真实网络请求。");
    }

    private void register() {
        append("register -> AppApi.auth().register");
        AppApi.auth().register("debug-user", "123456", "13800138000",
                result -> runOnUiThread(() -> onAuthResult("register", result)));
    }

    private void login() {
        append("login -> AppApi.auth().login");
        AppApi.auth().login("13800138000", "123456",
                result -> runOnUiThread(() -> onAuthResult("login", result)));
    }

    private void detail() {
        append("detail -> AppApi.auth().detail");
        AppApi.auth().detail(result -> runOnUiThread(() -> onAuthResult("detail", result)));
    }

    private void replaySample() {
        append("replay -> AppApi.deviceData().replaySample");
        AppApi.deviceData().replaySample(result -> runOnUiThread(() -> {
            if (result.isOk() && result.data != null) {
                append("replay <- OK " + describeFile(result.data));
                append(preview(result.data));
            } else {
                append("replay <- " + describeResult(result));
            }
        }));
    }

    private void uploadLastFile() {
        append("upload -> AppApi.deviceData().uploadLastDataFile");
        AppApi.deviceData().uploadLastDataFile(result -> runOnUiThread(() -> {
            if (result.isOk() && result.data != null) {
                append("upload <- OK " + describeUpload(result.data));
            } else {
                append("upload <- " + describeResult(result));
            }
        }));
    }

    private void onAuthResult(@NonNull String tag, @NonNull CallResult<AuthUser> result) {
        if (result.isOk()) {
            append(tag + " <- OK " + describeUser(result.data));
        } else {
            append(tag + " <- " + describeResult(result));
        }
        renderEnv();
    }

    private void append(String line) {
        CharSequence old = viewBinding.verificationOutput.getText();
        String prefix = old == null || old.length() == 0 ? "" : old + "\n";
        viewBinding.verificationOutput.setText(prefix + line);
    }

    private static String describeResult(@NonNull CallResult<?> result) {
        return result.state + " code=" + result.code + " message=" + result.message;
    }

    private static String describeUser(AuthUser user) {
        if (user == null) return "-";
        return "accountId=" + user.accountId
                + ", username=" + dash(user.username)
                + ", phone=" + dash(user.phone)
                + ", token=" + dash(user.token);
    }

    private static String describeUpload(UploadedFile file) {
        return "fileId=" + file.fileId
                + ", fileName=" + dash(file.fileName)
                + ", path=" + dash(file.path)
                + ", url=" + dash(file.url);
    }

    private static String describeFile(@NonNull File file) {
        return file.getAbsolutePath() + " size=" + file.length();
    }

    private static String preview(@NonNull File file) {
        StringBuilder builder = new StringBuilder("file preview:");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (int i = 0; i < 5; i++) {
                String line = reader.readLine();
                if (line == null) break;
                builder.append("\n").append(line);
            }
        } catch (IOException e) {
            builder.append("\n读取失败: ").append(e.getMessage());
        }
        return builder.toString();
    }

    private static String dash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
