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
import com.hc.mixthebluetooth.R;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText usernameEdt;
    private TextInputEditText passwordEdt;
    private MaterialButton loginBtn;
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
    }

    private void setupInputValidation() {
        // 添加输入验证
        usernameEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    tilUsername.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        passwordEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    tilPassword.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setVariable() {
        loginBtn.setOnClickListener(v -> {
            // 添加按钮点击动画
            Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
            loginBtn.startAnimation(scaleAnimation);

            String username = usernameEdt.getText().toString().trim();
            String password = passwordEdt.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                if (username.isEmpty()) {
                    tilUsername.setError("Please enter username");
                }
                if (password.isEmpty()) {
                    tilPassword.setError("Please enter password");
                }
                return;
            }

            if (username.equals("admin") && password.equals("1")) {
                // 管理员登录成功
                homeApplication.setLimits("admin");
                homeApplication.setIsLogin("true");
                navigateToMain();
            } else if (username.equals("normal") && password.equals("1")) {
                // 普通用户登录
                homeApplication.setLimits("ordinary");
                homeApplication.setIsLogin("true");
                navigateToMain();
            } else {
                // 登录失败动画
                Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }
}