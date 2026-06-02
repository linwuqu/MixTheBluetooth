package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;

public interface BluetoothTransport {
    boolean isConnected();

    void sendBytes(@NonNull byte[] bytes);

    void sendText(@NonNull String text, @NonNull String charsetName);

    void disconnect();
}
