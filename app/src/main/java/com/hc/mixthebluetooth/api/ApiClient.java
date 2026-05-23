package com.hc.mixthebluetooth.api;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static volatile ApiClient instance;

    private final AuthApi authApi;
    private final FileApi fileApi;

    private ApiClient(@NonNull Context context) {
        if (ApiEnvironment.useMock()) {
            authApi = MockApi.authApi();
            fileApi = MockApi.fileApi();
            return;
        }

        AuthSessionStore sessionStore = new AuthSessionStore(context);
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = sessionStore.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiEnvironment.baseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        authApi = retrofit.create(AuthApi.class);
        fileApi = retrofit.create(FileApi.class);
    }

    @NonNull
    public static ApiClient get(@NonNull Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @NonNull
    public AuthApi authApi() {
        return authApi;
    }

    @NonNull
    public FileApi fileApi() {
        return fileApi;
    }
}
