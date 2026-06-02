package com.hc.mixthebluetooth.impl;

import android.content.Context;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileUploadService;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.application.auth.DefaultAuthService;
import com.hc.mixthebluetooth.application.cgm.DefaultCgmWorkflow;
import com.hc.mixthebluetooth.application.cgm.DefaultCgmJobService;
import com.hc.mixthebluetooth.impl.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.application.file.DefaultFileUploadService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.driver.implementation.file.AndroidFileRecorder;
import com.hc.mixthebluetooth.driver.implementation.file.AssetReplaySource;
import com.hc.mixthebluetooth.driver.implementation.log.AndroidAppLogger;
import com.hc.mixthebluetooth.persistence.EncryptedSessionStore;
import com.hc.mixthebluetooth.persistence.EncryptedSettingsStore;
import com.hc.mixthebluetooth.driver.implementation.http.RetrofitServerClient;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;
import com.hc.mixthebluetooth.runtime.EnvConfig;

public final class AppApiBootstrap {
    private static boolean initialized;

    private AppApiBootstrap() {
    }

    public static synchronized void init(Context context) {
        if (initialized) return;

        Context app = context.getApplicationContext();
        EnvConfig env = EnvConfig.fromBuildConfig();
        SessionStore sessionStore = EncryptedSessionStore.create(app);
        SettingsStore settingsStore = EncryptedSettingsStore.create(app);
        AppLogger logger = new AndroidAppLogger();
        AndroidFileRecorder recorder = new AndroidFileRecorder(app);

        BioAiEndpoints endpoints = RetrofitServerClient.create(env.baseUrl(), sessionStore::token, env.debug());
        FileUploadService fileService = new DefaultFileUploadService(endpoints);
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);
        CgmService cgmService = new DefaultCgmJobService(endpoints);
        CgmWorkflow cgmWorkflow = new DefaultCgmWorkflow(recorder, cgmService, logger);

        ApiTraceLogger.text("AppApiBootstrap", "ENV", "config",
                "env=" + env.env()
                        + "\nbaseUrl=" + env.baseUrl()
                        + "\nremote=" + env.remoteName()
                        + "\nnetworkEnabled=" + env.networkEnabled());

        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                recorder,
                new AssetReplaySource(app),
                fileService
        );

        AppApi.install(
                authService,
                fileService,
                deviceDataService,
                cgmService,
                cgmWorkflow,
                env,
                sessionStore,
                settingsStore,
                logger
        );
        initialized = true;
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }
}
