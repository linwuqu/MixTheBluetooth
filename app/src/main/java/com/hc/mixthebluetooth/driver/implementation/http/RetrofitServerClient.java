package com.hc.mixthebluetooth.driver.implementation.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.driver.capability.HttpTransport;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public final class RetrofitServerClient {
    public interface TokenProvider {
        @Nullable
        String token();
    }

    private RetrofitServerClient() {
    }

    @NonNull
    public static BioAiEndpoints create(@NonNull String baseUrl, @NonNull TokenProvider tokenProvider) {
        return create(baseUrl, tokenProvider, false);
    }

    @NonNull
    public static BioAiEndpoints create(@NonNull String baseUrl,
                                         @NonNull TokenProvider tokenProvider,
                                         boolean bodyLogging) {
        return create(transport(baseUrl, tokenProvider, bodyLogging));
    }

    @NonNull
    public static HttpTransport transport(@NonNull String baseUrl,
                                          @NonNull TokenProvider tokenProvider,
                                          boolean bodyLogging) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(bodyLogging ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.BASIC);

        // 业务级 250004 拦截：需要 sessionStore 引用。tokenProvider 自身就是 SessionStore 适配的 lambda，
        // 调用方传入 sessionStore 后将其包成 TokenProvider 时再注入拦截器。
        // 这里我们允许 transport 重载传入自定义 client：调用方在 AppApiBootstrap 里完成 wiring。
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = tokenProvider.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("token", token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        return new RetrofitHttpTransport(baseUrl, client);
    }

    /**
     * 装载 SessionAuthInterceptor 的便捷方法。
     */
    @NonNull
    public static HttpTransport transport(@NonNull String baseUrl,
                                          @NonNull TokenProvider tokenProvider,
                                          @NonNull okhttp3.Interceptor sessionAuthInterceptor,
                                          boolean bodyLogging) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(bodyLogging ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = tokenProvider.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("token", token);
                    }
                    return chain.proceed(builder.build());
                })
                .addNetworkInterceptor(sessionAuthInterceptor)
                .addInterceptor(logging)
                .build();

        return new RetrofitHttpTransport(baseUrl, client);
    }

    @NonNull
    public static BioAiEndpoints create(@NonNull String baseUrl, @NonNull OkHttpClient client) {
        return create(new RetrofitHttpTransport(baseUrl, client));
    }

    @NonNull
    public static BioAiEndpoints create(@NonNull HttpTransport transport) {
        return transport.retrofit().create(BioAiEndpoints.class);
    }
}
