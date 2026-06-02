package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

public interface HttpTransport {
    @NonNull
    OkHttpClient okHttpClient();

    @NonNull
    Retrofit retrofit();
}
