package com.hc.mixthebluetooth.ui.intro;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.ui.auth.LoginActivity;
import com.hc.mixthebluetooth.ui.main.MainActivity;

public class IntroActivity extends AppCompatActivity {

    private boolean hasClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lntro);
        findViewById(android.R.id.content).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasClicked) return;
                hasClicked = true;
                navigateToLogin();
            }
        });

        if (AppApi.sessionStore().isTokenValid()) {
            // token 有效：显示状态文本并自动跳主页
            TextView hint = findViewById(R.id.autoLoginHint);
            if (hint != null) hint.setVisibility(View.VISIBLE);
            if (hint != null) {
                hint.postDelayed(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!AppApi.sessionStore().isTokenValid()) {
                        navigateToLogin();
                        return;
                    }
                    startActivity(new Intent(IntroActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }, 1500L);
            }
        }
    }

    private void navigateToLogin() {
        startActivity(new Intent(IntroActivity.this, LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
