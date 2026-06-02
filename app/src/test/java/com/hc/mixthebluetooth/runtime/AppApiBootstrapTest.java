package com.hc.mixthebluetooth.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.driver.capability.BluetoothTransport;
import com.hc.mixthebluetooth.driver.capability.FileRecorder;
import com.hc.mixthebluetooth.driver.capability.HttpTransport;
import com.hc.mixthebluetooth.persistence.MemoryPreferencesStore;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppApiBootstrapTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void tearDown() {
        AppApi.clearForTest();
    }

    @Test
    public void staticLanUsesHttpBackedServices() throws Exception {
        AppContainer container = container(new EnvConfig("static-lan", "http://example.test/", true, "static-lan", true));

        assertEquals("static-lan", container.envConfig.env());
        assertNoStaticLoop(container.authService);
        assertNoStaticLoop(container.fileUploadService);
        assertNoStaticLoop(container.cgmService);
        assertNoStaticLoop(container.cgmWorkflow);
    }

    @Test
    public void devUsesHttpBackedServices() throws Exception {
        AppContainer container = container(new EnvConfig("dev", "http://example.test/", true, "dev", true));

        assertEquals("dev", container.envConfig.env());
        assertNoStaticLoop(container.authService);
        assertNoStaticLoop(container.fileUploadService);
        assertNoStaticLoop(container.cgmService);
        assertNoStaticLoop(container.cgmWorkflow);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedEnvThrows() {
        new EnvConfig("prod", "http://example.test/", true, "prod", true);
    }

    @Test
    public void containerInstallsAllAppApiContracts() throws Exception {
        AppContainer container = container(new EnvConfig("dev", "http://example.test/", true, "dev", true));

        AppApiBootstrap.install(container);

        assertSame(container.authService, AppApi.auth());
        assertSame(container.fileUploadService, AppApi.file());
        assertSame(container.deviceDataService, AppApi.deviceData());
        assertSame(container.cgmService, AppApi.cgm());
        assertSame(container.cgmWorkflow, AppApi.cgmWorkflow());
        assertSame(container.deviceGateway, AppApi.deviceGateway());
        assertSame(container.textCodec, AppApi.textCodec());
        assertSame(container.envConfig, AppApi.env());
        assertSame(container.sessionStore, AppApi.sessionStore());
        assertSame(container.settingsStore, AppApi.settingsStore());
        assertSame(container.logger, AppApi.logger());
    }

    private AppContainer container(EnvConfig envConfig) throws Exception {
        return AppApiBootstrap.createForTest(
                envConfig,
                new MemoryPreferencesStore(),
                new FakeHttpTransport(envConfig.baseUrl()),
                new FakeBluetoothTransport(),
                new FakeFileRecorder(temporaryFolder.newFile("CGM_Cache_data.txt")),
                new NoOpLogger()
        );
    }

    private static void assertNoStaticLoop(Object value) {
        assertNotNull(value);
        String name = value.getClass().getName();
        if (name.contains("Static") || name.contains("Loop")) {
            throw new AssertionError("Unexpected fake service: " + name);
        }
    }

    private static final class FakeHttpTransport implements HttpTransport {
        private final OkHttpClient client = new OkHttpClient.Builder().build();
        private final Retrofit retrofit;

        FakeHttpTransport(String baseUrl) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        @NonNull
        @Override
        public OkHttpClient okHttpClient() {
            return client;
        }

        @NonNull
        @Override
        public Retrofit retrofit() {
            return retrofit;
        }
    }

    private static final class FakeBluetoothTransport implements BluetoothTransport {
        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void sendBytes(@NonNull byte[] bytes) {
        }

        @Override
        public void sendText(@NonNull String text, @NonNull String charsetName) {
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class FakeFileRecorder implements FileRecorder {
        private final File file;

        FakeFileRecorder(File file) {
            this.file = file;
        }

        @Override
        public void start() {
        }

        @Override
        public void appendLine(@NonNull String line) {
        }

        @NonNull
        @Override
        public File finish() {
            return file;
        }

        @Override
        public void reset() {
        }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override
        public void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message) {
        }

        @Override
        public void json(@NonNull String owner, @NonNull String api, @NonNull String stage, Object value) {
        }
    }
}
