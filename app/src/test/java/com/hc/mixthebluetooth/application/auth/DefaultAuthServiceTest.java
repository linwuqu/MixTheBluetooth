package com.hc.mixthebluetooth.application.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.api.persistence.SessionStore;
import com.hc.mixthebluetooth.persistence.EncryptedSessionStore;
import com.hc.mixthebluetooth.persistence.MemoryPreferencesStore;
import com.hc.mixthebluetooth.driver.implementation.http.RetrofitServerClient;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class DefaultAuthServiceTest {
    @Test
    public void loginWithHttpServerSavesSession() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"accountId\":1,\"username\":\"debug-user\",\"phone\":\"13800138000\",\"token\":\"token\"}}"));
            server.start();

            SessionStore store = new EncryptedSessionStore(new MemoryPreferencesStore());
            DefaultAuthService service = new DefaultAuthService(
                    RetrofitServerClient.create(server.url("/").toString(), () -> null, true),
                    store
            );
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<AuthUser>> result = new AtomicReference<>();

            service.login("13800138000", "123456", value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().isOk());
            assertEquals("13800138000", result.get().data.phone);
            assertEquals("token", store.currentUser().token);
        }
    }

}
