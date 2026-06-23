package com.hc.mixthebluetooth.application.auth;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp Network 拦截器：识别后端业务码 250004（ACCOUNT_UNLOGIN），
 * 触发清 session + 通知 UI 跳回登录页。
 * <p>
 * 注意：必须挂在 addNetworkInterceptor 上，application interceptor 拿不到真实响应。
 */
public final class SessionAuthInterceptor implements Interceptor {

    private static final String OWNER = "SessionAuth";
    /**
     * 与后端 BizCodeEnum.ACCOUNT_UNLOGIN.getCode() 对齐。
     */
    private static final int CODE_UNLOGIN = 250004;

    private final SessionStore sessionStore;
    private final Gson gson = new Gson();
    private final AtomicBoolean expiredPosted = new AtomicBoolean(false);

    public SessionAuthInterceptor(@NonNull SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (!response.isSuccessful()) {
            return response;
        }
        if (sessionStore.token() == null) {
            // 未登录态发起的请求（如 login/register）不需要处理
            return response;
        }

        ResponseBody body = response.body();
        if (body == null) return response;

        byte[] bytes;
        try {
            bytes = body.bytes();
        } catch (IOException e) {
            return response;
        }

        boolean unlogin = false;
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            JsonObject obj = gson.fromJson(text, JsonObject.class);
            if (obj != null && obj.has("code") && obj.get("code").getAsInt() == CODE_UNLOGIN) {
                unlogin = true;
            }
        } catch (Throwable ignored) {
            // 不是 JSON 或格式不一致，忽略
        }

        if (unlogin && expiredPosted.compareAndSet(false, true)) {
            ApiTraceLogger.text(OWNER, request.url().encodedPath(), "session_expired", "code=250004");
            sessionStore.clear();
            // 在主线程发出退登事件
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                    SessionEventBus.getInstance()::postSessionExpired);
            // 给后续请求一点时间收起，避免短时间内多次弹
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> expiredPosted.set(false), 1500L);
        }

        return response.newBuilder()
                .body(ResponseBody.create(bytes, body.contentType()))
                .build();
    }
}
