package com.hc.mixthebluetooth.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EncryptedSettingsStoreTest {
    @Test
    public void defaultsAreStable() {
        EncryptedSettingsStore store = new EncryptedSettingsStore(new MemoryPreferencesStore());

        assertEquals("GBK", store.textEncoding());
        assertTrue(store.firstLaunch());
        assertTrue(store.deviceFilterEnabled());
    }

    @Test
    public void textEncodingRoundTrips() {
        EncryptedSettingsStore store = new EncryptedSettingsStore(new MemoryPreferencesStore());

        store.setTextEncoding("UTF-8");

        assertEquals("UTF-8", store.textEncoding());
    }

    @Test
    public void firstLaunchRoundTrips() {
        EncryptedSettingsStore store = new EncryptedSettingsStore(new MemoryPreferencesStore());

        store.setFirstLaunch(false);

        assertFalse(store.firstLaunch());
    }

    @Test
    public void deviceFilterRoundTrips() {
        EncryptedSettingsStore store = new EncryptedSettingsStore(new MemoryPreferencesStore());

        store.setDeviceFilterEnabled(false);

        assertFalse(store.deviceFilterEnabled());
    }

    @Test
    public void genericLegacyValuesRoundTrip() {
        EncryptedSettingsStore store = new EncryptedSettingsStore(new MemoryPreferencesStore());

        store.putBoolean("KEY_HEX_SEND", true);
        store.putString("KEY_DATA", "value");
        store.putInt("widthKey", 128);

        assertTrue(store.getBoolean("KEY_HEX_SEND", false));
        assertEquals("value", store.getString("KEY_DATA", null));
        assertEquals(128, store.getInt("widthKey", -1));
    }
}
