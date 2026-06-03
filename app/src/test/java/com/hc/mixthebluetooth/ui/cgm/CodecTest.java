package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.codec.TextCodec;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.device.DeviceGateway;
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
import java.nio.charset.Charset;

public class CodecTest {
    @After
    public void tearDown() {
        AppApi.clearForTest();
    }

    @Test
    public void decodeReturnsNullForNullOrEmptyBytes() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);

        assertNull(Codec.decode(null, options));
        assertNull(Codec.decode(new byte[0], options));
    }

    @Test
    public void decodeRemovesZeroCharactersAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);
        byte[] bytes = "  12.5\u0000Ω,3.2uS  ".getBytes(Charset.forName("UTF-8"));

        assertEquals("12.5Ω,3.2uS", Codec.decode(bytes, options));
    }

    @Test
    public void decodeUsesUtf8WhenCharsetIsNull() {
        Codec.Options options = new Codec.Options(null, false, false);
        byte[] bytes = "hello".getBytes(Charset.forName("UTF-8"));

        assertEquals("hello", Codec.decode(bytes, options));
    }

    @Test
    public void decodeRemovesNullsAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);

        assertEquals("hello", Codec.decode(new byte[]{' ', 'h', 'e', 'l', 'l', 'o', 0, ' '}, options));
    }

    @Test
    public void rawDecodePreservesNewlineBoundariesForReplayBuffer() {
        Codec.Options options = new Codec.Options("UTF-8", false, false, false);
        byte[] bytes = "Start Playback\nEIS:1,1000,0.12\n".getBytes(Charset.forName("UTF-8"));

        assertEquals("Start Playback\nEIS:1,1000,0.12\n", Codec.decode(bytes, options));
    }

    @Test
    public void encodeTextUsesSettingsStoreEncoding() {
        SettingsStore settings = installApiWithSettings();
        settings.setTextEncoding("UTF-8");

        assertArrayEquals(
                "惟".getBytes(Charset.forName("UTF-8")),
                Codec.encodeText(null, "惟")
        );
    }

    private static SettingsStore installApiWithSettings() {
        MemoryPreferencesStore memory = new MemoryPreferencesStore();
        SettingsStore settings = new EncryptedSettingsStore(memory);
        SessionStore session = new EncryptedSessionStore(memory);
        AppApi.install(
                new NoOpAuthService(),
                (inputFile, callback) -> {
                },
                new NoOpDeviceDataService(),
                new NoOpCgmService(),
                new NoOpCgmWorkflow(),
                new NoOpDeviceGateway(),
                new NoOpTextCodec(),
                new EnvConfig("dev", "http://example.test/", true, "dev", true),
                session,
                settings,
                new NoOpLogger()
        );
        return settings;
    }

    private static final class NoOpAuthService implements AuthService {
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

    private static final class NoOpDeviceDataService implements DeviceDataService {
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

    private static final class NoOpCgmService implements CgmService {
        @Override
        public void uploadAndPoll(File cacheFile, ApiCallback<CallResult<CgmResult>> callback) {
        }

        @Override
        public void poll(long jobId, ApiCallback<CallResult<CgmResult>> callback) {
        }
    }

    private static final class NoOpCgmWorkflow implements CgmWorkflow {
        @NonNull
        @Override
        public Update onReadCacheSent() {
            return Update.message("ok");
        }

        @NonNull
        @Override
        public Update onDeleteCacheSent() {
            return Update.message("ok");
        }

        @NonNull
        @Override
        public Update onDeviceText(@NonNull String text, @NonNull ApiCallback<CallResult<CgmResult>> callback) {
            return Update.message("ok");
        }

        @Override
        public void reset() {
        }
    }

    private static final class NoOpDeviceGateway implements DeviceGateway {
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

    private static final class NoOpTextCodec implements TextCodec {
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

    private static final class NoOpLogger implements AppLogger {
        @Override
        public void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message) {
        }

        @Override
        public void json(@NonNull String owner, @NonNull String api, @NonNull String stage, Object value) {
        }
    }
}
