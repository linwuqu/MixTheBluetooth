package com.hc.mixthebluetooth.impl.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StaticAuthServiceTest {
    @Test
    public void registerThenLoginThenDetailPreservesSession() {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService service = new StaticAuthService(store);
        AtomicReference<CallResult<AuthUser>> register = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> login = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> detail = new AtomicReference<>();

        service.register(
                StaticBioAiFixtures.USERNAME,
                StaticBioAiFixtures.PASSWORD,
                StaticBioAiFixtures.PHONE,
                register::set
        );
        service.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, login::set);
        service.detail(detail::set);

        assertTrue(register.get().isOk());
        assertTrue(login.get().isOk());
        assertTrue(detail.get().isOk());
        assertEquals(StaticBioAiFixtures.ACCOUNT_ID, detail.get().data.accountId);
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, detail.get().data.token);
        assertNotNull(store.currentUser());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, store.currentUser().token);
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
