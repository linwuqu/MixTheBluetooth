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
import com.hc.mixthebluetooth.debug.VerificationActivity;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

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

        apiDebugBtn.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, VerificationActivity.class)));
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

            loginBtn.setEnabled(false);
            AppApi.auth().login(username, password, result -> runOnUiThread(() -> {
                loginBtn.setEnabled(true);
                if (result.isOk()) {
                    if (AppApi.env().useMock && tryLocalDebugLogin(username, password)) {
                        return;
                    }
                    homeApplication.setLimits("ordinary");
                    homeApplication.setIsLogin("true");
                    navigateToMain();
                } else {
                    if (AppApi.env().useMock && tryLocalDebugLogin(username, password)) {
                        return;
                    }
                    loginBtn.setEnabled(true);
                    Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_SHORT).show();
                }
            }));
        });
    }

    private boolean tryLocalDebugLogin(String username, String password) {
        if (username.equals("admin") && password.equals("1")) {
            homeApplication.setLimits("admin");
            homeApplication.setIsLogin("true");
            navigateToMain();
            return true;
        }
        if (username.equals("normal") && password.equals("1")) {
            homeApplication.setLimits("ordinary");
            homeApplication.setIsLogin("true");
            navigateToMain();
            return true;
        }
        return false;
    }

    private void navigateToMain() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }
}
