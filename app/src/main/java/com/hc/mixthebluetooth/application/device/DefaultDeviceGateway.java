package com.hc.mixthebluetooth.application.device;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.device.DeviceGateway;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.api.persistence.SettingsStore;
import com.hc.mixthebluetooth.driver.capability.BluetoothTransport;

public final class DefaultDeviceGateway implements DeviceGateway {
    private static final String OWNER = "DefaultDeviceGateway";
    private static final String API_DEVICE = "DEVICE_GATEWAY";

    private final BluetoothTransport bluetoothTransport;
    private final SettingsStore settingsStore;
    private final AppLogger logger;

    public DefaultDeviceGateway(@NonNull BluetoothTransport bluetoothTransport,
                                @NonNull SettingsStore settingsStore,
                                @NonNull AppLogger logger) {
        this.bluetoothTransport = bluetoothTransport;
        this.settingsStore = settingsStore;
        this.logger = logger;
    }

    @Override
    public boolean isConnected() {
        return bluetoothTransport.isConnected();
    }

    @Override
    public void sendText(@NonNull String text) {
        try {
            bluetoothTransport.sendText(text, settingsStore.textEncoding());
        } catch (RuntimeException e) {
            logger.text(OWNER, API_DEVICE, "sendTextFailure", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            throw e;
        }
    }

    @Override
    public void sendBytes(@NonNull byte[] bytes) {
        try {
            bluetoothTransport.sendBytes(bytes);
        } catch (RuntimeException e) {
            logger.text(OWNER, API_DEVICE, "sendBytesFailure", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            throw e;
        }
    }

    @Override
    public void disconnect() {
        bluetoothTransport.disconnect();
    }
}
