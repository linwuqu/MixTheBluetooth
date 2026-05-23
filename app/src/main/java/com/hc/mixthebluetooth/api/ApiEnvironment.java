package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class ApiEnvironment {
    private ApiEnvironment() {
    }

    @NonNull
    public static String env() {
        return BuildConfig.API_ENV;
    }

    @NonNull
    public static String baseUrl() {
        return BuildConfig.API_BASE_URL;
    }

    public static boolean useMock() {
        return BuildConfig.USE_MOCK_API;
    }
}
