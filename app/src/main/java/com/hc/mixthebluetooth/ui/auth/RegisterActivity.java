package com.hc.mixthebluetooth.ui.auth;

import android.view.View;

import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.BuildConfig;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.databinding.ActivityAccountRegisterBinding;

public class RegisterActivity extends BaseActivity<ActivityAccountRegisterBinding> {
    private static final String DEV_USERNAME = "bioai-dev-user";
    private static final String DEV_PHONE = "18800000001";
    private static final String DEV_PASSWORD = "123456";

    @Override
    public void initAll() {
        if (useDevPlaceholders()) {
            viewBinding.registerPhone.setText(DEV_PHONE);
            viewBinding.registerUsername.setText(DEV_USERNAME);
            viewBinding.registerPassword.setText(DEV_PASSWORD);
        }
        bindClickListener(viewBinding.registerSubmit);
    }

    @Override
    protected ActivityAccountRegisterBinding getViewBinding() {
        return ActivityAccountRegisterBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onClickView(View view) {
        if (isCheck(viewBinding.registerSubmit)) {
            submit();
        }
    }

    private void submit() {
        String phone = viewBinding.registerPhone.getText().toString().trim();
        String username = viewBinding.registerUsername.getText().toString().trim();
        String password = viewBinding.registerPassword.getText().toString().trim();

        if (phone.isEmpty() || username.isEmpty() || password.isEmpty()) {
            viewBinding.registerStatus.setText("手机号、用户名和密码不能为空");
            return;
        }

        viewBinding.registerSubmit.setEnabled(false);
        viewBinding.registerStatus.setText("注册中...");

        AppApi.auth().register(username, password, phone, result -> runOnUiThread(() -> {
            viewBinding.registerSubmit.setEnabled(true);
            viewBinding.registerStatus.setText(result.isOk() ? "注册成功" : result.message);
        }));
    }

    private boolean useDevPlaceholders() {
        return BuildConfig.DEBUG
                && ("static-lan".equals(BuildConfig.API_ENV) || "lan-static".equals(BuildConfig.API_ENV));
    }
}
