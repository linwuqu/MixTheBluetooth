package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class EnvConfig {
    @NonNull
    public final String env;
    @NonNull
    public final String baseUrl;
    public final boolean useMock;
    public final boolean debug;
    @NonNull
    public final String remoteName;
    public final boolean networkEnabled;

    public EnvConfig(@NonNull String env,
                     @NonNull String baseUrl,
                     boolean useMock,
                     boolean debug,
                     @NonNull String remoteName,
                     boolean networkEnabled) {
        this.env = env;
        this.baseUrl = baseUrl;
        this.useMock = useMock;
        this.debug = debug;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public static EnvConfig fromBuildConfig() {
        return new EnvConfig(
                BuildConfig.API_ENV,
                BuildConfig.API_BASE_URL,
                BuildConfig.USE_MOCK_API,
                BuildConfig.DEBUG,
                "",
                false
        );
    }

    @NonNull
    public EnvConfig withRemote(@NonNull String remoteName, boolean networkEnabled) {
        return new EnvConfig(env, baseUrl, useMock, debug, remoteName, networkEnabled);
    }
}
