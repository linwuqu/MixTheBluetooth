package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AuthSessionStoreTest {
    @Test
    public void saveAndReadTokenAndAccount() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());

        AccountInfo info = new AccountInfo();
        info.accountId = 42L;
        info.phone = "13800138000";
        info.username = "tester";
        info.token = "token-value";
        store.save(info);

        assertEquals("token-value", store.token());
        assertEquals(42L, store.accountId());
        assertEquals("13800138000", store.phone());
    }

    @Test
    public void clearRemovesToken() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());

        AccountInfo info = new AccountInfo();
        info.token = "token-value";
        store.save(info);
        store.clear();

        assertNull(store.token());
    }

    private static final class MemoryStore implements AuthSessionStore.Store {
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
