package com.hc.mixthebluetooth.application.auth;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.persistence.SessionStore;

/**
 * 主动登出辅助：清空本地 session + 通知 UI 跳到登录页。
 * <p>
 * 任何位置（菜单点击、设置页、对话框、远端推下来的强制登出）都能调。
 */
public final class LogoutHelper {
    private LogoutHelper() {
    }

    public static void performLogout() {
        performLogout(AppApi.sessionStore());
    }

    public static void performLogout(@NonNull SessionStore store) {
        store.clear();
        SessionEventBus.getInstance().postSessionExpired();
    }
}
