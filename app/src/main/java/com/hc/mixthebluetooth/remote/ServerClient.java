package com.hc.mixthebluetooth.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ServerClient {
    public interface TokenProvider {
        @Nullable
        String token();
    }

    private ServerClient() {
    }

    @NonNull
    public static ServerEndpoints create(@NonNull String baseUrl, @NonNull TokenProvider tokenProvider) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = tokenProvider.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        return create(baseUrl, client);
    }

    @NonNull
    public static ServerEndpoints create(@NonNull String baseUrl, @NonNull OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ServerEndpoints.class);
    }
}
