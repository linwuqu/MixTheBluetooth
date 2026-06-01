package com.hc.mixthebluetooth.impl;

import android.content.Context;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.EnvConfig;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.impl.auth.DefaultAuthService;
import com.hc.mixthebluetooth.impl.cgm.DefaultCgmService;
import com.hc.mixthebluetooth.impl.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.impl.file.DefaultFileService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.ServerClient;
import com.hc.mixthebluetooth.remote.ServerEndpoints;

public final class AppApiBootstrap {
    private static boolean initialized;

    private AppApiBootstrap() {
    }

    public static synchronized void init(Context context) {
        if (initialized) return;

        Context app = context.getApplicationContext();
        EnvConfig env = EnvConfig.fromBuildConfig();
        SessionStore sessionStore = new SessionStore(app);

        ServerEndpoints endpoints = ServerClient.create(env.baseUrl, sessionStore, env.debug);
        env = env.withRemote("RetrofitServer(" + env.baseUrl + ")", true);
        ApiTraceLogger.text("AppApiBootstrap", "ENV", "config",
                "env=" + env.env + "\nbaseUrl=" + env.baseUrl + "\nremote=" + env.remoteName);

        FileService fileService = new DefaultFileService(endpoints);
        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                new DeviceDataRecorder(app),
                new DeviceReplaySample(app),
                fileService
        );
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);

        AppApi.install(authService, fileService, deviceDataService, new DefaultCgmService(endpoints), env);
        initialized = true;
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }
}
