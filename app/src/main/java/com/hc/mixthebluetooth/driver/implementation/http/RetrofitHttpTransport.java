package com.hc.mixthebluetooth.driver.implementation.http;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.driver.capability.HttpTransport;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitHttpTransport implements HttpTransport {
    private final OkHttpClient okHttpClient;
    private final Retrofit retrofit;

    public RetrofitHttpTransport(@NonNull String baseUrl, @NonNull OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @NonNull
    @Override
    public OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    @NonNull
    @Override
    public Retrofit retrofit() {
        return retrofit;
    }
}
