package com.hc.mixthebluetooth.runtime;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class EnvConfig {
    public static final String STATIC_LAN = "static-lan";
    public static final String DEV = "dev";

    private final String env;
    private final String baseUrl;
    private final boolean debug;
    private final String remoteName;
    private final boolean networkEnabled;

    public EnvConfig(@NonNull String env,
                     @NonNull String baseUrl,
                     boolean debug,
                     @NonNull String remoteName,
                     boolean networkEnabled) {
        this.env = normalizeEnv(env);
        this.baseUrl = baseUrl;
        this.debug = debug;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public static EnvConfig fromBuildConfig() {
        return new EnvConfig(
                BuildConfig.API_ENV,
                BuildConfig.API_BASE_URL,
                BuildConfig.API_DEBUG,
                BuildConfig.API_REMOTE_NAME,
                BuildConfig.API_NETWORK_ENABLED
        );
    }

    @NonNull
    public String env() {
        return env;
    }

    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    public boolean debug() {
        return debug;
    }

    @NonNull
    public String remoteName() {
        return remoteName;
    }

    public boolean networkEnabled() {
        return networkEnabled;
    }

    public boolean isStaticLan() {
        return STATIC_LAN.equals(env);
    }

    public boolean isDev() {
        return DEV.equals(env);
    }

    @NonNull
    public static String normalizeEnv(@NonNull String rawEnv) {
        String value = rawEnv.trim().toLowerCase();
        if (STATIC_LAN.equals(value) || DEV.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported api.env: " + rawEnv);
    }
}
