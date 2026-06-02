package com.hc.mixthebluetooth.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hc.mixthebluetooth.api.auth.AuthUser;

import org.junit.Test;

public class EncryptedSessionStoreTest {
    @Test
    public void loadReturnsEmptyUserWhenEmpty() {
        EncryptedSessionStore store = new EncryptedSessionStore(new MemoryPreferencesStore());

        AuthUser user = store.currentUser();

        assertEquals(0L, user.accountId);
        assertNull(user.token);
        assertNull(store.token());
    }

    @Test
    public void saveAndLoadRoundTripsTokenAndAccount() {
        EncryptedSessionStore store = new EncryptedSessionStore(new MemoryPreferencesStore());
        AuthUser input = new AuthUser();
        input.accountId = 42L;
        input.username = "debug-user";
        input.phone = "13800138000";
        input.token = "token";

        store.save(input);
        AuthUser loaded = store.currentUser();

        assertEquals(42L, loaded.accountId);
        assertEquals("debug-user", loaded.username);
        assertEquals("13800138000", loaded.phone);
        assertEquals("token", loaded.token);
        assertEquals("token", store.token());
    }

    @Test
    public void clearRemovesSession() {
        EncryptedSessionStore store = new EncryptedSessionStore(new MemoryPreferencesStore());
        AuthUser input = new AuthUser();
        input.accountId = 42L;
        input.token = "token";

        store.save(input);
        store.clear();

        assertNull(store.token());
        assertEquals(0L, store.currentUser().accountId);
    }

    @Test
    public void blankTokenIsIgnored() {
        EncryptedSessionStore store = new EncryptedSessionStore(new MemoryPreferencesStore());
        AuthUser input = new AuthUser();
        input.accountId = 42L;
        input.token = " ";

        store.save(input);

        assertNull(store.token());
    }
}
