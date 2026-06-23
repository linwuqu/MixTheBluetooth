package com.hc.mixthebluetooth.application.auth;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application 级单例事件总线。
 * <p>
 * 用于在没有 Activity 上下文可用的线程（如 OkHttp 拦截器）通知 UI 跳转到登录页。
 */
public final class SessionEventBus {

    public interface SessionExpiredListener {
        @MainThread
        void onSessionExpired();
    }

    private static final SessionEventBus INSTANCE = new SessionEventBus();

    public static SessionEventBus getInstance() {
        return INSTANCE;
    }

    private final CopyOnWriteArrayList<SessionExpiredListener> listeners = new CopyOnWriteArrayList<>();

    private SessionEventBus() {
    }

    public void register(@NonNull SessionExpiredListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void unregister(@NonNull SessionExpiredListener listener) {
        listeners.remove(listener);
    }

    public void postSessionExpired() {
        for (SessionExpiredListener l : listeners) {
            try {
                l.onSessionExpired();
            } catch (Throwable ignored) {
            }
        }
    }
}
