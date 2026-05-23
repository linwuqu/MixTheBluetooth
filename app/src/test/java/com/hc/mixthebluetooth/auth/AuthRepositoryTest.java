package com.hc.mixthebluetooth.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;
import com.hc.mixthebluetooth.api.AuthApi;
import com.hc.mixthebluetooth.api.MockApi;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;

public class AuthRepositoryTest {
    @Test
    public void registerSavesAccountAndToken() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());
        AuthRepository repository = new AuthRepository(MockApi.authApi(), store);

        repository.register("tester", "123456", "13800138000", new NoopCallback());

        assertEquals(1001L, store.accountId());
        assertEquals("13800138000", store.phone());
        assertEquals("mock-token", store.token());
    }

    @Test
    public void loginSavesAccountWithoutRequiringToken() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());
        AuthRepository repository = new AuthRepository(new NoTokenAuthApi(), store);

        repository.login("13800138000", "123456", new NoopCallback());

        assertEquals(2002L, store.accountId());
        assertEquals("13800138000", store.phone());
        assertNull(store.token());
    }

    @Test
    public void detailSavesReturnedAccount() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());
        AuthRepository repository = new AuthRepository(MockApi.authApi(), store);

        repository.detail(new NoopCallback());

        assertEquals(1001L, store.accountId());
        assertEquals("13800138000", store.phone());
    }

    @Test
    public void clearSessionClearsSavedAccount() {
        AuthSessionStore store = new AuthSessionStore(new MemoryStore());
        AuthRepository repository = new AuthRepository(MockApi.authApi(), store);

        repository.login("13800138000", "123456", new NoopCallback());
        repository.clearSession();

        assertEquals(0L, repository.accountId());
        assertNull(repository.phone());
        assertNull(repository.token());
    }

    private static final class NoTokenAuthApi implements AuthApi {
        @Override
        public Call<JsonData<AccountInfo>> register(AccountRegisterReq req) {
            return MockApi.authApi().register(req);
        }

        @Override
        public Call<JsonData<AccountInfo>> login(AccountLoginReq req) {
            AccountInfo info = new AccountInfo();
            info.accountId = 2002L;
            info.username = "no-token-user";
            info.phone = req.phone;

            JsonData<AccountInfo> json = new JsonData<>();
            json.code = 0;
            json.data = info;
            json.msg = "";
            json.success = true;
            return new MockApi.FakeCall<>(json);
        }

        @Override
        public Call<JsonData<AccountInfo>> detail() {
            return MockApi.authApi().detail();
        }
    }

    private static final class NoopCallback implements AuthRepository.ResultCallback {
        @Override
        public void onSuccess(@NonNull AccountInfo info) {
        }

        @Override
        public void onError(@NonNull String message) {
            throw new AssertionError(message);
        }
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
