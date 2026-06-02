package com.hc.mixthebluetooth.ui.auth;

import android.view.View;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.databinding.ActivityAccountRegisterBinding;

public class RegisterActivity extends BaseActivity<ActivityAccountRegisterBinding> {
    @Override
    public void initAll() {
        new DefaultNavigationBar.Builder(this, findViewById(R.id.account_register_activity))
                .setTitle("账号注册")
                .hideLeftText()
                .hideRightText()
                .builer();
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
}
