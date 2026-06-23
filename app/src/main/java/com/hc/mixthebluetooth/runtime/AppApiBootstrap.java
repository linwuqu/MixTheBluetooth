package com.hc.mixthebluetooth.runtime;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;
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
import com.hc.mixthebluetooth.driver.implementation.bluetooth.AndroidBluetoothController;
import com.hc.mixthebluetooth.application.auth.DefaultAuthService;
import com.hc.mixthebluetooth.application.auth.SessionAuthInterceptor;
import com.hc.mixthebluetooth.application.cgm.DefaultCgmWorkflow;
import com.hc.mixthebluetooth.application.cgm.DefaultCgmJobService;
import com.hc.mixthebluetooth.application.device.DefaultDeviceGateway;
import com.hc.mixthebluetooth.application.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.application.file.DefaultFileUploadService;
import com.hc.mixthebluetooth.driver.capability.BluetoothTransport;
import com.hc.mixthebluetooth.driver.capability.DeviceReplayRecorder;
import com.hc.mixthebluetooth.driver.capability.FileRecorder;
import com.hc.mixthebluetooth.driver.capability.HttpTransport;
import com.hc.mixthebluetooth.driver.capability.PreferencesStore;
import com.hc.mixthebluetooth.driver.capability.ReplaySource;
import com.hc.mixthebluetooth.driver.implementation.bluetooth.AndroidBluetoothTransport;
import com.hc.mixthebluetooth.driver.implementation.codec.AnalysisTextCodec;
import com.hc.mixthebluetooth.driver.implementation.file.AndroidFileRecorder;
import com.hc.mixthebluetooth.driver.implementation.file.AssetReplaySource;
import com.hc.mixthebluetooth.driver.implementation.http.RetrofitServerClient;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;
import com.hc.mixthebluetooth.driver.implementation.log.AndroidAppLogger;
import com.hc.mixthebluetooth.persistence.EncryptedSessionStore;
import com.hc.mixthebluetooth.persistence.EncryptedSettingsStore;

import java.io.File;
import java.util.Collections;

public final class AppApiBootstrap {
    private static boolean initialized;

    private AppApiBootstrap() {
    }

    public static synchronized void init(@NonNull Application application) {
        if (initialized) return;

        AppContainer container = create(application.getApplicationContext());
        install(container);
        initialized = true;
    }

    static AppContainer createForTest(@NonNull EnvConfig env,
                                      @NonNull PreferencesStore preferencesStore,
                                      @NonNull HttpTransport httpTransport,
                                      @NonNull BluetoothTransport bluetoothTransport,
                                      @NonNull FileRecorder fileRecorder,
                                      @NonNull AppLogger logger) {
        SessionStore sessionStore = new EncryptedSessionStore(sessionStore(preferencesStore));
        SettingsStore settingsStore = new EncryptedSettingsStore(preferencesStore);
        return create(
                env,
                sessionStore,
                settingsStore,
                logger,
                httpTransport,
                bluetoothTransport,
                new AnalysisTextCodec(),
                fileRecorder,
                replayRecorder(fileRecorder),
                () -> Collections.emptyList()
        );
    }

    static void install(@NonNull AppContainer container) {
        container.logger.text("AppApiBootstrap", "ENV", "config",
                "env=" + container.envConfig.env()
                        + "\nbaseUrl=" + container.envConfig.baseUrl()
                        + "\nremote=" + container.envConfig.remoteName()
                        + "\nnetworkEnabled=" + container.envConfig.networkEnabled());

        AppApi.install(
                container.authService,
                container.fileUploadService,
                container.deviceDataService,
                container.cgmService,
                container.cgmWorkflow,
                container.deviceGateway,
                container.textCodec,
                container.envConfig,
                container.sessionStore,
                container.settingsStore,
                container.logger
        );
    }

    @NonNull
    private static AppContainer create(@NonNull Context app) {
        EnvConfig env = EnvConfig.fromBuildConfig();
        SessionStore sessionStore = EncryptedSessionStore.create(app);
        SettingsStore settingsStore = EncryptedSettingsStore.create(app);
        AppLogger logger = new AndroidAppLogger();
        AndroidFileRecorder recorder = new AndroidFileRecorder(app);
        HttpTransport httpTransport = RetrofitServerClient.transport(
                env.baseUrl(),
                sessionStore::token,
                new SessionAuthInterceptor(sessionStore),
                env.debug());
        BluetoothTransport bluetoothTransport = new AndroidBluetoothTransport(AndroidBluetoothController.getInstance());
        TextCodec textCodec = new AnalysisTextCodec();

        return create(
                env,
                sessionStore,
                settingsStore,
                logger,
                httpTransport,
                bluetoothTransport,
                textCodec,
                recorder,
                recorder,
                new AssetReplaySource(app)
        );
    }

    @NonNull
    private static AppContainer create(@NonNull EnvConfig env,
                                       @NonNull SessionStore sessionStore,
                                       @NonNull SettingsStore settingsStore,
                                       @NonNull AppLogger logger,
                                       @NonNull HttpTransport httpTransport,
                                       @NonNull BluetoothTransport bluetoothTransport,
                                       @NonNull TextCodec textCodec,
                                       @NonNull FileRecorder fileRecorder,
                                       @NonNull DeviceReplayRecorder replayRecorder,
                                       @NonNull ReplaySource replaySource) {
        BioAiEndpoints endpoints = RetrofitServerClient.create(httpTransport);
        FileUploadService fileService = new DefaultFileUploadService(endpoints);
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);
        CgmService cgmService = new DefaultCgmJobService(endpoints);
        CgmWorkflow cgmWorkflow = new DefaultCgmWorkflow(fileRecorder, cgmService, logger);
        DeviceGateway deviceGateway = new DefaultDeviceGateway(bluetoothTransport, settingsStore, logger);
        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                replayRecorder,
                replaySource,
                fileService
        );

        return new AppContainer(
                env,
                authService,
                fileService,
                deviceDataService,
                cgmService,
                cgmWorkflow,
                deviceGateway,
                sessionStore,
                settingsStore,
                textCodec,
                logger
        );
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }

    @NonNull
    private static DeviceReplayRecorder replayRecorder(@NonNull FileRecorder fileRecorder) {
        if (fileRecorder instanceof DeviceReplayRecorder) {
            return (DeviceReplayRecorder) fileRecorder;
        }
        return new DeviceReplayRecorder() {
            @NonNull
            @Override
            public com.hc.mixthebluetooth.api.CallResult<File> consumeLine(String line) {
                return com.hc.mixthebluetooth.api.CallResult.pending("runtime test replay recorder");
            }

            @Override
            public File currentFile() {
                return null;
            }
        };
    }

    @NonNull
    private static SessionStore.Store sessionStore(@NonNull PreferencesStore preferencesStore) {
        return new SessionStore.Store() {
            @Override
            public void putString(@NonNull String key, @NonNull String value) {
                preferencesStore.putString(key, value);
            }

            @Override
            public void putLong(@NonNull String key, long value) {
                preferencesStore.putLong(key, value);
            }

            @Override
            public String getString(@NonNull String key) {
                return preferencesStore.getString(key, null);
            }

            @Override
            public long getLong(@NonNull String key, long defaultValue) {
                return preferencesStore.getLong(key, defaultValue);
            }

            @Override
            public void remove(@NonNull String key) {
                preferencesStore.remove(key);
            }

            @Override
            public void clear() {
                preferencesStore.clear();
            }
        };
    }
}
