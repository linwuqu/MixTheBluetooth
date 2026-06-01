package com.hc.mixthebluetooth.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.impl.auth.StaticAuthService;
import com.hc.mixthebluetooth.impl.cgm.StaticCgmService;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StaticLoopServiceChainTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void staticAuthThenCgmChainProducesRenderableJob64() throws Exception {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService auth = new StaticAuthService(store);
        StaticCgmService cgm = new StaticCgmService();
        File file = temporaryFolder.newFile("cgm-cache.txt");
        Files.write(file.toPath(), "Start Playback\npayload\nPlayback all done\n".getBytes(StandardCharsets.UTF_8));

        AtomicReference<CallResult<AuthUser>> register = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> login = new AtomicReference<>();
        AtomicReference<CallResult<ServerModels.CgmJobData>> cgmResult = new AtomicReference<>();

        auth.register(StaticBioAiFixtures.USERNAME, StaticBioAiFixtures.PASSWORD, StaticBioAiFixtures.PHONE, register::set);
        auth.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, login::set);
        cgm.uploadAndPoll(file, cgmResult::set);

        assertTrue(register.get().isOk());
        assertTrue(login.get().isOk());
        assertNotNull(store.currentUser());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, store.currentUser().token);
        assertTrue(cgmResult.get().isOk());
        assertEquals(64L, cgmResult.get().data.jobId);
        assertEquals("GENERATED", cgmResult.get().data.status);
        assertNotNull(cgmResult.get().data.summaryJson.units.get(0).points);
    }

    private static final class MemoryStore implements SessionStore.Store {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            values.put(key, value);
        }

        @Override
        public void putLong(@NonNull String key, long value) {
            values.put(key, value);
        }

        @Nullable
        @Override
        public String getString(@NonNull String key) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public long getLong(@NonNull String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public void remove(@NonNull String key) {
            values.remove(key);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
