package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.runtime.EnvConfig;

public final class AppApi {
    private static AuthService auth;
    private static FileService file;
    private static DeviceDataService deviceData;
    private static CgmService cgm;
    private static CgmWorkflow cgmWorkflow;
    private static EnvConfig env;
    private static SessionStore sessionStore;
    private static SettingsStore settingsStore;
    private static AppLogger logger;

    private AppApi() {
    }

    public static synchronized void install(@NonNull AuthService authService,
                                            @NonNull FileService fileService,
                                            @NonNull DeviceDataService deviceDataService,
                                            @NonNull CgmService cgmService,
                                            @NonNull EnvConfig envConfig) {
        install(authService, fileService, deviceDataService, cgmService, null, envConfig, null, null, null);
    }

    public static synchronized void install(@NonNull AuthService authService,
                                            @NonNull FileService fileService,
                                            @NonNull DeviceDataService deviceDataService,
                                            @NonNull CgmService cgmService,
                                            @NonNull EnvConfig envConfig,
                                            SessionStore session,
                                            SettingsStore settings,
                                            AppLogger appLogger) {
        install(authService, fileService, deviceDataService, cgmService, null, envConfig, session, settings, appLogger);
    }

    public static synchronized void install(@NonNull AuthService authService,
                                            @NonNull FileService fileService,
                                            @NonNull DeviceDataService deviceDataService,
                                            @NonNull CgmService cgmService,
                                            CgmWorkflow workflow,
                                            @NonNull EnvConfig envConfig,
                                            SessionStore session,
                                            SettingsStore settings,
                                            AppLogger appLogger) {
        auth = authService;
        file = fileService;
        deviceData = deviceDataService;
        cgm = cgmService;
        cgmWorkflow = workflow;
        env = envConfig;
        sessionStore = session;
        settingsStore = settings;
        logger = appLogger;
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
    public static synchronized CgmWorkflow cgmWorkflow() {
        if (cgmWorkflow == null) throw new IllegalStateException("AppApi is not initialized");
        return cgmWorkflow;
    }

    @NonNull
    public static synchronized EnvConfig env() {
        if (env == null) throw new IllegalStateException("AppApi is not initialized");
        return env;
    }

    @NonNull
    public static synchronized SessionStore sessionStore() {
        if (sessionStore == null) throw new IllegalStateException("AppApi is not initialized");
        return sessionStore;
    }

    @NonNull
    public static synchronized SettingsStore settingsStore() {
        if (settingsStore == null) throw new IllegalStateException("AppApi is not initialized");
        return settingsStore;
    }

    @NonNull
    public static synchronized AppLogger logger() {
        if (logger == null) throw new IllegalStateException("AppApi is not initialized");
        return logger;
    }

    public static synchronized void clearForTest() {
        auth = null;
        file = null;
        deviceData = null;
        cgm = null;
        cgmWorkflow = null;
        env = null;
        sessionStore = null;
        settingsStore = null;
        logger = null;
    }
}
