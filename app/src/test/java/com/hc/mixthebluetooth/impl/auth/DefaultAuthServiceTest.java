package com.hc.mixthebluetooth.impl.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.MockServer;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultAuthServiceTest {
    @Test
    public void loginWithMockServerSavesSession() {
        SessionStore store = new SessionStore(new MemoryStore());
        DefaultAuthService service = new DefaultAuthService(new MockServer(), store);
        AtomicReference<CallResult<AuthUser>> result = new AtomicReference<>();

        service.login("13800138000", "123456", result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertEquals("13800138000", result.get().data.phone);
        assertEquals("mock-token", store.currentUser().token);
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
