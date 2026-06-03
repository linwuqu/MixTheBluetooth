package com.hc.mixthebluetooth.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.mixthebluetooth.BuildConfig;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.databinding.ActivityLoginBinding;
import com.hc.mixthebluetooth.ui.main.MainActivity;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    private static final String DEV_PHONE = "18800000001";
    private static final String DEV_PASSWORD = "123456";

    private ActivityLoginBinding viewBinding;
    private TextInputEditText usernameEdt;
    private TextInputEditText passwordEdt;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    //控制权限管理
    private HomeApplication homeApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        initView();
        setupAnimations();
        setupInputValidation();
        setVariable();
        homeApplication = (HomeApplication) getApplication();
    }

    private void initView() {
        usernameEdt = viewBinding.username;
        passwordEdt = viewBinding.password;
        tilUsername = viewBinding.tilUsername;
        tilPassword = viewBinding.tilPassword;

        if (useDevPlaceholders()) {
            usernameEdt.setText(DEV_PHONE);
            passwordEdt.setText(DEV_PASSWORD);
        }
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

        viewBinding.registerBtn.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
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
        viewBinding.loginBtn.setOnClickListener(v -> {
            // 添加按钮点击动画
            Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
            viewBinding.loginBtn.startAnimation(scaleAnimation);

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

    private void setFormEnabled(boolean enabled) {
        viewBinding.loginBtn.setEnabled(enabled);
        viewBinding.registerBtn.setEnabled(enabled);
    }

    private boolean useDevPlaceholders() {
        return BuildConfig.DEBUG
                && ("static-lan".equals(BuildConfig.API_ENV) || "lan-static".equals(BuildConfig.API_ENV));
    }

    private void navigateToMain() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }
}
