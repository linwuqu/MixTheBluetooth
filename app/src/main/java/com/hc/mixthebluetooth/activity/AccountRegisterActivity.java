package com.hc.mixthebluetooth.activity;

import android.view.View;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.ApiClient;
import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;
import com.hc.mixthebluetooth.api.AuthSessionStore;
import com.hc.mixthebluetooth.databinding.ActivityAccountRegisterBinding;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AccountRegisterActivity extends BaseActivity<ActivityAccountRegisterBinding> {
    private AuthSessionStore sessionStore;

    @Override
    public void initAll() {
        sessionStore = new AuthSessionStore(this);
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

        AccountRegisterReq req = new AccountRegisterReq(username, password, phone, null);
        ApiClient.get(this).authApi().register(req).enqueue(new Callback<JsonData<AccountInfo>>() {
            @Override
            public void onResponse(@NonNull Call<JsonData<AccountInfo>> call,
                                   @NonNull Response<JsonData<AccountInfo>> response) {
                viewBinding.registerSubmit.setEnabled(true);
                JsonData<AccountInfo> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    sessionStore.save(body.data);
                    viewBinding.registerStatus.setText("注册成功");
                } else {
                    viewBinding.registerStatus.setText(body != null && body.msg != null ? body.msg : "注册失败");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonData<AccountInfo>> call, @NonNull Throwable t) {
                viewBinding.registerSubmit.setEnabled(true);
                viewBinding.registerStatus.setText(t.getMessage() != null ? t.getMessage() : "网络错误");
            }
        });
    }
}
