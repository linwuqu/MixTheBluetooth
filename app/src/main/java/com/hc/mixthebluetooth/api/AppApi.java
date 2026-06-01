package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;

public final class AppApi {
    private static AuthService auth;
    private static FileService file;
    private static DeviceDataService deviceData;
    private static CgmService cgm;
    private static EnvConfig env;

    private AppApi() {
    }

    public static synchronized void install(@NonNull AuthService authService,
                                            @NonNull FileService fileService,
                                            @NonNull DeviceDataService deviceDataService,
                                            @NonNull CgmService cgmService,
                                            @NonNull EnvConfig envConfig) {
        auth = authService;
        file = fileService;
        deviceData = deviceDataService;
        cgm = cgmService;
        env = envConfig;
    }

    @NonNull
    public static synchronized AuthService auth() {
        if (auth == null) throw new IllegalStateException("AppApi is not initialized");
        return auth;
    }

    @NonNull
    public static synchronized FileService file() {
        if (file == null) throw new IllegalStateException("AppApi is not initialized");
        return file;
    }

    @NonNull
    public static synchronized DeviceDataService deviceData() {
        if (deviceData == null) throw new IllegalStateException("AppApi is not initialized");
        return deviceData;
    }

    @NonNull
    public static synchronized CgmService cgm() {
        if (cgm == null) throw new IllegalStateException("AppApi is not initialized");
        return cgm;
    }

    @NonNull
    public static synchronized EnvConfig env() {
        if (env == null) throw new IllegalStateException("AppApi is not initialized");
        return env;
    }

    public static synchronized void clearForTest() {
        auth = null;
        file = null;
        deviceData = null;
        cgm = null;
        env = null;
    }
}
