package com.hc.mixthebluetooth.impl;

import android.content.Context;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.impl.auth.DefaultAuthService;
import com.hc.mixthebluetooth.impl.cgm.DefaultCgmService;
import com.hc.mixthebluetooth.impl.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.impl.file.DefaultFileService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.persistence.EncryptedSessionStore;
import com.hc.mixthebluetooth.remote.ServerClient;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
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

        ServerEndpoints endpoints = ServerClient.create(env.baseUrl(), sessionStore::token, env.debug());
        FileService fileService = new DefaultFileService(endpoints);
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);
        CgmService cgmService = new DefaultCgmService(endpoints);

        ApiTraceLogger.text("AppApiBootstrap", "ENV", "config",
                "env=" + env.env()
                        + "\nbaseUrl=" + env.baseUrl()
                        + "\nremote=" + env.remoteName()
                        + "\nnetworkEnabled=" + env.networkEnabled());

        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                new DeviceDataRecorder(app),
                new DeviceReplaySample(app),
                fileService
        );

        AppApi.install(authService, fileService, deviceDataService, cgmService, env);
        initialized = true;
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }
}
