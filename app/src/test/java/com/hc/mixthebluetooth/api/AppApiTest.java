package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertSame;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.codec.TextCodec;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.device.DeviceGateway;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.persistence.EncryptedSessionStore;
import com.hc.mixthebluetooth.persistence.EncryptedSettingsStore;
import com.hc.mixthebluetooth.persistence.MemoryPreferencesStore;
import com.hc.mixthebluetooth.runtime.EnvConfig;

import org.junit.After;
import org.junit.Test;

import java.io.File;

public class AppApiTest {
    @After
    public void tearDown() {
        AppApi.clearForTest();
    }

    @Test(expected = IllegalStateException.class)
    public void accessBeforeInstallFails() {
        AppApi.auth();
    }

    @Test
    public void installMakesContractsAvailable() {
        Contracts contracts = new Contracts();

        install(contracts);

        assertSame(contracts.auth, AppApi.auth());
        assertSame(contracts.file, AppApi.file());
        assertSame(contracts.deviceData, AppApi.deviceData());
        assertSame(contracts.cgm, AppApi.cgm());
        assertSame(contracts.cgmWorkflow, AppApi.cgmWorkflow());
        assertSame(contracts.deviceGateway, AppApi.deviceGateway());
        assertSame(contracts.textCodec, AppApi.textCodec());
        assertSame(contracts.env, AppApi.env());
        assertSame(contracts.sessionStore, AppApi.sessionStore());
        assertSame(contracts.settingsStore, AppApi.settingsStore());
        assertSame(contracts.logger, AppApi.logger());
    }

    @Test
    public void reinstallReplacesContractsForTests() {
        Contracts first = new Contracts();
        Contracts second = new Contracts();

        install(first);
        install(second);

        assertSame(second.auth, AppApi.auth());
        assertSame(second.file, AppApi.file());
        assertSame(second.deviceData, AppApi.deviceData());
        assertSame(second.cgm, AppApi.cgm());
        assertSame(second.cgmWorkflow, AppApi.cgmWorkflow());
        assertSame(second.deviceGateway, AppApi.deviceGateway());
        assertSame(second.textCodec, AppApi.textCodec());
        assertSame(second.env, AppApi.env());
        assertSame(second.sessionStore, AppApi.sessionStore());
        assertSame(second.settingsStore, AppApi.settingsStore());
        assertSame(second.logger, AppApi.logger());
    }

    private static void install(@NonNull Contracts contracts) {
        AppApi.install(
                contracts.auth,
                contracts.file,
                contracts.deviceData,
                contracts.cgm,
                contracts.cgmWorkflow,
                contracts.deviceGateway,
                contracts.textCodec,
                contracts.env,
                contracts.sessionStore,
                contracts.settingsStore,
                contracts.logger
        );
    }

    private static final class Contracts {
        final AuthService auth = new FakeAuthService();
        final FileService file = (inputFile, callback) -> {
        };
        final DeviceDataService deviceData = new FakeDeviceDataService();
        final CgmService cgm = new FakeCgmService();
        final CgmWorkflow cgmWorkflow = new FakeCgmWorkflow();
        final DeviceGateway deviceGateway = new FakeDeviceGateway();
        final TextCodec textCodec = new FakeTextCodec();
        final EnvConfig env = new EnvConfig("dev", "http://example.test/", true, "dev", true);
        final MemoryPreferencesStore memory = new MemoryPreferencesStore();
        final SessionStore sessionStore = new EncryptedSessionStore(memory);
        final SettingsStore settingsStore = new EncryptedSettingsStore(memory);
        final AppLogger logger = new AppLogger() {
            @Override
            public void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message) {
            }

            @Override
            public void json(@NonNull String owner, @NonNull String api, @NonNull String stage, Object value) {
            }
        };
    }

    private static final class FakeAuthService implements AuthService {
        @Override
        public void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback) {
        }

        @Override
        public void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback) {
        }

        @Override
        public void detail(ApiCallback<CallResult<AuthUser>> callback) {
        }

        @Override
        public AuthUser currentUser() {
            return new AuthUser();
        }

        @Override
        public void clearSession() {
        }
    }

    private static final class FakeDeviceDataService implements DeviceDataService {
        @Override
        public void replaySample(ApiCallback<CallResult<File>> callback) {
        }

        @Override
        public void consumeLine(String line, ApiCallback<CallResult<File>> callback) {
        }

        @Override
        public File lastDataFile() {
            return null;
        }

        @Override
        public void uploadLastDataFile(ApiCallback<CallResult<UploadedFile>> callback) {
        }
    }

    private static final class FakeCgmService implements CgmService {
        @Override
        public void uploadAndPoll(File cacheFile, ApiCallback<CallResult<CgmResult>> callback) {
        }

        @Override
        public void poll(long jobId, ApiCallback<CallResult<CgmResult>> callback) {
        }
    }

    private static final class FakeCgmWorkflow implements CgmWorkflow {
        @Override
        public void onDeviceLine(@NonNull String line, @NonNull ApiCallback<CallResult<CgmResult>> callback) {
        }

        @Override
        public void reset() {
        }
    }

    private static final class FakeDeviceGateway implements DeviceGateway {
        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void sendText(@NonNull String text) {
        }

        @Override
        public void sendBytes(@NonNull byte[] bytes) {
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class FakeTextCodec implements TextCodec {
        @NonNull
        @Override
        public byte[] encode(@NonNull String text, @NonNull String charsetName) {
            return new byte[0];
        }

        @NonNull
        @Override
        public String decode(@NonNull byte[] bytes, @NonNull String charsetName) {
            return "";
        }
    }
}
