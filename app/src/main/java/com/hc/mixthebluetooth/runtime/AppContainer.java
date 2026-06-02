package com.hc.mixthebluetooth.runtime;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.codec.TextCodec;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.device.DeviceGateway;
import com.hc.mixthebluetooth.api.file.FileUploadService;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;

public final class AppContainer {
    @NonNull
    public final EnvConfig envConfig;
    @NonNull
    public final AuthService authService;
    @NonNull
    public final FileUploadService fileUploadService;
    @NonNull
    public final DeviceDataService deviceDataService;
    @NonNull
    public final CgmService cgmService;
    @NonNull
    public final CgmWorkflow cgmWorkflow;
    @NonNull
    public final DeviceGateway deviceGateway;
    @NonNull
    public final SessionStore sessionStore;
    @NonNull
    public final SettingsStore settingsStore;
    @NonNull
    public final TextCodec textCodec;
    @NonNull
    public final AppLogger logger;

    public AppContainer(@NonNull EnvConfig envConfig,
                        @NonNull AuthService authService,
                        @NonNull FileUploadService fileUploadService,
                        @NonNull DeviceDataService deviceDataService,
                        @NonNull CgmService cgmService,
                        @NonNull CgmWorkflow cgmWorkflow,
                        @NonNull DeviceGateway deviceGateway,
                        @NonNull SessionStore sessionStore,
                        @NonNull SettingsStore settingsStore,
                        @NonNull TextCodec textCodec,
                        @NonNull AppLogger logger) {
        this.envConfig = envConfig;
        this.authService = authService;
        this.fileUploadService = fileUploadService;
        this.deviceDataService = deviceDataService;
        this.cgmService = cgmService;
        this.cgmWorkflow = cgmWorkflow;
        this.deviceGateway = deviceGateway;
        this.sessionStore = sessionStore;
        this.settingsStore = settingsStore;
        this.textCodec = textCodec;
        this.logger = logger;
    }
}
