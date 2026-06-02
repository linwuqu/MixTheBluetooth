package com.hc.mixthebluetooth.driver.implementation.bluetooth;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.driver.capability.BluetoothTransport;

import java.util.List;

public final class AndroidBluetoothTransport implements BluetoothTransport {
    private final HoldBluetooth holdBluetooth;

    public AndroidBluetoothTransport(@NonNull HoldBluetooth holdBluetooth) {
        this.holdBluetooth = holdBluetooth;
    }

    @Override
    public boolean isConnected() {
        return currentModule() != null;
    }

    @Override
    public void sendBytes(@NonNull byte[] bytes) {
        DeviceModule module = currentModule();
        if (module != null) {
            holdBluetooth.sendData(module, bytes.clone());
        }
    }

    @Override
    public void sendText(@NonNull String text, @NonNull String charsetName) {
        byte[] bytes = Analysis.getBytes(text, charsetName, false);
        sendBytes(bytes != null ? bytes : new byte[0]);
    }

    @Override
    public void disconnect() {
        DeviceModule module = currentModule();
        if (module != null) {
            holdBluetooth.tempDisconnect(module);
        }
    }

    private DeviceModule currentModule() {
        List<DeviceModule> modules = holdBluetooth.getConnectedArray();
        return modules == null || modules.isEmpty() ? null : modules.get(0);
    }
}
