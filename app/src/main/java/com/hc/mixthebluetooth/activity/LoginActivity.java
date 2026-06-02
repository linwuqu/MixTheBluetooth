package com.hc.mixthebluetooth.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.mixthebluetooth.BuildConfig;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    private static final String DEV_USERNAME = "bioai-dev-user";
    private static final String DEV_PHONE = "18800000001";
    private static final String DEV_PASSWORD = "123456";
    private static final String API_REGISTER = "POST /api/account/v1/register";
    private static final String API_LOGIN = "POST /api/account/v1/login";

    private TextInputEditText usernameEdt;
    private TextInputEditText passwordEdt;
    private MaterialButton loginBtn;
    private MaterialButton registerBtn;
    private MaterialButton apiDebugBtn;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    //控制权限管理
    private HomeApplication homeApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initView();
        setupAnimations();
        setupInputValidation();
        setVariable();
        homeApplication = (HomeApplication) getApplication();
    }

    private void initView() {
        usernameEdt = findViewById(R.id.username);
        passwordEdt = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);
        registerBtn = findViewById(R.id.registerBtn);
        apiDebugBtn = findViewById(R.id.apiDebugBtn);
        if (!BuildConfig.DEBUG) {
            apiDebugBtn.setVisibility(View.GONE);
        } else {
            apiDebugBtn.setText("真实 HTTP 联调");
        }
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
    }

    private void setupAnimations() {
        // 添加输入框焦点变化动画
        usernameEdt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tilUsername.setBoxStrokeColor(ContextCompat.getColor(this, R.color.colorPrimary));
            } else {
                tilUsername.setBoxStrokeColor(ContextCompat.getColor(this, R.color.gray));
            }
        });

        passwordEdt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tilPassword.setBoxStrokeColor(ContextCompat.getColor(this, R.color.colorPrimary));
            } else {
                tilPassword.setBoxStrokeColor(ContextCompat.getColor(this, R.color.gray));
            }
        });

        registerBtn.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, AccountRegisterActivity.class)));

        apiDebugBtn.setOnClickListener(v -> startDevRealHttpFlow());
    }

    private void setupInputValidation() {
        // 添加输入验证
        usernameEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    tilUsername.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        passwordEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    tilPassword.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setVariable() {
        loginBtn.setOnClickListener(v -> {
            // 添加按钮点击动画
            Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
            loginBtn.startAnimation(scaleAnimation);

            String username = Objects.requireNonNull(usernameEdt.getText()).toString().trim();
            String password = Objects.requireNonNull(passwordEdt.getText()).toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                if (username.isEmpty()) {
                    tilUsername.setError("Please enter username");
                }
                if (password.isEmpty()) {
                    tilPassword.setError("Please enter password");
                }
                return;
            }

            setFormEnabled(false);
            AppApi.auth().login(username, password, result -> runOnUiThread(() -> {
                setFormEnabled(true);
                if (result.isOk()) {
                    homeApplication.setLimits("ordinary");
                    homeApplication.setIsLogin("true");
                    navigateToMain();
                } else {
                    Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_SHORT).show();
                }
            }));
        });
    }

    private void startDevRealHttpFlow() {
        usernameEdt.setText(DEV_PHONE);
        passwordEdt.setText(DEV_PASSWORD);
        setFormEnabled(false);

        ApiTraceLogger.json("LoginActivity", API_REGISTER, "request",
                ApiTraceLogger.maskedAuthBody(DEV_USERNAME, DEV_PHONE, DEV_PASSWORD));
        AppApi.auth().register(DEV_USERNAME, DEV_PASSWORD, DEV_PHONE, registerResult -> {
            ApiTraceLogger.json("LoginActivity", API_REGISTER, "result", registerResult);
            loginDevAccount();
        });
    }

    private void loginDevAccount() {
        ApiTraceLogger.json("LoginActivity", API_LOGIN, "request",
                ApiTraceLogger.maskedAuthBody(null, DEV_PHONE, DEV_PASSWORD));
        AppApi.auth().login(DEV_PHONE, DEV_PASSWORD, loginResult -> runOnUiThread(() -> {
            setFormEnabled(true);
            ApiTraceLogger.json("LoginActivity", API_LOGIN, "result", loginResult);
            if (loginResult.isOk()) {
                homeApplication.setLimits("ordinary");
                homeApplication.setIsLogin("true");
                navigateToMain();
            } else {
                Toast.makeText(LoginActivity.this, loginResult.message, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void setFormEnabled(boolean enabled) {
        loginBtn.setEnabled(enabled);
        registerBtn.setEnabled(enabled);
        apiDebugBtn.setEnabled(enabled);
    }

    private void navigateToMain() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }
}
