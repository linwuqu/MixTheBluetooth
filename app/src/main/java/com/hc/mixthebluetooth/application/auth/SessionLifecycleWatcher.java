package com.hc.mixthebluetooth.application.auth;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.ui.auth.LoginActivity;
import com.hc.mixthebluetooth.ui.auth.RegisterActivity;
import com.hc.mixthebluetooth.ui.intro.IntroActivity;
import com.hc.mixthebluetooth.ui.main.MainActivity;

/**
 * Application 级 Activity 生命周期回调：
 * 1) 任何 Activity onResume 时检查 token，无效则引导去登录页。
 * 2) 监听 SessionEventBus 的退登事件，跳转登录页。
 */
public final class SessionLifecycleWatcher implements Application.ActivityLifecycleCallbacks {

    private final SessionEventBus.SessionExpiredListener expiredListener = this::redirectToLogin;

    public static void install(@NonNull Application app) {
        SessionLifecycleWatcher w = new SessionLifecycleWatcher();
        app.registerActivityLifecycleCallbacks(w);
        SessionEventBus.getInstance().register(w.expiredListener);
    }

    private void redirectToLogin() {
        // 由 Network 拦截器在主线程回调，这里再走一次防护性跳转
        for (Activity a : ActiveActivities.getAll()) {
            if (a == null) continue;
            if (a.isFinishing() || a.isDestroyed()) continue;
            if (a instanceof LoginActivity || a instanceof IntroActivity || a instanceof RegisterActivity) continue;
            Intent i = new Intent(a, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            a.startActivity(i);
            a.finish();
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        ActiveActivities.add(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // 兜底：每个 Activity 进入前台时检查一次 session
        if (activity instanceof LoginActivity || activity instanceof IntroActivity || activity instanceof RegisterActivity) return;
        if (!AppApi.sessionStore().isTokenValid()) {
            Intent i = new Intent(activity, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(i);
            activity.finish();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        ActiveActivities.remove(activity);
    }
}
