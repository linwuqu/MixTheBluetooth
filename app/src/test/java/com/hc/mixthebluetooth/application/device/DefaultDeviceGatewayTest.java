package com.hc.mixthebluetooth.application.device;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.driver.capability.BluetoothTransport;
import com.hc.mixthebluetooth.persistence.EncryptedSettingsStore;
import com.hc.mixthebluetooth.persistence.MemoryPreferencesStore;

import org.junit.Test;

public class DefaultDeviceGatewayTest {
    @Test
    public void sendTextUsesSettingsEncoding() {
        FakeBluetoothTransport bluetooth = new FakeBluetoothTransport();
        SettingsStore settings = new EncryptedSettingsStore(new MemoryPreferencesStore());
        settings.setTextEncoding("UTF-8");
        DefaultDeviceGateway gateway = new DefaultDeviceGateway(bluetooth, settings, new NoOpLogger());

        gateway.sendText("hello");

        assertEquals("hello", bluetooth.text);
        assertEquals("UTF-8", bluetooth.charsetName);
    }

    @Test
    public void sendBytesDelegatesToBluetoothTransport() {
        FakeBluetoothTransport bluetooth = new FakeBluetoothTransport();
        DefaultDeviceGateway gateway = new DefaultDeviceGateway(
                bluetooth,
                new EncryptedSettingsStore(new MemoryPreferencesStore()),
                new NoOpLogger()
        );
        byte[] bytes = new byte[]{1, 2, 3};

        gateway.sendBytes(bytes);

        assertArrayEquals(bytes, bluetooth.bytes);
    }

    @Test
    public void disconnectDelegatesToBluetoothTransport() {
        FakeBluetoothTransport bluetooth = new FakeBluetoothTransport();
        DefaultDeviceGateway gateway = new DefaultDeviceGateway(
                bluetooth,
                new EncryptedSettingsStore(new MemoryPreferencesStore()),
                new NoOpLogger()
        );

        gateway.disconnect();

        assertTrue(bluetooth.disconnected);
    }

    @Test
    public void isConnectedDelegatesToBluetoothTransport() {
        FakeBluetoothTransport bluetooth = new FakeBluetoothTransport();
        bluetooth.connected = true;
        DefaultDeviceGateway gateway = new DefaultDeviceGateway(
                bluetooth,
                new EncryptedSettingsStore(new MemoryPreferencesStore()),
                new NoOpLogger()
        );

        assertTrue(gateway.isConnected());
    }

    private static final class FakeBluetoothTransport implements BluetoothTransport {
        boolean connected;
        byte[] bytes;
        String text;
        String charsetName;
        boolean disconnected;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void sendBytes(@NonNull byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void sendText(@NonNull String text, @NonNull String charsetName) {
            this.text = text;
            this.charsetName = charsetName;
        }

        @Override
        public void disconnect() {
            disconnected = true;
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
